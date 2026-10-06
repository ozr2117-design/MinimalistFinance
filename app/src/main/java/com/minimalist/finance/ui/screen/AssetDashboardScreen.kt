package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.data.model.TransactionType
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel
import java.util.Locale

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

    val netAsset = totalIncome - totalExpense

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

            items(books) { book ->
                val bookRecords = records.filter { it.bookId == book.id }
                val bIncome = bookRecords.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val bExpense = bookRecords.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val bNet = bIncome - bExpense
                val symbol = if (book.currency == "USD") "$" else "¥"

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
                        Text(text = book.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = book.subtitle, fontSize = 12.sp, color = textSecondary)
                    }
                    Text(
                        text = String.format(Locale.CHINA, "%s %.2f", symbol, bNet),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            // 资金账户池列表
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "资金账户余额", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
            }

            items(accounts) { acc ->
                val symbol = if (acc.currency == "USD") "$" else "¥"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = acc.name, fontSize = 15.sp, color = textColor)
                    }
                    Text(
                        text = String.format(Locale.CHINA, "%s %.2f", symbol, acc.balance),
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                }
            }
        }
    }
}

