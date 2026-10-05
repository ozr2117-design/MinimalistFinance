package com.minimalist.finance.data.model

import androidx.room.Entity
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
 * 账本表 (支持无限新建，物理隔离)
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
 * 分类类型：支出、收入
 */
enum class TransactionType {
    EXPENSE,    // 支出
    INCOME,     // 收入
    TRANSFER    // 转账
}

/**
 * 分类表 (支出精简五项：衣食住行其他；收入六大经典)
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val type: TransactionType,
    val bookId: Long = 0, // 0 表示通用分类，特定 bookId 表示账本专属
    val sortOrder: Int = 0
)

/**
 * 资产账户表 (微信钱包、支付宝、招商银行、长桥证券等)
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val currency: String = "CNY",
    val balance: Double = 0.0,
    val iconName: String = "account_balance"
)

/**
 * 记账明细流水表
 */
@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,                      // 所属账本
    val type: TransactionType,             // 支出 / 收入 / 转账
    val amount: Double,                    // 交易金额
    val categoryId: Long? = null,          // 分类ID
    val fromAccountId: Long? = null,       // 转出账户/付款账户
    val toAccountId: Long? = null,         // 转入账户 (仅转账)
    val fee: Double = 0.0,                 // 手续费
    val discount: Double = 0.0,            // 优惠扣减
    val remark: String = "",               // 备注
    val tag: String = "",                  // 标签
    val photoUri: String? = null,          // 票据图片
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 周期规则表 (房租、工资、固定定投)
 */
@Entity(tableName = "periodic_rules")
data class PeriodicRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val amount: Double,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val bookId: Long,
    val frequency: String,                 // MONTHLY, WEEKLY, YEARLY, DAILY
    val dayOfMonth: Int = 1,               // 每月第几天触发
    val isAutoRecord: Boolean = true,      // 是否全自动记账 (false 为弹窗提醒)
    val nextTriggerTime: Long,
    val isEnabled: Boolean = true
)

/**
 * 存钱计划表 (365天存钱法、52周存钱法、12存单法)
 */
@Entity(tableName = "saving_plans")
data class SavingPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                      // 如 "365存钱法"
    val planType: String,                  // FLEXIBLE, DAY_365, WEEK_52, FIXED, DEPOSIT_12
    val targetAmount: Double,              // 目标金额
    val currentAmount: Double = 0.0,       // 当前已存金额
    val targetSavingsBookId: Long,         // 关联的长期储蓄账本ID
    val completedSteps: Int = 0,           // 已打卡步数
    val totalSteps: Int = 365,             // 总步数
    val isFinished: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
