package com.minimalist.finance.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 自动记账全链路诊断快照日志管理器
 *
 * 用于在遇到漏记、误记或重复记账时，一键导出最近发生的无障碍与通知栏抓取事件，
 * 记录原始文本树、判定原因与提取结果，供开发者极速排查与单测回归。
 * 支持本地磁盘 JSON 镜像持久化，彻底防止应用被系统回收导致日志丢失。
 */
data class CaptureSnapshot(
    var timestamp: Long = System.currentTimeMillis(),
    val pkg: String,
    val channel: String, // "通知栏 (Notification)" 或 "界面窗口 (Window)"
    val status: String,  // 例如："✅ 成功入库: -¥11.40" 或 "⛔ 拦截: 命中订单列表Tab栏"
    val extractedAmount: Double? = null,
    val extractedMerchant: String? = null,
    val extractedAccount: String? = null,
    val rawTexts: List<String> = emptyList(),
    var repeatCount: Int = 1
)

object AutoRecordDiagnosticLogger {
    private const val MAX_ENTRIES = 50
    private const val CACHE_FILE = "autorecord_diagnostic_snapshots.json"
    private val buffer = ConcurrentLinkedDeque<CaptureSnapshot>()
    private var appContext: Context? = null
    private var isLoadedFromDisk = false

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        if (!isLoadedFromDisk) {
            loadFromDisk()
        }
    }

    @Synchronized
    private fun loadFromDisk() {
        val ctx = appContext ?: return
        if (isLoadedFromDisk) return
        isLoadedFromDisk = true
        try {
            val file = File(ctx.filesDir, CACHE_FILE)
            if (!file.exists()) return
            val jsonStr = file.readText(Charsets.UTF_8)
            if (jsonStr.isBlank()) return
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<CaptureSnapshot>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rawArray = obj.optJSONArray("rawTexts")
                val rawTexts = mutableListOf<String>()
                if (rawArray != null) {
                    for (j in 0 until rawArray.length()) {
                        rawTexts.add(rawArray.getString(j))
                    }
                }
                list.add(
                    CaptureSnapshot(
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        pkg = obj.optString("pkg", ""),
                        channel = obj.optString("channel", ""),
                        status = obj.optString("status", ""),
                        extractedAmount = if (obj.has("extractedAmount")) obj.optDouble("extractedAmount") else null,
                        extractedMerchant = if (obj.has("extractedMerchant")) obj.optString("extractedMerchant") else null,
                        extractedAccount = if (obj.has("extractedAccount")) obj.optString("extractedAccount") else null,
                        rawTexts = rawTexts,
                        repeatCount = obj.optInt("repeatCount", 1)
                    )
                )
            }
            buffer.clear()
            list.forEach { buffer.addLast(it) }
        } catch (_: Exception) {}
    }

    private fun persistToDiskAsync() {
        val ctx = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshots = buffer.toList()
                val jsonArray = JSONArray()
                snapshots.forEach { s ->
                    val obj = JSONObject().apply {
                        put("timestamp", s.timestamp)
                        put("pkg", s.pkg)
                        put("channel", s.channel)
                        put("status", s.status)
                        s.extractedAmount?.let { put("extractedAmount", it) }
                        s.extractedMerchant?.let { put("extractedMerchant", it) }
                        s.extractedAccount?.let { put("extractedAccount", it) }
                        put("repeatCount", s.repeatCount)
                        val rawArray = JSONArray()
                        s.rawTexts.take(40).forEach { rawArray.put(it) }
                        put("rawTexts", rawArray)
                    }
                    jsonArray.put(obj)
                }
                val file = File(ctx.filesDir, CACHE_FILE)
                file.writeText(jsonArray.toString(), Charsets.UTF_8)
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun log(snapshot: CaptureSnapshot) {
        if (!isLoadedFromDisk && appContext != null) {
            loadFromDisk()
        }
        val first = buffer.peekFirst()
        // 相邻拦截事件去重：若是同包名同拦截原因、在 5 秒内连续派发的滑屏/重绘事件，合并计数，绝不挤爆缓冲区！
        if (first != null && !snapshot.status.startsWith("✅") &&
            first.pkg == snapshot.pkg &&
            first.status == snapshot.status &&
            first.channel == snapshot.channel &&
            snapshot.timestamp - first.timestamp < 5000L
        ) {
            first.repeatCount += 1
            first.timestamp = snapshot.timestamp
            persistToDiskAsync()
            return
        }

        buffer.addFirst(snapshot)
        while (buffer.size > MAX_ENTRIES) {
            buffer.removeLast()
        }
        persistToDiskAsync()
    }

    fun getSnapshots(): List<CaptureSnapshot> {
        if (!isLoadedFromDisk && appContext != null) {
            loadFromDisk()
        }
        return buffer.toList()
    }

    fun getFormattedLogs(): String {
        if (!isLoadedFromDisk && appContext != null) {
            loadFromDisk()
        }
        if (buffer.isEmpty()) {
            return "【数簿自动记账诊断日志】\n暂无最近抓取事件记录（请在发生支付或在微信/淘宝/京东浏览后再试）。"
        }
        val sb = StringBuilder()
        sb.append("=== 数簿自动记账诊断日志快照 (共 ${buffer.size} 条) ===\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
        buffer.forEachIndexed { index, s ->
            sb.append("\n----------------------------------------\n")
            sb.append("【#${index + 1}】时间: ${sdf.format(Date(s.timestamp))}${if (s.repeatCount > 1) " (连续触发 ${s.repeatCount} 次已合并)" else ""}\n")
            sb.append("通道: ${s.channel} | 应用: ${s.pkg}\n")
            sb.append("判定: ${s.status}\n")
            if (s.extractedAmount != null) {
                sb.append("提取: 金额=¥${s.extractedAmount}, 商户=${s.extractedMerchant ?: "无"}, 账户=${s.extractedAccount ?: "无"}\n")
            }
            sb.append("页面原始文本 (${s.rawTexts.size} 个节点):\n")
            val previewTexts = s.rawTexts.take(40)
            previewTexts.forEachIndexed { i, t ->
                sb.append("  [$i] \"$t\"\n")
            }
            if (s.rawTexts.size > 40) {
                sb.append("  ... 剩余 ${s.rawTexts.size - 40} 个节点已省略 ...\n")
            }
        }
        sb.append("\n========================================\n")
        return sb.toString()
    }

    /**
     * 一键将诊断快照静默上报至 GitHub Issue (通过 Cloudflare Worker 安全转发)
     */
    fun uploadLogs(onResult: (success: Boolean, message: String) -> Unit) {
        val logs = getFormattedLogs()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val title = "【自动记账诊断】${sdf.format(Date())}"
        val mainHandler = Handler(Looper.getMainLooper())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://shubu-report.ozr2117.workers.dev")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val jsonPayload = JSONObject().apply {
                    put("title", title)
                    put("content", logs)
                }.toString()

                conn.outputStream.use { os ->
                    os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                val responseText = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                }

                val resObj = JSONObject(responseText)
                if (resObj.optBoolean("success", false)) {
                    val issueNum = resObj.optInt("issue_number", 0)
                    mainHandler.post {
                        onResult(true, "已成功上报至 GitHub Issue #${if (issueNum > 0) issueNum else ""}")
                    }
                } else {
                    val err = resObj.optString("error", "上报失败")
                    mainHandler.post {
                        onResult(false, "上报失败: $err")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    onResult(false, "网络异常: ${e.localizedMessage ?: "请检查网络"}")
                }
            }
        }
    }
}
