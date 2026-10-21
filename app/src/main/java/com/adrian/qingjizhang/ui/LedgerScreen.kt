package com.adrian.qingjizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.adrian.qingjizhang.core.BackupJson
import com.adrian.qingjizhang.core.BudgetProgress
import com.adrian.qingjizhang.core.Money
import com.adrian.qingjizhang.core.formatDayHeader
import com.adrian.qingjizhang.core.formatMonth
import com.adrian.qingjizhang.core.formatTimeOfDay
import com.adrian.qingjizhang.data.local.RecordEntity
import com.adrian.qingjizhang.theme.Semantic

/**
 * 流水页：月度总览卡 + 按日分组流水列表 + FAB 记一笔。
 * 平板（Compact 之外）内容列限宽 600dp 居中。
 */
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    widthSize: WindowWidthSizeClass,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    skipColdStartDialog: Boolean = false,
    onExported: (android.net.Uri) -> Unit,
    onOpenRecurring: () -> Unit,
) {
    val monthState by viewModel.monthState.collectAsStateWithLifecycle()
    val coldStartShown by viewModel.coldStartShown.collectAsStateWithLifecycle()
    val dialogMode by viewModel.dialogMode.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val expenseCats by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCats by viewModel.incomeCategories.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    // 冷启动自动弹「记一笔」（VM 存活期间只触发一次）
    LaunchedEffect(Unit) {
        if (!coldStartShown) {
            // 先消费掉标记：即使这次被抑制，也不能让切 Tab 回来时补弹
            viewModel.markColdStartShown()
            // V1.4：从小组件卡片主体进来时只看流水，不弹记账框
            if (!skipColdStartDialog) viewModel.openAddDialog()
        }
    }
    // Bug修复：提示消息的收集已上移到 MainActivity（AppRoot），
    // 之前内外两层 Scaffold 挂同一个 SnackbarHostState，导致一条消息渲染两次。

    var deletingRecord by remember { mutableStateOf<RecordEntity?>(null) }

    // ---- JSON 备份/恢复（V1.1 T4.3）----
    // SAF 文件选择器：用户挑备份 json → 读文本 → 校验 → 弹强警告确认框
    var pendingRestore by remember { mutableStateOf<BackupJson.File?>(null) }
    var pendingRestoreText by remember { mutableStateOf<String?>(null) }
    val restorePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            kotlinx.coroutines.MainScope().launch {
                val text = viewModel.readBackupUri(uri) ?: return@launch
                val snapshot = viewModel.validateBackup(text) ?: return@launch
                pendingRestoreText = text
                pendingRestore = snapshot
            }
        }
    }
    fun doBackupJson() {
        kotlinx.coroutines.MainScope().launch {
            viewModel.exportBackup()?.let { onExported(it) } // 复用分享通道
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Scaffold(
            floatingActionButton = {
                val wide = widthSize != WindowWidthSizeClass.Compact
                if (wide) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openAddDialog() },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("记一笔") },
                    )
                } else {
                    FloatingActionButton(onClick = { viewModel.openAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "记一笔")
                    }
                }
            },
            topBar = {
                MonthHeader(
                    viewModel = viewModel,
                    onExported = onExported,
                    onBudgetClick = { viewModel.openBudgetDialog(monthState.budgetCents) },
                    onBackupJson = { doBackupJson() },
                    onPickRestoreFile = { restorePicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    onOpenRecurring = onOpenRecurring,
                )
            },
        ) { inner ->
            // 平板/桌面：内容列限宽 600dp 并水平居中（自适应布局核心）
            val wide = widthSize != WindowWidthSizeClass.Compact
            Box(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.TopCenter,
            ) {
                MonthBody(
                    state = monthState,
                    // 顺序铁律：widthIn 必须在 fillMaxWidth 之前——
                    // 反过来写时 fillMaxWidth 把 min 约束锁成父宽，widthIn(max) 会被约束强制失效
                    // （竖屏实测抓到的 bug：限宽从未生效，内容一直全宽）
                    modifier = Modifier.widthIn(max = if (wide) 600.dp else 640.dp).fillMaxWidth().padding(horizontal = 16.dp),
                    wide = wide,
                    onEdit = { rec, catName -> viewModel.openEditDialog(rec, catName) },
                    onDeleteRequest = { deletingRecord = it },
                    onBudgetClick = { viewModel.openBudgetDialog(monthState.budgetCents) },
                    filterBar = {
                        FilterBar(
                            filter = filter,
                            expenseCategories = expenseCats,
                            incomeCategories = incomeCats,
                            onChange = { viewModel.setFilter(it) },
                            onClear = { viewModel.clearFilter() },
                        )
                    },
                )
            }
        }
    }

    // ---- 记一笔 / 编辑对话框；Budget 模式走独立对话框 ----
    dialogMode?.let { mode ->
        when (mode) {
            is DialogMode.Add, is DialogMode.Edit -> AddRecordDialog(
                mode = mode,
                expenseCategories = expenseCats,
                incomeCategories = incomeCats,
                saving = saving,
                viewModel = viewModel,
                onDismiss = { viewModel.closeDialog() },
            )
            is DialogMode.Budget -> BudgetDialog(
                currentCents = mode.currentCents,
                viewModel = viewModel,
                onDismiss = { viewModel.closeDialog() },
            )
        }
    }

    // ---- 删除确认 ----
    deletingRecord?.let { rec ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("删除这笔流水？") },
            text = { Text("${Money.formatYuan(rec.amountCents)} 元 · 删除后不可恢复") },
            confirmButton = {
                TextButton(onClick = {
                    deletingRecord = null
                    viewModel.deleteRecord(rec)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingRecord = null }) { Text("取消") } },
        )
    }

    // ---- 恢复强警告确认框（V1.1 T4.3：展示备份统计 + 覆盖警告，用户确认才动手）----
    pendingRestore?.let { snap ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null; pendingRestoreText = null },
            title = { Text("从备份恢复？") },
            text = {
                Column {
                    Text(
                        "备份包含：${snap.records.size} 条流水、${snap.categories.size} 个分类、${snap.budgets.size} 条预算。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ 恢复将【清空当前全部数据】并替换为备份内容，此操作不可撤销。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = pendingRestoreText
                    pendingRestore = null
                    pendingRestoreText = null
                    if (text != null) viewModel.restoreBackup(text)
                }) { Text("确认恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null; pendingRestoreText = null }) { Text("取消") }
            },
        )
    }
}

