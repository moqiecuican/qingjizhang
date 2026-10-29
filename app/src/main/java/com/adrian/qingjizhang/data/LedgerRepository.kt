package com.adrian.qingjizhang.data

import android.content.Context
import androidx.room.withTransaction
import com.adrian.qingjizhang.core.BackupJson
import com.adrian.qingjizhang.core.Catchup
import com.adrian.qingjizhang.core.MonthStats
import com.adrian.qingjizhang.data.local.AppDatabase
import com.adrian.qingjizhang.data.local.BudgetEntity
import com.adrian.qingjizhang.data.local.CategoryBreakdownRow
import com.adrian.qingjizhang.data.local.CategoryEntity
import com.adrian.qingjizhang.data.local.DayTotalRow
import com.adrian.qingjizhang.data.local.RecordEntity
import com.adrian.qingjizhang.data.local.RecordWithCategory
import com.adrian.qingjizhang.data.local.RecurringRuleEntity
import com.adrian.qingjizhang.data.local.TodayExpenseRow
import com.adrian.qingjizhang.data.local.toBudgetKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * 仓库：UI 层只认这个类，不碰 DAO（方便将来测试与 KMP 迁移）。
 *
 * [appContext] 仅用于刷新桌面小组件（V1.4 §0.3：刷新调用统一收在 Repository 层，
 * 分散在 ViewModel 接线必漏）。单测/仪器化测试可传 null → 跳过刷新。
 */
class LedgerRepository(private val db: AppDatabase, private val appContext: Context? = null) {

    /** 进程级刷新 scope：脱离 ViewModel 生命周期，保证"保存即刷"不被 Activity 结束打断 */
    private val refreshScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private val recordDao = db.recordDao()
    private val categoryDao = db.categoryDao()
    private val recurringDao = db.recurringRuleDao()

    // ---------- 流水 ----------

    fun observeRecords(fromDay: Long, toDay: Long): Flow<List<RecordWithCategory>> =
        recordDao.observeRecordsWithCategory(fromDay, toDay)

    fun observeMonthStats(fromDay: Long, toDay: Long): Flow<MonthStats> =
        recordDao.observeMonthStats(fromDay, toDay)

    suspend fun addRecord(record: RecordEntity): Long = recordDao.insert(record).also { notifyWidgets() }

    suspend fun updateRecord(record: RecordEntity) {
        recordDao.update(record)
        notifyWidgets()
    }

    suspend fun deleteRecord(record: RecordEntity) {
        recordDao.delete(record)
        notifyWidgets()
    }

    suspend fun getAllRecords(): List<RecordWithCategory> = recordDao.getAllWithCategory()

    // ---------- 分类 ----------

    fun observeCategories(isExpense: Boolean): Flow<List<CategoryEntity>> =
        categoryDao.observeCategories(isExpense)

    /** 新增分类。同名返回 false（UI 提示"分类已存在"） */
    suspend fun addCategory(name: String, isExpense: Boolean): Boolean {
        if (categoryDao.countByName(isExpense, name) > 0) return false
        val nextSort = (categoryDao.maxSortOrder(isExpense) ?: -1) + 1
        categoryDao.insert(CategoryEntity(name = name, isExpense = isExpense, sortOrder = nextSort))
        return true
    }

    /** 重命名。同名或不存在返回 false */
    suspend fun renameCategory(id: Long, newName: String): Boolean {
        val cat = categoryDao.getById(id) ?: return false
        if (categoryDao.countByName(cat.isExpense, newName) > 0) return false
        categoryDao.update(cat.copy(name = newName))
        return true
    }

    /** 删除。该分类下已有流水则拒绝（返回 false），保护历史数据不悬空 */
    suspend fun deleteCategory(id: Long): Boolean {
        if (recordDao.countForCategory(id) > 0) return false
        categoryDao.getById(id)?.let { categoryDao.delete(it) }
        return true
    }

    // ---------- 预算 ----------

    fun observeBudget(ym: YearMonth): Flow<BudgetEntity?> =
        db.budgetDao().observeBudget(ym.toBudgetKey())

    suspend fun setBudget(ym: YearMonth, amountCents: Long) =
        db.budgetDao().upsert(BudgetEntity(yearMonthKey = ym.toBudgetKey(), amountCents = amountCents))

    suspend fun clearBudget(ym: YearMonth) =
        db.budgetDao().delete(ym.toBudgetKey())

    suspend fun getAllBudgets(): List<BudgetEntity> = db.budgetDao().getAll()

    // ---------- 统计聚合（V1.2 T1.1）----------

    fun observeDayTotals(fromDay: Long, toDay: Long): Flow<List<DayTotalRow>> =
        recordDao.observeDayTotals(fromDay, toDay)

    fun observeCategoryBreakdown(fromDay: Long, toDay: Long): Flow<List<CategoryBreakdownRow>> =
        recordDao.observeCategoryBreakdown(fromDay, toDay)

