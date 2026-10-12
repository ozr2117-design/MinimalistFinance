package com.minimalist.finance.data.model

/**
 * 本月轻量预算模型 (防暴走、日均可用额度、消耗进度)
 */
data class MonthlyBudgetInfo(
    val budgetAmount: Double = 0.0,          // 设定总预算 (0.0 表示未设置)
    val spentAmount: Double = 0.0,           // 当月实际支出
    val remainingBudget: Double = 0.0,       // 预算剩余可用额度 (可负数)
    val daysInMonth: Int = 30,               // 当月总天数
    val dayOfMonth: Int = 1,                 // 今天是当月第几天
    val daysRemaining: Int = 30,             // 当月剩余天数 (含今天)
    val dailyAvailable: Double = 0.0,        // 剩余日均可用额度
    val usagePercent: Float = 0f,            // 预算消耗百分比 (0.0f ~ 100f+)
    val isOverBudget: Boolean = false        // 是否已超支
) {
    val statusText: String
        get() = when {
            budgetAmount <= 0.0 -> "未设预算"
            isOverBudget -> "已严重超支"
            usagePercent >= 80f -> "额度紧张"
            usagePercent >= 50f -> "进度合理"
            else -> "额度健康"
        }

    val statusColor: Long
        get() = when {
            budgetAmount <= 0.0 -> 0xFF888888
            isOverBudget -> 0xFFFF5252
            usagePercent >= 80f -> 0xFFFF9800
            else -> 0xFF4CAF50
        }
}

/**
 * 刚需生存 vs 享受生活 消费结构模型
 */
data class LifestyleVsNecessity(
    val necessityExpense: Double = 0.0,      // 刚需生存支出 (住行医水电通信还贷等)
    val lifestyleExpense: Double = 0.0,      // 享受品质支出 (食玩衣娱乐外卖购物等)
    val otherExpense: Double = 0.0,          // 其他支出
    val totalExpense: Double = 0.0,          // 总支出
    val necessityRatio: Float = 0f,          // 刚需占比 (0f ~ 100f)
    val lifestyleRatio: Float = 0f,          // 享受品质占比 (0f ~ 100f)
    val insightAdvice: String = ""           // 温度与正向反馈建议文案
)

/**
 * 分类支出排行榜单项
 */
data class CategoryExpenseRank(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,                   // 占当月总支出百分比 (0f ~ 100f)
    val count: Int                           // 笔数
)

/**
 * 月度宏观洞察与温度小结
 */
data class MonthlyInsightSummary(
    val totalTransactions: Int = 0,          // 本月记账总笔数
    val daysRecorded: Int = 0,               // 坚持记账天数
    val avgExpensePerTx: Double = 0.0,       // 单笔均销
    val maxSingleExpense: Double = 0.0,      // 本月单笔最大支出
    val encouragement: String = ""           // 财务温暖格言/正反馈
)
