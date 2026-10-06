package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel

@Composable
fun DrawerMenu(
    viewModel: MainViewModel,
    onNavigateToBooks: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToPeriodic: () -> Unit,
    onNavigateToAutoRecord: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val books by viewModel.allBooks.collectAsState()
    val curBookId by viewModel.currentBookId.collectAsState()
    val currentBookName = books.find { it.id == curBookId }?.name ?: "日常消费账本"
    val themeMode by viewModel.currentThemeMode.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(if (isDark) DarkSurface else LightSurface)
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            // 用户头部卡片 (传统数簿印章)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF8B1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "數",
                        color = Color(0xFFFFD700),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text(
                        text = "数簿",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "每一笔都有数",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
            }

            HorizontalDivider(color = if (isDark) DarkBorder else LightBorder)

            // 菜单列表
            DrawerMenuItem(
                icon = Icons.Default.Book,
                title = "我的账本",
                subtitle = currentBookName,
                textColor = textColor,
                textSecondary = textSecondary,
                onClick = {
                    onCloseDrawer()
                    onNavigateToBooks()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.BarChart,
                title = "资产大盘",
                textColor = textColor,
                textSecondary = textSecondary,
                onClick = {
                    onCloseDrawer()
                    onNavigateToDashboard()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Search,
                title = "搜索账单",
                textColor = textColor,
                textSecondary = textSecondary,
                onClick = {
                    onCloseDrawer()
                    onNavigateToSearch()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.EventRepeat,
                title = "周期 · 分期",
                textColor = textColor,
                textSecondary = textSecondary,
                onClick = {
                    onCloseDrawer()
                    onNavigateToPeriodic()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Bolt,
                title = "自动记账",
                subtitle = "微信/支付宝无感",
                textColor = textColor,
                textSecondary = textSecondary,
                onClick = {
                    onCloseDrawer()
                    onNavigateToAutoRecord()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.FileDownload,
                title = "数据备份导出",
                textColor = textColor,
                textSecondary = textSecondary,
                onClick = {
                    onCloseDrawer()
                    onNavigateToBackup()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Palette,
                title = "主题外观",
                subtitle = when (themeMode) {
                    ThemeMode.DARK -> "夜间纯黑"
                    ThemeMode.LIGHT -> "日间极简"
                    ThemeMode.SYSTEM -> "跟随系统"
                },
                textColor = textColor,
                textSecondary = textSecondary,
                onClick = { showThemeDialog = true }
            )
        }

        // 底部设置
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onCloseDrawer()
                    onNavigateToAbout()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = "设置", tint = textSecondary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "设置 · 关于", fontSize = 15.sp, color = textSecondary)
        }
    }

    // 主题切换弹窗
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(text = "选择主题外观") },
            text = {
                Column {
                    ThemeOptionRow("跟随系统 (默认)", themeMode == ThemeMode.SYSTEM) {
                        viewModel.currentThemeMode.value = ThemeMode.SYSTEM
                        showThemeDialog = false
                    }
                    ThemeOptionRow("夜间纯黑 (AMOLED)", themeMode == ThemeMode.DARK) {
                        viewModel.currentThemeMode.value = ThemeMode.DARK
                        showThemeDialog = false
                    }
                    ThemeOptionRow("日间极简 (白)", themeMode == ThemeMode.LIGHT) {
                        viewModel.currentThemeMode.value = ThemeMode.LIGHT
                        showThemeDialog = false
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun ThemeOptionRow(title: String, isSelected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, fontSize = 15.sp)
        if (isSelected) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = BlueAccent)
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    textColor: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, tint = textColor, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
        }

        if (subtitle != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = subtitle, fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
