package com.minimalist.finance.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.text.SimpleDateFormat
import java.util.*

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

    // 导出文件缓存暂存
    var pendingJsonContent by remember { mutableStateOf<String?>(null) }
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }

    // 导入确认状态
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var importedJsonData by remember { mutableStateOf<String?>(null) }

    // SAF 导出 JSON 文件选择器
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && pendingJsonContent != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(pendingJsonContent!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "✅ 全量备份已成功导出保存！", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                pendingJsonContent = null
            }
        }
    }

    // SAF 导出 CSV 文件选择器
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null && pendingCsvContent != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(pendingCsvContent!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "✅ CSV 账单流水已成功导出保存！", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                pendingCsvContent = null
            }
        }
    }

    // SAF 导入 JSON 文件选择器
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    importedJsonData = text
                    showImportConfirmDialog = true
                } else {
                    Toast.makeText(context, "选取的文件内容为空！", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // SAF 导入 CSV 文件选择器
    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    viewModel.importRecordsCsv(text) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "选取的文件内容为空！", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取 CSV 失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
                    text = "数据备份与导入",
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
                            text = "「数簿」所有账本、账户、资产与消费记录均仅保存在你当前手机本地的 SQLite 数据库中，不连接任何第三方商业统计或广告服务器。你可以随时随地完全导出或导入恢复属于你的数据。",
                            fontSize = 13.sp,
                            color = textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 1. 换机数据迁移 (JSON 全量备份与导入)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📱", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "换机数据迁移 · 全量备份与导入", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Text(
                            text = "包含全部账本、资产账户、分类结构及全部记账历史流水。换新手机时，在新手机上一键导入即可完美无缝还原全部数据！",
                            fontSize = 13.sp,
                            color = textSecondary,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 导出 JSON
                            Button(
                                onClick = {
                                    viewModel.exportFullJsonBackup { json ->
                                        if (json.isNotBlank()) {
                                            pendingJsonContent = json
                                            val timeTag = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                            exportJsonLauncher.launch("数簿全量备份_$timeTag.json")
                                        } else {
                                            Toast.makeText(context, "导出失败，暂无有效数据", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("导出全量备份")
                            }

                            // 导入 JSON
                            FilledTonalButton(
                                onClick = {
                                    importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("导入还原备份")
                            }
                        }
                    }
                }
            }

            // 2. 表格账单明细 (Excel / CSV 格式导出与导入)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📊", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "账单明细表格 · CSV 导出与导入", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Text(
                            text = "通用 CSV 表格格式，支持在电脑 Excel 中双击直接查看分析，亦可将其他表格格式账单批量导入到「数簿」中。",
                            fontSize = 13.sp,
                            color = textSecondary,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 导出 CSV
                            OutlinedButton(
                                onClick = {
                                    viewModel.exportRecordsCsv { csv ->
                                        if (csv.isNotBlank()) {
                                            pendingCsvContent = csv
                                            val timeTag = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                            exportCsvLauncher.launch("数簿流水表格_$timeTag.csv")
                                        } else {
                                            Toast.makeText(context, "暂无流水记录可导出", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("导出流水表格")
                            }

                            // 导入 CSV
                            OutlinedButton(
                                onClick = {
                                    importCsvLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain", "*/*"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("导入流水表格")
                            }
                        }
                    }
                }
            }

            // 3. 危险区域：一键清空所有历史流水 (满足用户需求，恢复纯净空库)
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
                        Text(text = "清空所有账本下的所有记账流水记录，将资产统计与结余完全重置归零（不会删除账本与分类结构）。建议在清空前先导出备份。", fontSize = 13.sp, color = textSecondary)
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

    // JSON 备份导入模式选择弹窗 (合并导入 vs 覆盖还原)
    if (showImportConfirmDialog && importedJsonData != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                importedJsonData = null
            },
            title = {
                Text(text = "导入备份数据", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "已成功读取备份文件。请选择导入还原模式：\n\n" +
                            "• 【合并导入】(推荐)：保留现有数据，将备份中的新流水和账户追加进来\n\n" +
                            "• 【覆盖还原】：清空当前流水，完全按备份文件内容彻底还原",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importFullJsonBackup(importedJsonData!!, overwrite = false) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            showImportConfirmDialog = false
                            importedJsonData = null
                        }
                    }
                ) {
                    Text("合并导入 (推荐)")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.importFullJsonBackup(importedJsonData!!, overwrite = true) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showImportConfirmDialog = false
                                importedJsonData = null
                            }
                        }
                    ) {
                        Text("覆盖还原", color = CoralRed)
                    }
                    TextButton(
                        onClick = {
                            showImportConfirmDialog = false
                            importedJsonData = null
                        }
                    ) {
                        Text("取消")
                    }
                }
            }
        )
    }
}
