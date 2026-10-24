package com.minimalist.finance.util

import java.util.Collections
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 前置支付动作生命周期状态机 (Payment Initiation State Machine)
 *
 * 核心设计哲学：
 * 任何即时收银台完成页（如微信「支付成功」、支付宝「支付成功」），在物理世界中都绝不可能凭空产生！
 * 它必须且必然由用户在过去 120 秒内的主动支付动作（出示付款码、扫一扫、输入支付密码、指纹/面容核验或通知推送）触发。
 *
 * 本状态机彻底杜绝以下致命体验痛点：
 * 1. 付款后将手机放入裤兜未关闭页面，10分钟/半小时后再次解锁掏出手机，界面残留「支付成功」导致二次重复入库；
 * 2. 停留在微信/支付宝收银台结果页接电话或聊天，跨分钟后触发去重窗口超时二次记账；
 * 3. 单次付款码出示只能且必须消费唯一的 Session Token，彻底实现「一次付款动作 = 唯一且最多一笔记账」。
 */
object PaymentInitiationTracker {

    // 记录每个支付应用的最新支付动作发起时间戳
    private val lastInitiationTime = ConcurrentHashMap<String, Long>()

    // 记录每个支付应用当前待消费的支付 Session Token
    private val currentSessionToken = ConcurrentHashMap<String, String>()

    // 已成功消费入账的 Session Token 集合 (保留最近 128 条，防止内存溢出)
    private val consumedTokens = Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 128
        }
    })

    /**
     * 标记检测到真实支付前置动作（出示付款码、扫一扫、密码/指纹/确认付款）
     * @return 生成的本次支付动作唯一 Token
     */
    @Synchronized
    fun markInitiated(pkg: String): String {
        val now = System.currentTimeMillis()
        lastInitiationTime[pkg] = now
        val token = "${pkg}_${now}_${UUID.randomUUID().toString().take(8)}"
        currentSessionToken[pkg] = token
        return token
    }

    /**
     * 查询指定应用当前是否存在新鲜且尚未被消费的支付前置动作（默认 120 秒内）
     */
    @Synchronized
    fun hasFreshUnconsumedInitiation(pkg: String, maxAgeMs: Long = 120_000L): Boolean {
        val now = System.currentTimeMillis()
        val initTime = lastInitiationTime[pkg] ?: return false
        if (now - initTime > maxAgeMs) return false
        val token = currentSessionToken[pkg] ?: return false
        return !consumedTokens.contains(token)
    }

    /**
     * 查询指定应用在指定窗口内是否有支付动作发起（不核销）
     */
    @Synchronized
    fun hasRecentInitiation(pkg: String, maxAgeMs: Long = 30_000L): Boolean {
        val now = System.currentTimeMillis()
        val initTime = lastInitiationTime[pkg] ?: return false
        return (now - initTime) <= maxAgeMs
    }

    /**
     * 获取当前未消费的 Token（若有效）
     */
    @Synchronized
    fun getCurrentToken(pkg: String, maxAgeMs: Long = 120_000L): String? {
        val now = System.currentTimeMillis()
        val initTime = lastInitiationTime[pkg] ?: return null
        if (now - initTime > maxAgeMs) return null
        val token = currentSessionToken[pkg] ?: return null
        return if (!consumedTokens.contains(token)) token else null
    }

    /**
     * 校验并核销当前支付动作 Token。
     * 若成功，该 Token 即刻标记为已消费，同一收银台即使多次刷新或稍后重开，也绝不可能二次入账。
     */
    @Synchronized
    fun validateAndConsume(pkg: String, maxAgeMs: Long = 120_000L): Pair<Boolean, String> {
        val now = System.currentTimeMillis()
        val initTime = lastInitiationTime[pkg] ?: 0L
        val token = currentSessionToken[pkg]

        if (initTime == 0L || (now - initTime) > maxAgeMs) {
            val elapsedSec = if (initTime == 0L) -1 else (now - initTime) / 1000
            return Pair(false, "前置支付动作超时或未发生 (距离上次前置动作: ${elapsedSec}s, 阈值: ${maxAgeMs / 1000}s)")
        }

        if (token == null || consumedTokens.contains(token)) {
            return Pair(false, "当前收银台会话已记账完成 (Token 已消费: $token)")
        }

        consumedTokens.add(token)
        return Pair(true, token)
    }

    /**
     * 释放指定 Token（若后续逻辑因非消费/还款等原因取消入账，释放锁以便重试）
     */
    @Synchronized
    fun releaseToken(pkg: String, token: String?) {
        if (token != null) {
            consumedTokens.remove(token)
        }
    }

    /**
     * 清理所有状态（测试或重置时使用）
     */
    @Synchronized
    fun clearAll() {
        lastInitiationTime.clear()
        currentSessionToken.clear()
        consumedTokens.clear()
    }
}
