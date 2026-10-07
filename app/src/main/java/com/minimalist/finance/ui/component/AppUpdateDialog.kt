package com.minimalist.finance.ui.component

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
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
import com.minimalist.finance.util.AppUpdateInfo
import com.minimalist.finance.util.AppUpdateManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val logBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadProgressText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                AppUpdateManager.recordRemindLater(context)
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = BlueAccent,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "发现新版本 v${updateInfo.versionName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = textColor
                )
                if (updateInfo.releaseDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "发布日期: ${updateInfo.releaseDate}",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 更新说明卡片区域（带可滑动查看全部能力）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(logBg)
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "✨ 本次更新说明：",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueAccent
                        )
                        Text(
                            text = updateInfo.changelog,
                            fontSize = 13.sp,
                            color = textColor.copy(alpha = 0.9f),
                            lineHeight = 20.sp
                        )
                    }
                }

                // 下载进度条区域
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(4.dp))
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
            if (isDownloading) {
                TextButton(
                    onClick = {
                        isDownloading = false
                        onDismiss()
                    }
                ) {
                    Text("后台运行 / 取消", color = textSecondary)
                }
            } else {
                Button(
                    onClick = {
                        isDownloading = true
                        downloadProgress = 0f
                        downloadProgressText = "连接服务器..."
                        coroutineScope.launch {
                            val cacheFile = File(context.externalCacheDir ?: context.cacheDir, "shubu_update.apk")
                            val result = AppUpdateManager.downloadApk(
                                primaryUrl = updateInfo.downloadUrl,
                                backupUrl = updateInfo.backupDownloadUrl,
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
                            isDownloading = false
                            result.onSuccess { apkFile ->
                                onDismiss()
                                AppUpdateManager.installApk(context, apkFile)
                            }.onFailure { err ->
                                Toast.makeText(context, "应用内下载失败: ${err.message}，正在尝试跳转浏览器", Toast.LENGTH_LONG).show()
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
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
            if (!isDownloading) {
                TextButton(
                    onClick = {
                        AppUpdateManager.recordRemindLater(context)
                        onDismiss()
                    }
                ) {
                    Text("稍后再说")
                }
            }
        }
    )
}
