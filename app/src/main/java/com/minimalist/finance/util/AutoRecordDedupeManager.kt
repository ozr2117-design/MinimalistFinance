package com.minimalist.finance.util

import android.content.Context
import com.minimalist.finance.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 自动记账全维度持久化防重管理器 (Persistent Deduplication Engine)
 *
 * 核心特性：
 * 1. 交易订单号终身持久化：微信/支付宝单号本地记录，翻看历史账单永久防重；
 * 2. 交易特征指纹持久化：金额 + 商户 + 日期分钟级指纹，杜绝重复入账；
 * 3. 内存极速去重窗口：应对 Android 无障碍服务高频并发事件（消抖）；
 * 4. 自动容量维护：LRU 策略保留最新 3000 条，轻量零开销。
 */
object AutoRecordDedupeManager {
    private const val PREF_NAME = "auto_record_dedupe_store"
    private const val KEY_ORDERS = "recorded_orders"
    private const val KEY_FINGERPRINTS = "recorded_fingerprints"
    private const val MAX_STORED_COUNT = 3000

    // 内存快速缓存，避免高频打 SharedPreferences
    private val memoryOrderCache = ConcurrentHashMap<String, Long>()
    private val memoryFingerprintCache = ConcurrentHashMap<String, Long>()

    // 短时防抖缓存 (key -> timestamp)
    private val debounceCache = ConcurrentHashMap<String, Long>()

    // 内部互转与还款短时间对冲缓存: key = "$pkg-${amountStr}" -> Pair(type, timestamp)
    private val hedgePairCache = ConcurrentHashMap<String, Pair<TransactionType, Long>>()

