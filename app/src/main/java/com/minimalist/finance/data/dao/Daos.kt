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

    @Query("UPDATE books SET isDefault = (id = :bookId)")
    suspend fun setDefaultBook(bookId: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type AND (bookId = 0 OR bookId = :bookId) ORDER BY sortOrder ASC")
    fun getCategoriesByType(type: TransactionType, bookId: Long): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun getRecordsByBook(bookId: Long): Flow<List<Record>>

    @Query("SELECT * FROM records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<Record>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: Record): Long

    @Delete
    suspend fun deleteRecord(record: Record)

    @Query("SELECT SUM(amount) FROM records WHERE bookId = :bookId AND type = 'EXPENSE'")
    fun getTotalExpense(bookId: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM records WHERE bookId = :bookId AND type = 'INCOME'")
    fun getTotalIncome(bookId: Long): Flow<Double?>
}

@Dao
interface PeriodicRuleDao {
    @Query("SELECT * FROM periodic_rules WHERE isEnabled = 1")
    fun getEnabledRules(): Flow<List<PeriodicRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: PeriodicRule): Long
}

@Dao
interface SavingPlanDao {
    @Query("SELECT * FROM saving_plans ORDER BY id DESC")
    fun getAllPlans(): Flow<List<SavingPlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: SavingPlan): Long

    @Update
    suspend fun updatePlan(plan: SavingPlan)
}
