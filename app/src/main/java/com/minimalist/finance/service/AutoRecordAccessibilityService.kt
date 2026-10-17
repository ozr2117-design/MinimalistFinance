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
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.minimalist.finance.MainActivity
import com.minimalist.finance.R
import com.minimalist.finance.data.AppDatabase
import com.minimalist.finance.data.model.Record
import com.minimalist.finance.data.model.TransactionType
import com.minimalist.finance.util.AutoRecordDedupeManager
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
                    remark = merchantName,
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
                // 若为状态初次切换，微延时 300ms/500ms 兜底重试一次（解决微信内嵌 Web 渲染延迟与电商/支付宝多层跳转回跳时机）
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val delay = if (pkg in listOf("com.taobao.taobao", "com.taobao.litetao", "com.tmall.wireless", "com.taobao.idlefish")) 500L else 300L
                    mainHandler.postDelayed({
                        val currentPkg = rootInActiveWindow?.packageName?.toString() ?: pkg
                        if (isPaymentApp(currentPkg)) {
                            handleWindow(currentPkg, null)
                        }
                    }, delay)
                }
            }
        }
    }

    private fun isPaymentApp(pkg: String): Boolean {
        return pkg in listOf(
            "com.tencent.mm",                 // 微信
            "com.eg.android.AlipayGphone",    // 支付宝
            "com.unionpay",                   // 云闪付
            "com.taobao.taobao",              // 手机淘宝
            "com.taobao.litetao",             // 淘特 (淘宝特价版)
            "com.tmall.wireless",             // 手机天猫
            "com.taobao.idlefish",            // 闲鱼
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

        // 核心排除 1：坚决拦截信用卡还款、账单提醒、还款入账等非消费场景（杜绝误记还款与账单通知）
        if (isCreditCardRepayment(pkg, "", listOf(combined), combined)) {
            // 若为云闪付入账通知，提取金额并向对冲引擎注册 INCOME 事件，用于抵消成对的还款转出
            if (pkg == "com.unionpay") {
                val hedgedAmount = extractAmountFromSingleText(combined)
                if (hedgedAmount != null && hedgedAmount > 0.0) {
                    val wasHedged = AutoRecordDedupeManager.checkAndRegisterHedge(pkg, TransactionType.INCOME, hedgedAmount, 90_000L)
                    if (wasHedged) {
                        val now = System.currentTimeMillis()
                        scope.launch {
                            val db = AppDatabase.getDatabase(applicationContext, scope)
                            db.recordDao().deleteRecentRecordsByAmount(hedgedAmount, now - 90_000L)
                        }
                    }
                }
            }
            return
        }

        // 核心排除 1.5：严密拦截一切营销领券、满减活动、红包推广通知（彻底根除云闪付等推送“领88元红包/立减券”误记）
        val isMarketingNotif = combined.contains("立减") || combined.contains("红包") ||
                combined.contains("优惠券") || combined.contains("代金券") || combined.contains("立减券") ||
                combined.contains("消费券") || combined.contains("福利") || combined.contains("免费领") ||
                combined.contains("最高立减") || combined.contains("抽奖") || combined.contains("享满减") ||
                combined.contains("满减") || combined.contains("待领取") || combined.contains("已到账礼包")
        if (isMarketingNotif) {
            return
        }

        // 核心排除 2：通知中存在未支付 / 待支付等预备状态词
        if (isPrePaymentOrCart(combined)) {
            return
        }

        // 识别微信、支付宝、云闪付、淘宝等的通知（支出或收入）
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
                (combined.contains("信用卡") && (combined.contains("消费") || combined.contains("扣款") || combined.contains("支出") || combined.contains("支付"))) ||
                combined.contains("消费支出") ||
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
                combined.contains("支付凭证") || combined.contains("付款凭证") ||
                combined.contains("付款支出") || (combined.contains("你有一笔") && combined.contains("支出")) ||
                combined.contains("付款") || combined.contains("支付") ||
                combined.contains("停车缴费") || combined.contains("已扣费")) {
                notifType = TransactionType.EXPENSE
            }
        } else if (pkg == "com.unionpay") {
            if (combined.contains("收款到账") || combined.contains("成功入账")) {
                notifType = TransactionType.INCOME
            } else if (combined.contains("付款成功") || combined.contains("支付成功") ||
                combined.contains("扣款成功") || combined.contains("成功消费") ||
                combined.contains("消费支出") || combined.contains("银联付款成功")) {
                notifType = TransactionType.EXPENSE
            }
        } else if (pkg in listOf("com.taobao.taobao", "com.taobao.litetao", "com.tmall.wireless", "com.taobao.idlefish")) {
            if (combined.contains("支付成功") || combined.contains("付款成功") || combined.contains("购买成功") ||
                combined.contains("交易成功") || combined.contains("已付款") || combined.contains("已支付") ||
                combined.contains("等待卖家发货") || combined.contains("买家已付款")) {
                notifType = TransactionType.EXPENSE
            }
        } else {
            if (combined.contains("收款成功") || combined.contains("收款到账") || combined.contains("入账")) {
                notifType = TransactionType.INCOME
            } else if (combined.contains("支付成功") || combined.contains("扣款成功") ||
                combined.contains("付款成功") || combined.contains("消费支出") ||
                combined.contains("成功消费") || combined.contains("交易成功") ||
                combined.contains("已扣款") || combined.contains("停车")) {
                notifType = TransactionType.EXPENSE
            }
        }

        if (notifType == null) return

        val amount = extractAmountFromSingleText(combined) ?: return
        if (amount <= 0.0) return

        val partyName = extractMerchantFromNotification(combined)
        if (isCreditCardRepayment(pkg, partyName, listOf(combined), combined)) {
            return
        }

        val detectedAccount = extractAccountFromTexts(pkg, listOf(combined))
        val orderId = extractOrderIdFromSingleText(combined)
        saveAutoRecord(pkg, amount, partyName, combined, notifType, detectedAccount, orderId = orderId)
    }

    // ====== 通道 2：页面节点解析逻辑 ======
    private fun handleWindow(pkg: String, event: AccessibilityEvent?) {
        // 若为延时兜底重试任务（event == null），先校验当前前台活动包名：若用户已按 Home 退出或切走，坚决不解析残留后台节点！
        if (event == null) {
            val currentActivePkg = rootInActiveWindow?.packageName?.toString()
            if (currentActivePkg != null && currentActivePkg != pkg) {
                return
            }
        }

        // 多维度聚合节点：event.source + rootInActiveWindow + windows 交互窗口列表
        val nodesToInspect = mutableListOf<AccessibilityNodeInfo>()
        event?.source?.let {
            if (it.packageName?.toString() == pkg) {
                nodesToInspect.add(it)
            }
        }
        rootInActiveWindow?.let {
            if (it.packageName?.toString() == pkg && !nodesToInspect.contains(it)) {
                nodesToInspect.add(it)
            }
        }
        try {
            windows?.forEach { win ->
                val root = win.root ?: return@forEach
                val winPkg = root.packageName?.toString()
                // 彻底排除系统状态栏与导航栏窗口，严防将状态栏网速 (如 6.68KB/s)、电量、时间混入页面节点！
                if (winPkg == "com.android.systemui" || win.type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                    return@forEach
                }
                // 排除过渡动画中残留的非活动或最小化窗口
                if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION && !win.isFocused) {
                    return@forEach
                }
                if (winPkg == pkg && !nodesToInspect.contains(root)) {
                    nodesToInspect.add(root)
                }
            }
        } catch (_: Exception) {}

        if (nodesToInspect.isEmpty()) return

        val texts = mutableListOf<String>()
        nodesToInspect.forEach { node ->
            collectTexts(node, texts)
        }
        if (texts.isEmpty()) return

        val joined = texts.joinToString(" ")

        // 核心防误触 1：各应用主界面（首页/导航栏/功能大厅）坚决不触碰，彻底根除“点进云闪付/支付宝无操作被广告误记”
        if (isAppHomeNavigationScreen(pkg, texts, joined)) {
            return
        }

        // 核心防误触 2：绝对拦截任何【查账、账单列表、历史明细、对账、统计、微信聊天/公众号翻看记录】页面
        // 这是彻底杜绝“进微信看付款记录/查账却重复记账”的最高优先级防线！
        if (isBrowsingHistoryOrChatting(pkg, texts, joined)) {
            return
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

        // 2. 支付/扣款成功语义判定 (支出 - 必须有明确的即时已完成/已扣款成功标志，微信内严格限定收银台完成页)
        val isExpense = if (pkg == "com.tencent.mm") {
            (joined.contains("支付成功") || joined.contains("付款成功") || joined.contains("向商家付款成功") ||
             joined.contains("扫码付款成功") || (joined.contains("支付停车费") && joined.contains("道闸已抬起")) ||
             (joined.contains("停车缴费") && joined.contains("成功")) ||
             (joined.contains("下单成功") && !joined.contains("待支付")) ||
             (joined.contains("取餐号") && !joined.contains("去支付"))) &&
             // 在微信内，必须包含收银台支付结果操作按钮或停车/取餐标志（绝对杜绝服务号消息卡片）
             (joined.contains("完成") || joined.contains("返回商家") || joined.contains("停车") || joined.contains("取餐号"))
        } else if (pkg == "com.unionpay") {
            // 云闪付必须是明确的收银台即时凭证（绝不被历史生活缴费卡片“已缴费”或首页营销立减广告误触发）
            val hasUnionpaySuccess = (joined.contains("支付成功") || joined.contains("付款成功") || joined.contains("银联付款成功") ||
                    (joined.contains("交易成功") && !joined.contains("优惠")) ||
                    (joined.contains("扣款成功") && (joined.contains("商户") || joined.contains("店") || joined.contains("元"))))
            val isMarketingOrAd = joined.contains("生活缴费") || joined.contains("缴费明细") ||
                    joined.contains("立减") || joined.contains("优惠券") || joined.contains("红包") ||
                    joined.contains("营销") || joined.contains("示例") || joined.contains("天天立减") ||
                    joined.contains("最高立减") || joined.contains("满减") || joined.contains("待领取") ||
                    joined.contains("活动") || joined.contains("专享")
            hasUnionpaySuccess && !isMarketingOrAd
        } else {
            joined.contains("支付成功") ||
            joined.contains("付款成功") ||
            joined.contains("交易成功") ||
            joined.contains("支付已完成") ||
            joined.contains("购买成功") ||
            joined.contains("买家已付款") ||
            joined.contains("等待卖家发货") ||
            joined.contains("等待商家发货") ||
            joined.contains("已成功付款") ||
            joined.contains("免密支付成功") ||
            joined.contains("自动扣款成功") ||
            joined.contains("扫码付款成功") ||
            joined.contains("成功付款") ||
            joined.contains("向商家付款成功") ||
            (joined.contains("支付停车费") && (joined.contains("支付成功") || joined.contains("道闸已抬起"))) ||
            (joined.contains("停车缴费") && (joined.contains("成功") || joined.contains("完成"))) ||
            (joined.contains("下单成功") && !joined.contains("待支付")) ||
            (joined.contains("取餐号") && !joined.contains("去支付")) ||
            (joined.contains("订单已完成") && !joined.contains("待付款")) ||
            (joined.contains("订单已支付") && !joined.contains("去结算")) ||
            (joined.contains("特约商户") && (joined.contains("支付成功") || joined.contains("交易成功"))) ||
            joined.contains("扣款成功") ||
            joined.contains("银联付款成功")
        }

        val targetType = when {
            isIncome -> TransactionType.INCOME
            isExpense -> TransactionType.EXPENSE
            else -> return
        }

        // 核心防误触 3：若已判定支出或收入，检查界面是否明确处于主订单未支付动作中
        // 注意：若已经明确支付成功，底部的广告推荐词（如“需付款/立即购买/加入购物车/去拼单”）绝不误杀！
        val hasExplicitSuccess = joined.contains("支付成功") || joined.contains("付款成功") ||
                joined.contains("交易成功") || joined.contains("买家已付款") ||
                joined.contains("等待卖家发货") || joined.contains("等待商家发货") ||
                joined.contains("订单已完成") || joined.contains("订单已支付") ||
                joined.contains("免密支付成功") || joined.contains("自动扣款成功") ||
                joined.contains("已成功付款") || joined.contains("购买成功")
        val hasPendingPaymentAction = joined.contains("未完成支付") || joined.contains("放弃支付") ||
                joined.contains("订单待付款") || joined.contains("需付款") || joined.contains("合计待支付")
        if (hasPendingPaymentAction && !hasExplicitSuccess) {
            return
        }

        // 电商与外卖应用（美团/京东/饿了么/淘宝/天猫/闲鱼）二次安全校验
        val isShoppingApp = pkg in listOf(
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
            "com.jingdong.app.mall",
            "me.ele",
            "com.taobao.taobao",
            "com.taobao.litetao",
            "com.tmall.wireless",
            "com.taobao.idlefish"
        )
        if (isShoppingApp) {
            val hasClearShoppingSuccess = joined.contains("支付成功") || joined.contains("付款成功") ||
                    joined.contains("购买成功") || joined.contains("交易成功") || joined.contains("买家已付款") ||
                    joined.contains("等待卖家发货") || joined.contains("下单成功") ||
                    joined.contains("订单已完成") || joined.contains("订单已支付") ||
                    joined.contains("免密支付成功") || joined.contains("自动扣款成功") ||
                    joined.contains("已成功付款")
            if (!hasClearShoppingSuccess) {
                return
            }
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

        // 核心排除：坚决拦截信用卡还款、账单提醒、还款入账等非消费场景（杜绝误记还款）
        if (isCreditCardRepayment(pkg, partyName, texts, joined)) {
            return
        }

        saveAutoRecord(pkg, amount, partyName, joined, targetType, detectedAccount, orderId, billTimestamp)
    }

    /**
     * 统一安全入库逻辑（核心持久化防重与全维度熔断机制）
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

        // 0. 信用卡还款终极排他底线拦截
        if (isCreditCardRepayment(pkg, partyName, listOf(rawContext), rawContext)) {
            return
        }

        // 0.1 内部转账与还款【同时间同金额对冲熔断】拦截
        // 90秒内同一应用发生完全相同金额的一出一进（如还款时卡A扣2060.89，卡B入2060.89）
        val isHedged = AutoRecordDedupeManager.checkAndRegisterHedge(pkg, type, amount, 90_000L)
        if (isHedged) {
            // 命中对冲对！说明上一笔和当前笔构成内部互转/还款对
            // 立即异步清理可能刚写入数据库的上一笔同金额记录，并终止当前入库
            scope.launch {
                val db = AppDatabase.getDatabase(applicationContext, scope)
                db.recordDao().deleteRecentRecordsByAmount(amount, now - 90_000L)
            }
            return
        }

        // 1. 唯一订单号持久化拦截：只要微信/支付宝单号曾记过账，绝对直接拦截（终身防重）
        if (!orderId.isNullOrBlank() && AutoRecordDedupeManager.isOrderRecorded(applicationContext, orderId)) {
            return
        }

        // 2. 交易特征指纹持久化拦截 (金额 + 商户 + 时间指纹)
        val fingerprint = AutoRecordDedupeManager.buildFingerprint(pkg, type, amount, partyName, billTimestamp)
        if (AutoRecordDedupeManager.isFingerprintRecorded(applicationContext, fingerprint)) {
            return
        }

        // 3. 短时间内存防抖 (60秒内同应用同金额同类型坚决不重复触发)
        if (AutoRecordDedupeManager.isDebounced(pkg, type, amount, 60_000L)) {
            return
        }

        // 4. 历史旧账单查阅拦截：若单据自带的时间过旧，说明用户只是在查账/翻阅往期凭证！
        // 电商应用（从提交到支付完成返回）容许10分钟，其余即时收银凭证严格限制3分钟
        val isShopping = pkg in listOf(
            "com.taobao.taobao", "com.taobao.litetao", "com.tmall.wireless", "com.taobao.idlefish",
            "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew", "com.jingdong.app.mall", "me.ele"
        )
        val maxBillAge = if (isShopping) 600_000L else 180_000L
        if (billTimestamp != null && now - billTimestamp > maxBillAge) {
            return
        }

        val accountName = customAccount ?: when (pkg) {
            "com.tencent.mm" -> "微信"
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.unionpay" -> "银行卡"
            "com.taobao.taobao", "com.taobao.litetao", "com.tmall.wireless", "com.taobao.idlefish" -> "支付宝"
            "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew" -> "美团"
            "com.jingdong.app.mall" -> "京东支付"
            else -> "支付宝"
        }

        val displayName = if (partyName.isNotBlank() && !isSystemWord(partyName)) {
            partyName
        } else {
            when {
                type == TransactionType.INCOME -> "微信转账收款"
                pkg == "com.jingdong.app.mall" -> if (rawContext.contains("外卖") || rawContext.contains("送达") || rawContext.contains("骑手")) "京东外卖" else "京东购物"
                pkg in listOf("com.taobao.taobao", "com.taobao.litetao", "com.tmall.wireless") -> "淘宝购物"
                pkg in listOf("com.sankuai.meituan", "com.sankuai.meituan.takeoutnew") -> "美团外卖"
                else -> "日常消费"
            }
        }

        val catName = classifyCategory("$displayName $rawContext", type)

        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext, scope)

            // 5. 数据库订单号终身排重：若数据库中已经存过这个微信/支付宝交易单号，绝对不再记！
            if (!orderId.isNullOrBlank()) {
                val existingCount = db.recordDao().getRecordCountByOrderId(orderId)
                if (existingCount > 0) {
                    AutoRecordDedupeManager.markRecorded(applicationContext, orderId, fingerprint)
                    return@launch
                }
            }

            // 6. 数据库当天同商户同金额排重拦截：
            // 如果商户名称明确（如“肯德基”），且今天已经记过一笔完全一样的商户+金额，绝对不再记！
            if (partyName.isNotBlank() && !isSystemWord(partyName)) {
                val todayStart = AutoRecordDedupeManager.getTodayStartMillis()
                val existingMerchantCount = db.recordDao().getSameMerchantRecordCount(
                    amount = amount,
                    type = type,
                    merchant = partyName,
                    sinceTimestamp = todayStart
                )
                if (existingMerchantCount > 0) {
                    AutoRecordDedupeManager.markRecorded(applicationContext, orderId, fingerprint)
                    return@launch
                }
            }

            // 7. 数据库底层熔断防重：检查数据库内近 10 分钟 (600秒) 是否存在相同金额相同类型的记账
            val recentDupCount = db.recordDao().getRecentDuplicateCount(amount, type, now - 600_000L)
            if (recentDupCount > 0) {
                AutoRecordDedupeManager.markRecorded(applicationContext, orderId, fingerprint)
                return@launch
            }

            val remarkText = if (!orderId.isNullOrBlank()) {
                "$displayName [单号:$orderId]"
            } else {
                displayName
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

            // 写入持久化防重存储与防抖缓存（入库成功后方显式占位，确保幂等）
            AutoRecordDedupeManager.markRecorded(applicationContext, orderId, fingerprint)
            AutoRecordDedupeManager.markDebounced(pkg, type, amount)

            com.minimalist.finance.util.MilestoneEncouragementManager.checkAndCelebrate(applicationContext, scope)

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

    /**
     * 核心防误触 1：各应用主界面（首页/导航栏/功能大厅）坚决不触碰，彻底根除“点进云闪付/支付宝无操作被广告误记”
     */
    private fun isAppHomeNavigationScreen(pkg: String, texts: List<String>, joined: String): Boolean {
        // 1. 微信主界面 (底栏三要素)
        if (pkg == "com.tencent.mm") {
            if (texts.contains("通讯录") && texts.contains("发现") && texts.contains("我")) {
                return true
            }
        }
        // 2. 云闪付 (com.unionpay) 主界面与服务大厅
        // 用户仅仅点进云闪付首页，屏幕上常驻“首页/生活/卡管理/我的”或“扫一扫/付款码/乘车码”，绝不可记账！
        if (pkg == "com.unionpay") {
            val hasBottomNav = texts.contains("首页") && (texts.contains("生活") || texts.contains("卡管理") || texts.contains("我的") || texts.contains("金融"))
            val hasTopQuickActions = (texts.contains("扫一扫") || texts.contains("收付款") || texts.contains("付款码")) &&
                    (texts.contains("乘车码") || texts.contains("转账") || texts.contains("信用卡还款"))
            if (hasBottomNav || hasTopQuickActions) {
                return true
            }
        }
        // 3. 支付宝 (com.eg.android.AlipayGphone) 主界面
        if (pkg == "com.eg.android.AlipayGphone") {
            if (texts.contains("理财") && texts.contains("生活") && texts.contains("消息") && texts.contains("我的")) {
                return true
            }
            if (texts.contains("扫一扫") && texts.contains("收钱") && texts.contains("付钱") && texts.contains("出行")) {
                return true
            }
        }
        // 4. 淘宝 (com.taobao.taobao) 主界面
        if (pkg == "com.taobao.taobao") {
            // 如果页面已明确包含支付成功、买家已付款、等待卖家发货，绝不能误当成主界面拦截！
            val hasExplicitSuccess = joined.contains("买家已付款") || joined.contains("等待卖家发货") ||
                    joined.contains("等待商家发货") || joined.contains("支付成功") ||
                    joined.contains("付款成功") || joined.contains("免密支付成功") ||
                    joined.contains("自动扣款成功") || joined.contains("交易成功") ||
                    joined.contains("订单已完成") || joined.contains("订单已支付") ||
                    joined.contains("已成功付款") || joined.contains("购买成功")
            if (hasExplicitSuccess) {
                return false
            }
            if (texts.contains("消息") && texts.contains("购物车") && texts.contains("我的淘宝")) {
                return true
            }
        }
        return false
    }

    /**
     * 核心防误触：绝对拦截任何【查账、账单列表、历史明细、对账、统计、微信聊天/公众号翻看记录】页面
     * 这是彻底杜绝“进微信看付款记录/查账却重复记账”的最高优先级防线！
     */
    private fun isBrowsingHistoryOrChatting(pkg: String, texts: List<String>, joined: String): Boolean {
        val isShopping = pkg in listOf(
            "com.taobao.taobao", "com.taobao.litetao", "com.tmall.wireless", "com.taobao.idlefish",
            "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew", "com.jingdong.app.mall", "me.ele"
        )

        // 1. 微信 / 支付宝 账单列表、交易记录与流水明细页面特征（多条账单集合页）
        val universalBillKeywords = listOf(
            "全部账单", "账单列表", "账单明细", "全部交易类型", "月账单", "年账单", "按月查看",
            "收支明细", "资金流水", "零钱明细", "对账中心", "账单分类", "月度汇总",
            "交易记录", "我的账单"
        )
        if (universalBillKeywords.any { joined.contains(it) }) {
            return true
        }

        // 售后开票等辅助词：仅在非电商平台（微信/支付宝/云闪付全局查账）中作为拦截依据
        // 电商订单详情页自带“开发票/联系商户”，绝不因带有发票和联系卖家误杀正常刚完成的订单！
        if (!isShopping) {
            val nonShoppingBillKeywords = listOf("开发票", "申请开票", "联系商户", "常见问题", "投诉商家", "退款明细", "退款记录")
            if (nonShoppingBillKeywords.any { joined.contains(it) }) {
                return true
            }
        }

        // 2. 核心铁证：针对微信 (com.tencent.mm)，若同时出现“单号/订单号”以及“支付时间/交易时间”，
        // 刚在微信收银台付完款的即时完成界面绝不罗列单号与时间，出现此特征必定为微信历史账单详情翻查，坚决拦截！
        val hasOrderIdLabel = joined.contains("交易单号") || joined.contains("商户单号") ||
                joined.contains("订单号") || joined.contains("支付单号")
        val hasTimeLabel = joined.contains("支付时间") || joined.contains("交易时间") ||
                joined.contains("创建时间") || joined.contains("成交时间")
        if (pkg == "com.tencent.mm" && hasOrderIdLabel && hasTimeLabel) {
            return true
        }
        // 针对非电商应用：若出现订单号且伴随售后/对账特征，拦截！
        if (!isShopping && hasOrderIdLabel && (joined.contains("对此订单有疑问") || joined.contains("对此交易有疑问") || joined.contains("常见问题") || joined.contains("投诉商家") || joined.contains("开发票"))) {
            return true
        }
        // 针对非电商应用的历史账单明细翻查（页面明确为账单详情/交易明细且带有单号和时间）
        if (!isShopping && (joined.contains("账单详情") || joined.contains("交易明细")) && hasOrderIdLabel && hasTimeLabel) {
            return true
        }

        // 3. 筛选与统计特征：仅在非电商平台（微信/支付宝/云闪付全局查账）拦截“筛选 + 统计/全部/类型/金额”
        // 电商订单完成页自带“全部宝贝/猜你喜欢/筛选”，绝不在此误杀！
        if (!isShopping && joined.contains("筛选") && (joined.contains("统计") || joined.contains("全部") || joined.contains("类型") || joined.contains("金额"))) {
            return true
        }

        // 4. 信用卡还款、账单提醒、还款通知特征（坚决不作为日常消费记账）
        if (isCreditCardRepayment(pkg, "", texts, joined)) {
            return true
        }

        // 5. 微信聊天窗口 / 公众号 / 服务号聚合流 / 银行动账模板消息卡片特征
        // 无论里面显示什么旧凭证或卡片，只要处于微信聊天、公众号、服务号消息流环境，坚决拦截
        if (pkg == "com.tencent.mm") {
            val chatAndFeedKeywords = listOf(
                "切换到输入法", "按住说话", "按住 说话", "发送", "发消息", "自定义菜单",
                "聊天信息", "公众号", "微信号", "聊天记录", "清空聊天记录", "移出移进",
                "服务号", "订阅号", "交易提醒", "消费提醒", "动账提醒", "动账通知",
                "交易类型", "日常消费", "信用卡消费", "尾号", "卡号", "轻触阅读", "查看全文"
            )
            if (chatAndFeedKeywords.any { joined.contains(it) }) {
                return true
            }
        }

        // 6. 屏幕中出现多笔独立金额（账单列表或聊天记录流的典型特征）
        // 严格限定于微信 (com.tencent.mm)：电商订单页有原价、店铺满减、优惠券、运费、实付款，多笔金额属于正常现象，绝不可一刀切拦截！
        if (pkg == "com.tencent.mm") {
            val countCurrencies = Regex("""[¥￥]\s*-?\s*[0-9]+""").findAll(joined).count()
            if (countCurrencies >= 3) {
                return true
            }
        }

        // 7. 电商应用（淘宝/天猫/淘特/闲鱼/京东/美团/饿了么）订单列表与历史查单翻账流特征拦截！
        // 坚决杜绝“点进淘宝看全部订单/待发货/待收货/订单详情，却将历史旧订单误记账”！
        if (isShopping) {
            // (1) 【订单详情页铁证拦截】：用户从列表点进某笔历史订单查看详情、物流轨迹或联系商家
            // 即时支付完成收银台绝对不会包含“订单详情”、“订单编号”、“申请售后”、“联系卖家”
            val hasOrderDetailMarkers = listOf(
                "订单详情", "订单编号", "订单号", "申请售后", "联系卖家",
                "订单信息", "下单时间", "成交时间"
            ).any { joined.contains(it) }
            if (hasOrderDetailMarkers) {
                return true
            }

            // (2) 订单分类 Tab 栏与订单搜索顶栏特征（即时单笔收银台绝不会有一整排订单分类 Tab 栏）：
            val statusTabCount = listOf(
                "待付款", "待发货", "待收货", "待评价", 
                "退款/售后", "退款/退货", "待收", "已完成", "已取消"
            ).count { joined.contains(it) }
            val hasOrderListNav = listOf(
                "全部订单", "我的订单", "搜索订单", "订单列表", "全部", "管理", "筛选", "订单回收站"
            ).any { joined.contains(it) }
            if (statusTabCount >= 2 && (hasOrderListNav || joined.contains("全部") || joined.contains("管理") || joined.contains("筛选"))) {
                return true
            }

            // (3) 订单卡片列表多单共存铁证（统计页面中“交易成功”、“实付款”、“查看物流”、“追加评价”出现次数）：
            val countTradeSuccess = Regex("""交易成功""").findAll(joined).count()
            val countBuyerPaid = Regex("""买家已付款""").findAll(joined).count()
            val countActualPay = Regex("""实付款""").findAll(joined).count()
            val countViewLogistics = Regex("""查看物流""").findAll(joined).count()
            val countEvaluate = Regex("""(追加评价|去评价|评价晒单)""").findAll(joined).count()
            val countRebuy = Regex("""(再买一单|再次购买|常购清单)""").findAll(joined).count()
            val countOrderNo = Regex("""(订单号|订单编号)\s*[:：]?\s*\d{8,}""").findAll(joined).count()

            // 多卡片共存 → 铁证直接拦截
            if (countActualPay >= 2) return true
            if (countTradeSuccess >= 2 || countBuyerPaid >= 2) return true
            if (countViewLogistics >= 2 || countEvaluate >= 2 || countRebuy >= 2) return true
            if (countOrderNo >= 2) return true

            // (4) 【列表底部单卡片残留封堵】：
            // 即使用户滑到底部导致 Tab 栏滚出屏幕、只露出最后 1 张卡片，
            // 只要出现“查看物流”或“追加评价”或“再买一单”等历史订单专属操作按钮，100% 拦截！
            val hasHistoryOnlyActions = countViewLogistics >= 1 || countEvaluate >= 1 || countRebuy >= 1
            if (hasHistoryOnlyActions && (countTradeSuccess >= 1 || countActualPay >= 1)) {
                return true
            }

            // (5) 单卡片无明确即时支付标志拦截：
            // 即时支付完成页必定带有“支付成功”或“付款成功”，而历史订单列表卡片仅有“交易成功”或“买家已付款”
            val hasPaySuccessMarker = joined.contains("支付成功") || joined.contains("付款成功") || joined.contains("支付结果")
            if (!hasPaySuccessMarker && (countTradeSuccess >= 1 || countBuyerPaid >= 1) && countActualPay >= 1) {
                return true
            }

            // (6) 伴随订单搜索管理栏与订单状态
            if (hasOrderListNav && (countTradeSuccess >= 1 || countActualPay >= 1) &&
                (joined.contains("待付款") || joined.contains("待发货") || joined.contains("待收货") || joined.contains("退款/售后"))) {
                return true
            }
        }

        return false
    }

    /**
     * 信用卡还款全方位精准排他拦截
     */
    private fun isCreditCardRepayment(pkg: String, partyName: String, texts: List<String>, joined: String): Boolean {
        // 1. 商户名称明确为还款相关
        val repaymentMerchantKeywords = listOf(
            "还款", "信用卡", "信用卡中心", "卡还款", "还卡", "溢缴款"
        )
        if (repaymentMerchantKeywords.any { partyName.contains(it) }) {
            return true
        }

        // 2. 页面/通知文本包含强还款特征
        val repaymentKeywords = listOf(
            "还款", "还款成功", "信用卡还款", "还款提醒", "还款金额", "还款入账", "已还款",
            "自动还款", "本期账单", "账单提醒", "账单通知", "应还金额", "应还款", "最低还款",
            "对账单", "溢缴款", "分期还款", "还信用卡", "还款代扣", "信用卡还款到账",
            "账单已出", "本期应还", "最后还款日", "已成功还款"
        )
        if (repaymentKeywords.any { joined.contains(it) }) {
            return true
        }

        // 3. 信用卡 + (账单/还款/成功/入账) 组合特征
        if (joined.contains("信用卡") && (
            joined.contains("还款") || joined.contains("账单") || joined.contains("入账") ||
            joined.contains("应还") || joined.contains("成功还款") || joined.contains("溢缴") ||
            joined.contains("代扣")
        )) {
            return true
        }

        // 4. 云闪付 (com.unionpay) 银行卡还款与跨行互转专属指纹拦截
        if (pkg == "com.unionpay") {
            // (1) 拦截一切云闪付入账通知（绝大多数为还款到账、跨行转账或信用卡溢缴入账，非商业经营收款）
            if (joined.contains("入账通知") || joined.contains("汇款入账") || joined.contains("银联入账") ||
                Regex(""".*尾号为\d{4}的银行卡.*入账.*""").containsMatchIn(joined)) {
                return true
            }

            // (2) 拦截纯卡号转出通知 (无任何真实商户名字，仅有“尾号为xxxx的银行卡于...消费xx元”)
            val hasCardPattern = Regex(""".*尾号为\d{4}的银行卡.*于.*分\s*消费.*""").containsMatchIn(joined) ||
                                 Regex(""".*尾号为\d{4}的银行卡.*付款成功.*""").containsMatchIn(joined)
            val hasRealMerchant = partyName.isNotBlank() &&
                                  partyName != "日常消费" &&
                                  partyName != "微信转账收款" &&
                                  !isSystemWord(partyName) &&
                                  (joined.contains("在【") || joined.contains("向【") || joined.contains("商户") || joined.contains("店"))
            if (hasCardPattern && !hasRealMerchant) {
                if (joined.contains("信用卡") || joined.contains("还款") || joined.contains("转账") || joined.contains("对公")) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * 未付款 / 待支付 / 结算中 / 选餐购物车 / 选规格拦截
     */
    private fun isPrePaymentOrCart(joined: String): Boolean {
        val prePaymentKeywords = listOf(
            "提交订单", "去支付", "立即支付", "立即付款", "确认付款", "确认支付", "待付款", "待支付",
            "未支付", "去结算", "立即购买", "去买单", "选择支付方式", "找人付", "去拼单",
            "确认订单", "还需支付", "放弃支付", "继续支付", "确认下单", "去凑单", "加入购物车",
            "选好了", "选择优惠券", "请输入支付密码", "验证支付密码", "指纹验证", "还差",
            "起送", "另需配送费", "外卖专享", "差￥", "差¥", "加一份", "去结算(",
            "未完成支付", "待结账", "未付款", "订单待付款", "需付款", "需支付", "合计待支付"
        )
        return prePaymentKeywords.any { joined.contains(it) }
    }

    private fun extractOrderIdFromSingleText(text: String): String? {
        val regex = Regex("""(?:交易单号|商户单号|订单号|交易号|单号)[:：\s]+([A-Za-z0-9_]{8,40})""")
        return regex.find(text)?.groupValues?.get(1)
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

        // 0. 优先扫描明确的【付款时间 / 支付时间 / 成交时间】（严防被早前几小时甚至几天的“创建时间/下单时间”误导）
        val payTimeLabels = listOf("付款时间", "支付时间", "成交时间")
        for (i in texts.indices) {
            val t = texts[i]
            for (label in payTimeLabels) {
                if (t.contains(label)) {
                    val candidate = t.substringAfter(label)
                    val m = regexFull.find(candidate)
                    if (m != null) {
                        val str = m.value.replace('/', '-').replace('.', '-').trim()
                        try {
                            val date = if (str.length > 16) sdfFull.parse(str) else sdfShort.parse(str)
                            if (date != null) return date.time
                        } catch (_: Exception) {}
                    }
                    if (i + 1 < texts.size) {
                        val mNext = regexFull.find(texts[i + 1])
                        if (mNext != null) {
                            val str = mNext.value.replace('/', '-').replace('.', '-').trim()
                            try {
                                val date = if (str.length > 16) sdfFull.parse(str) else sdfShort.parse(str)
                                if (date != null) return date.time
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

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

        // 4. 月-日 时:分 格式 (如 "10-11 12:51", "10/11 12:51")
        val regexMonthDayShort = Regex("""([0-1]?[0-9])[-/.]([0-3]?[0-9])\s+([0-2][0-9]:[0-5][0-9](?::[0-5][0-9])?)""")
        for (t in texts) {
            val m = regexMonthDayShort.find(t)
            if (m != null) {
                val month = m.groupValues[1].toIntOrNull() ?: 1
                val day = m.groupValues[2].toIntOrNull() ?: 1
                val timeStr = m.groupValues[3]
                try {
                    val sdf = if (timeStr.length > 5) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                              else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                    val date = sdf.parse(String.format(java.util.Locale.CHINA, "%04d-%02d-%02d %s", currentYear, month, day, timeStr))
                    if (date != null) return date.time
                } catch (_: Exception) {}
            }
        }

        // 4.1 纯月日简写格式 (如 "06.13", "06-04", "10.15", 常见于电商订单卡片日期)
        val regexPureMonthDay = Regex("""^([0-1]?[0-9])[.-]([0-3]?[0-9])$""")
        for (t in texts) {
            val trimmed = t.trim()
            val m = regexPureMonthDay.matchEntire(trimmed)
            if (m != null) {
                val month = m.groupValues[1].toIntOrNull() ?: 0
                val day = m.groupValues[2].toIntOrNull() ?: 0
                if (month in 1..12 && day in 1..31) {
                    val cal = java.util.Calendar.getInstance()
                    val nowMonth = cal.get(java.util.Calendar.MONTH) + 1
                    var targetYear = currentYear
                    if (month > nowMonth) {
                        targetYear -= 1
                    }
                    cal.set(java.util.Calendar.YEAR, targetYear)
                    cal.set(java.util.Calendar.MONTH, month - 1)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            }
        }

        // 5. 纯时间格式 (如 "12:51", "12:51:30") 提取单据时间与当前做旧单拦截
        val regexTimeOnly = Regex("""(?:交易时间|支付时间|时间)?\s*([0-2][0-9]:[0-5][0-9](?::[0-5][0-9])?)""")
        for (t in texts) {
            if (t.contains("时间") || t.contains("12:") || t.contains("13:") || t.contains("14:")) {
                val m = regexTimeOnly.find(t)
                if (m != null) {
                    val timeParts = m.groupValues[1].split(":")
                    if (timeParts.size >= 2) {
                        val hour = timeParts[0].toIntOrNull() ?: 0
                        val minute = timeParts[1].toIntOrNull() ?: 0
                        val second = if (timeParts.size > 2) timeParts[2].toIntOrNull() ?: 0 else 0
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                        cal.set(java.util.Calendar.MINUTE, minute)
                        cal.set(java.util.Calendar.SECOND, second)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        return cal.timeInMillis
                    }
                }
            }
        }

        return null
    }


    // 金额噪声排他：余额类、优惠红包类、卡类、返利类，坚决不可作为消费金额
    private fun isAmountNoise(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("余额") || lower.contains("可用") || lower.contains("账户余额") ||
               lower.contains("卡余额") || lower.contains("零钱余额") || lower.contains("储值") ||
               lower.contains("e卡") || lower.contains("礼品卡") || lower.contains("购物卡") ||
               lower.contains("红包") || lower.contains("优惠") || lower.contains("立减") ||
               lower.contains("满减") || lower.contains("抵扣") || lower.contains("京豆") ||
               lower.contains("淘金币") || lower.contains("积分") || lower.contains("返利") ||
               lower.contains("省¥") || lower.contains("省￥") || lower.contains("运费险") ||
               lower.contains("原价") || lower.contains("划线价")
    }

    private fun isSystemWord(word: String): Boolean {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return true

        // 1. 状态栏网速过滤（如 "6.68KB/s", "0.13 KB/s", "1.95MB/s", "120 B/s", "10 K/s" 等）
        if (cleaned.matches(Regex("""^[0-9]+(\.[0-9]+)?\s*(B/s|KB/s|MB/s|GB/s|Kb/s|Mb/s|k/s|m/s|b/s)$""", RegexOption.IGNORE_CASE)) ||
            cleaned.contains("/s", ignoreCase = true)) {
            return true
        }

        // 2. 状态栏系统状态（网络信号、电池电量、系统时间）
        if (cleaned in listOf("4G", "5G", "HD", "VoLTE", "WiFi", "WIFI") ||
            cleaned.matches(Regex("""^[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?$""")) ||
            cleaned.matches(Regex("""^[0-9]{1,3}%$"""))) {
            return true
        }

        // 3. 优惠券、红包、立减金与营销文本（如 "0.01元红包", "5元立减券", "平台优惠" 等绝非商户名）
        if (cleaned.contains("红包") || cleaned.contains("优惠券") || cleaned.contains("代金券") ||
            cleaned.contains("立减券") || cleaned.contains("消费券") || cleaned.contains("抵扣券") ||
            cleaned.contains("满减") || cleaned.contains("抵扣") || cleaned.contains("京豆") ||
            cleaned.contains("淘金币") || cleaned.contains("返利")) {
            return true
        }
        if (cleaned.matches(Regex("""^[0-9]+(\.[0-9]+)?元?(红包|优惠|券|立减|抵扣|满减).*"""))) {
            return true
        }

        // 4. 常见业务系统关键字
        return cleaned in listOf(
            "微信支付", "支付宝", "京东支付", "京东白条", "京东E卡", "E卡", "礼品卡", "购物卡",
            "支付成功", "付款成功", "交易成功", "完成", "收款成功", "账单详情", "人民币",
            "零钱余额", "账户余额", "卡余额", "转账时间", "收款时间", "你已收款", "资金已存入零钱", "你已收款，资金已存入零钱",
            "微信支付凭证", "付款凭证", "支付方式", "扣款成功", "交易明细", "查看账单", "查看详情",
            "返回商家", "关闭", "确定", "返回", "商家小程序", "我的订单", "取餐号", "订单已完成",
            "全部账单", "常见问题", "账单服务", "退款", "退款成功", "退款申请", "退款中", "退款明细", "退款结果",
            "日常", "日常消费", "消费", "交易", "交易类型", "交易摘要", "信用卡消费", "尾号", "银行卡", "信用卡还款", "还款"
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
        if (pkg == "com.jingdong.app.mall") {
            if (joined.contains("京东白条") || joined.contains("白条")) {
                return "京东白条"
            }
            if (joined.contains("京东E卡") || joined.contains("E卡") || joined.contains("礼品卡")) {
                return "京东E卡"
            }
            if (joined.contains("京东支付")) {
                return "京东支付"
            }
        }
        return null
    }

    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.packageName?.toString() == "com.android.systemui") return
        node.text?.let {
            val str = it.toString().trim()
            if (str.isNotEmpty()) list.add(str)
        }
        node.contentDescription?.let {
            val str = it.toString().trim()
            if (str.isNotEmpty()) list.add(str)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            node.hintText?.let {
                val str = it.toString().trim()
                if (str.isNotEmpty()) list.add(str)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, list)
        }
    }

    // ====== 增强金额提取算法 ======
    private fun extractAmountFromList(texts: List<String>): Double? {
        // 0. 最高优先级：收银台与电商明确结算标签（实付款/实付/实付金额/实际支付/应付金额/总计/合计）
        // 杜绝抓取商品原价、划线价、抵扣券或优惠金额
        val finalAmountLabels = listOf("实付款", "实付金额", "实际支付", "实付", "应付金额", "总计", "合计")
        for (i in texts.indices) {
            val t = texts[i].trim()
            for (label in finalAmountLabels) {
                // 形式 A: 标签与金额在同一个节点中 (如 "实付款: ¥23.90", "实付23.90元", "合计：¥88.00", "实付款: ¥0.00")
                if (t.contains(label)) {
                    val afterLabel = t.substringAfter(label).trim()
                    val m = Regex("""[¥￥]?\s*([0-9]+\.?[0-9]*)""").find(afterLabel)
                    if (m != null) {
                        val v = m.groupValues[1].toDoubleOrNull()
                        // 关键修复：允许 v >= 0.0！若实付款明确为 0.00，立即返回 0.0，绝不跌入备用规则误抓余额！
                        if (v != null && v >= 0.0) return v
                    }
                }
                // 形式 B: 标签与金额分立于相邻的 1~4 个节点 (如 node i: "实付款", node i+1: "¥", node i+2: "23.90" 或 "0.00")
                if (t == label || t == "$label:" || t == "$label：" || (t.startsWith(label) && t.length <= label.length + 4)) {
                    for (offset in 1..4) {
                        if (i + offset < texts.size) {
                            val rawCand = texts[i + offset].trim()
                            if (rawCand == "¥" || rawCand == "￥" || rawCand.isEmpty() ||
                                rawCand.startsWith("含运费") || rawCand.startsWith("（含运费")) {
                                continue
                            }
                            var combinedAmountStr = rawCand.removePrefix("¥").removePrefix("￥").removePrefix("-").trim()
                            if (i + offset + 1 < texts.size) {
                                val nextDec = texts[i + offset + 1].trim()
                                if (nextDec.startsWith(".") && nextDec.length in 2..3 && nextDec.substring(1).all { it.isDigit() }) {
                                    combinedAmountStr += nextDec
                                }
                            }
                            val v = combinedAmountStr.toDoubleOrNull()
                            if (v != null && v >= 0.0) return v
                            val m = Regex("""[¥￥]?\s*([0-9]+\.?[0-9]*)""").find(rawCand)
                            if (m != null) {
                                val v2 = m.groupValues[1].toDoubleOrNull()
                                if (v2 != null && v2 >= 0.0) return v2
                            }
                        }
                    }
                }
            }
        }

        // 1. 带有货币符号的字符串 (如 "¥ 1.20", "¥1.20", "￥12.50", "-¥4.00", "¥ -4.00", "¥100")
        // 严格排除余额、红包、优惠、立减等噪声节点，防止抓到卡余额
        val regexSymbol = Regex("""[¥￥]\s*-?\s*([0-9]+\.?[0-9]*)""")
        for (i in texts.indices) {
            val t = texts[i].trim()
            val prev = if (i > 0) texts[i - 1].trim() else ""
            if (isAmountNoise(t) || isAmountNoise(prev) || t.startsWith("-") || t.contains("-¥") || t.contains("-￥")) {
                continue
            }
            val m = regexSymbol.find(t)
            if (m != null) {
                val v = m.groupValues[1].toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }

        // 2. 货币符号与数字分立在相邻节点 (如 node 1: "¥" / "￥", node 2: "1.20" 或 "100")
        for (i in texts.indices) {
            val t = texts[i].trim()
            if ((t == "¥" || t == "￥") && i + 1 < texts.size) {
                val prev = if (i > 0) texts[i - 1].trim() else ""
                val nextStr = texts[i + 1].trim()
                if (isAmountNoise(prev) || isAmountNoise(nextStr) || nextStr.startsWith("-")) {
                    continue
                }
                val v = nextStr.removePrefix("-").trim().toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }

        // 3. 带有"元"的文本 (如 "4.00元", "12元", "-4.00元", "100元")
        val regexYuan = Regex("""(?:^|[^\d])-?\s*([0-9]+\.?[0-9]*)\s*元""")
        for (i in texts.indices) {
            val t = texts[i].trim()
            val prev = if (i > 0) texts[i - 1].trim() else ""
            if (isAmountNoise(t) || isAmountNoise(prev) || t.startsWith("-")) {
                continue
            }
            val m = regexYuan.find(t)
            if (m != null) {
                val v = m.groupValues[1].toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }

        // 3.1 金额与"元"分立在相邻节点 (如 node 1: "15.00", node 2: "元")
        for (i in texts.indices) {
            if (i + 1 < texts.size && texts[i + 1].trim() == "元") {
                val prev = if (i > 0) texts[i - 1].trim() else ""
                val cand = texts[i].trim().removePrefix("-").trim()
                if (isAmountNoise(prev) || isAmountNoise(cand)) continue
                val v = cand.toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }

        // 4. 独立纯金额节点 (如 "12.50", "128.00", 兼容一位或两位小数)
        for (i in texts.indices) {
            val t = texts[i].trim()
            val prev = if (i > 0) texts[i - 1].trim() else ""
            if (isAmountNoise(t) || isAmountNoise(prev) || t.startsWith("-")) continue
            val cleaned = t.removePrefix("-").trim()
            if (cleaned.matches(Regex("""^[0-9]+\.[0-9]{1,2}$"""))) {
                val v = cleaned.toDoubleOrNull()
                if (v != null && v > 0) return v
            }
        }

        // 5. 紧随在结算标签后面的纯数值 (支持整数 "100")
        val amountPrefixes = listOf("实付款", "金额", "合计", "成功付款", "付款成功", "支付成功", "总计")
        for (i in texts.indices) {
            val t = texts[i].trim()
            for (p in amountPrefixes) {
                if (t == p || t == "$p:" || t == "$p：") {
                    if (i + 1 < texts.size) {
                        val next = texts[i + 1].trim()
                        if (isAmountNoise(next)) continue
                        val cand = next.removePrefix("¥").removePrefix("￥").removePrefix("-").trim()
                        val v = cand.toDoubleOrNull()
                        if (v != null && v >= 0.0) return v
                    }
                } else if (t.startsWith(p)) {
                    val cand = t.substringAfter(p).removePrefix(":").removePrefix("：").removePrefix("¥").removePrefix("￥").trim()
                    if (isAmountNoise(cand)) continue
                    val v = cand.toDoubleOrNull()
                    if (v != null && v >= 0.0) return v
                }
            }
        }
        return null
    }

    private fun extractAmountFromSingleText(text: String): Double? {
        if (isAmountNoise(text)) return null
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
        val regexPure = Regex("""(?:支付|消费|扣款|转账|收款|到账|支出|付款)\s*[:：]?\s*-?\s*([0-9]+(?:\.[0-9]{1,2})?)""")
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
            "特约商户", "交易对象", "收款方", "收款人", "商户", "店名", "商家",
            "店铺", "店铺名称", "掌柜", "卖家", "购买商家"
        )
        for (i in texts.indices) {
            val t = texts[i].trim()
            for (label in primaryLabels) {
                if (t == label || t == "$label:" || t == "$label：") {
                    if (i + 1 < texts.size) {
                        val candidate = texts[i + 1].trim()
                        if (candidate.isNotBlank() && !isSystemWord(candidate) && !isAmountNoise(candidate) &&
                            !candidate.startsWith("¥") && !candidate.startsWith("￥") &&
                            !Regex("""^[0-9]""").containsMatchIn(candidate)) {
                            return candidate
                        }
                    }
                } else if (t.startsWith(label) && (t.contains(":") || t.contains("："))) {
                    val clean = t.substringAfter(":").substringAfter("：").trim()
                    if (clean.isNotBlank() && !isSystemWord(clean) && !isAmountNoise(clean) &&
                        !clean.startsWith("¥") && !clean.startsWith("￥") &&
                        !Regex("""^[0-9]""").containsMatchIn(clean)) {
                        return clean
                    }
                }
            }
        }

        // 第二优先级：商品/详情标签（如果包含停车费或商品名，清洗括号和车牌号）
        val secondaryLabels = listOf("商品", "商品说明", "商品名称", "交易商品", "商品信息", "宝贝名称", "购买商品")
        for (i in texts.indices) {
            val t = texts[i].trim()
            for (label in secondaryLabels) {
                if (t == label || t == "$label:" || t == "$label：") {
                    if (i + 1 < texts.size) {
                        val candidate = texts[i + 1].trim()
                        if (candidate.isNotBlank() && !isSystemWord(candidate) && !isAmountNoise(candidate) &&
                            !Regex("""^[0-9]""").containsMatchIn(candidate)) {
                            if (candidate.contains("停车")) {
                                return "停车缴费"
                            }
                            return candidate
                        }
                    }
                } else if (t.startsWith(label) && (t.contains(":") || t.contains("："))) {
                    val clean = t.substringAfter(":").substringAfter("：").trim()
                    if (clean.isNotBlank() && !isSystemWord(clean) && !isAmountNoise(clean) &&
                        !Regex("""^[0-9]""").containsMatchIn(clean)) {
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
                        if (prev.length in 2..32 && !isSystemWord(prev) && !isAmountNoise(prev) &&
                            !prev.startsWith("¥") && !prev.startsWith("￥") &&
                            !prev.contains("支付") && !prev.contains("付款") && !prev.contains("详情") &&
                            !prev.contains("/s", ignoreCase = true) &&
                            !Regex("""^[0-9]""").containsMatchIn(prev)) {
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
        val m4 = Regex("""(?:购买|宝贝|商品)的?【(.*?)】""").find(text)
        if (m4 != null) {
            val name = m4.groupValues[1].trim()
            if (name.isNotEmpty() && !isSystemWord(name)) return name
        }
        return ""
    }
}