/** 顶栏：‹ 2026年9月 › 月份切换 + 溢出菜单（预算/CSV/JSON 备份恢复） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MonthHeader(
    viewModel: LedgerViewModel,
    onExported: (android.net.Uri) -> Unit,
    onBudgetClick: () -> Unit,
    onBackupJson: () -> Unit,
    onPickRestoreFile: () -> Unit,
    onOpenRecurring: () -> Unit,
) {
    val ym by viewModel.month.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 行首占位：TopAppBar 的 title 槽自带 16dp 起始 inset，
                // 要让文本相对整屏居中，左侧占用宽度需比右侧（→ 箭头 48 + ⋮ 菜单 48 = 96）少 16dp
                // → 48 - 16 = 32dp。实测：不配平时偏左 42px；配平 48dp 时偏右 21px；32dp 归中。
                Spacer(Modifier.width(32.dp))
                IconButton(onClick = { viewModel.shiftMonth(-1) }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    formatMonth(ym),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = { viewModel.shiftMonth(1) }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下个月")
                }
                // DropdownMenu 锚定的是**父布局节点**：必须与触发按钮同属一个小父节点（Box）。
                // 放进 full-width 的标题行 → 弹窗对齐到这行的 start，菜单会跑到屏幕最左边
                // （2026-10-08 修复问题4时引入的回归；v1.2.1 之前它在 TopAppBar.actions 的小 Row 里所以正常）
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        // 菜单被夹在窗口内时，末端会对齐到锚点末端（= 屏幕右边缘）→ 实测贴死（0dp）。
                        // 左移 15dp：原状态下菜单还会溢出屏幕约 8px，故净可见右边距 ≈ 12dp
                        // （与 ⋮ 图标自身的 ~11dp 右边距对齐成一条线；实测面板右边界 3016px / 屏宽 3048）。
                        offset = androidx.compose.ui.unit.DpOffset(x = (-15).dp, y = 0.dp),
                    ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("周期记账") },
                    onClick = {
                        menuOpen = false
                        onOpenRecurring()
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("设置本月预算") },
                    onClick = {
                        menuOpen = false
                        onBudgetClick()
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("导出 CSV") },
                    onClick = {
                        menuOpen = false
                        kotlinx.coroutines.MainScope().launch {
                            viewModel.exportCsv()?.let { onExported(it) }
                        }
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("备份 JSON（换机用）") },
                    onClick = {
                        menuOpen = false
                        onBackupJson()
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("从备份恢复…") },
                    onClick = {
                        menuOpen = false
                        onPickRestoreFile()
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("回到本月") },
                    onClick = {
                        menuOpen = false
                        viewModel.shiftMonthToday()
                    },
                )
                    }
                }
            }
        },
    )
}

/** 月内容：总览卡 + 日分组列表；平板限宽 600dp 居中 */
@Composable
private fun MonthBody(
    state: MonthUiState,
    modifier: Modifier,
    wide: Boolean,
    onEdit: (RecordEntity, String) -> Unit,
    onDeleteRequest: (RecordEntity) -> Unit,
    onBudgetClick: () -> Unit,
    filterBar: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        filterBar()
        Spacer(Modifier.height(8.dp))
        OverviewCard(state, onBudgetClick = onBudgetClick)
        Spacer(Modifier.height(8.dp))
        if (state.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    "这个月还没有账。\n点右下角「记一笔」开始。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // V1.4 行距试验：2dp → 4dp（仅此一处；闪烁试验，回退标准见计划书 §0.5）
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.groups.forEach { group ->
                    stickyHeader { DayHeader(group) }
                    items(group.items, key = { it.record.id }) { row ->
                        RecordRow(row = row, onEdit = onEdit, onDeleteRequest = onDeleteRequest)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) } // FAB 不遮最后一行
            }
        }
    }
}

