package com.minimalist.finance.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
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
import java.util.concurrent.ConcurrentHashMap

/**
 * 极简记账 - 无障碍全自动记账核心引擎 (V2 增强防重与防漏版)
 *
 * 核心升级：
 * 1. 【双重防重熔断】：60秒内存防重 + 数据库底层同金额查重拦截，彻底解决停顿页面或广告刷新引发的重复记账；
 * 2. 【通知+界面双通道】：新增对微信/支付宝系统通知的监听，被收银机反扫付款码时即使没弹全屏也能零遗漏记录；
 * 3. 【真实商户智能识别】：升级启发式商户提取算法，告别“快捷支付”默认词，精准捕获店名；
 * 4. 【主页过滤防误触】：自动识别并过滤微信聊天主列表，避免误触。
 */
class AutoRecordAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        var isServiceRunning = false

        // 内存级 60 秒强力防重缓存：key -> 触发时间戳
        private val dedupeCache = ConcurrentHashMap<String, Long>()
        private var lastGlobalTime = 0L
        private var lastGlobalAmount = 0.0

        /**
         * 模拟测试公共接口 (支持支出与收入)
         */
        fun simulateAutoRecord(
            context: Context,
            amount: Double,
            merchantName: String,
            accountName: String,
            type: TransactionType = TransactionType.EXPENSE,
            onSuccess: (String) -> Unit
        ) {
            val ioScope = CoroutineScope(Dispatchers.IO)
            ioScope.launch {
                val db = AppDatabase.getDatabase(context, ioScope)
                val categoryName = classifyCategory(merchantName, type)
                val record = Record(
                    bookId = 1L,
                    type = type,
                    amount = amount,
                    categoryName = categoryName,
                    accountName = accountName,
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
         * 智能分类映射 (支出与收入分别映射)
         */
        fun classifyCategory(text: String, type: TransactionType = TransactionType.EXPENSE): String {
            val lower = text.lowercase()
            if (type == TransactionType.INCOME) {
                return when {
                    lower.contains("红包") -> "收红包"
                    lower.contains("工资") || lower.contains("薪水") || lower.contains("月薪") -> "工资"
                    lower.contains("奖金") || lower.contains("年终奖") -> "奖金"
                    lower.contains("生活费") -> "生活费"
                    lower.contains("外快") || lower.contains("兼职") -> "外快"
                    else -> "其它"
                }
            }
            return when {
                lower.contains("餐") || lower.contains("饭") || lower.contains("面") ||
                lower.contains("茶") || lower.contains("咖啡") || lower.contains("麦当劳") ||
                lower.contains("肯德基") || lower.contains("外卖") || lower.contains("美团") ||
                lower.contains("饿了么") || lower.contains("超市") || lower.contains("便利") ||
                lower.contains("生鲜") || lower.contains("果蔬") || lower.contains("小吃") ||
                lower.contains("喜茶") || lower.contains("星巴克") || lower.contains("食品") ||
                lower.contains("汉堡") || lower.contains("便利蜂") || lower.contains("全家") ||
                lower.contains("罗森") || lower.contains("菜市场") || lower.contains("饮") -> "食"

                lower.contains("打车") || lower.contains("滴滴") || lower.contains("高德") ||
                lower.contains("地铁") || lower.contains("公交") || lower.contains("加油") ||
                lower.contains("停车") || lower.contains("铁路") || lower.contains("12306") ||
                lower.contains("机票") || lower.contains("出行") || lower.contains("单车") ||
                lower.contains("高速") || lower.contains("中国石油") || lower.contains("中国石化") -> "行"

                lower.contains("衣") || lower.contains("服装") || lower.contains("优衣库") ||
                lower.contains("鞋") || lower.contains("专柜") || lower.contains("淘宝服饰") ||
                lower.contains("zara") || lower.contains("耐克") || lower.contains("阿迪") -> "衣"

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

        if (!isPaymentApp(pkg)) return

        when (event.eventType) {
            // 通道 1：通知栏捕获 (收银机反扫付款码时微信推送的付款通知)
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotification(pkg, event)
            }

            // 通道 2：页面内容变化与状态变化 (用户扫商家码后呈现的支付成功全屏页)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindow(pkg, event)
            }
        }
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

    // ====== 通道 1：通知解析逻辑 ======
    private fun handleNotification(pkg: String, event: AccessibilityEvent) {
        val parcelable = event.parcelableData
        if (parcelable !is Notification) return

        val extras = parcelable.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val ticker = parcelable.tickerText?.toString() ?: ""

        val combined = "$title $text $bigText $subText $ticker".trim()
        if (combined.isEmpty()) return

        // 识别微信或支付宝的通知（支出或收入）
        var notifType: TransactionType? = null
        if (pkg == "com.tencent.mm") {
            if (combined.contains("收款到账") || combined.contains("已收款") || combined.contains("收到转账") ||
                combined.contains("资金已存入零钱") || combined.contains("微信支付收款") || combined.contains("二维码收款到账") ||
                combined.contains("微信收款助手")) {
                notifType = TransactionType.INCOME
            } else if (title.contains("微信支付") || combined.contains("微信支付凭证") ||
                combined.contains("你有一笔微信支付支出") || combined.contains("成功付款") ||
                combined.contains("支付成功")) {
                notifType = TransactionType.EXPENSE
            }
        } else if (pkg == "com.eg.android.AlipayGphone") {
            if (combined.contains("收到转账") || combined.contains("成功收款") || combined.contains("收款到账") ||
                combined.contains("支付宝收款到账") || combined.contains("收到一笔款项")) {
                notifType = TransactionType.INCOME
            } else if (title.contains("支付宝") || combined.contains("成功付款") ||
                combined.contains("交易提醒") || combined.contains("支付成功")) {
                notifType = TransactionType.EXPENSE
            }
        } else {
            if (combined.contains("收款成功") || combined.contains("收款到账")) {
                notifType = TransactionType.INCOME
            } else if (combined.contains("支付成功") || combined.contains("扣款成功") || combined.contains("付款成功")) {
                notifType = TransactionType.EXPENSE
            }
        }

        if (notifType == null) return

        val amount = extractAmountFromSingleText(combined) ?: return
        if (amount <= 0.0) return

        val partyName = extractMerchantFromNotification(combined)
        saveAutoRecord(pkg, amount, partyName, combined, notifType)
    }

    // ====== 通道 2：页面节点解析逻辑 ======
    private fun handleWindow(pkg: String, event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectTexts(rootNode, texts)
        if (texts.isEmpty()) return

        val joined = texts.joinToString(" ")

        // 防误触：微信主界面（包含“通讯录”、“发现”、“我”导航栏）坚决不触碰
        if (pkg == "com.tencent.mm") {
            if (texts.contains("通讯录") && texts.contains("发现") && texts.contains("我")) {
                return
            }
        }

        // 收入语义判定 (微信转账收款、存入零钱、二维码收款等)
        val isIncome = joined.contains("你已收款") ||
                joined.contains("资金已存入零钱") ||
                joined.contains("已存入零钱") ||
                joined.contains("已存入零钱通") ||
                joined.contains("收到转账") ||
                joined.contains("收款到账") ||
                joined.contains("二维码收款到账") ||
                joined.contains("微信收款助手") ||
                (joined.contains("收款成功") && !joined.contains("向商家付款")) ||
                joined.contains("向你转账") ||
                joined.contains("成功收款") ||
                (joined.contains("微信转账") && joined.contains("已收款")) ||
                (joined.contains("转账时间") && joined.contains("收款时间"))

        // 支付成功语义判定 (支出)
        val isExpense = joined.contains("支付成功") ||
                joined.contains("付款成功") ||
                joined.contains("交易成功") ||
                joined.contains("支付已完成") ||
                joined.contains("付款凭证") ||
                joined.contains("微信支付凭证") ||
                joined.contains("扫码付款成功") ||
                joined.contains("成功付款") ||
                joined.contains("向商家付款成功") ||
                joined.contains("支付明细")

        val targetType = when {
            isIncome -> TransactionType.INCOME
            isExpense -> TransactionType.EXPENSE
            else -> return
        }

        // 提取金额
        val amount = extractAmountFromList(texts) ?: return
        if (amount <= 0.0) return

        val partyName = if (targetType == TransactionType.INCOME) {
            extractIncomeSourceFromList(texts)
        } else {
            extractMerchantFromList(texts)
        }
        saveAutoRecord(pkg, amount, partyName, joined, targetType)
    }

    /**
     * 统一安全入库逻辑（核心双重熔断防重机制）
     */
    private fun saveAutoRecord(
        pkg: String,
        amount: Double,
        partyName: String,
        rawContext: String,
        type: TransactionType = TransactionType.EXPENSE
    ) {
        val now = System.currentTimeMillis()

        // 1. 内存级防重检查：同一类型同一金额在 60 秒内严格只记一次
        val cacheKey = "$pkg-${type.name}-${String.format(java.util.Locale.US, "%.2f", amount)}"
        val lastSeen = dedupeCache[cacheKey] ?: 0L
        if (now - lastSeen < 60_000L) {
            // 内存命中，直接熔断拦截
            return
        }

        // 全局时间防重：15秒内同金额同类型坚决不触发
        if (now - lastGlobalTime < 15_000L && Math.abs(lastGlobalAmount - amount) < 0.001) {
            return
        }

        val accountName = when (pkg) {
            "com.tencent.mm" -> "微信"
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.unionpay" -> "银行卡"
            else -> "微信"
        }

        val displayName = if (partyName.isNotBlank() && !isSystemWord(partyName)) {
            partyName
        } else {
            if (type == TransactionType.INCOME) "微信转账收款" else "扫码商户"
        }

        val catName = classifyCategory("$displayName $rawContext", type)

        // 刷新内存去重戳
        dedupeCache[cacheKey] = now
        lastGlobalTime = now
        lastGlobalAmount = amount

        // 清理过期缓存
        cleanExpiredCache(now)

        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext, scope)

            // 2. 数据库底层熔断防重：检查数据库内近 60 秒是否存在相同金额相同类型的记账
            val recentDupCount = db.recordDao().getRecentDuplicateCount(amount, type, now - 60_000L)
            if (recentDupCount > 0) {
                // 数据库底层已存在同金额记录，放弃重复插入！
                return@launch
            }

            val record = Record(
                bookId = 1L, // 统一记入日常主账本
                type = type,
                amount = amount,
                categoryName = catName,
                accountName = accountName,
                remark = "自动记账: $displayName",
                tag = "自动记账",
                timestamp = now
            )
            db.recordDao().insertRecord(record)

            mainHandler.post {
                val prefix = if (type == TransactionType.INCOME) "收入 +" else "支出 -"
                Toast.makeText(
                    applicationContext,
                    "⚡ 数簿: 自动记录$prefix¥${String.format(java.util.Locale.CHINA, "%.2f", amount)} ($catName · $accountName · $displayName)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun cleanExpiredCache(now: Long) {
        val iterator = dedupeCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > 120_000L) {
                iterator.remove()
            }
        }
    }

    private fun isSystemWord(word: String): Boolean {
        return word in listOf(
            "微信支付", "支付宝", "支付成功", "付款成功", "交易成功", "完成", "收款成功", "账单详情", "人民币",
            "零钱余额", "转账时间", "收款时间", "你已收款", "资金已存入零钱", "你已收款，资金已存入零钱"
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

    // ====== 增强金额提取算法 ======
    private fun extractAmountFromList(texts: List<String>): Double? {
        // 1. 带有货币符号的字符串 (如 "¥ 1.20", "¥1.20", "￥12.50")
        val regexSymbol = Regex("""[¥￥]\s*([0-9]+\.?[0-9]*)""")
        for (t in texts) {
            val m = regexSymbol.find(t)
            if (m != null) {
                val v = m.groupValues[1].toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }
        // 2. 货币符号与数字分立在相邻节点 (如 node 1: "¥", node 2: "1.20")
        for (i in texts.indices) {
            val t = texts[i].trim()
            if ((t == "¥" || t == "￥") && i + 1 < texts.size) {
                val nextStr = texts[i + 1].trim()
                val v = nextStr.toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }
        // 3. 带有"元"的文本 (如 "1.20元", "12元")
        val regexYuan = Regex("""([0-9]+\.?[0-9]*)\s*元""")
        for (t in texts) {
            val m = regexYuan.find(t)
            if (m != null) {
                val v = m.groupValues[1].toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }
        // 4. 独立纯金额节点 (如 "1.20", "12.50")
        for (t in texts) {
            if (t.matches(Regex("""^[0-9]+\.[0-9]{2}$"""))) {
                val v = t.toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }
        return null
    }

    private fun extractAmountFromSingleText(text: String): Double? {
        val regex = Regex("""[¥￥]\s*([0-9]+\.?[0-9]*)""")
        val m = regex.find(text)
        if (m != null) {
            val v = m.groupValues[1].toDoubleOrNull()
            if (v != null && v > 0) return v
        }
        val regexYuan = Regex("""([0-9]+\.?[0-9]*)\s*元""")
        val m2 = regexYuan.find(text)
        if (m2 != null) {
            val v = m2.groupValues[1].toDoubleOrNull()
            if (v != null && v > 0) return v
        }
        return null
    }

    // ====== 增强收款来源人名提取算法 ======
    private fun extractIncomeSourceFromList(texts: List<String>): String {
        val targetLabels = listOf("付款方", "付款人", "转账人", "付款人全称", "来自")
        for (i in texts.indices) {
            val t = texts[i].trim()
            if (targetLabels.any { t.contains(it) }) {
                if (i + 1 < texts.size) {
                    val candidate = texts[i + 1].trim()
                    if (candidate.isNotBlank() && !targetLabels.any { candidate.contains(it) } &&
                        !candidate.startsWith("¥") && !candidate.startsWith("￥")) {
                        return candidate
                    }
                }
            }
        }
        return ""
    }

    // ====== 增强商户名提取算法 ======
    private fun extractMerchantFromList(texts: List<String>): String {
        val targetLabels = listOf("收款商家", "收款方全称", "商户名称", "收款方", "商品", "收款人", "商户全称", "交易对象")
        for (i in texts.indices) {
            val t = texts[i].trim()
            if (targetLabels.any { t.contains(it) }) {
                if (i + 1 < texts.size) {
                    val candidate = texts[i + 1].trim()
                    if (candidate.isNotBlank() && !targetLabels.any { candidate.contains(it) } &&
                        !candidate.startsWith("¥") && !candidate.startsWith("￥")) {
                        return candidate
                    }
                }
            }
        }

        // 启发式：紧邻金额上一项往往就是加粗的商户名
        for (i in texts.indices) {
            val t = texts[i].trim()
            if (t.matches(Regex("""^[¥￥]\s*[0-9]+(\.[0-9]{1,2})?$""")) || t.matches(Regex("""^[0-9]+\.[0-9]{2}$"""))) {
                if (i > 0) {
                    val prev = texts[i - 1].trim()
                    if (prev.length in 2..30 && !isSystemWord(prev)) {
                        return prev
                    }
                }
            }
        }
        return ""
    }

    private fun extractMerchantFromNotification(text: String): String {
        val m1 = Regex("""向\s*(.*?)\s*(付款|转账|支付)""").find(text)
        if (m1 != null) {
            val name = m1.groupValues[1].trim()
            if (name.isNotEmpty() && !isSystemWord(name)) return name
        }
        val m2 = Regex("""(收款方|收款商家|商户|收款人)[:：]\s*(.*?)\s*([￥¥0-9\s]|$)""").find(text)
        if (m2 != null) {
            val name = m2.groupValues[2].trim()
            if (name.isNotEmpty() && !isSystemWord(name)) return name
        }
        return ""
    }
}
