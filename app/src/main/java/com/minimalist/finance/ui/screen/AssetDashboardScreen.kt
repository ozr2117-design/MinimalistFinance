package com.minimalist.finance.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.data.model.Account
import com.minimalist.finance.data.model.Book
import com.minimalist.finance.data.model.CategoryExpenseRank
import com.minimalist.finance.data.model.LifestyleVsNecessity
import com.minimalist.finance.data.model.MonthlyBudgetInfo
import com.minimalist.finance.data.model.MonthlyInsightSummary
import com.minimalist.finance.data.model.TransactionType
import com.minimalist.finance.ui.component.BudgetSettingDialog
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel
import java.util.Locale

data class BookStat(val book: Book, val netAmount: Double, val symbol: String)
data class AccountStat(val account: Account, val currentBalance: Double, val symbol: String)

@Composable
fun AssetDashboardScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

    // 0: 预算与洞察, 1: 资产大盘
    var selectedTab by remember { mutableIntStateOf(0) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    val books by viewModel.allBooks.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val records by viewModel.allRecords.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()

    // 预算与洞察流
    val monthlyBudget by viewModel.monthlyBudget.collectAsState()
    val budgetInfo by viewModel.monthlyBudgetInfo.collectAsState()
    val lifestyleVsNecessity by viewModel.lifestyleVsNecessity.collectAsState()
    val categoryRanks by viewModel.categoryRanks.collectAsState()
    val monthlySummary by viewModel.monthlySummary.collectAsState()

    val totalInitialBalance = remember(accounts) {
        accounts.sumOf { it.balance }
    }
    val netAsset = totalInitialBalance + (totalIncome - totalExpense)

    // 账户弹窗状态
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    // 高性能记忆优化
    val bookStats = remember(books, records) {
        books.map { book ->
            val bookRecords = records.filter { it.bookId == book.id }
            val bIncome = bookRecords.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val bExpense = bookRecords.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            BookStat(book, bIncome - bExpense, if (book.currency == "USD") "$" else "¥")
        }
    }

    val accountStats = remember(accounts, records) {
        accounts.map { acc ->
            val accRecords = records.filter { it.accountName == acc.name }
            val aIncome = accRecords.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val aExpense = accRecords.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            AccountStat(acc, acc.balance + (aIncome - aExpense), if (acc.currency == "USD") "$" else "¥")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
                    }
                    Text(
                        text = "财务大盘与洞察",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                // 顶部双 Tab 切换器
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF242730) else Color(0xFFEBEFF5))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TabPillButton(
                        text = "🎯 预算 · 洞察",
                        isSelected = selectedTab == 0,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 0 }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TabPillButton(
                        text = "🏦 资产 · 账户",
                        isSelected = selectedTab == 1,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 1 }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            // Tab 0: 预算与消费洞察
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 预算防暴走卡片
                item {
                    BudgetOverviewCard(
                        budgetInfo = budgetInfo,
                        hasBudget = monthlyBudget > 0.0,
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary,
                        onOpenSetting = { showBudgetDialog = true }
                    )
                }

                // 2. 刚需生存 vs 享受生活结构透视卡片
                item {
                    LifestyleVsNecessityCard(
                        data = lifestyleVsNecessity,
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary
                    )
                }

                // 3. 当月支出 TOP 排行榜
                item {
                    CategoryRanksCard(
                        ranks = categoryRanks,
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary
                    )
                }

                // 4. 当月财务温度小结
                item {
                    MonthlySummaryCard(
                        summary = monthlySummary,
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary
                    )
                }

                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
        } else {
            // Tab 1: 资产大盘与账户列表
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 顶部核心总净资产卡片
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBg)
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(text = "预估全局总净资产 (CNY)", fontSize = 13.sp, color = textSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format(Locale.CHINA, "¥ %.2f", netAsset),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechBlue
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "累计总收入", fontSize = 12.sp, color = textSecondary)
                                    Text(
                                        text = String.format(Locale.CHINA, "+¥ %.2f", totalIncome),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MintGreen
                                    )
                                }
                                Column {
                                    Text(text = "累计总支出", fontSize = 12.sp, color = textSecondary)
                                    Text(
                                        text = String.format(Locale.CHINA, "-¥ %.2f", totalExpense),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CoralRed
                                    )
                                }
                                Column {
                                    Text(text = "累计净结余", fontSize = 12.sp, color = textSecondary)
                                    val netSign = if (netAsset >= 0) "+¥ " else "-¥ "
                                    Text(
                                        text = String.format(Locale.CHINA, "%s%.2f", netSign, Math.abs(netAsset)),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (netAsset >= 0) BlueAccent else CoralRed
                                    )
                                }
                            }
                        }
                    }
                }

                // 四大账本分类资产沉淀
                item {
                    Text(text = "四大账本资产分布", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                }

                items(bookStats, key = { "book_${it.book.id}" }) { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = stat.book.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = stat.book.subtitle, fontSize = 12.sp, color = textSecondary)
                        }
                        Text(
                            text = String.format(Locale.CHINA, "%s %.2f", stat.symbol, stat.netAmount),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                // 资金账户列表
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "常用资金账户余额", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text(text = "点击任意账户卡片可快速修改本金或删除多余账户", fontSize = 11.sp, color = textSecondary)
                        }
                        TextButton(onClick = { showAddAccountDialog = true }) {
                            Text("+ 添加账户", color = BlueAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                items(accountStats, key = { "account_${it.account.id}_${it.account.name}" }) { stat ->
                    val accIcon = getAccountIcon(stat.account.name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .clickable { editingAccount = stat.account }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = accIcon, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = stat.account.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                                Text(text = "初始本金: ¥ ${String.format(Locale.CHINA, "%.2f", stat.account.balance)}", fontSize = 11.sp, color = textSecondary)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format(Locale.CHINA, "%s %.2f", stat.symbol, stat.currentBalance),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (stat.currentBalance < 0) CoralRed else textColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "编辑余额", tint = textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
        }
    }

    // 预算设定弹窗
    if (showBudgetDialog) {
        BudgetSettingDialog(
            currentBudget = monthlyBudget,
            onDismiss = { showBudgetDialog = false },
            onSave = { newBudget ->
                viewModel.setMonthlyBudget(newBudget)
                showBudgetDialog = false
            }
        )
    }

    // 修改账户余额与管理弹窗
    editingAccount?.let { acc ->
        var editName by remember(acc) { mutableStateOf(acc.name) }
        var editBalance by remember(acc) {
            mutableStateOf(if (acc.balance == 0.0) "" else String.format(Locale.CHINA, "%.2f", acc.balance))
        }

        AlertDialog(
            onDismissRequest = { editingAccount = null },
            title = { Text(text = "修改【${acc.name}】余额", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "设置该资金账户的初始本金，系统将自动汇总记账收支计算当前实时余额并计入大盘总资产。",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("账户名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editBalance,
                        onValueChange = { editBalance = it },
                        label = { Text("初始本金 / 余额") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newBal = editBalance.toDoubleOrNull() ?: 0.0
                        viewModel.updateAccount(
                            acc.copy(
                                name = editName.trim().ifEmpty { acc.name },
                                balance = newBal
                            )
                        )
                        editingAccount = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.deleteAccount(acc)
                            editingAccount = null
                        }
                    ) {
                        Text("删除账户", color = CoralRed)
                    }
                    TextButton(onClick = { editingAccount = null }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    // 添加自定义账户弹窗
    if (showAddAccountDialog) {
        var newAccName by remember { mutableStateOf("") }
        var newAccBalance by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text(text = "添加资金账户", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newAccName,
                        onValueChange = { newAccName = it },
                        label = { Text("账户名称") },
                        placeholder = { Text("如：工商银行卡、交通卡、美团月付") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAccBalance,
                        onValueChange = { newAccBalance = it },
                        label = { Text("初始余额 (可选)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newAccName.trim()
                        if (trimmed.isNotEmpty()) {
                            val initBal = newAccBalance.toDoubleOrNull() ?: 0.0
                            viewModel.addAccount(trimmed, initBal)
                            showAddAccountDialog = false
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ---------------- UI 组件模块 ----------------

@Composable
private fun TabPillButton(
    text: String,
    isSelected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selectedBg = if (isDark) DarkSurfaceCard else Color.White
    val selectedTextColor = if (isDark) Color.White else Color(0xFF1E293B)
    val unselectedTextColor = if (isDark) DarkTextSecondary else LightTextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) selectedBg else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) selectedTextColor else unselectedTextColor
        )
    }
}

/**
 * 预算防暴走卡片
 */
@Composable
private fun BudgetOverviewCard(
    budgetInfo: MonthlyBudgetInfo,
    hasBudget: Boolean,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color,
    onOpenSetting: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp)
            .animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "本月预算防暴走",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(budgetInfo.statusColor).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = budgetInfo.statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(budgetInfo.statusColor)
                        )
                    }
                }

                TextButton(
                    onClick = onOpenSetting,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (hasBudget) "调整预算" else "+ 设定预算",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BlueAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!hasBudget) {
                Text(
                    text = "当月已支出 ¥ ${String.format(Locale.CHINA, "%.2f", budgetInfo.spentAmount)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoralRed
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "尚未设定本月预算上限。设定后将按剩余天数自动推算「每日可用支出额度」，防冲动消费。",
                    fontSize = 12.sp,
                    color = textSecondary,
                    lineHeight = 18.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "预算剩余可用额度", fontSize = 12.sp, color = textSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (budgetInfo.remainingBudget >= 0) {
                                String.format(Locale.CHINA, "¥ %.2f", budgetInfo.remainingBudget)
                            } else {
                                String.format(Locale.CHINA, "超支 ¥ %.2f", -budgetInfo.remainingBudget)
                            },
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(budgetInfo.statusColor)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "总预算: ¥ ${String.format(Locale.CHINA, "%.0f", budgetInfo.budgetAmount)}", fontSize = 11.sp, color = textSecondary)
                        Text(text = "已花: ¥ ${String.format(Locale.CHINA, "%.2f", budgetInfo.spentAmount)} (${String.format(Locale.CHINA, "%.1f", budgetInfo.usagePercent)}%)", fontSize = 11.sp, color = textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 进度条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray.copy(alpha = 0.2f))
                ) {
                    val progressFraction = (budgetInfo.usagePercent / 100f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(budgetInfo.statusColor))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 每日建议可用额度高亮微胶囊
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(budgetInfo.statusColor).copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💡", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "本月尚余 ${budgetInfo.daysRemaining} 天",
                                fontSize = 12.sp,
                                color = textColor
                            )
                        }
                        Text(
                            text = if (budgetInfo.remainingBudget > 0) {
                                "建议日均上限 ¥ ${String.format(Locale.CHINA, "%.1f", budgetInfo.dailyAvailable)}"
                            } else {
                                "额度见底，已超支防暴走"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(budgetInfo.statusColor)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 刚需生存 vs 享受生活结构透视卡片
 */
@Composable
private fun LifestyleVsNecessityCard(
    data: LifestyleVsNecessity,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "消费结构透视",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "当月支出 ¥ ${String.format(Locale.CHINA, "%.2f", data.totalExpense)}",
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 双色比例横条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                if (data.totalExpense > 0) {
                    val necFraction = (data.necessityRatio / 100f).coerceIn(0f, 1f)
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(if (necFraction > 0.01f) necFraction else 0.01f)
                                .fillMaxHeight()
                                .background(TechBlue)
                        )
                        Box(
                            modifier = Modifier
                                .weight(if (1f - necFraction > 0.01f) 1f - necFraction else 0.01f)
                                .fillMaxHeight()
                                .background(Color(0xFFFF9800))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 数据说明行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TechBlue))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "刚需生存 ${String.format(Locale.CHINA, "%.1f", data.necessityRatio)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "¥ ${String.format(Locale.CHINA, "%.2f", data.necessityExpense)}",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF9800)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "品质享受 ${String.format(Locale.CHINA, "%.1f", data.lifestyleRatio)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "¥ ${String.format(Locale.CHINA, "%.2f", data.lifestyleExpense)}",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 心理正反馈建议条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TechBlue.copy(alpha = 0.07f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = "✨", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = data.insightAdvice,
                        fontSize = 12.sp,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * 当月支出 TOP 排行榜
 */
@Composable
private fun CategoryRanksCard(
    ranks: List<CategoryExpenseRank>,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "当月支出 TOP 榜",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (ranks.isEmpty()) {
                Text(
                    text = "本月暂无支出记录，记账后再来看看吧 ~",
                    fontSize = 12.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ranks.forEachIndexed { index, item ->
                        val rankColor = when (index) {
                            0 -> Color(0xFFFF5252)
                            1 -> Color(0xFFFF9800)
                            2 -> Color(0xFF4CAF50)
                            else -> textSecondary
                        }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = rankColor,
                                        modifier = Modifier.width(18.dp)
                                    )
                                    Text(
                                        text = item.categoryName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${item.count}笔",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "¥ ${String.format(Locale.CHINA, "%.2f", item.amount)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${String.format(Locale.CHINA, "%.1f", item.percentage)}%",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 进度条
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.Gray.copy(alpha = 0.15f))
                            ) {
                                val rankFraction = (item.percentage / 100f).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = rankFraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(rankColor.copy(alpha = 0.85f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 当月财务温度小结卡片
 */
@Composable
private fun MonthlySummaryCard(
    summary: MonthlyInsightSummary,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "本月财务温度小结",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4 格指标
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryMetricItem(
                    title = "当月总记账",
                    value = "${summary.totalTransactions} 笔",
                    modifier = Modifier.weight(1f),
                    textColor = textColor,
                    textSecondary = textSecondary
                )
                SummaryMetricItem(
                    title = "记账天数",
                    value = "${summary.daysRecorded} 天",
                    modifier = Modifier.weight(1f),
                    textColor = textColor,
                    textSecondary = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryMetricItem(
                    title = "单笔均销",
                    value = "¥ ${String.format(Locale.CHINA, "%.1f", summary.avgExpensePerTx)}",
                    modifier = Modifier.weight(1f),
                    textColor = textColor,
                    textSecondary = textSecondary
                )
                SummaryMetricItem(
                    title = "单笔最大消费",
                    value = "¥ ${String.format(Locale.CHINA, "%.1f", summary.maxSingleExpense)}",
                    modifier = Modifier.weight(1f),
                    textColor = textColor,
                    textSecondary = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 温度小语
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF673AB7).copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = "🌱", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = summary.encouragement,
                        fontSize = 12.sp,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricItem(
    title: String,
    value: String,
    textColor: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = title, fontSize = 11.sp, color = textSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

private fun getAccountIcon(name: String): ImageVector {
    return when {
        name.contains("微信") -> Icons.Default.Chat
        name.contains("支付宝") -> Icons.Default.AccountBalanceWallet
        name.contains("卡") -> Icons.Default.CreditCard
        name.contains("现金") -> Icons.Default.Paid
        name.contains("券") || name.contains("理财") || name.contains("股票") -> Icons.Default.TrendingUp
        else -> Icons.Default.AccountBalance
    }
}
