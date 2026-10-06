import subprocess
import json
import urllib.request
import time
import os
import zipfile
import shutil

def get_github_token():
    p = subprocess.Popen(
        ['git', 'credential', 'fill'],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    out, _ = p.communicate(input="protocol=https\nhost=github.com\n\n")
    token = None
    for line in out.splitlines():
        if line.startswith("password="):
            token = line.split("=", 1)[1]
    return token

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

token = get_github_token()
if not token:
    print("Could not retrieve GitHub token!")
    exit(1)

headers = {
    "Authorization": f"Bearer {token}",
    "Accept": "application/vnd.github.v3+json",
    "User-Agent": "MinimalistFinance-CI"
}

repo = "ozr2117-design/MinimalistFinance"
url = f"https://api.github.com/repos/{repo}/actions/runs"

print("Waiting for GitHub Actions workflow to start...")
time.sleep(3)

opener = urllib.request.build_opener(NoRedirect)

for attempt in range(60): # up to 10 minutes
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            runs = data.get("workflow_runs", [])
            if runs:
                latest = runs[0]
                status = latest["status"]
                conclusion = latest.get("conclusion")
                commit_msg = latest["head_commit"]["message"].split("\n")[0]
                print(f"[{attempt+1}] Run #{latest['run_number']} ({commit_msg[:30]}...): Status={status}, Conclusion={conclusion}")
                
                if status == "completed":
                    if conclusion == "success":
                        print("Build succeeded! Downloading artifacts...")
                        artifacts_url = latest["artifacts_url"]
                        req_art = urllib.request.Request(artifacts_url, headers=headers)
                        with urllib.request.urlopen(req_art) as resp_art:
                            art_data = json.loads(resp_art.read().decode())
                            artifacts = art_data.get("artifacts", [])
                            if artifacts:
                                download_url = artifacts[0]["archive_download_url"]
                                print(f"Downloading from {download_url}...")
                                try:
                                    opener.open(urllib.request.Request(download_url, headers=headers))
                                except urllib.error.HTTPError as e:
                                    if e.code in (301, 302, 307):
                                        blob_url = e.headers['Location']
                                        with urllib.request.urlopen(blob_url) as resp_dl:
                                            zip_path = "artifact.zip"
                                            with open(zip_path, "wb") as f_out:
                                                f_out.write(resp_dl.read())
                                            print("Artifact downloaded, unzipping...")
                                            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                                                zip_ref.extractall(".")
                                            if os.path.exists(zip_path):
                                                os.remove(zip_path)
                                            if os.path.exists("app-debug.apk"):
                                                shutil.copyfile("app-debug.apk", "极简记账-v1.0.0.apk")
                                                shutil.copyfile("app-debug.apk", "数簿-v1.0.0.apk")
                                                print("Successfully updated app-debug.apk, 极简记账-v1.0.0.apk and 数簿-v1.0.0.apk!")
                                                exit(0)
                    else:
                        print(f"Build failed with conclusion: {conclusion}")
                        exit(1)
    except Exception as e:
        print(f"Error querying GitHub API: {e}")
    time.sleep(10)

print("Timeout waiting for build to complete!")
exit(1)
