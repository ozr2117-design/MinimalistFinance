package com.minimalist.finance.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.data.model.Category
import com.minimalist.finance.data.model.TransactionType
import com.minimalist.finance.ui.component.CustomKeypad
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToBooks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentType by viewModel.currentTransactionType.collectAsState()
    val amountExp by viewModel.amountExpression.collectAsState()
    val remark by viewModel.remarkText.collectAsState()
    val books by viewModel.allBooks.collectAsState()
    val curBookId by viewModel.currentBookId.collectAsState()
    val currentBook = books.find { it.id == curBookId }

    val categories by viewModel.currentCategories.collectAsState()
    val selectedCatId by viewModel.selectedCategoryId.collectAsState()

    // 胶囊状态
    val accountName by viewModel.selectedAccountName.collectAsState()
    val timestamp by viewModel.selectedTimestamp.collectAsState()
    val imageUri by viewModel.selectedImageUri.collectAsState()
    val tag by viewModel.selectedTag.collectAsState()

    var showAccountSheet by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }

    // 系统相册选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.selectedImageUri.value = it
        }
    }

    // 动态格式化时间为手机当前时间
    val dateDisplay = remember(timestamp) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            "今天 $timeFormat"
        } else {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }

    val themeColor = if (currentType == TransactionType.EXPENSE) CoralRed else MintGreen
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "菜单", tint = textColor)
                    }

                    // 顶部双 Tab (支出 / 收入)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabHeaderItem(
                            title = "支出",
                            isSelected = currentType == TransactionType.EXPENSE,
                            color = CoralRed,
                            onClick = {
                                viewModel.currentTransactionType.value = TransactionType.EXPENSE
                                viewModel.selectedCategoryId.value = null
                            }
                        )
                        TabHeaderItem(
                            title = "收入",
                            isSelected = currentType == TransactionType.INCOME,
                            color = MintGreen,
                            onClick = {
                                viewModel.currentTransactionType.value = TransactionType.INCOME
                                viewModel.selectedCategoryId.value = null
                            }
                        )
                    }

                    IconButton(onClick = onNavigateToBooks) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "账本与分类", tint = textColor)
                    }
                }

                // 当前所属账本小标签 (点击直达账本切换)
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
                        Text(text = "当前账本: ${book.name} (${book.currency})", fontSize = 12.sp, color = textSecondary)
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
            // 中间区域：根据当前账本动态展示专属分类网格
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (categories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载分类中...", color = textSecondary)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCatId == cat.id || (selectedCatId == null && cat == categories.first())
                            DynamicCategoryItem(
                                category = cat,
                                isSelected = isSelected,
                                activeColor = themeColor,
                                onClick = {
                                    viewModel.selectedCategoryId.value = cat.id
                                    viewModel.selectedCategoryName.value = cat.name
                                },
                                isDark = isDark
                            )
                        }
                    }
                }
            }

            // 下半部分：备注 + 大字金额 + 真实交互胶囊栏 + 计算器键盘
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(cardBg)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
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
                                viewModel.remarkText.value = "日常记录"
                            }
                    )
                    Text(
                        text = if (currentBook?.currency == "USD") "$ $amountExp" else "¥ $amountExp",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor
                    )
                }

                // 快捷属性胶囊行 (完全打通真机功能)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 账户选择 (唤起底部账户面板)
                    ClickablePill(
                        label = accountName,
                        isActive = true,
                        isDark = isDark,
                        onClick = { showAccountSheet = true }
                    )

                    // 2. 时间选择 (动态同步系统时间，支持弹出 DatePicker 补记往期)
                    ClickablePill(
                        label = dateDisplay,
                        isActive = false,
                        isDark = isDark,
                        onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = timestamp }
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth)
                                    }
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            newCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            newCal.set(Calendar.MINUTE, minute)
                                            viewModel.selectedTimestamp.value = newCal.timeInMillis
                                        },
                                        c.get(Calendar.HOUR_OF_DAY),
                                        c.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                c.get(Calendar.YEAR),
                                c.get(Calendar.MONTH),
                                c.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    )

                    // 3. 图片功能 (选择相册小票/账单，彻底免费无 VIP 限制)
                    ClickablePill(
                        label = if (imageUri != null) "图片 ✔" else "图片",
                        isActive = imageUri != null,
                        isDark = isDark,
                        onClick = {
                            imagePicker.launch("image/*")
                        }
                    )

                    // 4. 标签功能
                    ClickablePill(
                        label = if (tag.isNotEmpty()) tag else "标签",
                        isActive = tag.isNotEmpty(),
                        isDark = isDark,
                        onClick = { showTagDialog = true }
                    )
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

    // 标签输入弹窗
    if (showTagDialog) {
        var tempTag by remember { mutableStateOf(tag) }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("设置账单标签") },
            text = {
                OutlinedTextField(
                    value = tempTag,
                    onValueChange = { tempTag = it },
                    placeholder = { Text("如：聚餐、打折、房租、出差") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.selectedTag.value = tempTag
                    showTagDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("取消") }
            }
        )
    }

    // 底部账户选择面板 (1:1 对照截图四组账户)
    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = if (isDark) DarkSurface else LightSurface
        ) {
            AccountSelectorContent(
                isDark = isDark,
                onSelect = { acc ->
                    viewModel.selectedAccountName.value = acc
                    showAccountSheet = false
                }
            )
        }
    }
}

