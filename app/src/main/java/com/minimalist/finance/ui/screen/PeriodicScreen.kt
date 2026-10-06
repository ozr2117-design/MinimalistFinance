package com.minimalist.finance.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.data.model.*
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 分期管理, 1: 周期记账
    var isAdding by remember { mutableStateOf(false) }

    val periodicRules by viewModel.periodicRules.collectAsState()
    val installmentPlans by viewModel.installmentPlans.collectAsState()
    val books by viewModel.allBooks.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()

    if (isAdding) {
        // ====== 表单页 (信用卡分期 / 周期记账完整重构) ======
        var startDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
        var dayOfMonth by remember { mutableIntStateOf(10) } // 每月扣款日 (如 10 日)
        var frequency by remember { mutableStateOf("每月") }
        var endType by remember { mutableStateOf(if (selectedTab == 0) "按期数还清后自动结束" else "永不结束") }
        var isPeriodicExpense by remember { mutableStateOf(true) } // 周期：支出 vs 收入

        var selectedBookId by remember { mutableLongStateOf(books.firstOrNull { it.isDefault }?.id ?: books.firstOrNull()?.id ?: 1L) }
        var selectedBookName by remember { mutableStateOf(books.firstOrNull { it.id == selectedBookId }?.name ?: "日常消费账本") }

        var selectedAccountName by remember { mutableStateOf(accounts.firstOrNull()?.name ?: "微信") }
        var selectedCat by remember { mutableStateOf(if (selectedTab == 0) "分期还款" else "日常支出") }

        var amountText by remember { mutableStateOf("") }
        var timeOfDay by remember { mutableStateOf("10:00") }
        var remark by remember { mutableStateOf("") }
        var periodsCount by remember { mutableStateOf("12") } // 仅分期

        // 弹窗状态
        var showBookPicker by remember { mutableStateOf(false) }
        var showAccountPicker by remember { mutableStateOf(false) }
        var showDayOfMonthPicker by remember { mutableStateOf(false) }
        var showEndTypePicker by remember { mutableStateOf(false) }
        var showFrequencyPicker by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { isAdding = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
                    }
                    Text(
                        text = if (selectedTab == 0) "添加信用卡 / 花呗分期" else "添加周期记账",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            },
            bottomBar = {
                // 底部保存条 (预览 + 保存)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = {
                            val parsedAmt = amountText.toDoubleOrNull() ?: 0.0
                            val parsedP = periodsCount.toIntOrNull() ?: 12
                            if (parsedAmt <= 0.0) {
                                Toast.makeText(context, "请先输入金额再预览", Toast.LENGTH_SHORT).show()
                            } else if (selectedTab == 0) {
                                val monthly = parsedAmt / parsedP
                                Toast.makeText(context, "预览：每期应还 ¥ ${String.format(Locale.getDefault(), "%.2f", monthly)}，每月 ${dayOfMonth} 日扣款", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "预览：每期 ¥ $parsedAmt ($frequency)，扣款日 $startDate", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BlueAccent)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "预览", tint = Color.White)
                    }

                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount <= 0.0) {
                                Toast.makeText(context, "请输入有效的账单金额", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedTab == 0) {
                                // 保存信用卡/花呗分期计划
                                val totalP = periodsCount.toIntOrNull() ?: 12
                                val monthly = amount / totalP
                                viewModel.addInstallmentPlan(
                                    InstallmentPlan(
                                        name = if (remark.isNotBlank()) remark.trim() else "${selectedAccountName}分期",
                                        totalAmount = amount,
                                        totalPeriods = totalP,
                                        currentPeriod = 1,
                                        monthlyAmount = monthly,
                                        bookId = selectedBookId,
                                        accountName = selectedAccountName,
                                        dayOfMonth = dayOfMonth
                                    )
                                )
                                Toast.makeText(context, "已成功添加分期计划！", Toast.LENGTH_SHORT).show()
                            } else {
                                // 保存固定周期规则
                                val freqText = if (frequency == "每月") "每月 ${dayOfMonth} 日" else frequency
                                viewModel.addPeriodicRule(
                                    PeriodicRule(
                                        name = if (remark.isNotBlank()) remark.trim() else (if (isPeriodicExpense) "固定支出" else "固定收入"),
                                        type = if (isPeriodicExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                                        amount = amount,
                                        bookId = selectedBookId,
                                        categoryName = selectedCat,
                                        accountName = selectedAccountName,
                                        startDate = startDate,
                                        frequency = freqText,
                                        endType = endType,
                                        timeOfDay = timeOfDay
                                    )
                                )
                                Toast.makeText(context, "已成功添加周期规则！", Toast.LENGTH_SHORT).show()
                            }
                            isAdding = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("保存计划", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { innerPad ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPad)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 周期记账模式下的收支切换
                if (selectedTab == 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isPeriodicExpense) CoralRed else Color.Transparent)
                                    .clickable { isPeriodicExpense = true }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "支出 (房租/水电等)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPeriodicExpense) Color.White else textSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isPeriodicExpense) MintGreen else Color.Transparent)
                                    .clickable { isPeriodicExpense = false }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "收入 (工资/收租等)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isPeriodicExpense) Color.White else textSecondary
                                )
                            }
                        }
                    }
                }

                // 金额输入
                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(if (selectedTab == 0) "分期总金额 (¥)" else "周期金额 (¥)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 分期专属：期数快捷选择与试算卡片
                if (selectedTab == 0) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "分期总期数", fontSize = 13.sp, color = textSecondary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("3", "6", "12", "24", "36").forEach { p ->
                                    val isSelected = periodsCount == p
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) BlueAccent else cardBg)
                                            .clickable { periodsCount = p }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${p}期",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else textColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 实时分期还款试算卡片
                    item {
                        val parsedAmt = amountText.toDoubleOrNull() ?: 0.0
                        val parsedP = periodsCount.toIntOrNull() ?: 12
                        if (parsedAmt > 0 && parsedP > 0) {
                            val monthlyEst = parsedAmt / parsedP
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BlueAccent.copy(alpha = 0.12f))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "每期应还款 (自动试算)", fontSize = 12.sp, color = textSecondary)
                                        Text(
                                            text = "¥ ${String.format(Locale.getDefault(), "%.2f", monthlyEst)} / 期",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BlueAccent
                                        )
                                    }
                                    Text(
                                        text = "每月 ${dayOfMonth} 日扣款",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = BlueAccent
                                    )
                                }
                            }
                        }
                    }
                }

                // 每月扣款日 (账单日) - 信用卡/花呗最核心逻辑！
                item {
                    FormRow(label = "每月扣款日 (还款日)", value = "每月 ${dayOfMonth} 日", isDark = isDark) {
                        showDayOfMonthPicker = true
                    }
                }

                // 开始执行日期
                item {
                    FormRow(label = if (selectedTab == 0) "首期还款日期" else "开始日期", value = startDate, isDark = isDark) {
                        val c = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            startDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }
                }

                // 扣款具体时间 (时:分)
                item {
                    FormRow(label = "扣款时间点", value = timeOfDay, isDark = isDark) {
                        val parts = timeOfDay.split(":")
                        val curH = parts.getOrNull(0)?.toIntOrNull() ?: 10
                        val curM = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(context, { _, h, m ->
                            timeOfDay = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                        }, curH, curM, true).show()
                    }
                }

                // 重复周期 (仅周期记账模式需要独立选周期)
                if (selectedTab == 1) {
                    item {
                        FormRow(label = "重复周期", value = frequency, isDark = isDark) {
                            showFrequencyPicker = true
                        }
                    }
                }

                // 结束方式 (下拉/菜单弹窗)
                item {
                    FormRow(label = "结束方式", value = endType, isDark = isDark) {
                        showEndTypePicker = true
                    }
                }

                // 账本 (下拉选择真实账本)
                item {
                    FormRow(label = "入账账本", value = selectedBookName, isDark = isDark) {
                        showBookPicker = true
                    }
                }

                // 扣款/入账账户 (下拉选择真实账户)
                item {
                    FormRow(label = if (selectedTab == 0 || isPeriodicExpense) "扣款账户" else "入账账户", value = selectedAccountName, isDark = isDark) {
                        showAccountPicker = true
                    }
                }

                // 备注
                item {
                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        label = { Text(if (selectedTab == 0) "分期备注 (如：MacBook、花呗手机分期)" else "规则备注 (如：房租、公司工资)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // ====== 账本选择弹窗 ======
        if (showBookPicker) {
            AlertDialog(
                onDismissRequest = { showBookPicker = false },
                title = { Text(text = "选择入账账本", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        books.forEach { b ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedBookId = b.id
                                        selectedBookName = b.name
                                        showBookPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Book, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = b.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                                        if (b.isDefault) {
                                            Text(text = "默认账本", fontSize = 11.sp, color = textSecondary)
                                        }
                                    }
                                }
                                if (b.id == selectedBookId) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BlueAccent)
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ====== 扣款账户选择弹窗 ======
        if (showAccountPicker) {
            AlertDialog(
                onDismissRequest = { showAccountPicker = false },
                title = { Text(text = if (selectedTab == 0 || isPeriodicExpense) "选择扣款账户" else "选择入账账户", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts) { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedAccountName = acc.name
                                        showAccountPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (acc.name.contains("微信")) Icons.Default.ChatBubble else if (acc.name.contains("支付宝")) Icons.Default.Payment else Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = if (acc.name.contains("微信")) MintGreen else if (acc.name.contains("支付宝")) BlueAccent else CoralRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = acc.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                                        Text(text = "当前余额: ¥ ${String.format(Locale.getDefault(), "%.2f", acc.balance)}", fontSize = 11.sp, color = textSecondary)
                                    }
                                }
                                if (acc.name == selectedAccountName) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BlueAccent)
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ====== 每月扣款日 (1 ~ 31日) 选择弹窗 ======
        if (showDayOfMonthPicker) {
            AlertDialog(
                onDismissRequest = { showDayOfMonthPicker = false },
                title = { Text(text = "选择每月扣款日 / 账单日", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "常用日期快捷选：", fontSize = 13.sp, color = textSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf(5, 8, 9, 10, 15, 20, 25).forEach { d ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (dayOfMonth == d) BlueAccent else (if (isDark) DarkSurface else LightBorder))
                                        .clickable {
                                            dayOfMonth = d
                                            showDayOfMonthPicker = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${d}日",
                                        fontSize = 12.sp,
                                        fontWeight = if (dayOfMonth == d) FontWeight.Bold else FontWeight.Normal,
                                        color = if (dayOfMonth == d) Color.White else textColor
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "全部 1 ~ 31 日：", fontSize = 13.sp, color = textSecondary)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.height(200.dp)
                        ) {
                            items(31) { idx ->
                                val day = idx + 1
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (dayOfMonth == day) BlueAccent else (if (isDark) DarkSurface else LightBorder))
                                        .clickable {
                                            dayOfMonth = day
                                            showDayOfMonthPicker = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$day",
                                        fontSize = 13.sp,
                                        fontWeight = if (dayOfMonth == day) FontWeight.Bold else FontWeight.Normal,
                                        color = if (dayOfMonth == day) Color.White else textColor
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ====== 结束方式选择弹窗 ======
        if (showEndTypePicker) {
            val endOptions = if (selectedTab == 0) {
                listOf(
                    "按期数还清后自动结束" to "还满设定的总期数后自动完结并停止扣款",
                    "截至指定日期结束" to "到达指定的截止日期后自动停止",
                    "永不结束 / 长期循环" to "持续每月自动入账，直到手动停止"
                )
            } else {
                listOf(
                    "永不结束" to "长期按规律自动记账，适用于房租、工资等",
                    "执行固定次数后结束" to "按指定次数执行满后结束 (默认12次)",
                    "截至指定日期结束" to "到达指定截止日期后停止入账"
                )
            }

            AlertDialog(
                onDismissRequest = { showEndTypePicker = false },
                title = { Text(text = "选择结束方式", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        endOptions.forEach { (opt, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (opt == "截至指定日期结束") {
                                            val c = Calendar.getInstance()
                                            DatePickerDialog(context, { _, y, m, d ->
                                                endType = String.format(Locale.getDefault(), "截至 %04d-%02d-%02d 结束", y, m + 1, d)
                                                showEndTypePicker = false
                                            }, c.get(Calendar.YEAR) + 1, c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                                        } else {
                                            endType = opt
                                            showEndTypePicker = false
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = opt, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                                    Text(text = desc, fontSize = 12.sp, color = textSecondary)
                                }
                                if (endType.startsWith(opt.take(4))) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BlueAccent)
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ====== 重复周期选择弹窗 (仅周期记账) ======
        if (showFrequencyPicker) {
            val freqOptions = listOf(
                "每月" to "每月固定扣款日自动记账 (最常用)",
                "每周" to "每周固定星期几自动记账",
                "每工作日" to "周一至周五自动记账",
                "每天" to "每天固定时间自动记账",
                "每年" to "每年固定月份与日期自动记账"
            )

            AlertDialog(
                onDismissRequest = { showFrequencyPicker = false },
                title = { Text(text = "选择重复周期", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        freqOptions.forEach { (f, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        frequency = f
                                        showFrequencyPicker = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = f, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                                    Text(text = desc, fontSize = 12.sp, color = textSecondary)
                                }
                                if (frequency == f) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BlueAccent)
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

    } else {
        // ====== 列表页 (分期管理 / 周期记账) ======
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
                        }
                        Text(
                            text = "分期与周期管理",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }

                    // 顶部双 Tab (分期管理 | 周期记账)
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = BlueAccent
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("分期管理", fontSize = 16.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("周期记账", fontSize = 16.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            },
            bottomBar = {
                // 底部添加按钮
                Button(
                    onClick = { isAdding = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(50.dp)
                ) {
                    Text(if (selectedTab == 0) "+ 添加信用卡 / 花呗分期" else "+ 添加周期记账", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        ) { innerPad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPad)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (selectedTab == 0) {
                    // 分期 Tab 列表
                    if (installmentPlans.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            InstructionText(num = "1", text = "分期功能专门用于管理信用卡、花呗等分期还款场景，设定好每期金额后会自动为您入账；", textSecondary = textSecondary)
                            InstructionText(num = "2", text = "还款入账类型为支出，扣款账户将实时扣减相应金额；", textSecondary = textSecondary)
                            InstructionText(num = "3", text = "清晰追踪还款进度，每期扣款一目了然，防范逾期风险；", textSecondary = textSecondary)
                            InstructionText(num = "4", text = "随时可点击删除或查看还款进度。", textSecondary = textSecondary)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(installmentPlans) { plan ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(cardBg)
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = CoralRed, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = plan.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                            }
                                            IconButton(onClick = { viewModel.deleteInstallmentPlan(plan) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = textSecondary, modifier = Modifier.size(20.dp))
                                            }
                                        }

                                        LinearProgressIndicator(
                                            progress = { (plan.currentPeriod.toFloat() / plan.totalPeriods.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = BlueAccent
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "进度: ${plan.currentPeriod}/${plan.totalPeriods} 期", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BlueAccent)
                                            Text(text = "总额: ¥ ${plan.totalAmount}", fontSize = 13.sp, color = textSecondary)
                                        }

                                        HorizontalDivider(color = if (isDark) DarkBorder else LightBorder)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "每期应还: ¥ ${String.format(Locale.getDefault(), "%.2f", plan.monthlyAmount)}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textColor
                                                )
                                                Text(
                                                    text = "扣款账户: ${plan.accountName} · 每月 ${plan.dayOfMonth} 日",
                                                    fontSize = 12.sp,
                                                    color = textSecondary
                                                )
                                            }

                                            // 推进期数按钮
                                            if (plan.currentPeriod < plan.totalPeriods) {
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.updateInstallmentPlan(plan.copy(currentPeriod = plan.currentPeriod + 1))
                                                        Toast.makeText(context, "已推进至第 ${plan.currentPeriod + 1} 期还款", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("还完一期", fontSize = 12.sp)
                                                }
                                            } else {
                                                Text("已结清", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MintGreen)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 周期记账 Tab 列表
                    if (periodicRules.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            InstructionText(num = "1", text = "周期记账功能，用于管理房租、宽带、月薪等固定规律发生的收支；", textSecondary = textSecondary)
                            InstructionText(num = "2", text = "设定好重复频率与扣款日后，每个周期自动为您记账入库；", textSecondary = textSecondary)
                            InstructionText(num = "3", text = "支持支出与收入双向周期管理，随时可以暂停或删除。", textSecondary = textSecondary)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(periodicRules) { rule ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(cardBg)
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (rule.type == TransactionType.EXPENSE) CoralRed.copy(alpha = 0.15f) else MintGreen.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (rule.type == TransactionType.EXPENSE) "支出" else "收入",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (rule.type == TransactionType.EXPENSE) CoralRed else MintGreen
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = rule.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Switch(
                                                    checked = rule.isEnabled,
                                                    onCheckedChange = { checked ->
                                                        viewModel.updatePeriodicRule(rule.copy(isEnabled = checked))
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                IconButton(onClick = { viewModel.deletePeriodicRule(rule) }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = textSecondary, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "周期: ${rule.frequency} (${rule.timeOfDay})", fontSize = 13.sp, color = textSecondary)
                                            Text(
                                                text = "¥ ${rule.amount}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (rule.type == TransactionType.EXPENSE) CoralRed else MintGreen
                                            )
                                        }

                                        Text(
                                            text = "账户: ${rule.accountName} · 分类: ${rule.categoryName} · 生效日: ${rule.startDate}",
                                            fontSize = 12.sp,
                                            color = textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionText(num: String, text: String, textSecondary: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "$num.", fontSize = 14.sp, color = textSecondary, lineHeight = 20.sp)
        Text(text = text.substring(2), fontSize = 14.sp, color = textSecondary, lineHeight = 20.sp)
    }
}

@Composable
private fun FormRow(label: String, value: String, isDark: Boolean, onClick: () -> Unit) {
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 15.sp, color = textColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, fontSize = 14.sp, color = BlueAccent)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}
