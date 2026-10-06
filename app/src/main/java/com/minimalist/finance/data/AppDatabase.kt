package com.minimalist.finance.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minimalist.finance.data.dao.*
import com.minimalist.finance.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Book::class,
        Category::class,
        Account::class,
        Record::class,
        PeriodicRule::class,
        InstallmentPlan::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun recordDao(): RecordDao
    abstract fun periodicRuleDao(): PeriodicRuleDao
    abstract fun installmentPlanDao(): InstallmentPlanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "minimalist_finance.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            suspend fun populateInitialData(db: AppDatabase) {
                // 1. 初始化四大核心账本 (ID 分别为 1, 2, 3, 4)
                val b1 = db.bookDao().insertBook(
                    Book(id = 1, name = "日常消费账本", subtitle = "默认主账本", type = BookType.DAILY, currency = "CNY", isDefault = true)
                )
                val b2 = db.bookDao().insertBook(
                    Book(id = 2, name = "境内投资账本", subtitle = "A股 · 基金 · 固收", type = BookType.DOMESTIC_INVEST, currency = "CNY")
                )
                val b3 = db.bookDao().insertBook(
                    Book(id = 3, name = "境外投资账本", subtitle = "美港股 · 多币种", type = BookType.OVERSEAS_INVEST, currency = "USD")
                )
                val b4 = db.bookDao().insertBook(
                    Book(id = 4, name = "长期储蓄账本", subtitle = "定期 · 存单 · 国债", type = BookType.SAVINGS, currency = "CNY")
                )

                // 2. 初始化各账本专属分类 (彻底区分业务，不再千篇一律)

                // ====== 账本 1: 日常消费账本 (精简实用) ======
                val c1Expense = listOf(
                    Category(bookId = 1, name = "衣", iconName = "checkroom", type = TransactionType.EXPENSE, sortOrder = 1),
                    Category(bookId = 1, name = "食", iconName = "restaurant", type = TransactionType.EXPENSE, sortOrder = 2),
                    Category(bookId = 1, name = "住", iconName = "home", type = TransactionType.EXPENSE, sortOrder = 3),
                    Category(bookId = 1, name = "行", iconName = "directions_car", type = TransactionType.EXPENSE, sortOrder = 4),
                    Category(bookId = 1, name = "其他", iconName = "more_horiz", type = TransactionType.EXPENSE, sortOrder = 5)
                )
                val c1Income = listOf(
                    Category(bookId = 1, name = "工资", iconName = "work", type = TransactionType.INCOME, sortOrder = 1),
                    Category(bookId = 1, name = "生活费", iconName = "account_balance_wallet", type = TransactionType.INCOME, sortOrder = 2),
                    Category(bookId = 1, name = "收红包", iconName = "card_giftcard", type = TransactionType.INCOME, sortOrder = 3),
                    Category(bookId = 1, name = "外快", iconName = "paid", type = TransactionType.INCOME, sortOrder = 4),
                    Category(bookId = 1, name = "奖金", iconName = "emoji_events", type = TransactionType.INCOME, sortOrder = 5),
                    Category(bookId = 1, name = "其它", iconName = "more_horiz", type = TransactionType.INCOME, sortOrder = 6)
                )
                db.categoryDao().insertCategories(c1Expense + c1Income)

                // ====== 账本 2: 境内投资账本 (A股 / 公募基金 / 银行理财) ======
                val c2Expense = listOf(
                    Category(bookId = 2, name = "股票买入", iconName = "trending_up", type = TransactionType.EXPENSE, sortOrder = 1),
                    Category(bookId = 2, name = "基金申购", iconName = "pie_chart", type = TransactionType.EXPENSE, sortOrder = 2),
                    Category(bookId = 2, name = "理财购买", iconName = "account_balance", type = TransactionType.EXPENSE, sortOrder = 3),
                    Category(bookId = 2, name = "交易税费", iconName = "receipt_long", type = TransactionType.EXPENSE, sortOrder = 4),
                    Category(bookId = 2, name = "其他投入", iconName = "more_horiz", type = TransactionType.EXPENSE, sortOrder = 5)
                )
                val c2Income = listOf(
                    Category(bookId = 2, name = "股票卖出", iconName = "trending_down", type = TransactionType.INCOME, sortOrder = 1),
                    Category(bookId = 2, name = "基金赎回", iconName = "currency_exchange", type = TransactionType.INCOME, sortOrder = 2),
                    Category(bookId = 2, name = "分红派息", iconName = "paid", type = TransactionType.INCOME, sortOrder = 3),
                    Category(bookId = 2, name = "理财收益", iconName = "savings", type = TransactionType.INCOME, sortOrder = 4),
                    Category(bookId = 2, name = "打新收益", iconName = "military_tech", type = TransactionType.INCOME, sortOrder = 5),
                    Category(bookId = 2, name = "其它收益", iconName = "more_horiz", type = TransactionType.INCOME, sortOrder = 6)
                )
                db.categoryDao().insertCategories(c2Expense + c2Income)

                // ====== 账本 3: 境外投资账本 (美港股 / 多币种) ======
                val c3Expense = listOf(
                    Category(bookId = 3, name = "美股买入", iconName = "candlestick_chart", type = TransactionType.EXPENSE, sortOrder = 1),
                    Category(bookId = 3, name = "港股买入", iconName = "bar_chart", type = TransactionType.EXPENSE, sortOrder = 2),
                    Category(bookId = 3, name = "期权买入", iconName = "show_chart", type = TransactionType.EXPENSE, sortOrder = 3),
                    Category(bookId = 3, name = "跨境电汇", iconName = "flight_takeoff", type = TransactionType.EXPENSE, sortOrder = 4),
                    Category(bookId = 3, name = "其他投入", iconName = "more_horiz", type = TransactionType.EXPENSE, sortOrder = 5)
                )
                val c3Income = listOf(
                    Category(bookId = 3, name = "美股卖出", iconName = "attach_money", type = TransactionType.INCOME, sortOrder = 1),
                    Category(bookId = 3, name = "港股卖出", iconName = "sell", type = TransactionType.INCOME, sortOrder = 2),
                    Category(bookId = 3, name = "境外股息", iconName = "savings", type = TransactionType.INCOME, sortOrder = 3),
                    Category(bookId = 3, name = "美元利息", iconName = "price_check", type = TransactionType.INCOME, sortOrder = 4),
                    Category(bookId = 3, name = "期权盈利", iconName = "auto_graph", type = TransactionType.INCOME, sortOrder = 5),
                    Category(bookId = 3, name = "其它收益", iconName = "more_horiz", type = TransactionType.INCOME, sortOrder = 6)
                )
                db.categoryDao().insertCategories(c3Expense + c3Income)

                // ====== 账本 4: 长期储蓄账本 (定期存单 / 国债) ======
                val c4Expense = listOf(
                    Category(bookId = 4, name = "定期存款", iconName = "account_balance", type = TransactionType.EXPENSE, sortOrder = 1),
                    Category(bookId = 4, name = "大额存单", iconName = "lock", type = TransactionType.EXPENSE, sortOrder = 2),
                    Category(bookId = 4, name = "储蓄国债", iconName = "shield", type = TransactionType.EXPENSE, sortOrder = 3),
                    Category(bookId = 4, name = "养老储蓄", iconName = "elderly", type = TransactionType.EXPENSE, sortOrder = 4),
                    Category(bookId = 4, name = "其他储蓄", iconName = "more_horiz", type = TransactionType.EXPENSE, sortOrder = 5)
                )
                val c4Income = listOf(
                    Category(bookId = 4, name = "存单到期", iconName = "key", type = TransactionType.INCOME, sortOrder = 1),
                    Category(bookId = 4, name = "利息到账", iconName = "monetization_on", type = TransactionType.INCOME, sortOrder = 2),
                    Category(bookId = 4, name = "国债兑付", iconName = "redeem", type = TransactionType.INCOME, sortOrder = 3),
                    Category(bookId = 4, name = "提前支取", iconName = "emergency", type = TransactionType.INCOME, sortOrder = 4),
                    Category(bookId = 4, name = "其他回款", iconName = "more_horiz", type = TransactionType.INCOME, sortOrder = 5)
                )
                db.categoryDao().insertCategories(c4Expense + c4Income)

                // 3. 基础资金账户 (纯净初始余额全部为 0)
                db.accountDao().insertAccount(Account(name = "微信零钱", balance = 0.0))
                db.accountDao().insertAccount(Account(name = "支付宝余额", balance = 0.0))
                db.accountDao().insertAccount(Account(name = "招商银行储蓄卡", balance = 0.0))
                db.accountDao().insertAccount(Account(name = "证券账户 (A股/基金)", balance = 0.0))
                db.accountDao().insertAccount(Account(name = "海外证券账户 (USD)", balance = 0.0, currency = "USD"))
            }
        }
    }
}
