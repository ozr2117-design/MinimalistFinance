package com.minimalist.finance.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minimalist.finance.data.AppDatabase
import com.minimalist.finance.data.model.*
import com.minimalist.finance.ui.theme.ThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val bookDao = database.bookDao()
    val categoryDao = database.categoryDao()
    val accountDao = database.accountDao()
    val recordDao = database.recordDao()
    val savingPlanDao = database.savingPlanDao()

    // 主题状态
    var currentThemeMode = MutableStateFlow(ThemeMode.DARK)

    // 当前选中的账本
    val allBooks = bookDao.getAllActiveBooks().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var currentBookId = MutableStateFlow<Long>(1L)

    // 当前页面交易类型 (支出 / 收入 / 转账)
    var currentTransactionType = MutableStateFlow(TransactionType.EXPENSE)

    // 分类列表
    val expenseCategories = categoryDao.getCategoriesByType(TransactionType.EXPENSE, 0)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val incomeCategories = categoryDao.getCategoriesByType(TransactionType.INCOME, 0)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var selectedCategoryId = MutableStateFlow<Long?>(null)

    // 转账账户
    val allAccounts = accountDao.getAllAccounts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var fromAccountId = MutableStateFlow<Long?>(null)
    var toAccountId = MutableStateFlow<Long?>(null)

    // 金额与输入表达式
    var amountExpression = MutableStateFlow("0.0")
    var remarkText = MutableStateFlow("")
    var feeText = MutableStateFlow("0.0")
    var discountText = MutableStateFlow("0.0")

    val allRecords = recordDao.getAllRecords().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteRecord(record: Record) {
        viewModelScope.launch {
            recordDao.deleteRecord(record)
        }
    }

    init {
        viewModelScope.launch {
            allBooks.collect { books ->
                if (books.isNotEmpty() && currentBookId.value == 1L) {
                    currentBookId.value = books.firstOrNull { it.isDefault }?.id ?: books.first().id
                }
            }
        }
    }

    // 键盘点击逻辑
    fun onDigit(d: String) {
        val cur = amountExpression.value
        if (cur == "0.0" || cur == "0") {
            amountExpression.value = d
        } else {
            amountExpression.value = cur + d
        }
    }

    fun onDot() {
        val cur = amountExpression.value
        if (!cur.endsWith(".") && !cur.split("+", "-").last().contains(".")) {
            amountExpression.value = "$cur."
        }
    }

    fun onOperator(op: String) {
        val cur = amountExpression.value
        if (cur.isNotEmpty() && !cur.endsWith("+") && !cur.endsWith("-")) {
            amountExpression.value = "$cur$op"
        }
    }

    fun onBackspace() {
        val cur = amountExpression.value
        if (cur.length > 1) {
            amountExpression.value = cur.dropLast(1)
        } else {
            amountExpression.value = "0.0"
        }
    }

    fun onClear() {
        amountExpression.value = "0.0"
    }

    // 简单计算表达式总和
    private fun evaluateAmount(): Double {
        val exp = amountExpression.value
        return try {
            val tokens = Regex("(?=[+-])|(?<=[+-])").split(exp).filter { it.isNotBlank() }
            var result = 0.0
            var currentOp = "+"
            for (token in tokens) {
                if (token == "+" || token == "-") {
                    currentOp = token
                } else {
                    val num = token.toDoubleOrNull() ?: 0.0
                    if (currentOp == "+") result += num else result -= num
                }
            }
            if (result < 0) 0.0 else result
        } catch (e: Exception) {
            0.0
        }
    }

    // 保存当前流水
    fun saveRecord(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val amount = evaluateAmount()
            if (amount <= 0.0) return@launch

            val record = Record(
                bookId = currentBookId.value,
                type = currentTransactionType.value,
                amount = amount,
                categoryId = selectedCategoryId.value,
                fromAccountId = fromAccountId.value,
                toAccountId = toAccountId.value,
                fee = feeText.value.toDoubleOrNull() ?: 0.0,
                discount = discountText.value.toDoubleOrNull() ?: 0.0,
                remark = remarkText.value
            )
            recordDao.insertRecord(record)
            onClear()
            remarkText.value = ""
            onSuccess()
        }
    }

    // 新增账本
    fun addNewBook(name: String, subtitle: String, type: BookType, currency: String) {
        viewModelScope.launch {
            bookDao.insertBook(
                Book(
                    name = name,
                    subtitle = subtitle,
                    type = type,
                    currency = currency
                )
            )
        }
    }

    // 交换转账账户
    fun swapTransferAccounts() {
        val temp = fromAccountId.value
        fromAccountId.value = toAccountId.value
        toAccountId.value = temp
    }
}
