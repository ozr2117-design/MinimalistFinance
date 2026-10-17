package com.minimalist.finance.util

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
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
 */
data class CaptureSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val pkg: String,
    val channel: String, // "通知栏 (Notification)" 或 "界面窗口 (Window)"
    val status: String,  // 例如："✅ 成功入库: -¥11.40" 或 "⛔ 拦截: 命中订单列表Tab栏"
    val extractedAmount: Double? = null,
    val extractedMerchant: String? = null,
    val extractedAccount: String? = null,
    val rawTexts: List<String> = emptyList()
)

object AutoRecordDiagnosticLogger {
    private const val MAX_ENTRIES = 10
    private val buffer = ConcurrentLinkedDeque<CaptureSnapshot>()

    fun log(snapshot: CaptureSnapshot) {
        buffer.addFirst(snapshot)
        while (buffer.size > MAX_ENTRIES) {
            buffer.removeLast()
        }
    }

    fun getSnapshots(): List<CaptureSnapshot> {
        return buffer.toList()
    }

    fun getFormattedLogs(): String {
        if (buffer.isEmpty()) {
            return "【数簿自动记账诊断日志】\n暂无最近抓取事件记录（请在发生支付或在微信/淘宝/京东浏览后再试）。"
        }
        val sb = StringBuilder()
        sb.append("=== 数簿自动记账诊断日志快照 (共 ${buffer.size} 条) ===\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
        buffer.forEachIndexed { index, s ->
            sb.append("\n----------------------------------------\n")
            sb.append("【#${index + 1}】时间: ${sdf.format(Date(s.timestamp))}\n")
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
