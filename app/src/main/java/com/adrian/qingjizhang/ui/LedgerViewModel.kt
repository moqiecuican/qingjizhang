package com.adrian.qingjizhang.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adrian.qingjizhang.core.BackupJson
import com.adrian.qingjizhang.core.Catchup
import com.adrian.qingjizhang.core.ChartModels
import com.adrian.qingjizhang.core.CsvBuilder
import com.adrian.qingjizhang.core.DonutData
import com.adrian.qingjizhang.core.Granularity
import com.adrian.qingjizhang.core.MonthStats
import com.adrian.qingjizhang.core.Money
import com.adrian.qingjizhang.core.RecordFilter
import com.adrian.qingjizhang.core.RecordFiltering
import com.adrian.qingjizhang.core.Recurrence
import com.adrian.qingjizhang.core.TimeBar
import com.adrian.qingjizhang.core.rangeEpochDays
import com.adrian.qingjizhang.data.LedgerRepository
import com.adrian.qingjizhang.data.local.AppDatabase
import com.adrian.qingjizhang.data.local.seedPresetCategories
import com.adrian.qingjizhang.data.local.CategoryEntity
import com.adrian.qingjizhang.data.local.RecordEntity
import com.adrian.qingjizhang.data.local.RecordWithCategory
import com.adrian.qingjizhang.data.local.RecurrencePeriod
import com.adrian.qingjizhang.data.local.RecurringRuleEntity
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 统计页状态（V1.2） */
data class StatsUiState(
    val granularity: Granularity = Granularity.Month,
    val windowLabel: String = "",
    val isCustom: Boolean = false,
    val donut: DonutData = DonutData(emptyList(), emptyList(), 0),
    val bars: List<TimeBar> = emptyList(),
    val barsCapped: Boolean = false,
    /** 柱状图原始按天聚合（跳转流水页时挑桶内金额最大日所在月用） */
    val dayTotals: List<com.adrian.qingjizhang.data.local.DayTotalRow> = emptyList(),
    val isLoading: Boolean = true,
)

/** 对话框模式：null=关闭；Add=记一笔；Edit=编辑已有流水 */
sealed interface DialogMode {
    data object Add : DialogMode
    data class Edit(val record: RecordEntity, val categoryName: String) : DialogMode
    data class Budget(val currentCents: Long?) : DialogMode
}

/** 流水列表的日分组（ViewModel 组装好，UI 直接渲染） */
data class DayGroup(
    val dateEpochDay: Long,
    val incomeCents: Long,
    val expenseCents: Long,
    val items: List<RecordWithCategory>,
)

