package com.minimalist.finance.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val isCustomTime by viewModel.isCustomTimestamp.collectAsState()
    val imageUri by viewModel.selectedImageUri.collectAsState()
    val tag by viewModel.selectedTag.collectAsState()

    var showAccountSheet by remember { mutableStateOf(false) }
    val accountSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTagDialog by remember { mutableStateOf(false) }
    var showRemarkDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }

    // 监听应用从后台切回前台：若未手动锁死往期时间，自动秒级同步至手机当前最新真实时间！
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncCurrentTimeIfAuto()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncCurrentTimeIfAuto()
    }

    // 系统相册选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.selectedImageUri.value = it
        }
    }

    // 动态格式化时间为手机当前时间 (精准支持“今天”、“昨天”或具体日期)
    val dateDisplay = remember(timestamp, isCustomTime) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            "今天 $timeFormat"
        } else {
            val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            if (yest.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                yest.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
                "昨天 $timeFormat"
            } else {
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
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
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showRemarkDialog = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (remark.isEmpty()) "点此输入备注..." else remark,
                            color = if (remark.isEmpty()) textSecondary else textColor,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (remark.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.remarkText.value = "" },
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空备注",
                                    tint = textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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

                    // 2. 时间选择 (动态同步系统时间，支持类似钱迹的快捷日期与精确时分调节)
                    ClickablePill(
                        label = dateDisplay,
                        isActive = isCustomTime,
                        isDark = isDark,
                        onClick = { showTimeDialog = true }
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

    // 备注自由输入与历史记忆常用词弹窗
    if (showRemarkDialog) {
        var tempRemark by remember { mutableStateOf(remark) }
        val recentRemarks by viewModel.recentRemarks.collectAsState()
        val quickScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { showRemarkDialog = false },
            title = {
                Text(
                    text = "记账备注",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = tempRemark,
                        onValueChange = { tempRemark = it },
                        placeholder = { Text("输入备注详情 (如：买菜、加油、日用品)...") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (recentRemarks.isNotEmpty()) {
                        Text(
                            text = "最近常用备注：",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(quickScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentRemarks.forEach { tagText ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (tempRemark == tagText) themeColor.copy(alpha = 0.15f) else (if (isDark) DarkSurface else Color(0xFFF1F5F9)),
                                    border = if (tempRemark == tagText) BorderStroke(1.dp, themeColor) else null,
                                    modifier = Modifier.clickable {
                                        tempRemark = tagText
                                    }
                                ) {
                                    Text(
                                        text = tagText,
                                        fontSize = 13.sp,
                                        color = if (tempRemark == tagText) themeColor else textColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "💡 输入备注后，系统将自动记住并置顶您的常用词",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.remarkText.value = tempRemark
                    if (tempRemark.isNotBlank()) {
                        viewModel.recordRemarkToHistory(tempRemark)
                    }
                    showRemarkDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                Row {
                    if (tempRemark.isNotEmpty()) {
                        TextButton(onClick = { tempRemark = "" }) { Text("清空") }
                    }
                    TextButton(onClick = { showRemarkDialog = false }) { Text("取消") }
                }
            }
        )
    }

    // 钱迹风格：高精度时间选择器 (支持今/昨/前快捷切换、日历挑选与精确时分调节)
    if (showTimeDialog) {
        var tempCal by remember {
            mutableStateOf(Calendar.getInstance().apply { timeInMillis = timestamp })
        }
        val isToday = remember(tempCal.timeInMillis) {
            val now = Calendar.getInstance()
            now.get(Calendar.YEAR) == tempCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == tempCal.get(Calendar.DAY_OF_YEAR)
        }
        val isYesterday = remember(tempCal.timeInMillis) {
            val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            yest.get(Calendar.YEAR) == tempCal.get(Calendar.YEAR) &&
            yest.get(Calendar.DAY_OF_YEAR) == tempCal.get(Calendar.DAY_OF_YEAR)
        }
        val isBeforeYesterday = remember(tempCal.timeInMillis) {
            val before = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
            before.get(Calendar.YEAR) == tempCal.get(Calendar.YEAR) &&
            before.get(Calendar.DAY_OF_YEAR) == tempCal.get(Calendar.DAY_OF_YEAR)
        }

        val dateText = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA).format(tempCal.time)
        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(tempCal.time)

        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "调整记账时间",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isToday) MintGreen.copy(alpha = 0.15f) else BlueAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isToday) "今天" else (if (isYesterday) "昨天" else (if (isBeforeYesterday) "前天" else "往期补记")),
                            fontSize = 12.sp,
                            color = if (isToday) MintGreen else BlueAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 大字日期与时分预览卡片
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) DarkSurface else Color(0xFFF1F5F9))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = dateText,
                                fontSize = 13.sp,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = timeText,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }

                    // 快捷日期切换栏 (类似钱迹: 今 / 昨 / 前 / 日历)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "快捷日期选择：", fontSize = 12.sp, color = textSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterPillButton(
                                label = "今 (实时)",
                                isSelected = isToday,
                                activeColor = MintGreen,
                                isDark = isDark,
                                modifier = Modifier.weight(1f)
                            ) {
                                tempCal = Calendar.getInstance()
                            }

                            FilterPillButton(
                                label = "昨天",
                                isSelected = isYesterday,
                                activeColor = BlueAccent,
                                isDark = isDark,
                                modifier = Modifier.weight(1f)
                            ) {
                                val c = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, -1)
                                    set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE))
                                }
                                tempCal = c
                            }

                            FilterPillButton(
                                label = "前天",
                                isSelected = isBeforeYesterday,
                                activeColor = BlueAccent,
                                isDark = isDark,
                                modifier = Modifier.weight(1f)
                            ) {
                                val c = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, -2)
                                    set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE))
                                }
                                tempCal = c
                            }

                            IconButton(
                                onClick = {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val c = Calendar.getInstance().apply {
                                                timeInMillis = tempCal.timeInMillis
                                                set(Calendar.YEAR, y)
                                                set(Calendar.MONTH, m)
                                                set(Calendar.DAY_OF_MONTH, d)
                                            }
                                            tempCal = c
                                        },
                                        tempCal.get(Calendar.YEAR),
                                        tempCal.get(Calendar.MONTH),
                                        tempCal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDark) DarkSurface else Color(0xFFF1F5F9))
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "自选日期", tint = textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // 具体时分调节
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "具体时间调整 (时:分)：", fontSize = 12.sp, color = textSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            val c = Calendar.getInstance().apply {
                                                timeInMillis = tempCal.timeInMillis
                                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                set(Calendar.MINUTE, minute)
                                            }
                                            tempCal = c
                                        },
                                        tempCal.get(Calendar.HOUR_OF_DAY),
                                        tempCal.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("调整时分 ($timeText)")
                            }

                            FilledTonalButton(
                                onClick = {
                                    val now = Calendar.getInstance()
                                    val c = Calendar.getInstance().apply {
                                        timeInMillis = tempCal.timeInMillis
                                        set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
                                        set(Calendar.MINUTE, now.get(Calendar.MINUTE))
                                    }
                                    tempCal = c
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("同步此时刻")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isChosenNow = isToday && Math.abs(System.currentTimeMillis() - tempCal.timeInMillis) < 120_000L
                        viewModel.selectedTimestamp.value = tempCal.timeInMillis
                        viewModel.isCustomTimestamp.value = !isChosenNow
                        showTimeDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 底部账户选择面板 (1:1 对照截图四组账户，极限 120Hz 顺滑调优)
    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = accountSheetState,
            containerColor = if (isDark) DarkSurface else LightSurface
        ) {
            AccountSelectorContent(
                selectedAccount = accountName,
                isDark = isDark,
                onSelect = { acc ->
                    viewModel.selectedAccountName.value = acc
                    showAccountSheet = false
                },
                onClose = { showAccountSheet = false }
            )
        }
    }
}

