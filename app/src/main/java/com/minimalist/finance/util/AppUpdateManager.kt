package com.minimalist.finance.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