/** 月视图状态 */
data class MonthUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val stats: MonthStats = MonthStats(),
    val groups: List<DayGroup> = emptyList(),
    /** null = 本月未设预算 */
    val budgetCents: Long? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repo = LedgerRepository(db, app.applicationContext)

    init {
        // 预置分类（幂等，IO 线程）；分类 Flow 会自动收到新数据刷新 UI
        viewModelScope.launch { db.seedPresetCategories() }
    }

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** 搜索与筛选条件（全空=不过滤；monthState 依赖它，须先声明） */
    private val _filter = MutableStateFlow(RecordFilter())
    val filter: StateFlow<RecordFilter> = _filter.asStateFlow()

    /** 冷启动只弹一次记账框的开关（VM 在旋屏等配置变更中存活，天然满足"仅冷启动"语义） */
    private val _coldStartShown = MutableStateFlow(false)
    val coldStartShown: StateFlow<Boolean> = _coldStartShown.asStateFlow()

    private val _dialogMode = MutableStateFlow<DialogMode?>(null)
    val dialogMode: StateFlow<DialogMode?> = _dialogMode.asStateFlow()

    /** quick_add 模式（V1.4）：对话框关闭后退回桌面 */
    private val _quickAddActive = MutableStateFlow(false)
    val quickAddActive: StateFlow<Boolean> = _quickAddActive.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** 一次性提示消息（Snackbar） */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: Flow<String> = _messages.asSharedFlow()

    /** 上次补录的 epochDay（0=从未）。进程内有效；跨天后自然再触发一次。 */
    private var lastCatchUpDay: Long = 0L

    /**
     * 打开 / 从后台回到前台时补录（V1.3 T2.2，V1.4 修复触发时机）。
     *
     * **原实现只在 init 里补一次**：进程不退出（`moveTaskToBack` 让进程常驻）时跨天不会补账，
     * 流水页与小组件会一直缺当天的自动账；更糟的是小组件显示的是合法空态（¥0.00 +「今日还没有记账」），
     * 用户看不出这是错的。现在由 `AppRoot` 监听 `ON_RESUME` 调用本方法。
     *
     * 同一天只跑一次（`lastCatchUpDay` 判重），避免每次切前台都查库开事务；
     * 幂等本身由唯一索引 `(recurringRuleId, dateEpochDay)` + `IGNORE` 兜住，重复调用零风险。
     */
    fun onAppResumed() {
        // 零点任务续期与补录【解耦】（审查 U3）：每次回到前台都确保任务在位。
        // 策略必须用 KEEP 而不是默认的 REPLACE：这里会被高频调用（每次 ON_RESUME），
        // REPLACE 会取消正在跑的零点 Worker（极端时序下中断零点刷新）；
        // KEEP 的语义是「已有未完成的同名 work 就不动」——
        //   · 已有排队任务 → 不动（避免堆积，本函数是幂等的）
        //   · 零点 Worker 正在跑 → 不动（它跑完会自己 APPEND_OR_REPLACE 续下一天）
        //   · 任务丢了（被系统清理/首次安装）→ 自动补上
        runCatching {
            com.adrian.qingjizhang.ui.widget.scheduleMidnightRefresh(
                getApplication<Application>().applicationContext,
                androidx.work.ExistingWorkPolicy.KEEP,
            )
        }

        val today = LocalDate.now().toEpochDay()
        if (lastCatchUpDay == today) return
        lastCatchUpDay = today
        viewModelScope.launch {
            // 审查 U2 补：不能用 runCatching —— 它 catch Throwable 会吞掉 CancellationException，
            // 导致协程取消信号不透传（结构化并发失效），且会把"取消"误记成"失败"日志。
            val result = try {
                Result.success(repo.catchUpRecurring(today))
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e   // 取消必须原样抛回
            } catch (t: Throwable) {
                Result.failure(t)
            }
            // 失败要回滚判重：补录是幂等的，下次 ON_RESUME 重试安全；
            // 若失败也占着 today，当天就再也不会重试了。
            result.onFailure { t ->
                if (lastCatchUpDay == today) lastCatchUpDay = 0L
                android.util.Log.w("LedgerViewModel", "catchUpRecurring failed", t)
            }
            result.onSuccess { generated ->
                if (generated > 0) _messages.tryEmit("已自动补录 $generated 笔周期账")
            }
        }
    }

    /** 冷启动也走同一条路径（与 ON_RESUME 判重配合，不会重复补） */
    init {
        onAppResumed()
    }

    val expenseCategories: StateFlow<List<CategoryEntity>> =
        repo.observeCategories(true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val incomeCategories: StateFlow<List<CategoryEntity>> =
        repo.observeCategories(false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthState: StateFlow<MonthUiState> =
        combine(_month, _filter) { ym, f -> ym to f }
            .flatMapLatest { (ym, f) ->
                val (from, to) = ym.rangeEpochDays()
                combine(
                    repo.observeMonthStats(from, to),
                    repo.observeRecords(from, to),
                    repo.observeBudget(ym),
                ) { stats, records, budget ->
                    // 筛选生效：分组与汇总都走过滤后的行（内存过滤，contains 语义）
                    val rows = RecordFiltering.apply(records, f)
                    val effStats = if (f.isActive) RecordFiltering.statsOf(rows) else stats
                    MonthUiState(
                        yearMonth = ym,
                        stats = effStats,
                        groups = rows.groupBy { it.record.dateEpochDay }
                            .map { (day, dayRows) ->
                                DayGroup(
                                    dateEpochDay = day,
                                    incomeCents = dayRows.filter { !it.record.isExpense }.sumOf { it.record.amountCents },
                                    expenseCents = dayRows.filter { it.record.isExpense }.sumOf { it.record.amountCents },
                                    items = dayRows,
                                )
                            }
                            .sortedByDescending { it.dateEpochDay },
                        budgetCents = budget?.amountCents,
                        isLoading = false,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthUiState())

    // ---------- 月份切换 ----------

    fun shiftMonth(deltaMonths: Int) {
        _month.value = _month.value.plusMonths(deltaMonths.toLong())
    }

    fun shiftMonthToday() { _month.value = YearMonth.now() }

    // ---------- 记一笔 / 编辑 ----------

    fun openAddDialog() { _dialogMode.value = DialogMode.Add }

    /**
     * 小组件「记一笔」入口（V1.4 §0.1）：直达记账框，并在对话框关闭后 finish 退桌面。
     * 同时 markColdStartShown()，避免与冷启动自动弹窗"双弹"（§0.1 冲突验收点）。
     */
    fun onQuickAdd() {
        markColdStartShown()
        if (_dialogMode.value == null) _dialogMode.value = DialogMode.Add
        _quickAddActive.value = true
    }

    /** Activity 完成退桌面后清标记 */
    fun consumeQuickAdd() { _quickAddActive.value = false }

    fun openEditDialog(record: RecordEntity, categoryName: String) {
        _dialogMode.value = DialogMode.Edit(record, categoryName)
    }

    fun closeDialog() {
        _dialogMode.value = null
        _saving.value = false
    }

    fun markColdStartShown() { _coldStartShown.value = true }

    /**
     * 保存（新增或编辑）。amountInput 是用户输入的「元」字符串。
     * 校验失败发 message 并返回，不关对话框；成功后切到对应月份并回调 onSaved。
     */
    fun saveRecord(
        amountInput: String,
        isExpense: Boolean,
        categoryId: Long?,
        date: LocalDate,
        note: String,
        editing: RecordEntity?,
        onSaved: () -> Unit = {},
    ) {
        if (_saving.value) return // 防连点
        val cents = Money.parseToCents(amountInput)
        if (cents == null) {
            _messages.tryEmit("请输入有效金额（0～999999.99 元，最多两位小数）")
            return
        }
        val catId = categoryId
        if (catId == null) { _messages.tryEmit("请选择分类"); return }
        _saving.value = true
        viewModelScope.launch {
            try {
                if (editing == null) {
                    repo.addRecord(
                        RecordEntity(
                            amountCents = cents,
                            isExpense = isExpense,
                            categoryId = catId,
                            dateEpochDay = date.toEpochDay(),
                            note = note.trim(),
                        )
                    )
                } else {
                    repo.updateRecord(
                        editing.copy(
                            amountCents = cents,
                            isExpense = isExpense,
                            categoryId = catId,
                            dateEpochDay = date.toEpochDay(),
                            note = note.trim(),
                            updatedAtMillis = System.currentTimeMillis(), // 合并地基：后写的赢
                        )
                    )
                }
                _month.value = YearMonth.from(date) // 保证记完立刻可见
                closeDialog()
                onSaved()
            } finally {
                _saving.value = false
            }
        }
    }

    // ---------- 预算 ----------

    fun openBudgetDialog(currentCents: Long?) {
        _dialogMode.value = DialogMode.Budget(currentCents)
    }

    /** 设置当月支出总预算。amountInput 为「元」字符串；空串=清除预算 */
    fun saveBudget(amountInput: String) {
        val ym = _month.value
        val input = amountInput.trim()
        if (input.isEmpty()) {
            viewModelScope.launch {
                repo.clearBudget(ym)
                _messages.tryEmit("已清除 ${ym.monthValue} 月预算")
            }
            closeDialog()
            return
        }
        val cents = Money.parseToCents(input)
        if (cents == null || cents <= 0) {
            _messages.tryEmit("请输入有效预算金额（大于 0，最多两位小数）")
            return
        }
        viewModelScope.launch {
            repo.setBudget(ym, cents)
            _messages.tryEmit("已设置 ${ym.monthValue} 月预算 ¥${Money.formatYuan(cents)}")
            closeDialog()
        }
    }

    // ---------- 删除流水 ----------

    fun deleteRecord(record: RecordEntity) {
        viewModelScope.launch {
            repo.deleteRecord(record)
            _messages.tryEmit("已删除")
        }
    }

    // ---------- 分类管理 ----------

    fun addCategory(name: String, isExpense: Boolean) {
        val n = name.trim()
        if (n.isEmpty()) { _messages.tryEmit("分类名不能为空"); return }
        viewModelScope.launch {
            if (!repo.addCategory(n, isExpense)) _messages.tryEmit("已有同名分类：$n")
        }
    }

    fun renameCategory(id: Long, newName: String) {
        val n = newName.trim()
        if (n.isEmpty()) { _messages.tryEmit("分类名不能为空"); return }
        viewModelScope.launch {
            if (!repo.renameCategory(id, n)) _messages.tryEmit("改名失败（重名或分类不存在）")
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            if (!repo.deleteCategory(id)) _messages.tryEmit("该分类下已有流水，不能删除")
        }
    }

    // ---------- 搜索与筛选（V1.2 Phase 3）----------

    fun setFilter(f: RecordFilter) { _filter.value = f }
    fun clearFilter() { _filter.value = RecordFilter() }

    // ---------- 统计（V1.2 T1.3）----------

    private val _granularity = MutableStateFlow(Granularity.Month)
    val granularity: StateFlow<Granularity> = _granularity.asStateFlow()

    /** 当前窗口锚点（桶起点 epochDay）；自定义区间存在时仅作翻页基准 */
    private val _statsAnchor = MutableStateFlow(
        ChartModels.bucketStart(LocalDate.now().toEpochDay(), Granularity.Month)
    )

    /** 自定义起止区间（非 null 时覆盖粒度窗口） */
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customRange: StateFlow<Pair<Long, Long>?> = _customRange.asStateFlow()

    val statsState: StateFlow<StatsUiState> =
        combine(_granularity, _statsAnchor, _customRange) { g, anchor, custom -> Triple(g, anchor, custom) }
            .flatMapLatest { (g, anchor, custom) ->
                val (wFrom, wTo) = custom ?: ChartModels.bucketRange(anchor, g)
                // 柱状图取含当前窗口在内的最近 MAX_BARS 个桶
                val barsFrom = ChartModels.shiftBucketStart(
                    custom?.let { ChartModels.bucketStart(it.first, g) } ?: anchor, g, -(ChartModels.MAX_BARS - 1)
                )
                combine(
                    repo.observeDayTotals(barsFrom, wTo),
                    repo.observeCategoryBreakdown(wFrom, wTo),
                ) { dayTotals, breakdown ->
                    val allBars = ChartModels.bucketize(dayTotals, g)
                    val capped = ChartModels.capBars(allBars)
                    StatsUiState(
                        granularity = g,
                        windowLabel = custom?.let { (f, t) ->
                            val df = LocalDate.ofEpochDay(f); val dt = LocalDate.ofEpochDay(t)
                            "${df.monthValue}/${df.dayOfMonth}-${dt.monthValue}/${dt.dayOfMonth}"
                        } ?: ChartModels.bucketLabel(anchor, g),
                        isCustom = custom != null,
                        donut = ChartModels.mergeSmallSlices(breakdown),
                        bars = capped,
                        barsCapped = allBars.size > capped.size,
                        dayTotals = dayTotals,
                        isLoading = false,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun setGranularity(g: Granularity) {
        _granularity.value = g
        _statsAnchor.value = ChartModels.bucketStart(LocalDate.now().toEpochDay(), g)
    }

    fun shiftStatsWindow(delta: Int) {
        _customRange.value = null // 翻页即退出自定义区间
        _statsAnchor.value = ChartModels.shiftBucketStart(_statsAnchor.value, _granularity.value, delta)
    }

    fun setStatsCustomRange(fromDay: Long, toDay: Long) {
        if (fromDay <= toDay) _customRange.value = fromDay to toDay
    }

    fun clearStatsCustomRange() { _customRange.value = null }

    /** 点柱状图跳流水页：落到桶内金额最大那天所在月（跨月周不会跳到空月） */
    fun jumpToLedgerWindow(fromDay: Long, toDay: Long) {
        val totals = statsState.value.dayTotals
        val best = totals.filter { it.dateEpochDay in fromDay..toDay }
            .maxByOrNull { it.incomeCents + it.expenseCents }
        val targetDay = best?.dateEpochDay ?: toDay
        _month.value = YearMonth.from(LocalDate.ofEpochDay(targetDay))
    }

    // ---------- 周期记账（V1.3 T2.2/T2.3）----------

    val recurringRules: StateFlow<List<RecurringRuleEntity>> =
        repo.observeRecurringRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 新建（editing=null）或更新周期规则。
     *
     * 新建规则开始日 = 「今天之后的第一个到期日」（不回溯历史，用户已拍板）；
     * 游标置为 start-1，使**起始日当天**就入账（用户拍板：到期日当天打开就入账）。
     * 保存后立刻跑一次补录（幂等）：若规则今天就到期，账目当场出现。
     */
    fun saveRecurringRule(
        editing: RecurringRuleEntity?,
        name: String,
        amountInput: String,
        isExpense: Boolean,
        categoryId: Long?,
        period: RecurrencePeriod,
        dayOfWeek: Int,
        dayOfMonth: Int,
        monthOfYear: Int,
        note: String,
        isActive: Boolean,
        onSaved: () -> Unit = {},
    ) {
        val n = name.trim()
        if (n.isEmpty()) { _messages.tryEmit("规则名不能为空"); return }
        val cents = Money.parseToCents(amountInput)
        if (cents == null || cents <= 0) {
            _messages.tryEmit("请输入有效金额（0～999999.99 元，最多两位小数）")
            return
        }
        val catId = categoryId ?: run { _messages.tryEmit("请选择分类"); return }
        val paramError = when (period) {
            RecurrencePeriod.WEEKLY -> if (dayOfWeek !in 1..7) "请选择星期几" else null
            RecurrencePeriod.MONTHLY -> if (dayOfMonth !in 1..31) "每月日期需在 1～31 之间" else null
            RecurrencePeriod.YEARLY ->
                if (monthOfYear !in 1..12 || dayOfMonth !in 1..31) "每年需填写有效月份（1～12）与日期（1～31）" else null
        }
        if (paramError != null) { _messages.tryEmit(paramError); return }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (editing == null) {
                val draft = RecurringRuleEntity(
                    name = n,
                    amountCents = cents,
                    isExpense = isExpense,
                    categoryId = catId,
                    period = period,
                    dayOfWeek = dayOfWeek,
                    dayOfMonth = dayOfMonth,
                    monthOfYear = monthOfYear,
                    startDateEpochDay = 0,
                    lastOccurredEpochDay = 0,
                    note = note.trim(),
                    isActive = isActive,
                )
                val today = LocalDate.now().toEpochDay()
                val start = Recurrence.firstOccurrenceOnOrAfter(draft, today) ?: today
                repo.addRecurringRule(
                    draft.copy(
                        startDateEpochDay = start,
                        lastOccurredEpochDay = start - 1,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    )
                )
                _messages.tryEmit("已新增周期规则「$n」（${Catchup.describe(draft)}）")
            } else {
                repo.updateRecurringRule(
                    editing.copy(
                        name = n,
                        amountCents = cents,
                        isExpense = isExpense,
                        categoryId = catId,
                        period = period,
                        dayOfWeek = dayOfWeek,
                        dayOfMonth = dayOfMonth,
                        monthOfYear = monthOfYear,
                        note = note.trim(),
                        isActive = isActive,
                        updatedAtMillis = now,
                    )
                )
                _messages.tryEmit("已更新周期规则「$n」")
            }
            val generated = repo.catchUpRecurring(LocalDate.now().toEpochDay())
            if (generated > 0) _messages.tryEmit("已自动补录 $generated 笔周期账")
            onSaved()
        }
    }

    /** 删除规则：规则移除、不再生成；**历史自动账保留且「自动」标不消失**（用户拍板） */
    fun deleteRecurringRule(rule: RecurringRuleEntity) {
        viewModelScope.launch {
            repo.deleteRecurringRule(rule)
            _messages.tryEmit("已删除规则「${rule.name}」（历史自动账保留）")
        }
    }

    /** 暂停/启用切换：暂停只是不再生成新账，历史账不受影响 */
    fun toggleRecurringRule(rule: RecurringRuleEntity) {
        viewModelScope.launch {
            repo.updateRecurringRule(rule.copy(isActive = !rule.isActive, updatedAtMillis = System.currentTimeMillis()))
            _messages.tryEmit(if (rule.isActive) "已暂停「${rule.name}」" else "已启用「${rule.name}」")
        }
    }

    // ---------- CSV 导出 ----------

    /** 生成 CSV 到 cache 并返回可分享的 content Uri（FileProvider，免存储权限） */
    suspend fun exportCsv(): Uri? = withContext(Dispatchers.IO) {
        try {
            val ctx: Context = getApplication()
            val rows = repo.getAllRecords()
            if (rows.isEmpty()) { _messages.tryEmit("还没有任何流水，无需导出"); return@withContext null }
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, CsvBuilder.fileName())
            file.outputStream().use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM → Excel/WPS 中文不乱码
                out.write(CsvBuilder.build(rows).toByteArray(Charsets.UTF_8))
            }
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        } catch (e: Exception) {
            _messages.tryEmit("导出失败：${e.message}")
            null
        }
    }

    // ---------- JSON 备份/恢复（V1.1 T4.2/T4.3）----------

    /** 导出全量 JSON 备份，返回可分享 Uri */
    suspend fun exportBackup(): Uri? = withContext(Dispatchers.IO) {
        try {
            val ctx: Context = getApplication()
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, BackupJson.fileName())
            file.writeText(repo.exportBackupJson(), Charsets.UTF_8) // JSON 无需 BOM
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        } catch (e: Exception) {
            _messages.tryEmit("备份失败：${e.message}")
            null
        }
    }

    /** 读取用户选择的备份文件内容（SAF Uri → String），失败返回 null */
    suspend fun readBackupUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            _messages.tryEmit("读取备份失败：${e.message}")
            null
        }
    }

    /** 校验备份文本：合法返回快照（供确认框展示统计），非法发提示返回 null */
    fun validateBackup(text: String): BackupJson.File? = try {
        BackupJson.parse(text)
    } catch (e: IllegalArgumentException) {
        _messages.tryEmit(e.message ?: "备份文件无效")
        null
    }

    /** 执行恢复（调用前 UI 必须已弹过强警告确认框） */
    fun restoreBackup(text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.restoreFromJson(text)
                _messages.tryEmit("恢复完成")
                onDone()
            } catch (e: Exception) {
                _messages.tryEmit("恢复失败（数据未变动）：${e.message}")
            }
        }
    }
}
