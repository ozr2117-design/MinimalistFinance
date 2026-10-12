package com.minimalist.finance.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalist.finance.ui.theme.*
import java.util.Locale

@Composable
fun BudgetSettingDialog(
    currentBudget: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var inputAmount by remember(currentBudget) {
        mutableStateOf(if (currentBudget > 0.0) String.format(Locale.US, "%.0f", currentBudget) else "")
    }

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary

    val presets = listOf(2000.0, 3000.0, 5000.0, 8000.0, 10000.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = null,
                    tint = BlueAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "本月预算防暴走",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "设定本月目标支出上限后，数簿将按日为您动态测算可用额度，告别超支焦虑。",
                    fontSize = 13.sp,
                    color = textSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 快捷预设金额胶囊
                Text(text = "快捷推荐", fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { p ->
                        val isSelected = inputAmount == String.format(Locale.US, "%.0f", p)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) BlueAccent.copy(alpha = 0.2f) else (if (isDark) DarkSurface else Color(0xFFF1F5F9)),
                            border = if (isSelected) BorderStroke(1.dp, BlueAccent) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { inputAmount = String.format(Locale.US, "%.0f", p) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = "¥${String.format(Locale.US, "%.0f", p)}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) BlueAccent else textColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 自定义金额输入框
                OutlinedTextField(
                    value = inputAmount,
                    onValueChange = { inputAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("自定义月度预算金额 (元)") },
                    prefix = { Text("¥ ", color = textColor, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (currentBudget > 0.0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = { onSave(0.0) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("清空本月预算", color = CoralRed, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = inputAmount.toDoubleOrNull() ?: 0.0
                    onSave(amount)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存生效", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = textSecondary)
            }
        }
    )
}