// 预先静态构建账户四级分组数据，避免任何运行时内存分配与 chunked 计算，保障 120Hz 极限流畅
private val ACCOUNT_FUNDS = listOf("微信", "支付宝", "银行卡", "现金", "微信零钱通", "余额宝", "余利宝", "小荷包", "云闪付", "公积金", "QQ 钱包", "京东金融", "医保", "数字人民币", "华为钱包", "多多钱包", "Paypal", "其它")
private val ACCOUNT_CREDITS = listOf("信用卡", "花呗", "借呗", "京东白条", "美团月付", "抖音月付", "微信分付", "其它信用卡")
private val ACCOUNT_TOPUPS = listOf("话费", "水电", "饭卡", "押金", "公交卡", "会员卡", "加油卡", "石化钱包", "Apple", "其它充值卡")
private val ACCOUNT_INVESTMENTS = listOf("股票", "基金", "黄金", "外汇", "期货", "债券", "固定收益", "加密货币", "其它理财")

private val ACCOUNT_FUNDS_ROWS = ACCOUNT_FUNDS.chunked(4)
private val ACCOUNT_CREDITS_ROWS = ACCOUNT_CREDITS.chunked(4)
private val ACCOUNT_TOPUPS_ROWS = ACCOUNT_TOPUPS.chunked(4)
private val ACCOUNT_INVESTMENTS_ROWS = ACCOUNT_INVESTMENTS.chunked(4)

