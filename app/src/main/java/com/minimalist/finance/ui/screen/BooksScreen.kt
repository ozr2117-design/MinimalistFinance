package com.minimalist.finance.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.data.model.Book
import com.minimalist.finance.data.model.BookType
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.DEFAULT_PRESET_BOOKS
import com.minimalist.finance.ui.viewmodel.MainViewModel
import com.minimalist.finance.ui.viewmodel.PresetBookTemplate

@Composable
fun BooksScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val books by viewModel.allBooks.collectAsState()
    val curBookId by viewModel.currentBookId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
                }
                Text(
                    text = "我的账本",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "添加账本", tint = textColor)
                }
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
            items(books, key = { it.id }) { book ->
                val isSelected = book.id == curBookId
                BookCardItem(
                    book = book,
                    isSelected = isSelected,
                    onSelect = { viewModel.currentBookId.value = book.id },
                    onDelete = { bookToDelete = book },
                    isDark = isDark
                )
            }
        }
    }

    // 删除账本确认弹窗
    bookToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = CoralRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "删除「${target.name}」？",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "删除后该账本将从列表中移除。如果您以后需要，可随时在右上角「+」中一键恢复或重新添加。",
                    color = textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                    onClick = {
                        viewModel.deleteBook(target)
                        bookToDelete = null
                        Toast.makeText(context, "已删除「${target.name}」", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确认删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 添加账本双模式弹窗 (预设选项添加 / 自主添加)
    if (showAddDialog) {
        var selectedTab by remember { mutableStateOf(0) } // 0: 选项添加, 1: 自主添加

        // 自主添加的表单字段
        var customName by remember { mutableStateOf("") }
        var customSubtitle by remember { mutableStateOf("") }
        var customCurrency by remember { mutableStateOf("CNY") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Column {
                    Text(
                        text = "添加账本",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // 双模式切换选项卡
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) DarkSurface else Color(0xFFF1F5F9))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedTab == 0) (if (isDark) DarkSurfaceCard else Color.White) else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "预设选项 (一键添加)",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) BlueAccent else textSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedTab == 1) (if (isDark) DarkSurfaceCard else Color.White) else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "自主添加 (自定义)",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) BlueAccent else textSecondary
                            )
                        }
                    }
                }
            },
            text = {
                if (selectedTab == 0) {
                    // 模式 1: 预设推荐账本列表 (可一键恢复/添加)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "点击可直接添加经典账本及专属分类：",
                            fontSize = 12.sp,
                            color = textSecondary
                        )

                        DEFAULT_PRESET_BOOKS.forEach { preset ->
                            val isAlreadyAdded = books.any { it.name == preset.name }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) DarkSurface else Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isDark) DarkBorder else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = preset.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = BlueAccent.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = preset.currency,
                                                    fontSize = 10.sp,
                                                    color = BlueAccent,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = preset.subtitle,
                                            fontSize = 11.sp,
                                            color = textSecondary
                                        )
                                    }

                                    if (isAlreadyAdded) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = (if (isDark) DarkBorder else Color(0xFFE2E8F0))
                                        ) {
                                            Text(
                                                text = "已在列表中",
                                                fontSize = 11.sp,
                                                color = textSecondary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                viewModel.addPresetBook(preset)
                                                showAddDialog = false
                                                Toast.makeText(context, "已添加「${preset.name}」", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("+ 一键添加", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 模式 2: 自主定义账本
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("账本名称 (例如: 欧洲自驾游)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customSubtitle,
                            onValueChange = { customSubtitle = it },
                            label = { Text("副标题或备注 (例如: 2026秋季出行)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(text = "结算货币", fontSize = 12.sp, color = textSecondary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("CNY", "USD", "HKD", "EUR", "JPY").forEach { curr ->
                                val isCurSelected = customCurrency == curr
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurSelected) BlueAccent else (if (isDark) DarkSurface else Color(0xFFF1F5F9)),
                                    modifier = Modifier.clickable { customCurrency = curr }
                                ) {
                                    Text(
                                        text = curr,
                                        fontSize = 12.sp,
                                        color = if (isCurSelected) Color.White else textColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedTab == 1) {
                    Button(
                        onClick = {
                            if (customName.isNotBlank()) {
                                viewModel.addNewBook(
                                    name = customName.trim(),
                                    subtitle = customSubtitle.trim(),
                                    type = BookType.CUSTOM,
                                    currency = customCurrency
                                )
                                showAddDialog = false
                                Toast.makeText(context, "已创建「$customName」", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("立即创建")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun BookCardItem(
    book: Book,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean
) {
    // 根据账本类型赋予不同层次的高质感大片渐变底色
    val gradientBrush = when (book.type) {
        BookType.DAILY -> Brush.horizontalGradient(listOf(Color(0xFF8E3E28), Color(0xFFD67347)))
        BookType.DOMESTIC_INVEST -> Brush.horizontalGradient(listOf(Color(0xFF0F3B66), Color(0xFF2278C9)))
        BookType.OVERSEAS_INVEST -> Brush.horizontalGradient(listOf(Color(0xFF1E2640), Color(0xFF4C5D8A)))
        BookType.SAVINGS -> Brush.horizontalGradient(listOf(Color(0xFF6B4E1B), Color(0xFFB88E3E)))
        BookType.CUSTOM -> Brush.horizontalGradient(listOf(Color(0xFF333333), Color(0xFF666666)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(gradientBrush)
            .clickable { onSelect() }
            .padding(16.dp)
    ) {
        // 右上角选中状态图标 (对勾)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "激活中",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 仅对非默认账本开放删除按键 (日常消费主账本不可删除)
        if (!book.isDefault) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(if (isSelected) Alignment.TopStart else Alignment.TopEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "删除账本",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 居中大标题与副标题
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = book.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = book.subtitle,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        // 左下角货币徽章
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.22f),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = book.currency,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