// 账户选择器列表 (严格对照截图四组：资金账户、信用卡账户、充值账户、投资理财账户)
@Composable
private fun AccountSelectorContent(
    isDark: Boolean,
    onSelect: (String) -> Unit
) {
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary

    val funds = listOf("现金", "微信", "微信零钱通", "支付宝", "余额宝", "余利宝", "小荷包", "云闪付", "银行卡", "公积金", "QQ 钱包", "京东金融", "医保", "数字人民币", "华为钱包", "多多钱包", "Paypal", "其它")
    val credits = listOf("信用卡", "花呗", "借呗", "京东白条", "美团月付", "抖音月付", "微信分付", "其它信用卡")
    val topUps = listOf("话费", "水电", "饭卡", "押金", "公交卡", "会员卡", "加油卡", "石化钱包", "Apple", "其它充值卡")
    val investments = listOf("股票", "基金", "黄金", "外汇", "期货", "债券", "固定收益", "加密货币", "其它理财")

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(text = "选择记账账户", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
        }

        // 资金账户
        item {
            AccountGroupSection(title = "资金账户", items = funds, textColor = textColor, isDark = isDark, onSelect = onSelect)
        }

        // 信用卡账户
        item {
            AccountGroupSection(title = "信用卡账户", items = credits, textColor = textColor, isDark = isDark, onSelect = onSelect)
        }

        // 充值账户
        item {
            AccountGroupSection(title = "充值账户", items = topUps, textColor = textColor, isDark = isDark, onSelect = onSelect)
        }

        // 投资理财账户
        item {
            AccountGroupSection(title = "投资理财账户", items = investments, textColor = textColor, isDark = isDark, onSelect = onSelect)
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
private fun AccountGroupSection(
    title: String,
    items: List<String>,
    textColor: Color,
    isDark: Boolean,
    onSelect: (String) -> Unit
) {
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard
    Column {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BlueAccent)
        Spacer(modifier = Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { name ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .clickable { onSelect(name) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = name, fontSize = 12.sp, maxLines = 1, color = textColor)
                }
            }
        }
    }
}

@Composable
private fun ClickablePill(
    label: String,
    isActive: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) BlueAccent.copy(alpha = 0.2f) else if (isDark) DarkSurface else LightSurface)
            .border(
                1.dp,
                if (isActive) BlueAccent else if (isDark) DarkBorder else LightBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isActive) BlueAccent else if (isDark) DarkTextSecondary else LightTextSecondary
        )
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

@Composable
private fun DynamicCategoryItem(
    category: Category,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val icon = when (category.iconName) {
        "restaurant" -> Icons.Default.Restaurant
        "checkroom" -> Icons.Default.ShoppingBag
        "home" -> Icons.Default.Home
        "directions_car" -> Icons.Default.DirectionsCar
        "work" -> Icons.Default.Work
        "trending_up" -> Icons.Default.TrendingUp
        "trending_down" -> Icons.Default.TrendingDown
        "account_balance" -> Icons.Default.AccountBalance
        "candlestick_chart", "show_chart" -> Icons.Default.ShowChart
        "lock" -> Icons.Default.Lock
        "shield" -> Icons.Default.Shield
        else -> Icons.Default.Paid
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (isSelected) activeColor.copy(alpha = 0.22f) else Color.Transparent)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) activeColor else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category.name,
                tint = if (isSelected) activeColor else if (isDark) Color.LightGray else Color.DarkGray,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.name,
            fontSize = 12.sp,
            maxLines = 1,
            color = if (isDark) DarkTextPrimary else LightTextPrimary
        )
    }
}