    // ---------- 桌面小组件（V1.4 T4.2）----------

    /** 今日支出聚合（widget 数据源，一条 SQL） */
    suspend fun loadTodayExpense(today: Long): TodayExpenseRow = recordDao.todayExpense(today)

    /**
     * 刷新桌面小组件（§0.3 五个写入口的统一出口：增/改/删/补录/恢复）。
     * 注意：「5」是写入口的数量，不是刷新机制的数量——另有 3 条非写入刷新
     * （零点 Worker、30min 系统兜底、onAppWidgetOptionsChanged/onRestored）。
     * appContext 为 null（单测/仪器化）时直接跳过；刷新失败不影响业务写操作。
     */
    private fun notifyWidgets() {
        val ctx = appContext ?: return
        // 用应用级 scope，不用调用方协程：quick_add 保存后 Activity 立刻 finish →
        // viewModelScope 被取消，刷新会被中途掐掉（V1.4 真机踩坑）
        // (a) 立即尝试（正常情况下几百毫秒内更新）
        refreshScope.launch {
            // 用 refreshSafely 而不是 runCatching：后者会吞掉协程取消（审查 U2 同类问题）
            com.adrian.qingjizhang.ui.widget.WidgetRefresh.refreshSafely(ctx)
        }
        // (b) WorkManager 兜底：进程被回收也能完成（quick_add 保存后 Activity 立即退后台的场景）
        runCatching { com.adrian.qingjizhang.ui.widget.enqueueWidgetRefresh(ctx) }
    }

    // ---------- 周期记账（V1.3 T2.2）----------

    fun observeRecurringRules(): Flow<List<RecurringRuleEntity>> = recurringDao.observeAll()

    suspend fun addRecurringRule(rule: RecurringRuleEntity): Long = recurringDao.insert(rule)

    suspend fun updateRecurringRule(rule: RecurringRuleEntity) = recurringDao.update(rule)

    suspend fun deleteRecurringRule(rule: RecurringRuleEntity) = recurringDao.delete(rule)

    /**
     * 打开时补录：把启用规则 `(游标, 今天]` 内到期的账补齐入库，随后把游标推进到今天。
     *
     * 安全性：
     * - 单事务：插入与游标推进同生共死，中途失败整体回滚
     * - 插入走 IGNORE：同规则同日已有账 → 跳过（数据库层幂等，重复打开不会重复入账）
     * - 游标一律推进到今天：已覆盖区间不再回看 → 用户删掉的自动账不会复活
     *
     * @return 本次真正新生成的笔数（冲突跳过的不计）
     */
    suspend fun catchUpRecurring(today: Long, nowMillis: Long = System.currentTimeMillis()): Int {
        val active = recurringDao.getActive()
        if (active.isEmpty()) return 0
        val plan = Catchup.plan(active, today)
        val inserted = db.withTransaction {
            var n = 0
            for (entry in plan) {
                if (recordDao.insertIgnoringConflicts(Catchup.toRecord(entry, nowMillis)) > 0) n++
            }
            active.forEach { recurringDao.advanceCursor(it.id, today) }
            n
        }
        if (inserted > 0) notifyWidgets() // 钩子④：周期补录后刷新
        return inserted
    }

    // ---------- JSON 备份/恢复（V1.1 T4.2）----------

    /** 导出全量快照 JSON（四表原样，含 id/clientUid；V1.3 起周期规则随备份走） */
    suspend fun exportBackupJson(): String =
        BackupJson.build(
            categories = categoryDao.getAll(),
            records = recordDao.getAllEntities(),
            budgets = db.budgetDao().getAll(),
            recurringRules = recurringDao.getAll(),
        )

    /**
     * 从 JSON 恢复：单事务「清空四表（records/categories/budgets/recurring_rules）→ 按备份原样灌回（保留 id）」。
     * 事务保证中途失败自动回滚，不会出现"半新半旧"的库。
     * 先 parse 校验（格式/版本不对根本不进事务）。
     */
    suspend fun restoreFromJson(text: String) {
        val snapshot = BackupJson.parse(text) // 校验失败抛 IllegalArgumentException
        db.withTransaction {
            recordDao.deleteAll()
            categoryDao.deleteAll()
            db.budgetDao().deleteAll()
            recurringDao.deleteAll()
            // 顺序：先分类 → 再规则 → 后流水（引用完整性；旧版 v1 备份无规则字段则灌空列表）
            categoryDao.insertAllPreservingId(snapshot.categories)
            recurringDao.insertAllPreservingId(snapshot.recurringRules)
            recordDao.insertAllPreservingId(snapshot.records)
            db.budgetDao().insertAll(snapshot.budgets)
        }
        notifyWidgets() // 钩子⑤：恢复备份后刷新
    }
}