    @Volatile
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        try {
            val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val orders = sp.getStringSet(KEY_ORDERS, emptySet()) ?: emptySet()
            val fps = sp.getStringSet(KEY_FINGERPRINTS, emptySet()) ?: emptySet()
            val now = System.currentTimeMillis()
            orders.forEach { memoryOrderCache[it] = now }
            fps.forEach { memoryFingerprintCache[it] = now }
            isInitialized = true
        } catch (_: Exception) {
            isInitialized = true
        }
    }

    /**
     * 判断订单号是否已经记过账（永久去重）
     */
    fun isOrderRecorded(context: Context, orderId: String?): Boolean {
        if (orderId.isNullOrBlank()) return false
        init(context)
        return memoryOrderCache.containsKey(orderId)
    }

    /**
     * 判断交易特征指纹是否已经记过账
     */
    fun isFingerprintRecorded(context: Context, fingerprint: String?): Boolean {
        if (fingerprint.isNullOrBlank()) return false
        init(context)
        return memoryFingerprintCache.containsKey(fingerprint)
    }

    /**
     * 同步原子预占位（Check-and-Claim）：
     * 在同一个同步锁内完成“检查 + 立即占位”，彻底消除通知栏与前台窗口毫秒级并发导致的协程异步竞态！
     * @return true 表示成功获得记账处理权；false 表示已有相同记录在处理中或 60 秒内已记过
     */
    @Synchronized
    fun tryClaim(
        context: Context,
        pkg: String,
        type: TransactionType,
        amount: Double,
        fingerprint: String?,
        orderId: String?,
        windowMs: Long = 60_000L
    ): Boolean {
        init(context)
        val now = System.currentTimeMillis()
        val debounceKey = "$pkg-${type.name}-${String.format(Locale.US, "%.2f", amount)}"

        // 1. 检查订单号是否已记录/已占位
        if (!orderId.isNullOrBlank() && memoryOrderCache.containsKey(orderId)) {
            return false
        }

        // 2. 检查交易特征指纹是否已记录/已占位
        if (!fingerprint.isNullOrBlank() && memoryFingerprintCache.containsKey(fingerprint)) {
            return false
        }

        // 3. 检查短时间内存防抖 (如 60 秒内同应用同金额同类型)
        val lastDebounceTime = debounceCache[debounceKey] ?: 0L
        if (now - lastDebounceTime < windowMs) {
            return false
        }

        // 4. 原子性即刻预占位！（在同一同步块中立刻锁定，后续并发调用瞬间被拦截）
        debounceCache[debounceKey] = now
        if (!fingerprint.isNullOrBlank()) {
            memoryFingerprintCache[fingerprint] = now
        }
        if (!orderId.isNullOrBlank()) {
            memoryOrderCache[orderId] = now
        }

        return true
    }

    /**
     * 释放预占位（在入库被数据库排重拦截、还款拦截或异常终止时调用，避免死锁）
     */
    @Synchronized
    fun releaseClaim(
        pkg: String,
        type: TransactionType,
        amount: Double,
        fingerprint: String?,
        orderId: String?
    ) {
        val debounceKey = "$pkg-${type.name}-${String.format(Locale.US, "%.2f", amount)}"
        debounceCache.remove(debounceKey)
        if (!fingerprint.isNullOrBlank()) {
            memoryFingerprintCache.remove(fingerprint)
        }
        if (!orderId.isNullOrBlank()) {
            memoryOrderCache.remove(orderId)
        }
    }

    /**
     * 短时间内存防抖只读检查 (如 60 秒内同应用同金额同类型是否已被记录)
     */
    fun isDebounced(pkg: String, type: TransactionType, amount: Double, windowMs: Long = 60_000L): Boolean {
        val now = System.currentTimeMillis()
        val key = "$pkg-${type.name}-${String.format(Locale.US, "%.2f", amount)}"
        val lastTime = debounceCache[key] ?: 0L
        return (now - lastTime < windowMs)
    }

    /**
     * 显式标记完成防抖占用（仅在确认真正执行入库时调用，确保幂等）
     */
    fun markDebounced(pkg: String, type: TransactionType, amount: Double) {
        val now = System.currentTimeMillis()
        val key = "$pkg-${type.name}-${String.format(Locale.US, "%.2f", amount)}"
        debounceCache[key] = now
        // 清理超过 10 分钟的防抖缓存
        if (debounceCache.size > 200) {
            val it = debounceCache.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (now - entry.value > 600_000L) {
                    it.remove()
                }
            }
        }
    }

    /**
     * 云闪付/银行卡内部转账与还款双向对冲检测:
     * 若在短时间 (90 秒内) 发生同包名同金额但相反收支类型 (一进一出) 的交易，判定为互转/还款对冲，返回 true
     * 加注同步锁，确保复合读取与写入的绝对原子性
     */
    @Synchronized
    fun checkAndRegisterHedge(pkg: String, type: TransactionType, amount: Double, windowMs: Long = 90_000L): Boolean {
        val now = System.currentTimeMillis()
        val key = "$pkg-${String.format(Locale.US, "%.2f", amount)}"
        val existing = hedgePairCache[key]
        if (existing != null) {
            val (existingType, timestamp) = existing
            if (now - timestamp < windowMs && existingType != type) {
                hedgePairCache.remove(key)
                return true
            }
        }
        hedgePairCache[key] = Pair(type, now)
        // 清理超过 10 分钟的对冲缓存
        if (hedgePairCache.size > 200) {
            val it = hedgePairCache.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (now - entry.value.second > 600_000L) {
                    it.remove()
                }
            }
        }
        return false
    }

    /**
     * 标记该笔交易已完成记账并持久化
     */
    fun markRecorded(context: Context, orderId: String?, fingerprint: String?) {
        init(context)
        val now = System.currentTimeMillis()
        var needPersist = false

        if (!orderId.isNullOrBlank()) {
            memoryOrderCache[orderId] = now
            needPersist = true
        }

        if (!fingerprint.isNullOrBlank()) {
            memoryFingerprintCache[fingerprint] = now
            needPersist = true
        }

        if (needPersist) {
            saveToPreferencesAsync(context)
        }
    }

    private val persistLock = Any()

    private fun saveToPreferencesAsync(context: Context) {
        synchronized(persistLock) {
            try {
                val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val orders = HashSet(memoryOrderCache.keys.toList().takeLast(MAX_STORED_COUNT))
                val fps = HashSet(memoryFingerprintCache.keys.toList().takeLast(MAX_STORED_COUNT))
                sp.edit()
                    .putStringSet(KEY_ORDERS, orders)
                    .putStringSet(KEY_FINGERPRINTS, fps)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * 构建稳定交易指纹
     */
    fun buildFingerprint(
        pkg: String,
        type: TransactionType,
        amount: Double,
        merchant: String,
        billTimestamp: Long?
    ): String {
        val amountStr = String.format(Locale.US, "%.2f", amount)
        val cleanMerchant = merchant.trim()
        val timeKey = if (billTimestamp != null && billTimestamp > 0L) {
            // 精确到分钟 (yyyyMMddHHmm)
            val sdf = SimpleDateFormat("yyyyMMddHHmm", Locale.CHINA)
            sdf.format(Date(billTimestamp))
        } else {
            // 默认取当天日期 (yyyyMMdd)
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
            sdf.format(Date())
        }
        return "${pkg}_${type.name}_${amountStr}_${cleanMerchant}_$timeKey"
    }

    /**
     * 获取今天 00:00:00 的毫秒时间戳
     */
    fun getTodayStartMillis(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
