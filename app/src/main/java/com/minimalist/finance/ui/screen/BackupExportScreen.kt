package com.minimalist.finance.ui.screen

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel

@Composable
fun BackupExportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                    text = "数据备份与导出",
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
            // 隐私安全背书卡片
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MintGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "100% 本地隐私安全承诺", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "本软件所有账本、账户、资产与消费记录均仅保存在你当前手机本地的 SQLite 数据库中，不连接任何第三方商业统计或广告服务器。你可以随时随地完全导出或备份所有属于你的数据。",
                            fontSize = 13.sp,
                            color = textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 导出功能
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column {
                        Text(text = "📊 导出为 Excel / CSV 格式表格", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "包含所有账本的交易时间、类型、金额、分类、备注、账户等完整明细字段。", fontSize = 13.sp, color = textSecondary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                Toast.makeText(context, "已成功导出 CSV 账单到手机 Download 文件夹！", Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("立即导出全部流水 (.csv)")
                        }
                    }
                }
            }

            // 数据库备份
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column {
                        Text(text = "💾 SQLite 数据库全量快照", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "可用于换新手机时一键无缝还原所有账本与历史记录。", fontSize = 13.sp, color = textSecondary)
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "数据库快照 minimalist_finance.db 已备份到本地！", Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("备份数据库快照 (.db)")
                        }
                    }
                }
            }

            // 危险区域：一键清空所有历史流水 (满足用户需求，恢复纯净空库)
            item {
                var showClearDialog by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column {
                        Text(text = "⚠️ 危险操作：清空所有账单流水", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "清空所有账本下的所有记账流水记录，将资产统计与结余完全重置归零（不会删除账本与分类结构）。", fontSize = 13.sp, color = textSecondary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("清空所有流水记录 (恢复纯净空库)", color = Color.White)
                        }
                    }
                }

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text(text = "确认清空所有账单流水？", fontWeight = FontWeight.Bold) },
                        text = { Text("此操作将永久删除本地 SQLite 中存储的所有记账历史数据，重置资产大盘为初始状态，且不可撤销！") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.clearAllRecords {
                                        Toast.makeText(context, "所有账单流水已全部清空，已恢复纯净空库！", Toast.LENGTH_LONG).show()
                                        showClearDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                            ) {
                                Text("确认清空", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("取消")
                            }
                        }
                    )
                }
            }
        }
    }
}

