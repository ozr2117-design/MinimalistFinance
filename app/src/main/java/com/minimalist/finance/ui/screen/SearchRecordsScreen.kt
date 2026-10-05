package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.data.model.Record
import com.minimalist.finance.data.model.TransactionType
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    val allRecords by viewModel.allRecords.collectAsState()

    // 过滤流水
    val filteredRecords = remember(query, allRecords) {
        if (query.isBlank()) {
            allRecords
        } else {
            allRecords.filter {
                it.remark.contains(query, ignoreCase = true) ||
                it.amount.toString().contains(query) ||
                it.type.name.contains(query, ignoreCase = true)
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
                    }
                    Text(
                        text = "搜索账单明细",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("输入备注、金额关键词搜索...") },
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
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
                Text(text = if (query.isEmpty()) "暂无账单流水，快去记一笔吧" else "未找到匹配账单", color = textSecondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords) { record ->
                    RecordItemCard(
                        record = record,
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary,
                        onDelete = { viewModel.deleteRecord(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordItemCard(
    record: Record,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color,
    onDelete: () -> Unit
) {
    val isExpense = record.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) CoralRed else MintGreen
    val amountPrefix = if (isExpense) "-" else "+"
    val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(amountColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpense) Icons.Default.ShoppingBag else Icons.Default.Work,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (record.remark.isBlank()) (if (isExpense) "日常支出" else "日常收入") else record.remark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                Text(text = dateStr, fontSize = 12.sp, color = textSecondary)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$amountPrefix¥ ${String.format(Locale.getDefault(), "%.2f", record.amount)}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
