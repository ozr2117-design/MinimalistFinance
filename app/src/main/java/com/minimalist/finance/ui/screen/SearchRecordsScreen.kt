package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.data.model.Record
import com.minimalist.finance.data.model.TransactionType
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class TimeGroupingMode(val label: String) {
    MONTH("按月账单"),
    DAY("按日明细"),
    YEAR("按年对账"),
    ALL("全部平铺")
}

data class StatementGroup(
    val id: String,
    val title: String,
    val periodBadge: String,
    val expenseTotal: Double,
    val incomeTotal: Double,
    val balance: Double,
    val recordCount: Int,
    val records: List<Record>
)

@Composable
fun SearchRecordsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

    var query by remember { mutableStateOf("") }
    var selectedGrouping by remember { mutableStateOf(TimeGroupingMode.MONTH) }
    var selectedBookFilterId by remember { mutableStateOf<Long?>(null) } // null = 全部账本

    val allRecords by viewModel.allRecords.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()

    // 过滤流水：账本过滤 + 关键词过滤 (备注、分类、账户、标签、金额)
    val filteredRecords = remember(query, selectedBookFilterId, allRecords) {
        allRecords.filter { record ->
            val matchBook = selectedBookFilterId == null || record.bookId == selectedBookFilterId
            val matchQuery = if (query.isBlank()) {
                true
            } else {
                record.remark.contains(query, ignoreCase = true) ||
                record.categoryName.contains(query, ignoreCase = true) ||
                record.accountName.contains(query, ignoreCase = true) ||
                record.tag.contains(query, ignoreCase = true) ||
                record.amount.toString().contains(query)
            }
            matchBook && matchQuery
        }
    }

    // 类似银行/信用卡账单的按时间维度智能归类与统计
    val statementGroups = remember(filteredRecords, selectedGrouping) {
        when (selectedGrouping) {
            TimeGroupingMode.MONTH -> {
                val sdf = SimpleDateFormat("yyyy年MM月", Locale.CHINA)
                // 按月份降序排序
                filteredRecords
                    .groupBy { sdf.format(Date(it.timestamp)) }
                    .map { (monthStr, recs) ->
                        val expense = recs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                        val income = recs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                        StatementGroup(
                            id = monthStr,
                            title = "$monthStr 账单",
                            periodBadge = "月度对账",
                            expenseTotal = expense,
                            incomeTotal = income,
                            balance = income - expense,
                            recordCount = recs.size,
                            records = recs.sortedByDescending { it.timestamp }
                        )
                    }
            }
            TimeGroupingMode.DAY -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                val displaySdf = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA)
                filteredRecords
                    .groupBy { sdf.format(Date(it.timestamp)) }
                    .map { (_, recs) ->
                        val firstTime = recs.first().timestamp
                        val displayTitle = displaySdf.format(Date(firstTime))
                        val expense = recs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                        val income = recs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                        StatementGroup(
                            id = displayTitle,
                            title = displayTitle,
                            periodBadge = "当日流水",
                            expenseTotal = expense,
                            incomeTotal = income,
                            balance = income - expense,
                            recordCount = recs.size,
                            records = recs.sortedByDescending { it.timestamp }
                        )
                    }
            }
            TimeGroupingMode.YEAR -> {
                val sdf = SimpleDateFormat("yyyy年度", Locale.CHINA)
                filteredRecords
                    .groupBy { sdf.format(Date(it.timestamp)) }
                    .map { (yearStr, recs) ->
                        val expense = recs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                        val income = recs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                        StatementGroup(
                            id = yearStr,
                            title = "$yearStr 年度总账",
                            periodBadge = "年度对账",
                            expenseTotal = expense,
                            incomeTotal = income,
                            balance = income - expense,
                            recordCount = recs.size,
                            records = recs.sortedByDescending { it.timestamp }
                        )
                    }
            }
            TimeGroupingMode.ALL -> {
                val expense = filteredRecords.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val income = filteredRecords.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                listOf(
                    StatementGroup(
                        id = "ALL",
                        title = "全部历史对账单",
                        periodBadge = "全量汇总",
                        expenseTotal = expense,
                        incomeTotal = income,
                        balance = income - expense,
                        recordCount = filteredRecords.size,
                        records = filteredRecords.sortedByDescending { it.timestamp }
                    )
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .fillMaxWidth()
            ) {
                // 顶部标题栏
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
                        text = "我要查账",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                // 实时关键词搜索框 (支持备注、分类、账户、金额)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("输入备注、分类、账户或金额搜索...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textSecondary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清空", tint = textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // 类似信用卡查账的时间归类维度切换胶囊栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) DarkSurface else Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TimeGroupingMode.values().forEach { mode ->
                        val isSelected = selectedGrouping == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) (if (isDark) DarkSurfaceCard else Color.White) else Color.Transparent)
                                .clickable { selectedGrouping = mode }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) (if (isDark) Color.White else Color(0xFF1E293B)) else textSecondary
                            )
                        }
                    }
                }

                // 账本筛选水平滚动条 (保留账本独立性)
                if (allBooks.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterBookChip(
                            label = "全部账本",
                            isSelected = selectedBookFilterId == null,
                            isDark = isDark,
                            onClick = { selectedBookFilterId = null }
                        )
                        allBooks.forEach { book ->
                            FilterBookChip(
                                label = book.name,
                                isSelected = selectedBookFilterId == book.id,
                                isDark = isDark,
                                onClick = { selectedBookFilterId = book.id }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (filteredRecords.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (query.isEmpty()) "暂无符合条件的账单记录" else "未搜到「$query」相关的对账流水",
                        color = textSecondary,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "可尝试切换上方时间维度或清空搜索词",
                        color = textSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 按月/日/年划分的信用卡对账单区块
                statementGroups.forEach { group ->
                    item(key = group.id) {
                        StatementSummaryHeader(
                            group = group,
                            cardBg = cardBg,
                            textColor = textColor,
                            textSecondary = textSecondary,
                            isDark = isDark
                        )
                    }

                    items(group.records, key = { it.id }) { record ->
                        RecordItemCard(
                            record = record,
                            cardBg = cardBg,
                            textColor = textColor,
                            textSecondary = textSecondary,
                            isDark = isDark,
                            onDelete = { viewModel.deleteRecord(record) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// 账本筛选轻量胶囊
@Composable
private fun FilterBookChip(
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) BlueAccent.copy(alpha = 0.18f) else (if (isDark) DarkSurface else Color(0xFFF1F5F9)),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, BlueAccent) else null,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) BlueAccent else (if (isDark) DarkTextSecondary else LightTextSecondary),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// 类似银行/信用卡账单周期的统计头部卡片 (包含总支出、总收入、收支结余与笔数)
@Composable
private fun StatementSummaryHeader(
    group: StatementGroup,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color,
    isDark: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = BlueAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = group.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BlueAccent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${group.recordCount} 笔流水",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = BlueAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = if (isDark) DarkBorder else LightBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // 三列核算汇总 (支出 / 收入 / 结余)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "周期支出", fontSize = 11.sp, color = textSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "¥${String.format(Locale.CHINA, "%.2f", group.expenseTotal)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoralRed
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "周期收入", fontSize = 11.sp, color = textSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "¥${String.format(Locale.CHINA, "%.2f", group.incomeTotal)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintGreen
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "收支结余", fontSize = 11.sp, color = textSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    val balanceStr = String.format(Locale.CHINA, "%.2f", group.balance)
                    Text(
                        text = if (group.balance >= 0) "+¥$balanceStr" else "-¥${String.format(Locale.CHINA, "%.2f", -group.balance)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

// 单笔账单卡片 (包含分类、账户、商户备注、标签、准确时间、详情弹窗与删除)
@Composable
private fun RecordItemCard(
    record: Record,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color,
    isDark: Boolean,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDetailDialog by remember { mutableStateOf(false) }

    val isExpense = record.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) CoralRed else MintGreen
    val amountPrefix = if (isExpense) "-" else "+"
    val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))

    // 智能提取干净标题与单号：兼容旧格式（"自动记账: 肯德基 [单号:xxx]"）与新格式（"肯德基 [单号:xxx]"）
    val rawRemark = record.remark
    var cleanTitle = rawRemark.removePrefix("自动记账: ").trim()
    val orderIdMatch = Regex("\\[单号:([^\\]]+)\\]").find(cleanTitle)
    val orderId = orderIdMatch?.groupValues?.get(1)
    if (orderIdMatch != null) {
        cleanTitle = cleanTitle.replace(orderIdMatch.value, "").trim()
    }
    if (cleanTitle.isBlank()) {
        cleanTitle = if (record.categoryName.isNotBlank()) record.categoryName else (if (isExpense) "支出" else "收入")
    }

    // 点击卡片弹出的全貌详情对话框
    if (showDetailDialog) {
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = {
                Text(text = "流水详情", fontWeight = FontWeight.Bold, color = textColor)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "$amountPrefix¥${String.format(Locale.CHINA, "%.2f", record.amount)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    HorizontalDivider(color = if (isDark) DarkBorder else LightBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    DetailRow(label = "商户/说明", value = cleanTitle, textColor = textColor, textSecondary = textSecondary)
                    DetailRow(label = "分类", value = if (record.categoryName.isNotBlank()) record.categoryName else "未分类", textColor = textColor, textSecondary = textSecondary)
                    DetailRow(label = "账户", value = if (record.accountName.isNotBlank()) record.accountName else "默认账户", textColor = textColor, textSecondary = textSecondary)
                    DetailRow(label = "标签", value = if (record.tag.isNotBlank()) record.tag else "无", textColor = textColor, textSecondary = textSecondary)
                    val fullDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(record.timestamp))
                    DetailRow(label = "交易时间", value = fullDateStr, textColor = textColor, textSecondary = textSecondary)

                    if (!orderId.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        DetailRow(label = "交易单号", value = orderId, textColor = textColor, textSecondary = textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("交易单号", orderId))
                                Toast.makeText(context, "单号已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("复制交易单号", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text("完成", color = BlueAccent)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .clickable { showDetailDialog = true }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(amountColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpense) Icons.Default.ShoppingBag else Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 第一行：专注呈现商户/说明全名，最多2行，彻底杜绝短距离被截断
                Text(
                    text = cleanTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 2,
                    lineHeight = 20.sp,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 第二行：时间 · 分类 · 账户胶囊 · 标签胶囊
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = buildString {
                            append(dateStr)
                            if (record.categoryName.isNotBlank() && cleanTitle != record.categoryName) {
                                append(" · ")
                                append(record.categoryName)
                            }
                        },
                        fontSize = 12.sp,
                        color = textSecondary,
                        maxLines = 1,
                        softWrap = false
                    )

                    if (record.accountName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDark) DarkSurface else Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = record.accountName,
                                fontSize = 10.sp,
                                color = textSecondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (record.tag.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BlueAccent.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "#${record.tag}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = BlueAccent,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$amountPrefix¥${String.format(Locale.CHINA, "%.2f", record.amount)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "删除记录",
                    tint = textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    textColor: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = textSecondary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f),
            softWrap = true
        )
    }
}
