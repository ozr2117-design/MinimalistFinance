package com.minimalist.finance.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.minimalist.finance.service.AutoRecordAccessibilityService
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel

@Composable
fun AutoRecordScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

    // 检查权限与运行状态
    var isAccessibilityOn by remember { mutableStateOf(checkAccessibilityEnabled(context)) }
    var isOverlayOn by remember { mutableStateOf(checkOverlayEnabled(context)) }
    var isBatteryIgnored by remember { mutableStateOf(checkBatteryIgnored(context)) }
    var isEngineRunning by remember { mutableStateOf(AutoRecordAccessibilityService.isServiceRunning) }

    var autoRecordEnabled by remember { mutableStateOf(AutoRecordAccessibilityService.isEnabled(context)) }
    var keepAliveEnabled by remember { mutableStateOf(AutoRecordAccessibilityService.isKeepAliveEnabled(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            AutoRecordAccessibilityService.setKeepAliveEnabled(context, true)
            Toast.makeText(context, "通知权限已获取，常驻守护已启动", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "请允许通知权限以展示常驻守护通知", Toast.LENGTH_LONG).show()
        }
    }

    // 周期性轮询刷新真实运行状态并确保常驻通知生效
    LaunchedEffect(Unit) {
        if (keepAliveEnabled) {
            AutoRecordAccessibilityService.updateKeepAliveNotification(context)
        }
        while (true) {
            isAccessibilityOn = checkAccessibilityEnabled(context)
            isOverlayOn = checkOverlayEnabled(context)
            isBatteryIgnored = checkBatteryIgnored(context)
            isEngineRunning = AutoRecordAccessibilityService.isServiceRunning
            kotlinx.coroutines.delay(2000L)
        }
    }


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
                    text = "无障碍自动记账",
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 🌟 1. 核心运行状态诊断指示牌
            item {
                val (statusColor, statusTitle, statusSubtitle) = when {
                    isAccessibilityOn && isEngineRunning -> Triple(
                        MintGreen,
                        "🟢 记账核心引擎正常运行中",
                        "后台心跳正常 · 实时监听微信支付凭证、停车缴费与支付宝账单"
                    )
                    isAccessibilityOn && !isEngineRunning -> Triple(
                        WarmOrange,
                        "🟠 权限已开启，但服务未建立连接 (假死/休眠)",
                        "国产手机省电策略可能已将无障碍后台冻结。请点击右侧「重启服务」，在系统设置中先关闭再重新开启数簿即可唤醒！"
                    )
                    else -> Triple(
                        CoralRed,
                        "🔴 自动记账服务尚未开启",
                        "无障碍权限尚未授予，请参照下方三步法完成开启以激活自动记账。"
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = statusTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                            if (isAccessibilityOn && !isEngineRunning) {
                                TextButton(
                                    onClick = {
                                        openAccessibilitySettings(context)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("重启服务 >", color = BlueAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusSubtitle,
                            fontSize = 12.sp,
                            color = textSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // ⚡ 2. 核心总开关卡片 (带持久化)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "无感自动记账", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "在微信、支付宝付款成功或查看停车账单后，自动提取金额并极速记入账本。", fontSize = 12.sp, color = textSecondary)
                        }
                        Switch(
                            checked = autoRecordEnabled,
                            onCheckedChange = {
                                autoRecordEnabled = it
                                AutoRecordAccessibilityService.setEnabled(context, it)
                            }
                        )
                    }
                }
            }

            // 🔔 3. 常驻通知栏保活 (彻底根治后台假死)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MintGreen, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "常驻通知栏保活 (强烈推荐)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "在通知栏保持微弱守护，彻底防止微信付款或锁屏后被手机系统清理杀后台引发漏记。", fontSize = 12.sp, color = textSecondary)
                        }
                        Switch(
                            checked = keepAliveEnabled,
                            onCheckedChange = { isChecked ->
                                keepAliveEnabled = isChecked
                                if (isChecked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        AutoRecordAccessibilityService.setKeepAliveEnabled(context, true)
                                        Toast.makeText(context, "已开启常驻通知守护", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    AutoRecordAccessibilityService.setKeepAliveEnabled(context, false)
                                    Toast.makeText(context, "已关闭常驻通知", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                    }
                }
            }

            // 📋 4. 最近一次自动取数动态
            item {
                val lastLog = AutoRecordAccessibilityService.lastRecordLog
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "最近一次自动取数动态", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (lastLog.isNotBlank()) lastLog else "暂无触发记录，可在下方点击模拟按钮或在微信/支付宝付款验证。",
                            fontSize = 12.sp,
                            color = if (lastLog.isNotBlank()) MintGreen else textSecondary,
                            fontWeight = if (lastLog.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // 核心权限步骤卡片 (极简三步法)
            item {
                Text(text = "步骤与必要权限", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
            }

            // 第1步: 无障碍权限 (必须)
            item {
                PermissionStepCard(
                    stepNumber = "1",
                    title = "无障碍服务 (必须)",
                    subtitle = "用于感知微信与支付宝的支付成功页面与停车账单，提取金额",
                    isGranted = isAccessibilityOn,
                    grantedText = if (isEngineRunning) "已开启(活跃) ✔" else "已开启(待激活)",
                    notGrantedText = "去开启 >",
                    textColor = textColor,
                    textSecondary = textSecondary,
                    cardBg = cardBg,
                    onClick = {
                        openAccessibilitySettings(context)
                    }
                )
            }

            // 第2步: 悬浮窗权限 (推荐)
            item {
                PermissionStepCard(
                    stepNumber = "2",
                    title = "悬浮窗权限 (推荐)",
                    subtitle = "记账成功后在屏幕浮现轻量提醒，避免进入后台打扰操作",
                    isGranted = isOverlayOn,
                    grantedText = "已开启 ✔",
                    notGrantedText = "去授权 >",
                    textColor = textColor,
                    textSecondary = textSecondary,
                    cardBg = cardBg,
                    onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "请手动前往手机权限设置开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 第3步: 忽略电池优化 (保活防杀)
            item {
                PermissionStepCard(
                    stepNumber = "3",
                    title = "忽略电池优化 (防误杀)",
                    subtitle = "将省电策略调整为无限制，避免记账服务在后台被系统休眠清理",
                    isGranted = isBatteryIgnored,
                    grantedText = "已设置 ✔",
                    notGrantedText = "去设置 >",
                    textColor = textColor,
                    textSecondary = textSecondary,
                    cardBg = cardBg,
                    onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "请在电池设置中将数簿设为允许后台运行", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // ⚡ 优化亮点：模拟支付测试面板 (无需真花钱，一键检验自动记账)
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
                            Text(text = "🧪", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "模拟支付测试 (验证自动化入库)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BlueAccent)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "无需真实花钱付款，点击下方模拟按钮，即可立即验证记账服务、金额提取与智能分类入库是否正常工作：",
                            fontSize = 12.sp,
                            color = textSecondary,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // 测试按钮 0 (最新修复：微信交停车费场景)
                        Button(
                            onClick = {
                                AutoRecordAccessibilityService.simulateAutoRecord(
                                    context,
                                    4.00,
                                    "深圳市神州路通技术有限公司",
                                    "微信零钱通"
                                ) { cat ->
                                    Toast.makeText(context, "⚡ 模拟成功！已自动记账: 微信零钱通 ¥4.00 (商户: 深圳市神州路通 · 分类: $cat)", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🚗 模拟微信支付停车费 ¥4.00 (神州路通) -> 行 · 微信零钱通", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 测试按钮 1
                        OutlinedButton(
                            onClick = {
                                AutoRecordAccessibilityService.simulateAutoRecord(
                                    context,
                                    18.50,
                                    "麦当劳汉堡套餐",
                                    "微信"
                                ) { cat ->
                                    Toast.makeText(context, "⚡ 模拟成功！已自动记账: 微信支付 ¥18.50 (分类: $cat)", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🍔 模拟微信付款 ¥18.50 (麦当劳) -> 分类: 食")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 测试按钮 2
                        OutlinedButton(
                            onClick = {
                                AutoRecordAccessibilityService.simulateAutoRecord(
                                    context,
                                    26.00,
                                    "滴滴打车出行",
                                    "支付宝"
                                ) { cat ->
                                    Toast.makeText(context, "⚡ 模拟成功！已自动记账: 支付宝支付 ¥26.00 (分类: $cat)", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🚖 模拟支付宝付款 ¥26.00 (滴滴打车) -> 分类: 行")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 测试按钮 3
                        OutlinedButton(
                            onClick = {
                                AutoRecordAccessibilityService.simulateAutoRecord(
                                    context,
                                    6.80,
                                    "便利蜂生鲜便利店",
                                    "微信"
                                ) { cat ->
                                    Toast.makeText(context, "⚡ 模拟成功！已自动记账: 微信支付 ¥6.80 (分类: $cat)", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🏪 模拟便利店付款 ¥6.80 (便利蜂) -> 分类: 食")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 测试按钮 4 (饭店微信信用卡/扫码点餐)
                        OutlinedButton(
                            onClick = {
                                AutoRecordAccessibilityService.simulateAutoRecord(
                                    context,
                                    168.00,
                                    "探鱼烤鱼(万象天地店)",
                                    "招商银行(信用卡)"
                                ) { cat ->
                                    Toast.makeText(context, "⚡ 模拟成功！已自动记账: 招商银行(信用卡) ¥168.00 (商户: 探鱼烤鱼 · 分类: $cat)", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🍲 模拟微信信用卡/点餐小程序 ¥168.00 (探鱼) -> 食")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 测试按钮 5 (转账收款)
                        OutlinedButton(
                            onClick = {
                                AutoRecordAccessibilityService.simulateAutoRecord(
                                    context,
                                    1.20,
                                    "微信转账收款",
                                    "微信",
                                    com.minimalist.finance.data.model.TransactionType.INCOME
                                ) { cat ->
                                    Toast.makeText(context, "⚡ 模拟成功！已自动记账: 微信收款 +¥1.20 (分类: $cat)", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("💰 模拟微信转账收款 +¥1.20 (存入零钱) -> 收入")
                        }
                    }
                }
            }

            // 已适配应用卡片
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column {
                        Text(text = "👀 已完美适配的支付场景", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AppBadge("微信/小程序", "停车费/点餐")
                            AppBadge("支付宝", "余额/花呗")
                            AppBadge("云闪付", "银联银行卡")
                            AppBadge("美团外卖", "美团支付")
                        }
                    }
                }
            }

            // 国产系统避坑指南
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
                            Text(text = "💡", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "国产手机「受限制的设置」与防挂起说明", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "在小米澎湃OS / 华为鸿蒙 / vivo / OPPO 系统上：\n1. 若开启无障碍提示受限制，请点击下方直达数簿设置，点击右上角【三个点】选择【允许受限制的设置】；\n2. 若遇服务假死，请打开上方「常驻通知栏保活」，或在设置中重启一次无障碍开关。",
                            fontSize = 12.sp,
                            color = textSecondary,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "请在系统设置中打开应用管理", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("直达数簿应用详情设置 >", color = BlueAccent, fontSize = 13.sp)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        Toast.makeText(context, "请在已下载应用中找到「数簿」，关闭后重新开启即可激活！", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开系统无障碍设置，请手动前往设置开启", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PermissionStepCard(
    stepNumber: String,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    grantedText: String,
    notGrantedText: String,
    textColor: Color,
    textSecondary: Color,
    cardBg: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) MintGreen.copy(alpha = 0.2f) else CoralRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) MintGreen else CoralRed
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = textSecondary)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isGranted) grantedText else notGrantedText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isGranted) MintGreen else CoralRed
        )
    }
}

@Composable
private fun AppBadge(name: String, tag: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = tag, fontSize = 10.sp, color = Color.Gray)
    }
}

private fun checkAccessibilityEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${AutoRecordAccessibilityService::class.java.canonicalName}"
    val services = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(services)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) {
            return true
        }
    }
    return false
}

private fun checkOverlayEnabled(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun checkBatteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
}