@Composable
private fun AccountSelectorContent(
    selectedAccount: String,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
    ) {
        // 顶部固定标题栏：标题 + 当前选择提示 + 关闭按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "选择记账账户",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "当前选中：$selectedAccount",
                    fontSize = 12.sp,
                    color = BlueAccent
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            color = if (isDark) DarkBorder else LightBorder,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        // 丝滑滚动内容区域：预先测量，0 重组，120Hz 极限顺滑
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AccountGroupSection(
                title = "资金账户",
                rows = ACCOUNT_FUNDS_ROWS,
                selectedAccount = selectedAccount,
                textColor = textColor,
                isDark = isDark,
                onSelect = onSelect
            )

            AccountGroupSection(
                title = "信用卡账户",
                rows = ACCOUNT_CREDITS_ROWS,
                selectedAccount = selectedAccount,
                textColor = textColor,
                isDark = isDark,
                onSelect = onSelect
            )

            AccountGroupSection(
                title = "充值账户",
                rows = ACCOUNT_TOPUPS_ROWS,
                selectedAccount = selectedAccount,
                textColor = textColor,
                isDark = isDark,
                onSelect = onSelect
            )

            AccountGroupSection(
                title = "投资理财账户",
                rows = ACCOUNT_INVESTMENTS_ROWS,
                selectedAccount = selectedAccount,
                textColor = textColor,
                isDark = isDark,
                onSelect = onSelect
            )

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun AccountGroupSection(
    title: String,
    rows: List<List<String>>,
    selectedAccount: String,
    textColor: Color,
    isDark: Boolean,
    onSelect: (String) -> Unit
) {
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = BlueAccent,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { name ->
                        val isSelected = (name == selectedAccount)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) BlueAccent.copy(alpha = 0.18f) else cardBg
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) BlueAccent else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelect(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                color = if (isSelected) BlueAccent else textColor
                            )
                        }
                    }
                    val remaining = 4 - rowItems.size
                    repeat(remaining) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
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

@Composable
private fun FilterPillButton(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.18f) else (if (isDark) DarkSurface else Color(0xFFF1F5F9)),
        border = if (isSelected) BorderStroke(1.dp, activeColor) else null,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) activeColor else (if (isDark) DarkTextPrimary else LightTextPrimary)
            )
        }
    }
}
