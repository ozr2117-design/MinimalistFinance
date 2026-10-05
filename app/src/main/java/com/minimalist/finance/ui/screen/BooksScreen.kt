package com.minimalist.finance.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.data.model.Book
import com.minimalist.finance.data.model.BookType
import com.minimalist.finance.ui.theme.*
import com.minimalist.finance.ui.viewmodel.MainViewModel

@Composable
fun BooksScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val books by viewModel.allBooks.collectAsState()
    val curBookId by viewModel.currentBookId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
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
                    Icon(imageVector = Icons.Default.Add, contentDescription = "新建账本", tint = textColor)
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
            items(books) { book ->
                val isSelected = book.id == curBookId
                BookCardItem(
                    book = book,
                    isSelected = isSelected,
                    onSelect = { viewModel.currentBookId.value = book.id },
                    isDark = isDark
                )
            }
        }
    }

    // 新增账本弹窗
    if (showAddDialog) {
        var bookName by remember { mutableStateOf("") }
        var bookSubtitle by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(BookType.CUSTOM) }
        var currency by remember { mutableStateOf("CNY") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(text = "新建专属账本 (无限制)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = bookName,
                        onValueChange = { bookName = it },
                        label = { Text("账本名称 (例如: 欧洲自驾游)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bookSubtitle,
                        onValueChange = { bookSubtitle = it },
                        label = { Text("副标题或备注说明") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bookName.isNotBlank()) {
                            viewModel.addNewBook(
                                name = bookName,
                                subtitle = bookSubtitle,
                                type = selectedType,
                                currency = currency
                            )
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("立即创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消")
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
            .padding(18.dp)
    ) {
        // 右上角选中状态图标
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
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

        // 右下角三点操作图标
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "更多操作",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
        )
    }
}
