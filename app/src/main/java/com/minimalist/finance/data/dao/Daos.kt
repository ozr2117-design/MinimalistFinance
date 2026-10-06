package com.minimalist.finance.data.dao

import androidx.room.*
import com.minimalist.finance.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE isArchived = 0 ORDER BY id ASC")
    fun getAllActiveBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Long): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("SELECT * FROM books ORDER BY id ASC")
    suspend fun getAllBooksList(): List<Book>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE bookId = :bookId AND type = :type ORDER BY sortOrder ASC")
    fun getCategoriesByBookAndType(bookId: Long, type: TransactionType): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategoriesList(): List<Category>
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<Account>)

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllAccountsList(): List<Account>

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun getRecordsByBook(bookId: Long): Flow<List<Record>>

    @Query("SELECT * FROM records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<Record>>

    @Query("SELECT * FROM records ORDER BY timestamp ASC")
    suspend fun getAllRecordsList(): List<Record>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: Record): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<Record>)

    @Query("SELECT remark FROM records WHERE remark IS NOT NULL AND remark != '' ORDER BY id DESC LIMIT 100")
    suspend fun getRecentRawRemarks(): List<String>

    @Delete
    suspend fun deleteRecord(record: Record)

    @Query("DELETE FROM records")
    suspend fun clearAllRecords()

    @Query("SELECT SUM(amount) FROM records WHERE type = 'EXPENSE'")
    fun getTotalExpense(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM records WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM records WHERE bookId = :bookId AND type = 'EXPENSE'")
    fun getBookExpense(bookId: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM records WHERE bookId = :bookId AND type = 'INCOME'")
    fun getBookIncome(bookId: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM records WHERE amount = :amount AND timestamp >= :sinceTimestamp")
    suspend fun getRecentDuplicateCount(amount: Double, sinceTimestamp: Long): Int
}

@Dao
interface PeriodicRuleDao {
    @Query("SELECT * FROM periodic_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<PeriodicRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: PeriodicRule): Long

    @Delete
    suspend fun deleteRule(rule: PeriodicRule)

    @Update
    suspend fun updateRule(rule: PeriodicRule)
}

@Dao
interface InstallmentPlanDao {
    @Query("SELECT * FROM installment_plans ORDER BY id DESC")
    fun getAllPlans(): Flow<List<InstallmentPlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: InstallmentPlan): Long

    @Delete
    suspend fun deletePlan(plan: InstallmentPlan)

    @Update
    suspend fun updatePlan(plan: InstallmentPlan)
}
