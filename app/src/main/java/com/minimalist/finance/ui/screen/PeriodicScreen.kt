package com.minimalist.finance.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    var selectedTab by remember { mutableStateOf(0) } // 0: 分期管理 (图二默认), 1: 周期记账
    var isAdding by remember { mutableStateOf(false) } // 是否在新建表单页 (图一)

    val periodicRules by viewModel.periodicRules.collectAsState()
    val installmentPlans by viewModel.installmentPlans.collectAsState()
    val books by viewModel.allBooks.collectAsState()

    if (isAdding) {
        // ====== 表单页 (1:1 对照图一: 添加周期/分期记账) ======
        var startDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
        var frequency by remember { mutableStateOf("每月") }
        var endType by remember { mutableStateOf("永不结束") }
        var selectedBook by remember { mutableStateOf(books.firstOrNull()?.name ?: "日常消费账本") }
        var selectedCat by remember { mutableStateOf("房租") }
        var selectedAccount by remember { mutableStateOf("微信零钱") }
        var amountText by remember { mutableStateOf("") }
        var timeOfDay by remember { mutableStateOf("12:00") }
        var remark by remember { mutableStateOf("") }
        var periodsCount by remember { mutableStateOf("12") } // 仅分期

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
                        text = if (selectedTab == 0) "添加信用卡分期" else "添加周期记账",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            },
            bottomBar = {
                // 底部保存条 (对照图一：左侧预览，右侧保存)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { Toast.makeText(context, "规则预览正常", Toast.LENGTH_SHORT).show() },
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
                                // 保存分期计划
                                val totalP = periodsCount.toIntOrNull() ?: 12
                                val monthly = amount / totalP
                                viewModel.addInstallmentPlan(
                                    InstallmentPlan(
                                        name = if (remark.isNotBlank()) remark else "信用卡分期",
                                        totalAmount = amount,
                                        totalPeriods = totalP,
                                        currentPeriod = 1,
                                        monthlyAmount = monthly,
                                        bookId = viewModel.currentBookId.value,
                                        accountName = selectedAccount
                                    )
                                )
                                Toast.makeText(context, "已成功添加分期计划！", Toast.LENGTH_SHORT).show()
                            } else {
                                // 保存周期记账
                                viewModel.addPeriodicRule(
                                    PeriodicRule(
                                        name = if (remark.isNotBlank()) remark else "固定周期支出",
                                        type = TransactionType.EXPENSE,
                                        amount = amount,
                                        bookId = viewModel.currentBookId.value,
                                        categoryName = selectedCat,
                                        accountName = selectedAccount,
                                        startDate = startDate,
                                        frequency = frequency,
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
                            Text("保存", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                item {
                    FormRow(label = "开始日期", value = startDate, isDark = isDark) {
                        val c = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            startDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }
                }
                item {
                    FormRow(label = "重复周期", value = frequency, isDark = isDark) {
                        frequency = if (frequency == "每月") "每周" else if (frequency == "每周") "每年" else "每月"
                    }
                }
                item {
                    FormRow(label = "结束方式", value = endType, isDark = isDark) {
                        endType = if (endType == "永不结束") "执行12次后结束" else "永不结束"
                    }
                }
                item {
                    FormRow(label = "账本", value = selectedBook, isDark = isDark) {
                        val next = books.find { it.name != selectedBook }
                        if (next != null) selectedBook = next.name
                    }
                }
                item {
                    FormRow(label = "扣款账户", value = selectedAccount, isDark = isDark) {
                        selectedAccount = if (selectedAccount == "微信零钱") "招商银行卡" else "支付宝"
                    }
                }
                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("账单总金额 (¥)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedTab == 0) {
                    item {
                        OutlinedTextField(
                            value = periodsCount,
                            onValueChange = { periodsCount = it },
                            label = { Text("分期总期数 (如 3 / 6 / 12 / 24)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    FormRow(label = "账单时间", value = timeOfDay, isDark = isDark) {
                        TimePickerDialog(context, { _, h, m ->
                            timeOfDay = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                        }, 12, 0, true).show()
                    }
                }
                item {
                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        label = { Text("账单备注 (如：房租、电脑分期)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    } else {
        // ====== 列表页 (1:1 对照图二: 分期管理 / 周期记账) ======
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

                    // 顶部双 Tab (对照图二: 分期管理 | 周期记账)
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
                // 底部大蓝色按钮 (1:1 对照图二: + 添加)
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
                    Text("+ 添加", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    // 分期 Tab
                    if (installmentPlans.isEmpty()) {
                        // 1:1 展示图二中的 4 条新手说明指引！
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            InstructionText(num = "1", text = "分期功能，专门用于管理信用卡分期的场景，请按照你的信用卡分期情况设定，然后会每个月自动帮您入账；", textSecondary = textSecondary)
                            InstructionText(num = "2", text = "入账的账单为支出类型；", textSecondary = textSecondary)
                            InstructionText(num = "3", text = "分期任务不支持修改，请设置前仔细确认；", textSecondary = textSecondary)
                            InstructionText(num = "4", text = "分期入账的账单，与手动记录的账单无区别，可以修改、删除；", textSecondary = textSecondary)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(installmentPlans) { plan ->
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
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = plan.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                            IconButton(onClick = { viewModel.deleteInstallmentPlan(plan) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = textSecondary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { plan.currentPeriod.toFloat() / plan.totalPeriods.toFloat() },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = BlueAccent
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(text = "总额: ¥ ${plan.totalAmount} (进度 ${plan.currentPeriod}/${plan.totalPeriods} 期)", fontSize = 13.sp, color = textSecondary)
                                        Text(text = "每期应扣: ¥ ${String.format(Locale.getDefault(), "%.2f", plan.monthlyAmount)} (${plan.accountName})", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 周期记账 Tab
                    if (periodicRules.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            InstructionText(num = "1", text = "周期记账功能，用于管理房租、宽带、月薪等固定规律发生的收支；", textSecondary = textSecondary)
                            InstructionText(num = "2", text = "设定好重复频率后，每个周期自动为你记账入库；", textSecondary = textSecondary)
                            InstructionText(num = "3", text = "随时可以暂停或删除周期任务；", textSecondary = textSecondary)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(periodicRules) { rule ->
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
                                        Text(text = rule.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        Text(text = "${rule.frequency} · ${rule.accountName} · 金额 ¥ ${rule.amount}", fontSize = 13.sp, color = textSecondary)
                                        Text(text = "生效时间: ${rule.startDate} (${rule.timeOfDay})", fontSize = 12.sp, color = textSecondary)
                                    }
                                    IconButton(onClick = { viewModel.deletePeriodicRule(rule) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = textSecondary)
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
