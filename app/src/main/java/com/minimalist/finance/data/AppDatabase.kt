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
        SavingPlan::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun recordDao(): RecordDao
    abstract fun periodicRuleDao(): PeriodicRuleDao
    abstract fun savingPlanDao(): SavingPlanDao

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
                // 1. 预置四大账本
                val dailyBookId = db.bookDao().insertBook(
                    Book(
                        name = "日常消费账本",
                        subtitle = "默认主账本",
                        type = BookType.DAILY,
                        currency = "CNY",
                        isDefault = true
                    )
                )
                db.bookDao().insertBook(
                    Book(
                        name = "境内投资账本",
                        subtitle = "A股 · 基金 · 固收",
                        type = BookType.DOMESTIC_INVEST,
                        currency = "CNY"
                    )
                )
                db.bookDao().insertBook(
                    Book(
                        name = "境外投资账本",
                        subtitle = "美港股 · 多币种",
                        type = BookType.OVERSEAS_INVEST,
                        currency = "USD"
                    )
                )
                db.bookDao().insertBook(
                    Book(
                        name = "长期储蓄账本",
                        subtitle = "定期 · 存单 · 国债",
                        type = BookType.SAVINGS,
                        currency = "CNY"
                    )
                )

                // 2. 预置支出核心五项分类 (衣食住行其他)
                val expenseCategories = listOf(
                    Category(name = "衣", iconName = "checkroom", type = TransactionType.EXPENSE, sortOrder = 1),
                    Category(name = "食", iconName = "restaurant", type = TransactionType.EXPENSE, sortOrder = 2),
                    Category(name = "住", iconName = "home", type = TransactionType.EXPENSE, sortOrder = 3),
                    Category(name = "行", iconName = "directions_car", type = TransactionType.EXPENSE, sortOrder = 4),
                    Category(name = "其他", iconName = "more_horiz", type = TransactionType.EXPENSE, sortOrder = 5)
                )
                db.categoryDao().insertCategories(expenseCategories)

                // 3. 预置收入六大经典分类
                val incomeCategories = listOf(
                    Category(name = "工资", iconName = "work", type = TransactionType.INCOME, sortOrder = 1),
                    Category(name = "生活费", iconName = "account_balance_wallet", type = TransactionType.INCOME, sortOrder = 2),
                    Category(name = "收红包", iconName = "card_giftcard", type = TransactionType.INCOME, sortOrder = 3),
                    Category(name = "外快", iconName = "paid", type = TransactionType.INCOME, sortOrder = 4),
                    Category(name = "股票基金", iconName = "trending_up", type = TransactionType.INCOME, sortOrder = 5),
                    Category(name = "其它", iconName = "more_horiz", type = TransactionType.INCOME, sortOrder = 6)
                )
                db.categoryDao().insertCategories(incomeCategories)

                // 4. 预置基础资产账户
                db.accountDao().insertAccount(Account(name = "微信钱包", balance = 0.0))
                db.accountDao().insertAccount(Account(name = "支付宝", balance = 0.0))
                db.accountDao().insertAccount(Account(name = "招商银行储蓄卡", balance = 0.0))
                db.accountDao().insertAccount(Account(name = "证券投资账户", balance = 0.0))
            }
        }
    }
}
