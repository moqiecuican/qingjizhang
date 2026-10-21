package com.adrian.qingjizhang.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrian.qingjizhang.core.CategorySlice
import com.adrian.qingjizhang.core.Granularity
import com.adrian.qingjizhang.core.Money
import com.adrian.qingjizhang.theme.ChartPalette
import com.adrian.qingjizhang.ui.charts.BarChart
import com.adrian.qingjizhang.ui.charts.DonutChart
import java.time.LocalDate

/**
 * 统计页（V1.2）：窗口控件（粒度 周/月/年 + ‹› 翻页 + 自定义区间）→
 * 环形占比图（<5% 合并「其他」，点其他出明细）→ 近 N 桶收支柱状图（点柱跳流水）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: LedgerViewModel,
    wide: Boolean,
    contentPadding: PaddingValues,
    onJumpToLedger: (fromDay: Long, toDay: Long) -> Unit,
) {
    val state by viewModel.statsState.collectAsStateWithLifecycle()
    val customRange by viewModel.customRange.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()

    // 「其他」明细气泡锚点（null=关闭）；B 方案降级用 expandedOther 列表
    var popupAnchor by remember { mutableStateOf<Offset?>(null) }
    var expandedOther by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = if (wide) 600.dp else 640.dp).fillMaxSize().padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            // ---- 窗口控件 ----
            WindowControls(viewModel, state, customRange != null)
            Spacer(Modifier.height(12.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // ---- 环形图 + 图例 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("支出构成", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (state.donut.totalCents == 0L) {
                            Text("该窗口没有支出。", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Box {
                                DonutChart(
                                    slices = state.donut.mainSlices,
                                    otherSlice = com.adrian.qingjizhang.core.ChartModels.otherSlice(state.donut),
                                    centerText = "¥" + Money.formatYuan(state.donut.totalCents),
                                    onSectorTap = { index, tap ->
                                        if (index == -1) popupAnchor = tap else popupAnchor = null
                                    },
                                )
                                // 气泡：锚定触点，屏幕内 clamping 由 Popup 的 offset 粗控
                                popupAnchor?.let { anchor ->
                                    val other = com.adrian.qingjizhang.core.ChartModels.otherSlice(state.donut)
                                    if (other != null) {
                                        Popup(
                                            onDismissRequest = { popupAnchor = null },
                                            properties = PopupProperties(focusable = false),
                                            offset = androidx.compose.ui.unit.IntOffset(
                                                (anchor.x - 140).toInt().coerceAtLeast(8),
                                                (anchor.y + 20).toInt(),
                                            ),
                                        ) {
                                            OtherDetailsCard(state.donut.otherItems, onClose = { popupAnchor = null })
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            // 图例：主类目 + 其他行（其他行可点 = 更大触摸目标）
                            state.donut.mainSlices.forEachIndexed { i, s ->
                                LegendRow(
                                    color = ChartPalette.color(i, dark),
                                    name = s.name,
                                    cents = s.cents,
                                    ratio = s.ratio,
                                )
                            }
                            com.adrian.qingjizhang.core.ChartModels.otherSlice(state.donut)?.let { other ->
                                LegendRow(
                                    color = ChartPalette.other(dark),
                                    name = "其他（${state.donut.otherItems.size} 项）· 点看明细",
                                    cents = other.cents,
                                    ratio = other.ratio,
                                    onClick = { expandedOther = !expandedOther; popupAnchor = null },
                                )
                                // B 方案兜底：展开小字列表（与气泡内容同源，两者不互斥但通常二选一用）
                                if (expandedOther) {
                                    Column(Modifier.padding(start = 20.dp, top = 4.dp)) {
                                        state.donut.otherItems.forEach { item ->
                                            Text(
                                                "${item.name}  ¥${Money.formatYuan(item.cents)}  ${(item.ratio * 100).toInt().coerceAtLeast(1)}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ---- 柱状图 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("收支趋势（${state.granularity.label}）", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            if (state.barsCapped) {
                                Text("仅显示最近 12 个周期", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (state.bars.all { it.incomeCents == 0L && it.expenseCents == 0L }) {
                            Text("该范围内没有数据。", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            BarChart(bars = state.bars, onBarTap = { bar -> onJumpToLedger(bar.fromDay, bar.toDay) })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 窗口控件：粒度分段 + ‹ 窗口标签 › + 自定义区间 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowControls(viewModel: LedgerViewModel, state: StatsUiState, isCustom: Boolean) {
    var rangePickStage by remember { mutableStateOf<Int>(0) } // 0=未选 1=选起点中 2=选终点中
    var tempFrom by remember { mutableStateOf<Long?>(null) }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Granularity.entries.forEachIndexed { i, g ->
            SegmentedButton(
                selected = state.granularity == g && !isCustom,
                onClick = { viewModel.setGranularity(g) },
                shape = SegmentedButtonDefaults.itemShape(i, Granularity.entries.size),
            ) { Text(g.label) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { viewModel.shiftStatsWindow(-1) }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一${state.granularity.label}")
        }
        Text(
            if (isCustom) "${state.windowLabel}（自定义）" else state.windowLabel,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { viewModel.shiftStatsWindow(1) }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一${state.granularity.label}")
        }
        TextButton(onClick = {
            if (isCustom) viewModel.clearStatsCustomRange()
            else rangePickStage = 1
        }) {
            Text(if (isCustom) "清除" else "自定义")
        }
    }
    // 自定义区间：两段式日期选择（先起点后终点），state 提到 dialog 外便于 confirm 读取
    // 文案明确「起始/截止」——用户反馈：无提示时不知道要点两次、也不知道每次选的是什么
    if (rangePickStage == 1) {
        val dpState = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { rangePickStage = 0 },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        tempFrom = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                        rangePickStage = 2
                    }
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { rangePickStage = 0 }) { Text("取消") } },
        ) {
            androidx.compose.material3.DatePicker(
                state = dpState,
                title = { Text("选择起始日期（第 1 / 2 步）", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            )
        }
    }
    if (rangePickStage == 2) {
        val initMillis = tempFrom?.let {
            java.time.LocalDate.ofEpochDay(it).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val dpState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initMillis)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { rangePickStage = 0; tempFrom = null },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        val to = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                        tempFrom?.let { f -> if (f <= to) viewModel.setStatsCustomRange(f, to) }
                    }
                    rangePickStage = 0
                    tempFrom = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { rangePickStage = 0; tempFrom = null }) { Text("取消") } },
        ) {
            androidx.compose.material3.DatePicker(
                state = dpState,
                title = { Text("选择截止日期（第 2 / 2 步）", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            )
        }
    }
    if (isCustom) {
        Text("自定义区间生效中：点「清除」回到粒度窗口", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 「其他」明细气泡卡片 */
@Composable
private fun OtherDetailsCard(items: List<CategorySlice>, onClose: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 260.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("其他 · ${items.size} 项", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("关闭") }
            }
            items.forEach { item ->
                Text(
                    "${item.name}  ¥${Money.formatYuan(item.cents)}  ${(item.ratio * 100).toInt().coerceAtLeast(1)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 图例行：色块 + 名称 + 金额 + 百分比 */
@Composable
private fun LegendRow(color: Color, name: String, cents: Long, ratio: Float, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = color, modifier = Modifier.width(12.dp).height(12.dp), shape = RoundedCornerShape(3.dp)) {}
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("¥${Money.formatYuan(cents)}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text("${(ratio * 100).toInt().coerceAtLeast(1)}%", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
    }
}
