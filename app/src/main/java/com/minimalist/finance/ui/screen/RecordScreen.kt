package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.minimalist.finance.data.model.TransactionType
import com.minimalist.finance.ui.component.CustomKeypad
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel

@Composable
fun RecordScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToBooks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentType by viewModel.currentTransactionType.collectAsState()
    val amountExp by viewModel.amountExpression.collectAsState()
    val remark by viewModel.remarkText.collectAsState()
    val books by viewModel.allBooks.collectAsState()
    val curBookId by viewModel.currentBookId.collectAsState()

    val currentBook = books.find { it.id == curBookId }

    // 主色调随 Tab 变化
    val themeColor = when (currentType) {
        TransactionType.EXPENSE -> CoralRed
        TransactionType.INCOME -> MintGreen
        TransactionType.TRANSFER -> TechBlue
    }

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "菜单", tint = textColor)
                    }

                    // 顶部三 Tab (支出 / 收入 / 转账)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabHeaderItem(
                            title = "支出",
                            isSelected = currentType == TransactionType.EXPENSE,
                            color = CoralRed,
                            onClick = { viewModel.currentTransactionType.value = TransactionType.EXPENSE }
                        )
                        TabHeaderItem(
                            title = "收入",
                            isSelected = currentType == TransactionType.INCOME,
                            color = MintGreen,
                            onClick = { viewModel.currentTransactionType.value = TransactionType.INCOME }
                        )
                        TabHeaderItem(
                            title = "转账",
                            isSelected = currentType == TransactionType.TRANSFER,
                            color = TechBlue,
                            onClick = { viewModel.currentTransactionType.value = TransactionType.TRANSFER }
                        )
                    }

                    IconButton(onClick = onNavigateToBooks) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "账本与分类", tint = textColor)
                    }
                }

                // 当前所属账本小标签 (点击可切换)
                currentBook?.let { book ->
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardBg)
                            .clickable { onNavigateToBooks() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "当前账本: ${book.name}", fontSize = 12.sp, color = textSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 中间区域：分类展示 或 转账账户选择
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (currentType) {
                    TransactionType.EXPENSE -> {
                        // 支出精简五大分类：衣、食、住、行、其他
                        ExpenseCategoryRow(viewModel = viewModel, isDark = isDark)
                    }
                    TransactionType.INCOME -> {
                        // 收入六大经典分类：工资、生活费、收红包、外快、股票基金、其它
                        IncomeCategoryRow(viewModel = viewModel, isDark = isDark)
                    }
                    TransactionType.TRANSFER -> {
                        // 转账双卡片：转出 ⇄ 转入
                        TransferAccountSection(viewModel = viewModel, isDark = isDark)
                    }
                }
            }

            // 下半部分：备注 + 大字金额 + 胶囊栏 + 计算器键盘
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(cardBg)
            ) {
                // 备注输入框与金额行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (remark.isEmpty()) "点此输入备注..." else remark,
                        color = textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                // 模拟快速输入备注
                                viewModel.remarkText.value = "日常花销"
                            }
                    )
                    Text(
                        text = amountExp,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor
                    )
                }

                // 快捷属性胶囊行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PillTag(label = "账户", isDark = isDark)
                    PillTag(label = "今天 22:45", isDark = isDark)
                    PillTag(label = "图片", isDark = isDark)
                    if (currentType == TransactionType.TRANSFER) {
                        PillTag(label = "手续费", isDark = isDark)
                        PillTag(label = "优惠", isDark = isDark)
                    } else {
                        PillTag(label = "标签", isDark = isDark)
                    }
                }

                // 自定义计算器键盘
                CustomKeypad(
                    primaryColor = themeColor,
                    onDigitClick = { viewModel.onDigit(it) },
                    onOperatorClick = { viewModel.onOperator(it) },
                    onDotClick = { viewModel.onDot() },
                    onBackspace = { viewModel.onBackspace() },
                    onClear = { viewModel.onClear() },
                    onSaveAgain = { viewModel.saveRecord() },
                    onSave = { viewModel.saveRecord() }
                )
            }
        }
    }
}

