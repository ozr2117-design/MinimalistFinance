package com.minimalist.finance.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账本类型：日常、境内投资、境外投资、长期储蓄、自定义
 */
enum class BookType {
    DAILY,              // 日常消费
    DOMESTIC_INVEST,    // 境内投资 (A股/基金/固收)
    OVERSEAS_INVEST,    // 境外投资 (美港股/多币种)
    SAVINGS,            // 长期储蓄 (定期/存单/国债)
    CUSTOM              // 自定义账本
}

/**
 * 账本表
 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val subtitle: String = "",
    val type: BookType = BookType.CUSTOM,
    val currency: String = "CNY",
    val coverImage: String = "",
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 交易类型：支出、收入
 */
enum class TransactionType {
    EXPENSE,    // 支出
    INCOME      // 收入
}

/**
 * 分类表 (各账本拥有完全独立的专属分类，同账本同类型分类唯一)
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["bookId", "name", "type"], unique = true)
    ]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,             // 所属账本ID (1:日常, 2:境内投资, 3:境外投资, 4:长期储蓄)
    val name: String,
    val iconName: String,
    val type: TransactionType,
    val sortOrder: Int = 0
)

/**
 * 资产账户表 (账户名称唯一)
 */
@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val currency: String = "CNY",
    val balance: Double = 0.0,
    val iconName: String = "account_balance"
)

/**
 * 记账流水表 (完全真实记录，绝无虚假数据)
 */
@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,                      // 所属账本
    val type: TransactionType,             // 支出 / 收入
    val amount: Double,                    // 交易金额
    val categoryId: Long? = null,          // 分类ID
    val categoryName: String = "",         // 分类名称
    val accountName: String = "微信",      // 账户名称 (微信、支付宝、银行卡、现金等)
    val accountId: Long? = null,           // 扣款/入账账户ID
    val remark: String = "",               // 备注
    val tag: String = "",                  // 标签
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 周期规则表 (对应图一表单项)
 */
@Entity(tableName = "periodic_rules")
data class PeriodicRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                      // 规则备注/名称
    val type: TransactionType,             // 支出/收入
    val amount: Double,                    // 金额
    val bookId: Long,                      // 账本
    val categoryName: String,              // 分类
    val accountName: String,               // 扣款账户
    val startDate: String,                 // 开始日期 (如 2026-10-06)
    val frequency: String,                 // 重复周期: 每天 / 每周 / 每月 / 每年
    val endType: String = "永不结束",      // 结束方式
    val timeOfDay: String = "12:00",       // 账单时间点
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 分期管理表 (对应图二分期逻辑)
 */
@Entity(tableName = "installment_plans")
data class InstallmentPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                      // 分期项目名 (如 MacBook Pro 16寸)
    val totalAmount: Double,               // 总金额
    val totalPeriods: Int,                 // 总期数 (如 12)
    val currentPeriod: Int = 1,            // 当前期数
    val monthlyAmount: Double,             // 每期扣款金额
    val bookId: Long,                      // 关联账本
    val accountName: String,               // 扣款账户
    val dayOfMonth: Int = 8,               // 每月几号扣款
    val isFinished: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
