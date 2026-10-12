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

    // 资产账户流 (UI 层强力去重保障)
    val allAccounts = accountDao.getAllAccounts().map { list ->
        list.distinctBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 当前页面交易类型 (支出 / 收入)
    var currentTransactionType = MutableStateFlow(TransactionType.EXPENSE)

    // 动态根据当前选中的账本与交易类型拉取专属分类！(UI 层强力去重保障)
    val currentCategories = combine(currentBookId, currentTransactionType) { bId, type ->
        Pair(bId, type)
    }.flatMapLatest { (bId, type) ->
        categoryDao.getCategoriesByBookAndType(bId, type).map { list ->
            list.distinctBy { it.name }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var selectedCategoryId = MutableStateFlow<Long?>(null)
    var selectedCategoryName = MutableStateFlow<String>("")

    // 动态选中的扣款账户 (默认微信)
    var selectedAccountName = MutableStateFlow<String>("微信")

    // 动态选中的记账时间戳 (默认此时此刻真实手机时间)
    var selectedTimestamp = MutableStateFlow<Long>(System.currentTimeMillis())
    // 标记用户是否手动指定了过去/特殊时间 (若为 false，切回前台时自动更新为实时系统时间)
    var isCustomTimestamp = MutableStateFlow(false)

    fun syncCurrentTimeIfAuto() {
        if (!isCustomTimestamp.value) {
            selectedTimestamp.value = System.currentTimeMillis()
        }
    }

    // 动态选中的小票图片
    var selectedImageUri = MutableStateFlow<Uri?>(null)

    // 动态标签
    var selectedTag = MutableStateFlow<String>("")

    // 账单金额与备注
    var amountExpression = MutableStateFlow("0.0")
    var remarkText = MutableStateFlow("")

    // 智能记忆用户最近历史备注 (自动将最新输入的置于第1位)
    val recentRemarks = MutableStateFlow<List<String>>(emptyList())

    // 流水观察
    val allRecords = recordDao.getAllRecords().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 真实大盘统计 (完全基于本地 SQLite 计算，无任何虚假数据)
    val totalExpense = recordDao.getTotalExpense().map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val totalIncome = recordDao.getTotalIncome().map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    // 周期与分期规则流
    val periodicRules = periodicRuleDao.getAllRules().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val installmentPlans = installmentPlanDao.getAllPlans().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 本月轻量预算 (0.0 表示未设定)
    val monthlyBudget = MutableStateFlow<Double>(0.0)

    fun setMonthlyBudget(amount: Double) {
        val valid = maxOf(0.0, amount)
        monthlyBudget.value = valid
        val prefs = getApplication<Application>().getSharedPreferences("shubu_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("monthly_budget", valid.toFloat()).apply()
    }

    private fun getMonthTimeRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, daysInMonth)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return Pair(start, end)
    }

    // 1. 预算防暴走计算流
    val monthlyBudgetInfo: StateFlow<MonthlyBudgetInfo> = combine(allRecords, monthlyBudget) { records, budget ->
        val (start, end) = getMonthTimeRange()
        val nowCal = Calendar.getInstance()
        val dayOfMonth = nowCal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = nowCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = maxOf(1, daysInMonth - dayOfMonth + 1)

        val monthRecords = records.filter { it.timestamp in start..end }
        val monthExpense = monthRecords.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val monthIncome = monthRecords.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

        val remaining = if (budget > 0.0) budget - monthExpense else 0.0
        val dailyAvailable = if (budget > 0.0) maxOf(0.0, remaining) / daysRemaining else 0.0
        val usagePercent = if (budget > 0.0) ((monthExpense / budget) * 100).toFloat() else 0f
        val isOver = budget > 0.0 && monthExpense > budget

        MonthlyBudgetInfo(
            budgetAmount = budget,
            spentAmount = monthExpense,
            remainingBudget = remaining,
            daysInMonth = daysInMonth,
            dayOfMonth = dayOfMonth,
            daysRemaining = daysRemaining,
            dailyAvailable = dailyAvailable,
            usagePercent = usagePercent,
            isOverBudget = isOver
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, MonthlyBudgetInfo())

    // 2. 刚需生存 vs 享受生活 比例计算流
    val lifestyleVsNecessity: StateFlow<LifestyleVsNecessity> = allRecords.map { records ->
        val (start, end) = getMonthTimeRange()
        val monthRecords = records.filter { it.timestamp in start..end && it.type == TransactionType.EXPENSE }
        val total = monthRecords.sumOf { it.amount }

        var necessity = 0.0
        var lifestyle = 0.0
        var other = 0.0

        for (r in monthRecords) {
            val txt = "${r.categoryName} ${r.remark}".lowercase()
            if (isNecessityKeyword(txt)) {
                necessity += r.amount
            } else if (isLifestyleKeyword(txt)) {
                lifestyle += r.amount
            } else {
                other += r.amount
            }
        }

        val necRatio = if (total > 0) ((necessity / total) * 100).toFloat() else 0f
        val lifeRatio = if (total > 0) ((lifestyle / total) * 100).toFloat() else 0f

        val feedback = when {
            total <= 0.0 -> "本月尚无支出，保持空灵澄澈的收支状态！"
            necRatio >= 65f -> "刚需生存支出占比高达 ${String.format(Locale.CHINA, "%.0f", necRatio)}%，主要集中在住房通勤等基本盘，消费作风非常克制务实！"
            lifeRatio >= 55f -> "享受型品质生活消费占比达 ${String.format(Locale.CHINA, "%.0f", lifeRatio)}%，近期生活色彩丰富！可适当关注日均餐饮娱乐，留存充足的安全感备用金。"
            else -> "刚需生存（${String.format(Locale.CHINA, "%.0f", necRatio)}%）与品质生活（${String.format(Locale.CHINA, "%.0f", lifeRatio)}%）比例适中，既顾好了基本盘又犒赏了自己，财务结构非常健康！"
        }

        LifestyleVsNecessity(
            necessityExpense = necessity,
            lifestyleExpense = lifestyle,
            otherExpense = other,
            totalExpense = total,
            necessityRatio = necRatio,
            lifestyleRatio = lifeRatio,
            insightAdvice = feedback
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, LifestyleVsNecessity())

    // 3. 消费分类排行榜
    val categoryRanks: StateFlow<List<CategoryExpenseRank>> = allRecords.map { records ->
        val (start, end) = getMonthTimeRange()
        val monthExpenses = records.filter { it.timestamp in start..end && it.type == TransactionType.EXPENSE }
        val total = monthExpenses.sumOf { it.amount }

        monthExpenses
            .groupBy { it.categoryName.ifBlank { "未分类" } }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                val pct = if (total > 0) ((sum / total) * 100).toFloat() else 0f
                CategoryExpenseRank(cat, sum, pct, list.size)
            }
            .sortedByDescending { it.amount }
            .take(6)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 4. 周期财务温度小结
    val monthlySummary: StateFlow<MonthlyInsightSummary> = allRecords.map { records ->
        val (start, end) = getMonthTimeRange()
        val monthRecords = records.filter { it.timestamp in start..end }
        val monthExpenses = monthRecords.filter { it.type == TransactionType.EXPENSE }
        val totalExpense = monthExpenses.sumOf { it.amount }

        val totalCount = monthRecords.size
        val avg = if (monthExpenses.isNotEmpty()) totalExpense / monthExpenses.size else 0.0
        val maxRecord = monthExpenses.maxByOrNull { it.amount }
        val activeDays = monthRecords.map {
            SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(it.timestamp))
        }.distinct().size

        val encouragement = when {
            totalCount == 0 -> "流水见证真实的生活。每一笔记录，都是认真对待日子的足迹。"
            totalCount < 5 -> "良好的记账习惯正在萌芽，每一笔记录都在给未来积攒底气。"
            totalCount < 20 -> "节奏良好！坚持记账不仅是记数字，更是对生活的用心审视。"
            else -> "太棒了！本月已记账 $totalCount 笔，持续掌控财务节奏，生活越过越有数！"
        }

        MonthlyInsightSummary(
            totalTransactions = totalCount,
            daysRecorded = activeDays,
            avgExpensePerTx = avg,
            maxSingleExpense = maxRecord?.amount ?: 0.0,
            encouragement = encouragement
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, MonthlyInsightSummary())

    private fun isNecessityKeyword(text: String): Boolean {
        return text.contains("住") || text.contains("房租") || text.contains("水电") ||
               text.contains("物业") || text.contains("燃气") || text.contains("宽带") ||
               text.contains("行") || text.contains("交通") || text.contains("地铁") ||
               text.contains("公交") || text.contains("加油") || text.contains("停车") ||
               text.contains("车费") || text.contains("医") || text.contains("药") ||
               text.contains("健康") || text.contains("学") || text.contains("教育") ||
               text.contains("还贷") || text.contains("通信") || text.contains("话费") ||
               text.contains("高速") || text.contains("维修")
    }

    private fun isLifestyleKeyword(text: String): Boolean {
        return text.contains("食") || text.contains("餐饮") || text.contains("外卖") ||
               text.contains("咖啡") || text.contains("奶茶") || text.contains("零食") ||
               text.contains("麦当劳") || text.contains("肯德基") || text.contains("超市") ||
               text.contains("玩") || text.contains("娱乐") || text.contains("游戏") ||
               text.contains("旅游") || text.contains("电影") || text.contains("衣") ||
               text.contains("服饰") || text.contains("购物") || text.contains("数码") ||
               text.contains("美妆") || text.contains("护肤") || text.contains("宠物") ||
               text.contains("健身") || text.contains("运动")
    }

    init {
        // 读取持久化常用备注与预算
        val prefs = application.getSharedPreferences("shubu_prefs", android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString("recent_remarks", "") ?: ""
        val initialList = if (saved.isNotBlank()) saved.split("|||").filter { it.isNotBlank() } else emptyList()
        recentRemarks.value = initialList
        monthlyBudget.value = prefs.getFloat("monthly_budget", 0f).toDouble()

        viewModelScope.launch {
            try {
                val dbRemarks = recordDao.getRecentRawRemarks().map { it.trim() }.filter { it.isNotBlank() }.distinct()
                val merged = (recentRemarks.value + dbRemarks).distinct().take(30)
                recentRemarks.value = merged
            } catch (e: Exception) {
                // ignore
            }
        }

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

    // 记录备注到历史并置顶于第1位
    fun recordRemarkToHistory(remark: String) {
        val trimmed = remark.trim()
        if (trimmed.isBlank()) return
        val current = recentRemarks.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val updated = current.take(30)
        recentRemarks.value = updated
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("shubu_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("recent_remarks", updated.joinToString("|||")).apply()
        }
    }

    // 保存当前流水
    fun saveRecord(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val amount = evaluateAmount()
            if (amount <= 0.0) return@launch

            val currentRemark = remarkText.value.trim()
            if (currentRemark.isNotEmpty()) {
                recordRemarkToHistory(currentRemark)
            }

            val record = Record(
                bookId = currentBookId.value,
                type = currentTransactionType.value,
                amount = amount,
                categoryId = selectedCategoryId.value,
                categoryName = selectedCategoryName.value,
                accountName = selectedAccountName.value,
                remark = currentRemark,
                tag = selectedTag.value,
                timestamp = selectedTimestamp.value
            )
            recordDao.insertRecord(record)
            com.minimalist.finance.util.MilestoneEncouragementManager.checkAndCelebrate(getApplication(), viewModelScope)
            onClear()
            remarkText.value = ""
            selectedImageUri.value = null
            selectedTimestamp.value = System.currentTimeMillis() // 重置为最新时间
            isCustomTimestamp.value = false
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

    // 删除账本 (默认主账本不可删除)
    fun deleteBook(book: Book) {
        viewModelScope.launch {
            if (book.isDefault) return@launch
            if (currentBookId.value == book.id) {
                val allActive = bookDao.getAllBooksList().filter { it.id != book.id && !it.isArchived }
                val fallbackBook = allActive.firstOrNull { it.isDefault } ?: allActive.firstOrNull()
                fallbackBook?.let {
                    currentBookId.value = it.id
                }
            }
            bookDao.deleteBook(book)
        }
    }

    // 从预设选项模板一键恢复或新建账本
    fun addPresetBook(template: PresetBookTemplate) {
        viewModelScope.launch {
            val newId = bookDao.insertBook(
                Book(
                    name = template.name,
                    subtitle = template.subtitle,
                    type = template.type,
                    currency = template.currency
                )
            )
            val cats = mutableListOf<Category>()
            template.expenseCategories.forEachIndexed { index, catName ->
                cats.add(Category(bookId = newId, name = catName, iconName = "payments", type = TransactionType.EXPENSE, sortOrder = index + 1))
            }
            template.incomeCategories.forEachIndexed { index, catName ->
                cats.add(Category(bookId = newId, name = catName, iconName = "account_balance_wallet", type = TransactionType.INCOME, sortOrder = index + 1))
            }
            categoryDao.insertCategories(cats)
            currentBookId.value = newId
        }
    }

    // 新增自主定义账本
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

    fun updatePeriodicRule(rule: PeriodicRule) {
        viewModelScope.launch {
            periodicRuleDao.updateRule(rule)
        }
    }

    // 分期计划增删与修改
    fun addInstallmentPlan(plan: InstallmentPlan) {
        viewModelScope.launch {
            installmentPlanDao.insertPlan(plan)
        }
    }

    fun updateInstallmentPlan(plan: InstallmentPlan) {
        viewModelScope.launch {
            installmentPlanDao.updatePlan(plan)
        }
    }

    fun deleteInstallmentPlan(plan: InstallmentPlan) {
        viewModelScope.launch {
            installmentPlanDao.deletePlan(plan)
        }
    }

    // ==========================================
    // 数据备份与导入核心引擎 (JSON 全量迁移 + CSV 表格)
    // ==========================================

    fun exportFullJsonBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val books = bookDao.getAllBooksList()
                val accounts = accountDao.getAllAccountsList()
                val categories = categoryDao.getAllCategoriesList()
                val records = recordDao.getAllRecordsList()

                val root = org.json.JSONObject()
                root.put("version", 1)
                root.put("appName", "数簿")
                root.put("exportTime", System.currentTimeMillis())

                val booksArr = org.json.JSONArray()
                for (b in books) {
                    val obj = org.json.JSONObject()
                    obj.put("id", b.id)
                    obj.put("name", b.name)
                    obj.put("subtitle", b.subtitle)
                    obj.put("type", b.type.name)
                    obj.put("currency", b.currency)
                    obj.put("isDefault", b.isDefault)
                    booksArr.put(obj)
                }
                root.put("books", booksArr)

                val accountsArr = org.json.JSONArray()
                for (a in accounts) {
                    val obj = org.json.JSONObject()
                    obj.put("id", a.id)
                    obj.put("name", a.name)
                    obj.put("balance", a.balance)
                    obj.put("currency", a.currency)
                    accountsArr.put(obj)
                }
                root.put("accounts", accountsArr)

                val catsArr = org.json.JSONArray()
                for (c in categories) {
                    val obj = org.json.JSONObject()
                    obj.put("id", c.id)
                    obj.put("bookId", c.bookId)
                    obj.put("name", c.name)
                    obj.put("iconName", c.iconName)
                    obj.put("type", c.type.name)
                    obj.put("sortOrder", c.sortOrder)
                    catsArr.put(obj)
                }
                root.put("categories", catsArr)

                val recordsArr = org.json.JSONArray()
                for (r in records) {
                    val obj = org.json.JSONObject()
                    obj.put("id", r.id)
                    obj.put("bookId", r.bookId)
                    obj.put("type", r.type.name)
                    obj.put("amount", r.amount)
                    obj.put("categoryId", r.categoryId ?: 0L)
                    obj.put("categoryName", r.categoryName)
                    obj.put("accountName", r.accountName)
                    obj.put("remark", r.remark)
                    obj.put("tag", r.tag)
                    obj.put("timestamp", r.timestamp)
                    recordsArr.put(obj)
                }
                root.put("records", recordsArr)

                onResult(root.toString(2))
            } catch (e: Exception) {
                onResult("")
            }
        }
    }

    fun exportRecordsCsv(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val records = recordDao.getAllRecordsList()
                val sb = java.lang.StringBuilder()
                sb.append("\uFEFF") // UTF-8 BOM，防止 Excel 打开乱码
                sb.append("时间,账本编号,类型,金额,分类,账户,备注,标签\n")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                for (r in records) {
                    val timeStr = sdf.format(Date(r.timestamp))
                    val typeStr = if (r.type == TransactionType.EXPENSE) "支出" else "收入"
                    val cleanRemark = r.remark.replace(",", "，").replace("\n", " ")
                    val cleanTag = r.tag.replace(",", "，")
                    sb.append("$timeStr,${r.bookId},$typeStr,${r.amount},${r.categoryName},${r.accountName},$cleanRemark,$cleanTag\n")
                }
                onResult(sb.toString())
            } catch (e: Exception) {
                onResult("")
            }
        }
    }

    fun importFullJsonBackup(jsonStr: String, overwrite: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val root = org.json.JSONObject(jsonStr)
                if (overwrite) {
                    recordDao.clearAllRecords()
                }

                var importedAccounts = 0
                if (root.has("accounts")) {
                    val accountsArr = root.getJSONArray("accounts")
                    for (i in 0 until accountsArr.length()) {
                        val obj = accountsArr.getJSONObject(i)
                        val name = obj.getString("name").trim()
                        val balance = obj.optDouble("balance", 0.0)
                        val currency = obj.optString("currency", "CNY")
                        val existing = accountDao.getAllAccountsList().firstOrNull { it.name == name }
                        if (existing == null) {
                            accountDao.insertAccount(Account(name = name, balance = balance, currency = currency))
                            importedAccounts++
                        } else if (overwrite) {
                            accountDao.updateAccount(existing.copy(balance = balance))
                        }
                    }
                }

                var importedRecords = 0
                if (root.has("records")) {
                    val recordsArr = root.getJSONArray("records")
                    val recordList = mutableListOf<Record>()
                    for (i in 0 until recordsArr.length()) {
                        val obj = recordsArr.getJSONObject(i)
                        val typeStr = obj.optString("type", "EXPENSE")
                        val type = if (typeStr == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                        val record = Record(
                            bookId = obj.optLong("bookId", 1L),
                            type = type,
                            amount = obj.getDouble("amount"),
                            categoryId = if (obj.has("categoryId") && obj.getLong("categoryId") != 0L) obj.getLong("categoryId") else null,
                            categoryName = obj.optString("categoryName", "其他"),
                            accountName = obj.optString("accountName", "微信"),
                            remark = obj.optString("remark", ""),
                            tag = obj.optString("tag", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                        recordList.add(record)
                    }
                    if (recordList.isNotEmpty()) {
                        recordDao.insertRecords(recordList)
                        importedRecords = recordList.size
                    }
                }

                // 刷新常用备注
                val dbRemarks = recordDao.getRecentRawRemarks().map { it.trim() }.filter { it.isNotBlank() }.distinct()
                recentRemarks.value = dbRemarks.take(30)

                onResult(true, "成功恢复 $importedRecords 条记账流水与 $importedAccounts 个账户！")
            } catch (e: Exception) {
                onResult(false, "备份解析失败: ${e.message ?: "格式错误"}")
            }
        }
    }

    fun importRecordsCsv(csvStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val lines = csvStr.lines().filter { it.isNotBlank() }
                if (lines.size <= 1) {
                    onResult(false, "CSV 文件内容为空或无有效记录")
                    return@launch
                }
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val recordList = mutableListOf<Record>()
                for (line in lines.drop(1)) {
                    val parts = line.split(",").map { it.trim().trim('\uFEFF') }
                    if (parts.size >= 4) {
                        val timeStr = parts.getOrNull(0) ?: ""
                        val parsedTime = try { sdf.parse(timeStr)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                        val typeStr = parts.getOrNull(2) ?: "支出"
                        val type = if (typeStr.contains("收入")) TransactionType.INCOME else TransactionType.EXPENSE
                        val amount = parts.getOrNull(3)?.toDoubleOrNull() ?: continue
                        val catName = parts.getOrNull(4) ?: "其他"
                        val accName = parts.getOrNull(5) ?: "微信"
                        val remark = parts.getOrNull(6) ?: ""
                        val tag = parts.getOrNull(7) ?: ""
                        recordList.add(
                            Record(
                                bookId = currentBookId.value,
                                type = type,
                                amount = amount,
                                categoryName = catName,
                                accountName = accName,
                                remark = remark,
                                tag = tag,
                                timestamp = parsedTime
                            )
                        )
                    }
                }
                if (recordList.isNotEmpty()) {
                    recordDao.insertRecords(recordList)
                    onResult(true, "成功导入 ${recordList.size} 条流水记录！")
                } else {
                    onResult(false, "未解析到符合格式的记账流水行")
                }
            } catch (e: Exception) {
                onResult(false, "CSV 解析失败: ${e.message}")
            }
        }
    }
}

data class PresetBookTemplate(
    val name: String,
    val subtitle: String,
    val type: BookType,
    val currency: String,
    val expenseCategories: List<String>,
    val incomeCategories: List<String>
)

val DEFAULT_PRESET_BOOKS = listOf(
    PresetBookTemplate(
        name = "境内投资账本",
        subtitle = "A股 · 基金 · 固收",
        type = BookType.DOMESTIC_INVEST,
        currency = "CNY",
        expenseCategories = listOf("股票买入", "基金申购", "理财购买", "交易税费", "其他投入"),
        incomeCategories = listOf("股票卖出", "基金赎回", "分红派息", "理财收益", "打新收益", "其它收益")
    ),
    PresetBookTemplate(
        name = "境外投资账本",
        subtitle = "美港股 · 多币种",
        type = BookType.OVERSEAS_INVEST,
        currency = "USD",
        expenseCategories = listOf("美股买入", "港股买入", "期权买入", "跨境电汇", "其他投入"),
        incomeCategories = listOf("美股卖出", "港股卖出", "境外股息", "美元利息", "期权盈利", "其它收益")
    ),
    PresetBookTemplate(
        name = "长期储蓄账本",
        subtitle = "定期 · 存单 · 国债",
        type = BookType.SAVINGS,
        currency = "CNY",
        expenseCategories = listOf("定期存款", "大额存单", "储蓄国债", "养老储蓄", "其他储蓄"),
        incomeCategories = listOf("存单到期", "利息到账", "国债兑付", "提前支取", "其他回款")
    ),
    PresetBookTemplate(
        name = "旅行度假账本",
        subtitle = "机票 · 酒店 · 游玩",
        type = BookType.CUSTOM,
        currency = "CNY",
        expenseCategories = listOf("交通机票", "酒店住宿", "景区门票", "餐饮美食", "特色纪念", "其他旅行"),
        incomeCategories = listOf("旅行津贴", "退订回款", "同行平摊", "其它")
    ),
    PresetBookTemplate(
        name = "生意人情账本",
        subtitle = "应酬 · 礼金 · 往来",
        type = BookType.CUSTOM,
        currency = "CNY",
        expenseCategories = listOf("商务宴请", "人情礼金", "礼品送往", "差旅报销", "办公杂项"),
        incomeCategories = listOf("客户回款", "人情收礼", "报销入账", "其它")
    )
)
