package com.minimalist.finance.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val changelog: String,
    val downloadUrl: String,
    val backupDownloadUrl: String = ""
)

object AppUpdateManager {
    // 优先通过 Gitee 国内直连（免代理、零阻断、极速可用）
    private const val GITEE_URL = "https://gitee.com/ozr2117/MinimalistFinance/raw/main/version.json"
    // 备用通过 GitHub 官方主干
    private const val GITHUB_URL = "https://raw.githubusercontent.com/ozr2117-design/MinimalistFinance/main/version.json"

    suspend fun checkUpdate(currentVersionCode: Int): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        val jsonStr = fetchUrl(GITEE_URL) ?: fetchUrl(GITHUB_URL)
            ?: return@withContext Result.failure(Exception("无法连接版本更新服务器，请检查网络设置"))

        try {
            val json = JSONObject(jsonStr)
            val remoteCode = json.getInt("versionCode")
            val info = AppUpdateInfo(
                versionCode = remoteCode,
                versionName = json.getString("versionName"),
                releaseDate = json.optString("releaseDate", ""),
                changelog = json.optString("changelog", "有新版本发布，建议更新体验！"),
                downloadUrl = json.getString("downloadUrl"),
                backupDownloadUrl = json.optString("backupDownloadUrl", "")
            )
            if (remoteCode > currentVersionCode) {
                Result.success(info)
            } else {
                Result.success(null) // 当前已是最新版本
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        primaryUrl: String,
        backupUrl: String,
        targetFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val urls = listOfNotNull(
            primaryUrl.takeIf { it.isNotBlank() },
            backupUrl.takeIf { it.isNotBlank() }
        )

        var lastError: Exception? = null
        for (url in urls) {
            try {
                if (downloadToFile(url, targetFile, onProgress)) {
                    return@withContext Result.success(targetFile)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: Exception("下载更新安装包失败"))
    }

    private fun downloadToFile(
        urlStr: String,
        targetFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean {
        var currentUrl = urlStr
        var conn: HttpURLConnection? = null
        var redirects = 0

        while (redirects < 5) {
            val url = URL(currentUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Shubu-Android-App")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val newLocation = conn.getHeaderField("Location") ?: return false
                currentUrl = newLocation
                conn.disconnect()
                redirects++
            } else if (code == 200) {
                break
            } else {
                conn.disconnect()
                return false
            }
        }

        val connection = conn ?: return false
        val totalBytes = connection.contentLengthLong

        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) targetFile.delete()

        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloaded: Long = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    onProgress(downloaded, totalBytes)
                }
                output.flush()
            }
        }
        return targetFile.exists() && targetFile.length() > 0
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // Android 8.0+ 检查未知来源安装权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPermission = context.packageManager.canRequestPackageInstalls()
            if (!hasPermission) {
                val manageIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(manageIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(installIntent)
    }

    private fun fetchUrl(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Shubu-Android-App")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