@Composable
private fun TabHeaderItem(
    title: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        } else {
            Spacer(modifier = Modifier.height(3.dp))
        }
    }
}

// 支出分类排布 (衣食住行其他)
@Composable
private fun ExpenseCategoryRow(viewModel: MainViewModel, isDark: Boolean) {
    val items = listOf(
        Triple("衣", Icons.Default.ShoppingBag, 1L),
        Triple("食", Icons.Default.Restaurant, 2L),
        Triple("住", Icons.Default.Home, 3L),
        Triple("行", Icons.Default.DirectionsCar, 4L),
        Triple("其他", Icons.Default.MoreHoriz, 5L)
    )
    val selectedId by viewModel.selectedCategoryId.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEach { (name, icon, id) ->
            val isSelected = selectedId == id || (selectedId == null && id == 2L) // 默认选 "食"
            CategoryIconItem(
                name = name,
                icon = icon,
                isSelected = isSelected,
                activeColor = BlueAccent,
                onClick = { viewModel.selectedCategoryId.value = id },
                isDark = isDark
            )
        }
    }
}

// 收入分类排布
@Composable
private fun IncomeCategoryRow(viewModel: MainViewModel, isDark: Boolean) {
    val items = listOf(
        Triple("工资", Icons.Default.Work, 101L),
        Triple("生活费", Icons.Default.AccountBalanceWallet, 102L),
        Triple("收红包", Icons.Default.CardGiftcard, 103L),
        Triple("外快", Icons.Default.AttachMoney, 104L),
        Triple("股票基金", Icons.Default.TrendingUp, 105L),
        Triple("其它", Icons.Default.MoreHoriz, 106L)
    )
    val selectedId by viewModel.selectedCategoryId.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            items.take(4).forEach { (name, icon, id) ->
                CategoryIconItem(
                    name = name,
                    icon = icon,
                    isSelected = selectedId == id || (selectedId == null && id == 101L),
                    activeColor = MintGreen,
                    onClick = { viewModel.selectedCategoryId.value = id },
                    isDark = isDark
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Spacer(modifier = Modifier.width(24.dp))
            items.drop(4).forEach { (name, icon, id) ->
                CategoryIconItem(
                    name = name,
                    icon = icon,
                    isSelected = selectedId == id,
                    activeColor = MintGreen,
                    onClick = { viewModel.selectedCategoryId.value = id },
                    isDark = isDark
                )
                Spacer(modifier = Modifier.width(36.dp))
            }
        }
    }
}

// 转账卡片区域
@Composable
private fun TransferAccountSection(viewModel: MainViewModel, isDark: Boolean) {
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.CreditCard, contentDescription = null, tint = TechBlue)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "转出账户: 招商银行", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
            }

            HorizontalDivider(color = if (isDark) DarkBorder else LightBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null, tint = TechBlue)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "转入账户: 微信零钱通", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
            }
        }

        // 中间对调按钮
        IconButton(
            onClick = { viewModel.swapTransferAccounts() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(TechBlue)
        ) {
            Icon(imageVector = Icons.Default.SwapVert, contentDescription = "对调", tint = Color.White)
        }
    }
}

@Composable
private fun CategoryIconItem(
    name: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    isDark: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (isSelected) activeColor.copy(alpha = 0.25f) else Color.Transparent)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) activeColor else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = if (isSelected) activeColor else if (isDark) Color.LightGray else Color.DarkGray,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            color = if (isDark) DarkTextPrimary else LightTextPrimary
        )
    }
}

@Composable
private fun PillTag(label: String, isDark: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) DarkSurface else LightSurface)
            .border(1.dp, if (isDark) DarkBorder else LightBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 12.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
    }
}