/** 月度总览卡：收入/支出/结余 + 预算进度（设了预算才显示；点击可编辑） */
@Composable
private fun OverviewCard(state: MonthUiState, onBudgetClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCell("收入", Money.formatYuan(state.stats.incomeCents), Semantic.income())
            StatCell("支出", Money.formatYuan(state.stats.expenseCents), Semantic.expense())
            StatCell(
                "结余",
                (if (state.stats.balanceCents >= 0) "" else "-") +
                    Money.formatYuan(if (state.stats.balanceCents < 0) -state.stats.balanceCents else state.stats.balanceCents),
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        // ---- 预算区：未设=一行安静的入口；已设=进度条+剩余/超支 ----
        state.budgetCents?.let { budgetCents ->
            val progress = BudgetProgress(budgetCents, state.stats.expenseCents)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                val barColor = if (progress.isOver) Semantic.expense() else MaterialTheme.colorScheme.primary
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.ratio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (progress.isOver) {
                            "已超支 ¥${Money.formatYuan(-progress.remainingCents)}"
                        } else {
                            "预算剩余 ¥${Money.formatYuan(progress.remainingCents)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (progress.isOver) FontWeight.Bold else FontWeight.Normal,
                        color = if (progress.isOver) Semantic.expense() else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "预算 ¥${Money.formatYuan(budgetCents)} · 修改",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.clickable { onBudgetClick() },
                    )
                }
            }
        } ?: run {
            Text(
                "设置本月预算 ›",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { onBudgetClick() }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            "¥$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/** 日分组头（sticky：滚动时钉在顶部） */
@Composable
private fun DayHeader(group: DayGroup) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatDayHeader(group.dateEpochDay), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (group.expenseCents > 0) Text("支出 ¥${Money.formatYuan(group.expenseCents)}",
                    style = MaterialTheme.typography.labelMedium, color = Semantic.expense())
                if (group.incomeCents > 0) Text("收入 ¥${Money.formatYuan(group.incomeCents)}",
                    style = MaterialTheme.typography.labelMedium, color = Semantic.income())
            }
        }
    }
}

/** 单条流水 */
@Composable
private fun RecordRow(
    row: com.adrian.qingjizhang.data.local.RecordWithCategory,
    onEdit: (RecordEntity, String) -> Unit,
    onDeleteRequest: (RecordEntity) -> Unit,
) {
    val r = row.record
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clickable { onEdit(r, row.categoryName) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 分类名 + 创建时间（时:分，小灰字）——用户选定样式「餐饮 14:23」
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.categoryName, style = MaterialTheme.typography.bodyLarge)
                    // 「自动」小标（V1.3）：该笔由周期规则自动生成。
                    // 规则删除后依然显示 —— 这是真实发生过的账，用户明确要求保留标记。
                    if (r.recurringRuleId != null) {
                        Spacer(Modifier.width(6.dp))
                        AutoBadge()
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatTimeOfDay(r.createdAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (r.note.isNotEmpty()) {
                    Text(r.note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Text(
                (if (r.isExpense) "-" else "+") + "¥" + Money.formatYuan(r.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (r.isExpense) Semantic.expense() else Semantic.income(),
            )
            IconButton(onClick = { onDeleteRequest(r) }, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * 「自动」小标：标识该笔由周期规则自动生成（V1.3）。
 * 规则被删除后依然显示 —— 历史事实，用户明确要求保留。
 */
@Composable
private fun AutoBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            "自动",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
