package com.minimalist.finance.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.util.AppUpdateInfo
import com.minimalist.finance.util.AppUpdateManager
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadProgressText by remember { mutableStateOf("") }

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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF8B1E1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "數",
                            color = Color(0xFFFFD700),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "数簿", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Text(text = "版本 1.0.5 正式版 (Release)", fontSize = 13.sp, color = textSecondary)

                    Spacer(modifier = Modifier.height(12.dp))

                    // 检查更新按键 (Gitee 国内直连 / GitHub 备份镜像)
                    OutlinedButton(
                        onClick = {
                            if (isCheckingUpdate) return@OutlinedButton
                            isCheckingUpdate = true
                            coroutineScope.launch {
                                val result = AppUpdateManager.checkUpdate(currentVersionCode = 6)
                                isCheckingUpdate = false
                                result.onSuccess { info ->
                                    if (info != null) {
                                        updateInfo = info
                                        showUpdateDialog = true
                                    } else {
                                        Toast.makeText(context, "🎉 当前已是最新版本 (v1.0.5)", Toast.LENGTH_SHORT).show()
                                    }
                                }.onFailure { err ->
                                    Toast.makeText(context, "检查更新失败: ${err.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, BlueAccent.copy(alpha = 0.5f))
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = BlueAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "正在检查更新...", fontSize = 13.sp, color = BlueAccent)
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = BlueAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "检查新版本", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BlueAccent)
                        }
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "「每一笔都有数，过理性的生活」",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "数簿的目标不是说教理财，而是帮您在每一次收支中看清生活的真相。\n\n拒绝繁琐套路：无 VIP 限制、无广告、纯粹本地离线存储，把记账做到极致。",
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

    // 发现新版本弹窗
    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingUpdate) {
                    showUpdateDialog = false
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = BlueAccent,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "发现新版本 ${info.versionName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (info.releaseDate.isNotBlank()) {
                        Text(
                            text = "发布日期: ${info.releaseDate}",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                    HorizontalDivider(color = if (isDark) DarkBorder else LightBorder)
                    Text(
                        text = "更新内容：",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = info.changelog,
                        fontSize = 13.sp,
                        color = textColor.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )

                    // 下载进度条区域
                    if (isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = BlueAccent
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "正在下载更新包...",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                            Text(
                                text = downloadProgressText,
                                fontSize = 12.sp,
                                color = BlueAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (isDownloadingUpdate) {
                    TextButton(
                        onClick = {
                            isDownloadingUpdate = false
                            showUpdateDialog = false
                        }
                    ) {
                        Text("后台运行 / 取消", color = textSecondary)
                    }
                } else {
                    Button(
                        onClick = {
                            isDownloadingUpdate = true
                            downloadProgress = 0f
                            downloadProgressText = "连接服务器..."
                            coroutineScope.launch {
                                val cacheFile = File(context.externalCacheDir ?: context.cacheDir, "shubu_update.apk")
                                val result = AppUpdateManager.downloadApk(
                                    primaryUrl = info.downloadUrl,
                                    backupUrl = info.backupDownloadUrl,
                                    targetFile = cacheFile,
                                    onProgress = { current, total ->
                                        if (total > 0) {
                                            downloadProgress = current.toFloat() / total
                                            val currentMb = String.format("%.1f", current / (1024f * 1024f))
                                            val totalMb = String.format("%.1f", total / (1024f * 1024f))
                                            val pct = (downloadProgress * 100).toInt()
                                            downloadProgressText = "$currentMb / $totalMb MB ($pct%)"
                                        } else {
                                            val currentMb = String.format("%.1f", current / (1024f * 1024f))
                                            downloadProgressText = "已下载 $currentMb MB"
                                        }
                                    }
                                )
                                isDownloadingUpdate = false
                                result.onSuccess { apkFile ->
                                    showUpdateDialog = false
                                    AppUpdateManager.installApk(context, apkFile)
                                }.onFailure { err ->
                                    Toast.makeText(context, "应用内下载失败: ${err.message}，正在尝试跳转浏览器", Toast.LENGTH_LONG).show()
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    ) {
                        Text("立即在应用内更新")
                    }
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("稍后再说")
                    }
                }
            }
        )
    }
}
