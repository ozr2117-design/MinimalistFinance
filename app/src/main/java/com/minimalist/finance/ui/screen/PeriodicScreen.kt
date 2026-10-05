package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel

@Composable
fun PeriodicScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

    var selectedTab by remember { mutableStateOf(0) } // 0: 周期规则, 1: 分期计划

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
                        text = "周期与分期管理",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = BlueAccent
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("固定周期记账", fontSize = 15.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("账单分期计划", fontSize = 15.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedTab == 0) {
                // 固定周期记账列表
                item {
                    PeriodicRuleCard(
                        title = "每月固定房租",
                        desc = "每月 15 日自动扣划 (招行储蓄卡)",
                        amount = "-¥ 3,500.00",
                        amountColor = CoralRed,
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary
                    )
                }
                item {
                    PeriodicRuleCard(
                        title = "月度薪资入账",
                        desc = "每月 10 日定期入账",
                        amount = "+¥ 20,000.00",
                        amountColor = MintGreen,
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary
                    )
                }
                item {
                    PeriodicRuleCard(
                        title = "手机与家庭宽带套餐",
                        desc = "每月 1 日扣费",
                        amount = "-¥ 99.00",
                        amountColor = CoralRed,
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary
                    )
                }
            } else {
                // 分期还款看板
                item {
                    InstallmentPlanCard(
                        title = "MacBook Pro 16寸免息分期",
                        totalAmount = "¥ 12,000.00",
                        currentPeriod = 3,
                        totalPeriod = 12,
                        monthlyAmount = "¥ 1,000.00",
                        nextDueDate = "下期还款日: 11月08日",
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary
                    )
                }
                item {
                    InstallmentPlanCard(
                        title = "新手机 24期免息分期",
                        totalAmount = "¥ 5,999.00",
                        currentPeriod = 18,
                        totalPeriod = 24,
                        monthlyAmount = "¥ 249.95",
                        nextDueDate = "下期还款日: 11月15日",
                        cardBg = cardBg,
                        textColor = textColor,
                        textSecondary = textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodicRuleCard(
    title: String,
    desc: String,
    amount: String,
    amountColor: Color,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color
) {
    var isEnabled by remember { mutableStateOf(true) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc, fontSize = 12.sp, color = textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = amount, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = amountColor)
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { isEnabled = it }
        )
    }
}

@Composable
private fun InstallmentPlanCard(
    title: String,
    totalAmount: String,
    currentPeriod: Int,
    totalPeriod: Int,
    monthlyAmount: String,
    nextDueDate: String,
    cardBg: Color,
    textColor: Color,
    textSecondary: Color
) {
    val progress = currentPeriod.toFloat() / totalPeriod.toFloat()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text(text = "进行中 ($currentPeriod/$totalPeriod 期)", fontSize = 12.sp, color = BlueAccent, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = BlueAccent,
                trackColor = cardBg.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "总额: $totalAmount", fontSize = 13.sp, color = textSecondary)
                Text(text = "每期应还: $monthlyAmount", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = nextDueDate, fontSize = 12.sp, color = textSecondary)
        }
    }
}
