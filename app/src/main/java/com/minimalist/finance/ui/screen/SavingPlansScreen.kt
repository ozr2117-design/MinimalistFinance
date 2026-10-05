package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel

data class PlanTemplate(
    val name: String,
    val desc: String,
    val iconColor: Color,
    val target: String
)

@Composable
fun SavingPlansScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

    var savedToday by remember { mutableStateOf(false) }

    val templates = listOf(
        PlanTemplate("365 存钱法", "以 365 天为周期，每日存钱金额递增 (1+2+..+365)，累计可存 66,795 元", Color(0xFFFF5722), "¥ 66,795.00"),
        PlanTemplate("52 周存钱法", "第 1 周存 1 元，第 2 周存 2 元，依此类推，一年积攒 1,378 元", Color(0xFF9C27B0), "¥ 1,378.00"),
        PlanTemplate("12 存单法", "每月固定存入一笔定期存单，持续 12 个月，存单自动滚动生息", Color(0xFFFF9800), "¥ 12,000.00"),
        PlanTemplate("定额存钱法", "每个周期固定存入一笔金额，持续存入 N 次，积少成多", Color(0xFF00BCD4), "¥ 10,000.00"),
        PlanTemplate("灵活存钱法", "设定存钱总目标金额，每次存钱金额不固定，自由灵活", Color(0xFF2196F3), "¥ 50,000.00")
    )

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
                    text = "存钱计划",
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
            // 当前进行中的活跃计划卡片
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🔥 进行中：365 存钱法", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text(text = "第 18/365 天", fontSize = 12.sp, color = BlueAccent, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "已累计存入长期储蓄账本", fontSize = 12.sp, color = textSecondary)
                        Text(text = if (savedToday) "¥ 1,389.00" else "¥ 1,218.00", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MintGreen)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { savedToday = !savedToday },
                            colors = ButtonDefaults.buttonColors(containerColor = if (savedToday) Color.Gray else BlueAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (savedToday) "今日已打卡存入 ¥171.00 ✔" else "今日打卡：存入 ¥171.00 到长期储蓄")
                        }
                    }
                }
            }

            item {
                Text(text = "添加新的存钱计划", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
            }

            // 存钱模型列表
            items(templates) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(item.iconColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Savings, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(text = item.desc, fontSize = 12.sp, color = textSecondary, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}
