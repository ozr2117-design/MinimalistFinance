package com.minimalist.finance.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.minimalist.finance.data.AppDatabase
import com.minimalist.finance.data.model.Record
import com.minimalist.finance.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 极简记账 - 无障碍全自动记账核心服务
 * 本地无感提取微信、支付宝、云闪付、美团等支付完成账单，写入本地 SQLite，100% 隐私安全
 */
class AutoRecordAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        var isServiceRunning = false
        private var lastRecordedTime = 0L
        private var lastRecordedAmount = 0.0

        /**
         * 提供给设置界面进行“模拟支付测试”的公共方法
         */
        fun simulateAutoRecord(
            context: Context,
            amount: Double,
            merchantName: String,
            accountName: String,
            onSuccess: (String) -> Unit
        ) {
            val ioScope = CoroutineScope(Dispatchers.IO)
            ioScope.launch {
                val db = AppDatabase.getDatabase(context, ioScope)
                val categoryName = classifyCategory(merchantName)
                val record = Record(
                    bookId = 1L, // 默认记录到日常消费主账本
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    categoryName = categoryName,
                    remark = "自动记账: $merchantName",
                    tag = "自动记账",
                    timestamp = System.currentTimeMillis()
                )
                db.recordDao().insertRecord(record)
                Handler(Looper.getMainLooper()).post {
                    onSuccess(categoryName)
                }
            }
        }

        /**
         * 本地毫秒级智能分类映射
         */
        fun classifyCategory(text: String): String {
            val lower = text.lowercase()
            return when {
                lower.contains("餐") || lower.contains("饭") || lower.contains("面") ||
                lower.contains("茶") || lower.contains("咖啡") || lower.contains("麦当劳") ||
                lower.contains("肯德基") || lower.contains("外卖") || lower.contains("美团") ||
                lower.contains("饿了么") || lower.contains("超市") || lower.contains("便利") ||
                lower.contains("生鲜") || lower.contains("果蔬") || lower.contains("小吃") ||
                lower.contains("喜茶") || lower.contains("星巴克") || lower.contains("食品") ||
                lower.contains("便利蜂") -> "食"

                lower.contains("打车") || lower.contains("滴滴") || lower.contains("高德") ||
                lower.contains("地铁") || lower.contains("公交") || lower.contains("加油") ||
                lower.contains("停车") || lower.contains("铁路") || lower.contains("12306") ||
                lower.contains("机票") || lower.contains("出行") || lower.contains("单车") ||
                lower.contains("高速") -> "行"

                lower.contains("衣") || lower.contains("服装") || lower.contains("优衣库") ||
                lower.contains("鞋") || lower.contains("专柜") || lower.contains("淘宝服饰") -> "衣"

                lower.contains("房租") || lower.contains("物业") || lower.contains("水电") ||
                lower.contains("电费") || lower.contains("水费") || lower.contains("燃气") ||
                lower.contains("宽带") || lower.contains("酒店") || lower.contains("民宿") -> "住"

                else -> "其他"
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }

    override fun onInterrupt() {
        isServiceRunning = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // 仅处理已适配的支付类应用
        if (!isPaymentApp(pkg)) return

        val rootNode = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectTexts(rootNode, texts)

        parseAndRecord(pkg, texts)
    }

    private fun isPaymentApp(pkg: String): Boolean {
        return pkg in listOf(
            "com.tencent.mm",                 // 微信
            "com.eg.android.AlipayGphone",    // 支付宝
            "com.unionpay",                   // 云闪付
            "com.sankuai.meituan",            // 美团
            "com.sankuai.meituan.takeoutnew", // 美团外卖
            "com.jingdong.app.mall",          // 京东
            "me.ele"                          // 饿了么
        )
    }

    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        node.text?.let {
            val str = it.toString().trim()
            if (str.isNotEmpty()) list.add(str)
        }
        node.contentDescription?.let {
            val str = it.toString().trim()
            if (str.isNotEmpty()) list.add(str)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, list)
        }
    }

    private fun parseAndRecord(pkg: String, texts: List<String>) {
        val joined = texts.joinToString(" ")
        // 判定是否是支付成功或完成界面
        val isPaymentSuccess = joined.contains("支付成功") ||
                joined.contains("付款成功") ||
                joined.contains("交易成功") ||
                joined.contains("支付已完成") ||
                joined.contains("付款凭证") ||
                joined.contains("扫码付款成功") ||
                joined.contains("成功付款")

        if (!isPaymentSuccess) return

        // 提取金额
        val amount = extractAmount(texts) ?: return
        if (amount <= 0.0) return

        val now = System.currentTimeMillis()
        // 5秒内同金额防重复记录
        if (now - lastRecordedTime < 5000 && lastRecordedAmount == amount) {
            return
        }

        val accountName = when (pkg) {
            "com.tencent.mm" -> "微信"
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.unionpay" -> "银行卡"
            else -> "微信"
        }

        val merchantName = extractMerchant(texts)
        val catName = classifyCategory("$merchantName $joined")

        lastRecordedTime = now
        lastRecordedAmount = amount

        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext, scope)
            val record = Record(
                bookId = 1L,
                type = TransactionType.EXPENSE,
                amount = amount,
                categoryName = catName,
                remark = if (merchantName.isNotEmpty()) "自动记账: $merchantName" else "自动记账: 快捷支付",
                tag = "自动记账",
                timestamp = now
            )
            db.recordDao().insertRecord(record)

            mainHandler.post {
                Toast.makeText(
                    applicationContext,
                    "⚡ 极简记账: 已自动记录 ¥${String.format(java.util.Locale.CHINA, "%.2f", amount)} ($catName · $accountName)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun extractAmount(texts: List<String>): Double? {
        val regex = Regex("""[¥￥]\s*([0-9]+\.?[0-9]*)""")
        for (t in texts) {
            val match = regex.find(t)
            if (match != null) {
                val value = match.groupValues[1].toDoubleOrNull()
                if (value != null && value > 0) return value
            }
        }
        for (t in texts) {
            if (t.matches(Regex("""^[0-9]+\.[0-9]{2}$"""))) {
                val value = t.toDoubleOrNull()
                if (value != null && value > 0) return value
            }
        }
        return null
    }

    private fun extractMerchant(texts: List<String>): String {
        for (i in texts.indices) {
            val t = texts[i]
            if (t == "商户名称" || t == "收款方" || t == "商品" || t == "收款人") {
                if (i + 1 < texts.size) return texts[i + 1]
            }
        }
        return ""
    }
}
