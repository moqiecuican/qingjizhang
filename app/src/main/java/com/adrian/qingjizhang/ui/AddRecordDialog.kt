package com.adrian.qingjizhang.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.adrian.qingjizhang.core.Money
import com.adrian.qingjizhang.data.local.CategoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 记一笔 / 编辑流水 共用对话框。
 * - Add 模式初始：支出、金额空、日期今天；分类默认选「餐饮」（调用方传入的分类列表首位）
 * - Edit 模式：用已有流水预填
 * 金额实时校验（红色报错但不阻止输入），保存时再兜底一次。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRecordDialog(
    mode: DialogMode,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    saving: Boolean,
    viewModel: LedgerViewModel,
    onDismiss: () -> Unit,
) {
    val editing = (mode as? DialogMode.Edit)?.record
    var isExpense by rememberSaveable { mutableStateOf(editing?.isExpense ?: true) }
    var amount by rememberSaveable {
        mutableStateOf(editing?.let { Money.formatYuan(it.amountCents).removeSuffix(".00") } ?: "")
    }
    var categoryId by rememberSaveable { mutableLongStateOf(editing?.categoryId ?: 0L) } // 0 = 未选
    var epochDay by rememberSaveable { mutableLongStateOf(editing?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(editing?.note ?: "") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    // Bug修复：Add 模式自动聚焦金额框并唤起数字键盘。
    // 延迟一帧等 Dialog 窗口就绪，否则 requestFocus 时 IME 可能不弹。
    val amountFocus = remember { FocusRequester() }
    if (editing == null) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(150)
            amountFocus.requestFocus()
        }
    }

    val cats = if (isExpense) expenseCategories else incomeCategories
    val selectedCat = cats.firstOrNull { it.id == categoryId }
    // Add 模式下若「餐饮」还没加载出来，先把选中态留空，加载后补默认（取支出组第一个）
    if (editing == null && categoryId == 0L && expenseCategories.isNotEmpty() && isExpense) {
        categoryId = expenseCategories.first().id
    }

    // 保存动作：全宽主按钮与键盘「完成/回车」键共用同一条路径
    val focusManager = LocalFocusManager.current
    val doSave: () -> Unit = {
        viewModel.saveRecord(
            amountInput = amount,
            isExpense = isExpense,
            categoryId = if (categoryId == 0L) null else categoryId,
            date = LocalDate.ofEpochDay(epochDay),
            note = note,
            editing = editing,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows=false + 内容侧 imePadding()：
        // 键盘避让改在 Compose 布局层完成，窗口不再随 IME 自动 resize。
        // 修复「备注输入时弹窗内容疯狂上下抽搐」——原实现用窗口级 SOFT_INPUT_ADJUST_RESIZE，
        // 与弹窗内 verticalScroll 的「聚焦字段自动滚入视野」互相触发，形成布局反馈回路
        // （手机屏空间紧张必现；平板屏大不需滚动故不复现）。
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(0.94f)
            .imePadding(),
        title = { Text(if (editing == null) "记一笔" else "编辑流水") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---- 支出 / 收入 ----
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isExpense,
                        onClick = { isExpense = true; categoryId = 0L },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("支出") },
                    )
                    SegmentedButton(
                        selected = !isExpense,
                        onClick = { isExpense = false; categoryId = 0L },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("收入") },
                    )
                }

                // ---- 金额 ----
                val amountInvalid = amount.isNotEmpty() && Money.parseToCents(amount) == null
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.length <= 12) amount = it },
                    label = { Text("金额（元）") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    isError = amountInvalid,
                    supportingText = {
                        if (amountInvalid) Text("最多两位小数，需大于 0", color = MaterialTheme.colorScheme.error)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done, // 键盘右下角动作键=完成，直接触发保存
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); doSave() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
                )

                // ---- 分类 ----
                Text("分类", style = MaterialTheme.typography.labelLarge)
                if (cats.isEmpty()) {
                    Text("（还没有分类，请到「分类」页添加）", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cats.forEach { c ->
                            FilterChip(
                                selected = c.id == categoryId,
                                onClick = { categoryId = c.id },
                                label = { Text(c.name) },
                            )
                        }
                    }
                }

                // ---- 日期 ----
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(LocalDate.ofEpochDay(epochDay).toString())
                }

                // ---- 备注 ----
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 100) note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            // 主操作区：全宽填充式「保存」(52dp 高，键盘弹出也够得着) + 下方安静的「取消」
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { focusManager.clearFocus(); doSave() },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (editing == null) "保存" else "更新", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text("取消")
                }
            }
        },
    )

    // ---- 日期选择弹层（限制：可选到今天后一年，防手滑记到遥远未来）----
    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = epochDay * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis() + 366L * 86_400_000L
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}
