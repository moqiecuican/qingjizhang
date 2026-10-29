package com.adrian.qingjizhang.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrian.qingjizhang.core.Money
import com.adrian.qingjizhang.core.formatMonth

/**
 * 设置本月预算对话框（V1.1 T3.4）。
 * - 预填当前预算（有的话）；清空输入再保存 = 清除预算
 * - 小对话框天然不被键盘遮挡（DEVLOG 验证过），无需 adjustResize
 */
@Composable
fun BudgetDialog(
    currentCents: Long?,
    viewModel: LedgerViewModel,
    onDismiss: () -> Unit,
) {
    val ym by viewModel.month.collectAsStateWithLifecycle()
    var amount by rememberSaveable {
        mutableStateOf(currentCents?.let { Money.formatYuan(it).removeSuffix(".00") } ?: "")
    }
    val invalid = amount.isNotBlank() && (Money.parseToCents(amount) == null || Money.parseToCents(amount)!! <= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${formatMonth(ym)} 支出预算") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.length <= 12) amount = it },
                    label = { Text("预算金额（元）") },
                    placeholder = { Text("如 3000") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = {
                        if (invalid) {
                            Text("需大于 0，最多两位小数", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("清空金额并保存 = 取消本月预算")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.saveBudget(amount) },
                enabled = !invalid,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
