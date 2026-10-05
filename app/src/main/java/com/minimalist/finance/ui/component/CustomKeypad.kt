package com.minimalist.finance.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.ui.theme.*

@Composable
fun CustomKeypad(
    primaryColor: Color,
    onDigitClick: (String) -> Unit,
    onOperatorClick: (String) -> Unit,
    onDotClick: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSaveAgain: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val buttonBg = if (isDark) Color(0xFF14171E) else Color(0xFFF1F5F9)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 第一行: 1, 2, 3, 退格
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton(text = "1", onClick = { onDigitClick("1") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = "2", onClick = { onDigitClick("2") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = "3", onClick = { onDigitClick("3") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadIconButton(onClick = onBackspace, onLongClick = onClear, modifier = Modifier.weight(1f), bg = buttonBg, color = textSecondary)
        }

        // 第二行: 4, 5, 6, +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton(text = "4", onClick = { onDigitClick("4") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = "5", onClick = { onDigitClick("5") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = "6", onClick = { onDigitClick("6") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = "+", onClick = { onOperatorClick("+") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary, fontSize = 26)
        }

        // 第三行: 7, 8, 9, -
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton(text = "7", onClick = { onDigitClick("7") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = "8", onClick = { onDigitClick("8") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = "9", onClick = { onDigitClick("9") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = "−", onClick = { onOperatorClick("-") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary, fontSize = 26)
        }

        // 第四行: 再记, 0, ., 保存
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton(text = "再记", onClick = onSaveAgain, modifier = Modifier.weight(1f), bg = buttonBg, color = textSecondary, fontSize = 16)
            KeypadButton(text = "0", onClick = { onDigitClick("0") }, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary)
            KeypadButton(text = ".", onClick = onDotClick, modifier = Modifier.weight(1f), bg = buttonBg, color = textPrimary, fontSize = 26)
            KeypadButton(text = "保存", onClick = onSave, modifier = Modifier.weight(1f), bg = primaryColor, color = Color.White, fontSize = 18, isBold = true)
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color,
    color: Color,
    fontSize: Int = 22,
    isBold: Boolean = false
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun KeypadIconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color,
    color: Color
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "退格",
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
