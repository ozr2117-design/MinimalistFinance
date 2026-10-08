package com.minimalist.finance.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.minimalist.finance.MainActivity
import com.minimalist.finance.R
import com.minimalist.finance.data.AppDatabase
import com.minimalist.finance.data.model.Record
import com.minimalist.finance.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 极简记账 - 无障碍全自动记账核心引擎 (V3 深度强化保活与停车缴费防漏版)
 *
 * 核心升级：
 * 1. 【常驻前台保活】：新增前台低扰动通知与服务心跳检测，彻底根除国产手机后台静默休眠引发的“假死”；
 * 2. 【微信停车费/凭证全覆盖】：专门优化微信支付凭证、神州路通/捷停车等停车缴费场景的“-4.00”负数金额与商户提取；
 * 3. 【多窗口聚合穿透】：支持 flagRetrieveInteractiveWindows，穿透微信弹窗、H5、小程序与账单详情页；
 * 4. 【零钱通细化入账】：智能识别“零钱通”与“微信零钱”，账户归集更清晰；
 * 5. 【双重防重熔断】：60秒内存防重 + 数据库底层同金额查重拦截。
 */
class AutoRecordAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        var isServiceRunning = false
        var lastRecordLog: String = ""
        var lastRecordTime: Long = 0L

        // 内存级 10 分钟强力防重缓存：key -> 触发时间戳
        private val dedupeCache = ConcurrentHashMap<String, Long>()
        // 订单交易单号强力防重缓存：orderId -> 触发时间戳
        private val orderDedupeCache = ConcurrentHashMap<String, Long>()
        private var lastGlobalTime = 0L
        private var lastGlobalAmount = 0.0


        private const val CHANNEL_ID = "auto_record_service_channel"
        private const val NOTIFICATION_ID = 8888

        fun isEnabled(context: Context): Boolean {
            val sp = context.getSharedPreferences("auto_record_prefs", Context.MODE_PRIVATE)
            return sp.getBoolean("auto_record_enabled", true)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            val sp = context.getSharedPreferences("auto_record_prefs", Context.MODE_PRIVATE)
            sp.edit().putBoolean("auto_record_enabled", enabled).apply()
        }

        fun isKeepAliveEnabled(context: Context): Boolean {
            val sp = context.getSharedPreferences("auto_record_prefs", Context.MODE_PRIVATE)
            return sp.getBoolean("auto_record_keep_alive", true)
        }

        fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
            val sp = context.getSharedPreferences("auto_record_prefs", Context.MODE_PRIVATE)
            sp.edit().putBoolean("auto_record_keep_alive", enabled).apply()
            if (enabled) {
                updateKeepAliveNotification(context)
            } else {
                cancelKeepAliveNotification(context)
            }
        }

        fun updateKeepAliveNotification(context: Context) {
            if (!isKeepAliveEnabled(context)) {
                cancelKeepAliveNotification(context)
                return
            }
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "数簿自动记账守护服务",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "常驻通知用于防止后台记账服务被手机省电策略意外休眠"
                        setShowBadge(false)
                        enableVibration(false)
                        setSound(null, null)
                    }
                    nm.createNotificationChannel(channel)
                }

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("数簿已就绪 · 自动记账守护中")
                    .setContentText("实时监测微信/支付宝支付成功与停车账单凭证")
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

                nm.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }

        fun cancelKeepAliveNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
        }


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
                lastRecordLog = "${if (type == TransactionType.INCOME) "收入 +" else "支出 -"}¥${String.format(java.util.Locale.CHINA, "%.2f", amount)} ($categoryName · $accountName · $merchantName)"
                lastRecordTime = System.currentTimeMillis()
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
                lower.contains("停车") || lower.contains("泊车") || lower.contains("神州路通") ||
                lower.contains("路通") || lower.contains("捷停车") || lower.contains("停简单") ||
                lower.contains("etcp") || lower.contains("车牌") || lower.contains("车费") ||
                lower.contains("道闸") || lower.contains("铁路") || lower.contains("12306") ||
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

        // 动态提升无障碍配置，覆盖全屏、对话框、H5 与非重要节点
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 50
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            serviceInfo = info
        } catch (_: Exception) {}

        updateKeepAliveNotification(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        cancelKeepAliveNotification(this)
    }

    override fun onInterrupt() {
        isServiceRunning = false
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isEnabled(this)) return

        val pkg = event.packageName?.toString() ?: return
        if (!isPaymentApp(pkg)) return

        when (event.eventType) {
            // 通道 1：通知栏捕获 (微信/支付宝推送的付款或停车凭证通知)
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotification(pkg, event)
            }

            // 通道 2：页面内容变化与状态变化 (微信支付凭证/付款成功全屏页/停车小程序)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindow(pkg, event)
                // 若为状态初次切换，微延时 300ms 兜底重试一次（解决微信内嵌 Web 渲染延迟）
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    mainHandler.postDelayed({
                        handleWindow(pkg, null)
                    }, 300L)
                }
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

        // 识别微信、支付宝、云闪付的通知（支出或收入）
        var notifType: TransactionType? = null
        if (pkg == "com.tencent.mm") {
            if (combined.contains("收款到账") || combined.contains("已收款") || combined.contains("收到转账") ||
                combined.contains("资金已存入零钱") || combined.contains("微信支付收款") || combined.contains("二维码收款到账") ||
                combined.contains("微信收款助手")) {
                notifType = TransactionType.INCOME
            } else if (title.contains("微信支付") || combined.contains("微信支付凭证") ||
                combined.contains("你有一笔微信支付支出") || combined.contains("成功付款") ||
                combined.contains("支付成功") || combined.contains("已扣款") ||
                combined.contains("特约商户") || combined.contains("完成支付") ||
                combined.contains("信用卡") || combined.contains("消费支出") ||
                combined.contains("支付停车费") || combined.contains("停车缴费")) {
                notifType = TransactionType.EXPENSE
            }
        } else if (pkg == "com.eg.android.AlipayGphone") {
            if (combined.contains("收到转账") || combined.contains("成功收款") || combined.contains("收款到账") ||
                combined.contains("支付宝收款到账") || combined.contains("收到一笔款项")) {
                notifType = TransactionType.INCOME
            } else if (title.contains("支付宝") || combined.contains("成功付款") ||
                combined.contains("交易提醒") || combined.contains("支付成功") ||
                combined.contains("花呗") || combined.contains("扣款成功") ||
                combined.contains("停车缴费") || combined.contains("已扣费")) {
                notifType = TransactionType.EXPENSE
            }
        } else {
            if (combined.contains("收款成功") || combined.contains("收款到账") || combined.contains("入账")) {
                notifType = TransactionType.INCOME
            } else if (combined.contains("支付成功") || combined.contains("扣款成功") ||
                combined.contains("付款成功") || combined.contains("消费") ||
                combined.contains("交易成功") || combined.contains("已扣款") ||
                combined.contains("停车")) {
                notifType = TransactionType.EXPENSE
            }
        }

        if (notifType == null) return

        val amount = extractAmountFromSingleText(combined) ?: return
        if (amount <= 0.0) return

        val partyName = extractMerchantFromNotification(combined)
        val detectedAccount = extractAccountFromTexts(pkg, listOf(combined))
        saveAutoRecord(pkg, amount, partyName, combined, notifType, detectedAccount)
    }

    // ====== 通道 2：页面节点解析逻辑 ======
    private fun handleWindow(pkg: String, event: AccessibilityEvent?) {
        // 多维度聚合节点：event.source + rootInActiveWindow + windows 交互窗口列表
        val nodesToInspect = mutableListOf<AccessibilityNodeInfo>()
        event?.source?.let { nodesToInspect.add(it) }
        rootInActiveWindow?.let { if (!nodesToInspect.contains(it)) nodesToInspect.add(it) }
        try {
            windows?.forEach { win ->
                win.root?.let { if (!nodesToInspect.contains(it)) nodesToInspect.add(it) }
            }
        } catch (_: Exception) {}

        if (nodesToInspect.isEmpty()) return

        val texts = mutableListOf<String>()
        nodesToInspect.forEach { node ->
            collectTexts(node, texts)
        }
        if (texts.isEmpty()) return

        val joined = texts.joinToString(" ")

        // 核心防误触 1：微信主界面（包含“通讯录”、“发现”、“我”导航栏）坚决不触碰
        if (pkg == "com.tencent.mm") {
            if (texts.contains("通讯录") && texts.contains("发现") && texts.contains("我")) {
                return
            }
        }

        // 核心防误触 2：绝对拦截任何【查账、账单列表、历史明细、对账、统计、微信聊天/公众号翻看记录】页面
        // 这是彻底杜绝“进微信看付款记录/查账却重复记账”的最高优先级防线！
        if (isBrowsingHistoryOrChatting(pkg, texts, joined)) {
            return
        }

        // 核心防误触 3：绝对拦截所有待支付 / 结算中 / 选餐购物车 / 选规格 / 收银台未付款页面
        val prePaymentKeywords = listOf(
            "提交订单", "去支付", "立即支付", "立即付款", "确认付款", "确认支付", "待付款", "待支付",
            "未支付", "去结算", "立即购买", "去买单", "选择支付方式", "找人付", "去拼单",
            "确认订单", "还需支付", "放弃支付", "继续支付", "确认下单", "去凑单", "加入购物车",
            "选好了", "选择优惠券", "请输入支付密码", "验证支付密码", "指纹验证", "还差",
            "起送", "另需配送费", "外卖专享", "差￥", "差¥", "加一份", "去结算("
        )
        if (prePaymentKeywords.any { joined.contains(it) }) {
            return
        }

        // 核心防误触 4：美团、外卖、京东、饿了么等电商 App 极度严格过滤
        val isShoppingApp = pkg in listOf(
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
            "com.jingdong.app.mall",
            "me.ele"
        )
        if (isShoppingApp) {
            // 在外卖/电商 App 内，只有当明确展示全屏“支付成功”或“付款成功”，且绝无待付款字样时才允许触发
            val hasStrictShoppingSuccess = joined.contains("支付成功") || joined.contains("付款成功")
            if (!hasStrictShoppingSuccess) {
                return
            }
        }

        // 1. 收入语义判定 (微信转账收款、存入零钱、二维码收款等)
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

        // 2. 支付/扣款成功语义判定 (支出 - 必须有明确的即时已完成/已扣款成功标志，严禁任何账单或卡片凭证模糊词)
        val isExpense = joined.contains("支付成功") ||
                joined.contains("付款成功") ||
                joined.contains("交易成功") ||
                joined.contains("支付已完成") ||
                joined.contains("扫码付款成功") ||
                joined.contains("成功付款") ||
                joined.contains("向商家付款成功") ||
                // 停车场景与缴费场景
                (joined.contains("支付停车费") && (joined.contains("支付成功") || joined.contains("道闸已抬起"))) ||
                (joined.contains("停车缴费") && (joined.contains("成功") || joined.contains("完成"))) ||
                joined.contains("缴费成功") ||
                joined.contains("扣费成功") ||
                joined.contains("已缴费") ||
                // 饭店扫码点餐小程序
                (joined.contains("下单成功") && !joined.contains("待支付")) ||
                (joined.contains("取餐号") && !joined.contains("去支付")) ||
                (joined.contains("订单已完成") && !joined.contains("待付款")) ||
                (joined.contains("订单已支付") && !joined.contains("去结算")) ||
                // 聚合商户 / 商家小程序 (必须有明确支付成功标志)
                (joined.contains("特约商户") && (joined.contains("支付成功") || joined.contains("交易成功"))) ||
                // 银联云闪付 / 银行 App
                joined.contains("扣款成功") ||
                joined.contains("刷卡成功") ||
                joined.contains("银联付款成功")


        val targetType = when {
            isIncome -> TransactionType.INCOME
            isExpense -> TransactionType.EXPENSE
            else -> return
        }

        // 提取金额
        val amount = extractAmountFromList(texts) ?: return
        if (amount <= 0.0) return

        // 提取支付账户（如零钱通、微信零钱、具体信用卡等）
        val detectedAccount = extractAccountFromTexts(pkg, texts)

        // 提取唯一交易单号与订单时间（用于彻底杜绝二次触发与过滤历史旧账单）
        val orderId = extractOrderIdFromTexts(texts)
        val billTimestamp = extractBillTimestamp(texts)

        val partyName = if (targetType == TransactionType.INCOME) {
            extractIncomeSourceFromList(texts)
        } else {
            extractMerchantFromList(texts)
        }
        saveAutoRecord(pkg, amount, partyName, joined, targetType, detectedAccount, orderId, billTimestamp)
    }

    /**
     * 统一安全入库逻辑（核心多层熔断与订单单号终身排重机制）
     */
    private fun saveAutoRecord(
        pkg: String,
        amount: Double,
        partyName: String,
        rawContext: String,
        type: TransactionType = TransactionType.EXPENSE,
        customAccount: String? = null,
        orderId: String? = null,
        billTimestamp: Long? = null
    ) {
        val now = System.currentTimeMillis()

        // 1. 唯一订单号内存拦截：只要该微信/支付宝单号在内存中记录过，绝对直接拦截
        if (!orderId.isNullOrBlank()) {
            val lastSeenOrder = orderDedupeCache[orderId] ?: 0L
            if (lastSeenOrder > 0L) {
                return
            }
        }

        // 2. 内存级 10 分钟 (600秒) 强力防重检查：同一应用同一类型同一金额 10 分钟内严格只记一次
        val cacheKey = "$pkg-${type.name}-${String.format(java.util.Locale.US, "%.2f", amount)}"
        val lastSeen = dedupeCache[cacheKey] ?: 0L
        if (now - lastSeen < 600_000L) {
            return
        }

        // 3. 全局时间防重：2分钟 (120秒) 内全系统同金额同类型坚决不重复触发
        if (now - lastGlobalTime < 120_000L && Math.abs(lastGlobalAmount - amount) < 0.001) {
            return
        }

        // 4. 历史旧账单查阅拦截：若单据自带的时间早于当前系统时间 5 分钟以上，说明用户只是在查账/翻阅往期凭证！
        if (billTimestamp != null && now - billTimestamp > 300_000L) {
            return
        }

        val accountName = customAccount ?: when (pkg) {
            "com.tencent.mm" -> "微信"
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.unionpay" -> "银行卡"
            else -> "微信"
        }

        val displayName = if (partyName.isNotBlank() && !isSystemWord(partyName)) {
            partyName
        } else {
            if (type == TransactionType.INCOME) "微信转账收款" else "日常消费"
        }

        val catName = classifyCategory("$displayName $rawContext", type)

        // 刷新内存去重戳
        dedupeCache[cacheKey] = now
        if (!orderId.isNullOrBlank()) {
            orderDedupeCache[orderId] = now
        }
        lastGlobalTime = now
        lastGlobalAmount = amount

        // 清理过期缓存
        cleanExpiredCache(now)

        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext, scope)

            // 5. 数据库订单号终身排重：若数据库中已经存过这个微信/支付宝交易单号，绝对不再记！
            if (!orderId.isNullOrBlank()) {
                val existingCount = db.recordDao().getRecordCountByOrderId(orderId)
                if (existingCount > 0) {
                    return@launch
                }
            }

            // 6. 数据库底层熔断防重：检查数据库内近 10 分钟 (600秒) 是否存在相同金额相同类型的记账
            val recentDupCount = db.recordDao().getRecentDuplicateCount(amount, type, now - 600_000L)
            if (recentDupCount > 0) {
                return@launch
            }

            val remarkText = if (!orderId.isNullOrBlank()) {
                "自动记账: $displayName [单号:$orderId]"
            } else {
                "自动记账: $displayName"
            }

            val finalTimestamp = billTimestamp ?: now

            val record = Record(
                bookId = 1L, // 统一记入日常主账本
                type = type,
                amount = amount,
                categoryName = catName,
                accountName = accountName,
                remark = remarkText,
                tag = "自动记账",
                timestamp = finalTimestamp
            )
            db.recordDao().insertRecord(record)

            lastRecordLog = "${if (type == TransactionType.INCOME) "收入 +" else "支出 -"}¥${String.format(java.util.Locale.CHINA, "%.2f", amount)} ($catName · $accountName · $displayName)"
            lastRecordTime = now

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
            if (now - entry.value > 1200_000L) { // 20分钟清理
                iterator.remove()
            }
        }
        val orderIterator = orderDedupeCache.entries.iterator()
        while (orderIterator.hasNext()) {
            val entry = orderIterator.next()
            if (now - entry.value > 86400_000L) { // 24小时清理
                orderIterator.remove()
            }
        }
    }

    /**
     * 核心防误触：绝对拦截任何【查账、账单列表、历史明细、对账、统计、微信聊天/公众号翻看记录】页面
     * 这是彻底杜绝“进微信看付款记录/查账却重复记账”的最高优先级防线！
     */
    private fun isBrowsingHistoryOrChatting(pkg: String, texts: List<String>, joined: String): Boolean {
        // 1. 微信 / 支付宝 账单列表、交易记录与流水明细页面特征
        val billHistoryKeywords = listOf(
            "全部账单", "账单列表", "账单明细", "全部交易类型", "月账单", "年账单", "按月查看",
            "收支明细", "资金流水", "账单服务", "对此订单有疑问", "常见问题", "投诉商家",
            "联系商户", "开发票", "申请开票", "查看往期", "退款明细", "退款记录", "退款进度",
            "零钱明细", "对账中心", "账单分类", "月度汇总", "账单详情", "交易记录", "我的账单"
        )
        if (billHistoryKeywords.any { joined.contains(it) }) {
            return true
        }

        // 筛选与统计特征：包含“筛选”且包含“统计”或“全部”
        if (joined.contains("筛选") && (joined.contains("统计") || joined.contains("全部") || joined.contains("类型") || joined.contains("金额"))) {
            return true
        }

        // 2. 微信聊天窗口 / 公众号会话界面特征（无论个人聊天、群聊、还是微信支付服务号）
        // 无论里面显示什么旧凭证或卡片，只要处于聊天窗口环境，坚决拦截
        if (pkg == "com.tencent.mm") {
            val chatKeywords = listOf(
                "切换到输入法", "按住说话", "按住 说话", "发送", "发消息", "自定义菜单",
                "聊天信息", "公众号", "微信号", "聊天记录", "清空聊天记录", "移出移进"
            )
            if (chatKeywords.any { joined.contains(it) }) {
                return true
            }
        }

        // 3. 屏幕中出现多笔独立金额（账单列表或聊天记录流的典型特征）
        // 真实支付结果页屏幕上通常只有 1 个主金额（最多 1 个优惠减免金额）
        // 如果页面上出现了 3 个或以上的货币符号金额，说明必定是列表流水页面或聊天历史
        val countCurrencies = Regex("""[¥￥]\s*-?\s*[0-9]+""").findAll(joined).count()
        if (countCurrencies >= 3) {
            return true
        }

        return false
    }

    private fun extractOrderIdFromTexts(texts: List<String>): String? {
        val labels = listOf("交易单号", "商户单号", "订单号", "交易号", "支付单号")
        for (i in texts.indices) {
            val t = texts[i].trim()
            for (label in labels) {
                if (t == label || t == "$label:" || t == "$label：") {
                    if (i + 1 < texts.size) {
                        val candidate = texts[i + 1].trim()
                        if (candidate.matches(Regex("""^[A-Za-z0-9_]{8,40}$"""))) {
                            return candidate
                        }
                    }
                } else if (t.startsWith(label) && (t.contains(":") || t.contains("："))) {
                    val clean = t.substringAfter(":").substringAfter("：").trim()
                    if (clean.matches(Regex("""^[A-Za-z0-9_]{8,40}$"""))) {
                        return clean
                    }
                }
            }
        }
        return null
    }

    private fun extractBillTimestamp(texts: List<String>): Long? {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)

        // 1. 标准完整日期格式: 2026-10-08 18:24:00 或 2026-10-08 18:24 或 2026/10/08 18:24
        val sdfFull = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
        val sdfShort = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        val regexFull = Regex("""202[0-9][-/.][0-1][0-9][-/.][0-3][0-9]\s+[0-2][0-9]:[0-5][0-9](?::[0-5][0-9])?""")

        for (t in texts) {
            val m = regexFull.find(t)
            if (m != null) {
                val str = m.value.replace('/', '-').replace('.', '-').trim()
                try {
                    val date = if (str.length > 16) sdfFull.parse(str) else sdfShort.parse(str)
                    if (date != null) return date.time
                } catch (_: Exception) {}
            }
        }

        // 2. 中文月日格式: 10月8日 18:24 或 2026年10月8日 18:24
        val regexMonthDay = Regex("""(?:(202[0-9])年)?([0-1]?[0-9])月([0-3]?[0-9])日\s*([0-2][0-9]:[0-5][0-9])""")
        for (t in texts) {
            val m = regexMonthDay.find(t)
            if (m != null) {
                val year = m.groupValues[1].toIntOrNull() ?: currentYear
                val month = m.groupValues[2].toIntOrNull() ?: 1
                val day = m.groupValues[3].toIntOrNull() ?: 1
                val timeStr = m.groupValues[4]
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                    val date = sdf.parse(String.format(java.util.Locale.CHINA, "%04d-%02d-%02d %s", year, month, day, timeStr))
                    if (date != null) return date.time
                } catch (_: Exception) {}
            }
        }

        // 3. 相对时间: 今天 18:24 或 昨天 18:24
        val regexRelative = Regex("""(今天|昨天)\s*([0-2][0-9]:[0-5][0-9])""")
        for (t in texts) {
            val m = regexRelative.find(t)
            if (m != null) {
                val dayType = m.groupValues[1]
                val timeParts = m.groupValues[2].split(":")
                if (timeParts.size == 2) {
                    val hour = timeParts[0].toIntOrNull() ?: 0
                    val minute = timeParts[1].toIntOrNull() ?: 0
                    val cal = java.util.Calendar.getInstance()
                    if (dayType == "昨天") {
                        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                    }
                    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                    cal.set(java.util.Calendar.MINUTE, minute)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            }
        }

        return null
    }


    private fun isSystemWord(word: String): Boolean {
        val cleaned = word.trim()
        return cleaned in listOf(
            "微信支付", "支付宝", "支付成功", "付款成功", "交易成功", "完成", "收款成功", "账单详情", "人民币",
            "零钱余额", "转账时间", "收款时间", "你已收款", "资金已存入零钱", "你已收款，资金已存入零钱",
            "微信支付凭证", "付款凭证", "支付方式", "扣款成功", "交易明细", "查看账单", "查看详情",
            "返回商家", "关闭", "确定", "返回", "商家小程序", "我的订单", "取餐号", "订单已完成",
            "全部账单", "常见问题", "账单服务", "退款", "退款成功", "退款申请", "退款中", "退款明细", "退款结果"
        )
    }

    private fun extractAccountFromTexts(pkg: String, texts: List<String>): String? {
        val joined = texts.joinToString(" ")
        val banks = listOf(
            "招商银行", "工商银行", "建设银行", "农业银行", "中国银行", "交通银行",
            "平安银行", "中信银行", "浦发银行", "民生银行", "光大银行", "广发银行",
            "兴业银行", "邮政储蓄", "微众银行", "网商银行"
        )
        for (b in banks) {
            if (joined.contains(b)) {
                return if (joined.contains("信用卡")) "$b(信用卡)" else b
            }
        }
        if (joined.contains("信用卡")) {
            return "信用卡"
        }
        if (pkg == "com.tencent.mm") {
            if (joined.contains("零钱通")) {
                return "微信零钱通"
            }
            if (joined.contains("零钱")) {
                return "微信零钱"
            }
        }
        if (pkg == "com.eg.android.AlipayGphone" && (joined.contains("花呗") || joined.contains("花呗分期"))) {
            return "花呗"
        }
        return null
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
        // 1. 带有货币符号的字符串 (如 "¥ 1.20", "¥1.20", "￥12.50", "-¥4.00", "¥ -4.00")
        val regexSymbol = Regex("""[¥￥]\s*-?\s*([0-9]+\.?[0-9]*)""")
        for (t in texts) {
            val m = regexSymbol.find(t)
            if (m != null) {
                val v = m.groupValues[1].toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }
        // 2. 货币符号与数字分立在相邻节点 (如 node 1: "¥", node 2: "1.20" 或 "-4.00")
        for (i in texts.indices) {
            val t = texts[i].trim()
            if ((t == "¥" || t == "￥") && i + 1 < texts.size) {
                val nextStr = texts[i + 1].trim().removePrefix("-").trim()
                val v = nextStr.toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }
        // 3. 带有"元"的文本 (如 "4.00元", "12元", "-4.00元")
        val regexYuan = Regex("""(?:^|[^\d])-?\s*([0-9]+\.?[0-9]*)\s*元""")
        for (t in texts) {
            val m = regexYuan.find(t)
            if (m != null) {
                val v = m.groupValues[1].toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }
        // 4. 独立纯金额节点 (如 "-4.00", "4.00", "12.50", "128.00")
        for (t in texts) {
            val cleaned = t.trim().removePrefix("-").trim()
            if (cleaned.matches(Regex("""^[0-9]+\.[0-9]{2}$"""))) {
                val v = cleaned.toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }
        return null
    }

    private fun extractAmountFromSingleText(text: String): Double? {
        val regex = Regex("""[¥￥]\s*-?\s*([0-9]+\.?[0-9]*)""")
        val m = regex.find(text)
        if (m != null) {
            val v = m.groupValues[1].toDoubleOrNull()
            if (v != null && v > 0) return v
        }
        val regexYuan = Regex("""(?:^|[^\d])-?\s*([0-9]+\.?[0-9]*)\s*元""")
        val m2 = regexYuan.find(text)
        if (m2 != null) {
            val v = m2.groupValues[1].toDoubleOrNull()
            if (v != null && v > 0) return v
        }
        val regexPure = Regex("""(?:支付|消费|扣款|转账|收款|到账|支出)?\s*-?\s*([0-9]+\.[0-9]{2})\s*元?""")
        val m3 = regexPure.find(text)
        if (m3 != null) {
            val v = m3.groupValues[1].toDoubleOrNull()
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

    // ====== 工业级增强商户名提取算法 ======
    private fun extractMerchantFromList(texts: List<String>): String {
        // 第一优先级：强指定商户属性标签（优先于模糊的“商品”）
        val primaryLabels = listOf(
            "商户全称", "收款商家", "收款方全称", "商户名称", "收款人全称",
            "特约商户", "交易对象", "收款方", "收款人", "商户", "店名", "商家"
        )
        for (i in texts.indices) {
            val t = texts[i].trim()
            for (label in primaryLabels) {
                if (t == label || t == "$label:" || t == "$label：") {
                    if (i + 1 < texts.size) {
                        val candidate = texts[i + 1].trim()
                        if (candidate.isNotBlank() && !isSystemWord(candidate) &&
                            !candidate.startsWith("¥") && !candidate.startsWith("￥")) {
                            return candidate
                        }
                    }
                } else if (t.startsWith(label) && (t.contains(":") || t.contains("："))) {
                    val clean = t.substringAfter(":").substringAfter("：").trim()
                    if (clean.isNotBlank() && !isSystemWord(clean) && !clean.startsWith("¥") && !clean.startsWith("￥")) {
                        return clean
                    }
                }
            }
        }

        // 第二优先级：商品/详情标签（如果包含停车费或商品名，清洗括号和车牌号）
        val secondaryLabels = listOf("商品", "商品说明", "商品名称", "交易商品")
        for (i in texts.indices) {
            val t = texts[i].trim()
            for (label in secondaryLabels) {
                if (t == label || t == "$label:" || t == "$label：") {
                    if (i + 1 < texts.size) {
                        val candidate = texts[i + 1].trim()
                        if (candidate.isNotBlank() && !isSystemWord(candidate)) {
                            // 若带有停车费说明，如 "【粤A9Y992】在【46771130】支付停车费4.00元"
                            if (candidate.contains("停车")) {
                                return "停车缴费"
                            }
                            return candidate
                        }
                    }
                } else if (t.startsWith(label) && (t.contains(":") || t.contains("："))) {
                    val clean = t.substringAfter(":").substringAfter("：").trim()
                    if (clean.isNotBlank() && !isSystemWord(clean)) {
                        if (clean.contains("停车")) {
                            return "停车缴费"
                        }
                        return clean
                    }
                }
            }
        }

        // 针对页面标题直接包含停车费的场景
        for (t in texts) {
            if (t.contains("支付停车费") || t.contains("停车缴费")) {
                return "停车缴费"
            }
        }

        // 3. 启发式回溯：金额节点前 1~3 个节点通常是商户名或餐厅名
        for (i in texts.indices) {
            val t = texts[i].trim().removePrefix("-")
            val isAmount = t.matches(Regex("""^[¥￥]\s*[0-9]+(\.[0-9]{1,2})?$""")) ||
                    t.matches(Regex("""^[0-9]+\.[0-9]{2}$""")) ||
                    t.matches(Regex("""^[0-9]+\.?[0-9]*\s*元$"""))
            if (isAmount) {
                for (step in 1..3) {
                    val prevIdx = i - step
                    if (prevIdx >= 0) {
                        val prev = texts[prevIdx].trim()
                        if (prev.length in 2..32 && !isSystemWord(prev) &&
                            !prev.startsWith("¥") && !prev.startsWith("￥") &&
                            !prev.contains("支付") && !prev.contains("付款") && !prev.contains("详情")) {
                            return prev
                        }
                    }
                }
            }
        }
        return ""
    }

    private fun extractMerchantFromNotification(text: String): String {
        if (text.contains("停车") || text.contains("车牌")) {
            return "停车缴费"
        }
        val m1 = Regex("""向\s*(.*?)\s*(付款|转账|支付|消费)""").find(text)
        if (m1 != null) {
            val name = m1.groupValues[1].trim()
            if (name.isNotEmpty() && !isSystemWord(name)) return name
        }
        val m2 = Regex("""(收款方|收款商家|商户|收款人|特约商户)[:：]\s*(.*?)\s*([￥¥0-9\s]|$)""").find(text)
        if (m2 != null) {
            val name = m2.groupValues[2].trim()
            if (name.isNotEmpty() && !isSystemWord(name)) return name
        }
        val m3 = Regex("""在\s*(.*?)\s*(消费|支出|刷卡|支付)""").find(text)
        if (m3 != null) {
            val name = m3.groupValues[1].trim()
            if (name.isNotEmpty() && !isSystemWord(name)) return name
        }
        return ""
    }
}
