package com.adrian.qingjizhang.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrian.qingjizhang.core.Catchup
import com.adrian.qingjizhang.core.Money
import com.adrian.qingjizhang.core.Recurrence
import com.adrian.qingjizhang.data.local.CategoryEntity
import com.adrian.qingjizhang.data.local.RecurrencePeriod
import com.adrian.qingjizhang.data.local.RecurringRuleEntity
import com.adrian.qingjizhang.theme.Semantic
import java.time.LocalDate

/**
 * 周期记账管理页（V1.3 T2.3）。
 * 入口：流水页顶栏「⋮」→「周期记账」。支持规则的新增/编辑/删除/暂停。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRulesScreen(
    viewModel: LedgerViewModel,
    wide: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val rules by viewModel.recurringRules.collectAsStateWithLifecycle()
    val expenseCats by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCats by viewModel.incomeCategories.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<RecurringRuleEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecurringRuleEntity?>(null) }

    // 外层 contentPadding 加在 Box 上：让内层 Scaffold（含 FAB）整体落在内容区内，
    // 否则 FAB 会被底部导航栏压住（真机踩坑：Scaffold 作为根节点时收不到外层 padding）
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("周期记账") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "新增周期规则")
            }
        },
    ) { inner ->
        Box(
            // 与 LedgerScreen 同款：外层 contentPadding 在外、内层 Scaffold padding 在内
            modifier = Modifier.fillMaxSize().padding(inner),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .then(if (wide) Modifier.widthIn(max = 600.dp) else Modifier)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            ) {
                if (rules.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "还没有周期规则\n\n点右下角「+」添加固定收支，\n例如「每月 5 号 · 房租」。\n\n打开 app 时会自动把到期的账补录进来。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    val today = LocalDate.now().toEpochDay()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(rules, key = { it.id }) { rule ->
                            RuleCard(
                                rule = rule,
                                today = today,
                                onEdit = { editing = rule; showEditor = true },
                                onToggle = { viewModel.toggleRecurringRule(rule) },
                                onDelete = { deleting = rule },
                            )
                        }
                    }
                }
            }
        }
    }
    }

    if (showEditor) {
        RecurringRuleDialog(
            editing = editing,
            expenseCategories = expenseCats,
            incomeCategories = incomeCats,
            onDismiss = { showEditor = false },
            onSave = { name, amount, isExpense, catId, period, dow, dom, moy, note, active ->
                viewModel.saveRecurringRule(
                    editing = editing,
                    name = name,
                    amountInput = amount,
                    isExpense = isExpense,
                    categoryId = catId,
                    period = period,
                    dayOfWeek = dow,
                    dayOfMonth = dom,
                    monthOfYear = moy,
                    note = note,
                    isActive = active,
                    onSaved = { showEditor = false },
                )
            },
        )
    }

    deleting?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除周期规则？") },
            text = {
                Text("「${rule.name}」将不再自动生成新账。\n\n已经生成的历史账目会全部保留，并且仍然带「自动」标记（这是真实发生过的账，不做抹除）。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecurringRule(rule)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

/** 单条规则卡片：名称 + 金额 + 节奏 + 备注 + 下次入账 + 操作 */
@Composable
private fun RuleCard(
    rule: RecurringRuleEntity,
    today: Long,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                if (!rule.isActive) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "已暂停",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    (if (rule.isExpense) "-" else "+") + "¥" + Money.formatYuan(rule.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (rule.isExpense) Semantic.expense() else Semantic.income(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                Catchup.describe(rule),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rule.note.isNotEmpty()) {
                Text(
                    "备注：${rule.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (rule.isActive) {
                val next = Recurrence.firstOccurrenceOnOrAfter(rule, today + 1)
                if (next != null) {
                    Text(
                        "下次入账：${LocalDate.ofEpochDay(next)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onToggle) { Text(if (rule.isActive) "暂停" else "启用") }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * 规则新增/编辑对话框。
 * 校验（非空规则名、金额、分类、周期参数）在 ViewModel 里统一做，这里只负责取值与回显。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecurringRuleDialog(
    editing: RecurringRuleEntity?,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        name: String, amount: String, isExpense: Boolean, categoryId: Long?,
        period: RecurrencePeriod, dayOfWeek: Int, dayOfMonth: Int, monthOfYear: Int,
        note: String, isActive: Boolean,
    ) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var isExpense by rememberSaveable { mutableStateOf(editing?.isExpense ?: true) }
    var amount by rememberSaveable {
        mutableStateOf(editing?.let { Money.formatYuan(it.amountCents).removeSuffix(".00") } ?: "")
    }
    var categoryId by rememberSaveable { mutableLongStateOf(editing?.categoryId ?: 0L) }
    var period by rememberSaveable {
        mutableStateOf(editing?.period ?: RecurrencePeriod.MONTHLY)
    }
    var dayOfWeek by rememberSaveable {
        mutableIntStateOf(editing?.dayOfWeek?.takeIf { it in 1..7 } ?: 1)
    }
    var dayOfMonthText by rememberSaveable {
        mutableStateOf(
            (editing?.dayOfMonth?.takeIf { it in 1..31 } ?: LocalDate.now().dayOfMonth).toString()
        )
    }
    var monthOfYearText by rememberSaveable {
        mutableStateOf(
            (editing?.monthOfYear?.takeIf { it in 1..12 } ?: LocalDate.now().monthValue).toString()
        )
    }
    var note by rememberSaveable { mutableStateOf(editing?.note ?: "") }
    var isActive by rememberSaveable { mutableStateOf(editing?.isActive ?: true) }

    val cats = if (isExpense) expenseCategories else incomeCategories
    // 新建时默认选中支出组第一个（与记一笔一致）
    if (editing == null && categoryId == 0L && cats.isNotEmpty()) {
        categoryId = cats.first().id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // 与记一笔同款键盘避让：decorFitsSystemWindows=false + imePadding（避免布局反馈回路）
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.94f).imePadding(),
        title = { Text(if (editing == null) "新增周期规则" else "编辑周期规则") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---- 规则名（手动输入，无预设）----
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("规则名（必填）") },
                    placeholder = { Text("例如：房租、工资、健身房月卡") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- 分类 ----
                Text("分类", style = MaterialTheme.typography.labelLarge)
                if (cats.isEmpty()) {
                    Text("（还没有分类，请到「分类」页添加）", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        cats.forEach { c ->
                            FilterChip(
                                selected = c.id == categoryId,
                                onClick = { categoryId = c.id },
                                label = { Text(c.name) },
                            )
                        }
                    }
                }

                // ---- 周期粒度 ----
                Text("周期", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RecurrencePeriod.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = period == p,
                            onClick = { period = p },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = RecurrencePeriod.entries.size),
                            label = { Text(when (p) {
                                RecurrencePeriod.WEEKLY -> "每周"
                                RecurrencePeriod.MONTHLY -> "每月"
                                RecurrencePeriod.YEARLY -> "每年"
                            }) },
                        )
                    }
                }

                // ---- 周期参数 ----
                when (period) {
                    RecurrencePeriod.WEEKLY -> {
                        Text("星期几", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            (1..7).forEach { dow ->
                                FilterChip(
                                    selected = dayOfWeek == dow,
                                    onClick = { dayOfWeek = dow },
                                    label = { Text(Recurrence.weekdayLabel(dow)) },
                                )
                            }
                        }
                    }
                    RecurrencePeriod.MONTHLY -> {
                        OutlinedTextField(
                            value = dayOfMonthText,
                            onValueChange = { if (it.length <= 2) dayOfMonthText = it },
                            label = { Text("每月第几日（1～31）") },
                            supportingText = { Text("若当月没有该日（如 2 月 31 日），自动按当月最后一天入账") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    RecurrencePeriod.YEARLY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = monthOfYearText,
                                onValueChange = { if (it.length <= 2) monthOfYearText = it },
                                label = { Text("月（1～12）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = dayOfMonthText,
                                onValueChange = { if (it.length <= 2) dayOfMonthText = it },
                                label = { Text("日（1～31）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ---- 备注（每次自动入账时自动写进生成账）----
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 100) note = it },
                    label = { Text("备注（可选）") },
                    supportingText = { Text("每次自动入账时，这段备注会自动写进生成的那笔账") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- 启用状态 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        onSave(
                            name, amount, isExpense,
                            if (categoryId == 0L) null else categoryId,
                            period, dayOfWeek,
                            dayOfMonthText.toIntOrNull() ?: 0,
                            monthOfYearText.toIntOrNull() ?: 0,
                            note, isActive,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (editing == null) "保存" else "更新", style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
        },
    )
}
