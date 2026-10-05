package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.ui.theme.*

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

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
                    text = "设置 · 关于",
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(BlueAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "极简记账", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text(text = "版本 1.0.0 正式版 (Release)", fontSize = 13.sp, color = textSecondary)
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "🌟 产品初心", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text(
                            text = "拒绝繁琐与商业化套路：本软件彻底移除所有 VIP 会员购买限制、无任何开屏或插页广告、无理财推销骚扰。\n\n专为纯粹的个人财务记录而生：涵盖日常消费、境内外投资、长期储蓄四大独立账本体系，支持无限新建与本地极速秒开。",
                            fontSize = 13.sp,
                            color = textSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "💡 快捷记账技巧", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text(text = "• 支出精简五大分类：衣、食、住、行、其他\n• 计算器键盘：支持直接在输入框中按 + 和 - 边算边记\n• 连记模式：点击左下角「再记」可保存并直接开始下一笔", fontSize = 13.sp, color = textSecondary, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
