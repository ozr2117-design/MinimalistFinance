package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.minimalist.finance.data.model.TransactionType
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

    val books by viewModel.allBooks.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val records by viewModel.allRecords.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()

    val totalInitialBalance = remember(accounts) {
        accounts.sumOf { it.balance }
    }
    val netAsset = totalInitialBalance + (totalIncome - totalExpense)

    // 弹窗状态：添加自定义账户 / 修改已有账户
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    // 高性能记忆优化：在滚动时绝不在 UI 线程重复过滤遍历 records
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
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
                }
                Text(
                    text = "资产大盘总览",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部核心总净资产卡片 (完全由真实数据库驱动)
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

            // 资金账户列表 (通用主流账户 + 支持自由添加与随时修改)
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

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
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
