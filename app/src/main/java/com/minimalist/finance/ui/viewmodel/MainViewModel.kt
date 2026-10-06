package com.minimalist.finance.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minimalist.finance.data.AppDatabase
import com.minimalist.finance.data.model.*
import com.minimalist.finance.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val bookDao = database.bookDao()
    val categoryDao = database.categoryDao()
    val accountDao = database.accountDao()
    val recordDao = database.recordDao()
    val periodicRuleDao = database.periodicRuleDao()
    val installmentPlanDao = database.installmentPlanDao()

    // 主题状态
    var currentThemeMode = MutableStateFlow(ThemeMode.DARK)

    // 当前选中的账本
    val allBooks = bookDao.getAllActiveBooks().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var currentBookId = MutableStateFlow<Long>(1L)

    // 资产账户流
    val allAccounts = accountDao.getAllAccounts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 当前页面交易类型 (支出 / 收入)
    var currentTransactionType = MutableStateFlow(TransactionType.EXPENSE)

    // 动态根据当前选中的账本与交易类型拉取专属分类！
    val currentCategories = combine(currentBookId, currentTransactionType) { bId, type ->
        Pair(bId, type)
    }.flatMapLatest { (bId, type) ->
        categoryDao.getCategoriesByBookAndType(bId, type)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var selectedCategoryId = MutableStateFlow<Long?>(null)
    var selectedCategoryName = MutableStateFlow<String>("")

    // 动态选中的扣款账户 (默认微信)
    var selectedAccountName = MutableStateFlow<String>("微信")

    // 动态选中的记账时间戳 (默认此时此刻真实手机时间)
    var selectedTimestamp = MutableStateFlow<Long>(System.currentTimeMillis())

    // 动态选中的小票图片
    var selectedImageUri = MutableStateFlow<Uri?>(null)

    // 动态标签
    var selectedTag = MutableStateFlow<String>("")

    // 账单金额与备注
    var amountExpression = MutableStateFlow("0.0")
    var remarkText = MutableStateFlow("")

    // 流水观察
    val allRecords = recordDao.getAllRecords().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 真实大盘统计 (完全基于本地 SQLite 计算，无任何虚假数据)
    val totalExpense = recordDao.getTotalExpense().map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val totalIncome = recordDao.getTotalIncome().map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    // 周期与分期规则流
    val periodicRules = periodicRuleDao.getAllRules().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val installmentPlans = installmentPlanDao.getAllPlans().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
                categoryName = selectedCategoryName.value,
                accountName = selectedAccountName.value,
                remark = remarkText.value,
                tag = selectedTag.value,
                timestamp = selectedTimestamp.value
            )
            recordDao.insertRecord(record)
            onClear()
            remarkText.value = ""
            selectedImageUri.value = null
            selectedTimestamp.value = System.currentTimeMillis() // 重置为最新时间
            onSuccess()
        }
    }

    // 资产账户增删改
    fun addAccount(name: String, initialBalance: Double = 0.0, currency: String = "CNY") {
        viewModelScope.launch {
            val existing = allAccounts.value.firstOrNull { it.name.trim() == name.trim() }
            if (existing != null) {
                accountDao.updateAccount(existing.copy(balance = initialBalance))
            } else {
                accountDao.insertAccount(Account(name = name.trim(), balance = initialBalance, currency = currency))
            }
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            accountDao.updateAccount(account)
        }
    }

    fun updateAccountBalance(account: Account, newBalance: Double) {
        viewModelScope.launch {
            accountDao.updateAccount(account.copy(balance = newBalance))
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            accountDao.deleteAccount(account)
        }
    }

    // 删除单笔流水
    fun deleteRecord(record: Record) {
        viewModelScope.launch {
            recordDao.deleteRecord(record)
        }
    }

    // 一键清空所有历史流水 (还原纯净空库)
    fun clearAllRecords(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            recordDao.clearAllRecords()
            onComplete()
        }
    }

    // 新增账本
    fun addNewBook(name: String, subtitle: String, type: BookType, currency: String) {
        viewModelScope.launch {
            val newId = bookDao.insertBook(
                Book(name = name, subtitle = subtitle, type = type, currency = currency)
            )
            val defaultCats = listOf(
                Category(bookId = newId, name = "日常投入", iconName = "payments", type = TransactionType.EXPENSE, sortOrder = 1),
                Category(bookId = newId, name = "必要支出", iconName = "shopping_cart", type = TransactionType.EXPENSE, sortOrder = 2),
                Category(bookId = newId, name = "其它支出", iconName = "more_horiz", type = TransactionType.EXPENSE, sortOrder = 3),
                Category(bookId = newId, name = "日常回流", iconName = "account_balance_wallet", type = TransactionType.INCOME, sortOrder = 1),
                Category(bookId = newId, name = "其它收入", iconName = "more_horiz", type = TransactionType.INCOME, sortOrder = 2)
            )
            categoryDao.insertCategories(defaultCats)
            currentBookId.value = newId
        }
    }

    // 周期规则增删
    fun addPeriodicRule(rule: PeriodicRule) {
        viewModelScope.launch {
            periodicRuleDao.insertRule(rule)
        }
    }

    fun deletePeriodicRule(rule: PeriodicRule) {
        viewModelScope.launch {
            periodicRuleDao.deleteRule(rule)
        }
    }

    // 分期计划增删
    fun addInstallmentPlan(plan: InstallmentPlan) {
        viewModelScope.launch {
            installmentPlanDao.insertPlan(plan)
        }
    }

    fun deleteInstallmentPlan(plan: InstallmentPlan) {
        viewModelScope.launch {
            installmentPlanDao.deletePlan(plan)
        }
    }
}
