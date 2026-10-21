package com.adrian.qingjizhang.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adrian.qingjizhang.data.LedgerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 周期补录真库测试（V1.3 T2.5）。
 *
 * 用内存 Room 库 + 真仓储跑，验证补录的三条硬保证：
 * 1. **幂等**：游标推进后不重复；即使游标被回退，唯一索引也拦住重复入账（数据库层保险）
 * 2. **备注复制**：生成账的 note == 规则 note（用户特别要求的验收项）
 * 3. **暂停不生成**、**删规则后历史账仍带自动标**、**备份恢复规则与自动标完整回来**
 */
@RunWith(AndroidJUnit4::class)
class RecurringCatchupTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        kotlinx.coroutines.runBlocking { db.seedPresetCategories() }
        repo = LedgerRepository(db)
    }

    @After
    fun teardown() = db.close()

    private fun d(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    private fun rule(
        dayOfMonth: Int = 5,
        start: String = "2026-01-01",
        cursor: String = "2026-01-01",
        note: String = "规则备注",
        active: Boolean = true,
    ) = RecurringRuleEntity(
        name = "房租",
        amountCents = 350_000,
        isExpense = true,
        categoryId = 1,
        period = RecurrencePeriod.MONTHLY,
        dayOfMonth = dayOfMonth,
        startDateEpochDay = d(start),
        lastOccurredEpochDay = d(cursor),
        note = note,
        isActive = active,
    )

    @Test
    fun catchUp_generatesDueEntry_withRuleNote_andAutoMark() = runTest {
        val ruleId = repo.addRecurringRule(rule(cursor = "2026-03-05"))
        val n = repo.catchUpRecurring(today = d("2026-04-05"))
        assertEquals(1, n)
        val rec = db.recordDao().getAllEntities().single()
        assertEquals(350_000L, rec.amountCents)
        assertEquals(d("2026-04-05"), rec.dateEpochDay)
        assertEquals("规则备注", rec.note) // 规则备注自动写入生成账
        assertEquals(ruleId, rec.recurringRuleId) // 自动标记
    }

    @Test
    fun catchUp_isIdempotent_secondRunInsertsNothing() = runTest {
        repo.addRecurringRule(rule(cursor = "2026-03-05"))
        assertEquals(1, repo.catchUpRecurring(today = d("2026-04-05")))
        // 第二次（等价于"再打开一次 app"）：游标已推进到今天 → 无新账
        assertEquals(0, repo.catchUpRecurring(today = d("2026-04-05")))
        assertEquals(1, db.recordDao().getAllEntities().size)
    }

    /** 幂等的"硬"路径：把游标人为回退，唯一索引仍然拦住重复入账 */
    @Test
    fun catchUp_uniqueIndexBlocksDuplicate_evenIfCursorRewound() = runTest {
        val ruleId = repo.addRecurringRule(rule(cursor = "2026-03-05"))
        assertEquals(1, repo.catchUpRecurring(today = d("2026-04-05")))
        // 模拟异常：游标被退回旧位置
        db.recurringRuleDao().advanceCursor(ruleId, d("2026-03-05"))
        assertEquals(0, repo.catchUpRecurring(today = d("2026-04-05")))
        assertEquals(1, db.recordDao().getAllEntities().size)
    }

    @Test
    fun catchUp_skipsPausedRules() = runTest {
        repo.addRecurringRule(rule(cursor = "2026-03-05", active = false))
        assertEquals(0, repo.catchUpRecurring(today = d("2026-04-05")))
        assertEquals(0, db.recordDao().getAllEntities().size)
    }

    @Test
    fun catchUp_multipleMonths_generatesAll() = runTest {
        repo.addRecurringRule(rule(cursor = "2026-01-05"))
        assertEquals(3, repo.catchUpRecurring(today = d("2026-04-05")))
        assertEquals(
            listOf(d("2026-02-05"), d("2026-03-05"), d("2026-04-05")),
            db.recordDao().getAllEntities().map { it.dateEpochDay }.sorted(),
        )
    }

    /** 用户拍板：删除规则后，历史自动账保留且「自动」标记不消失 */
    @Test
    fun deletedRule_keepsAutoMarkOnHistory() = runTest {
        val ruleId = repo.addRecurringRule(rule(cursor = "2026-03-05"))
        repo.catchUpRecurring(today = d("2026-04-05"))
        repo.deleteRecurringRule(db.recurringRuleDao().getById(ruleId)!!)
        assertEquals(0, db.recurringRuleDao().getAll().size)
        val rec = db.recordDao().getAllEntities().single()
        assertEquals(ruleId, rec.recurringRuleId)
    }

    /** 唯一索引只管自动账：同日多笔手动账（recurringRuleId = NULL）必须允许 */
    @Test
    fun manualRecords_sameDayAllowed() = runTest {
        val cat = db.categoryDao().observeCategories(true).first().first().id
        db.recordDao().insert(RecordEntity(amountCents = 100, isExpense = true, categoryId = cat, dateEpochDay = d("2026-04-05")))
        db.recordDao().insert(RecordEntity(amountCents = 200, isExpense = true, categoryId = cat, dateEpochDay = d("2026-04-05")))
        assertEquals(2, db.recordDao().getAllEntities().size)
    }

    /** T2.6：备份→恢复后规则与自动标记完整回来（修 V1.3 疏漏） */
    @Test
    fun backupRestore_keepsRulesAndAutoMark() = runTest {
        val ruleId = repo.addRecurringRule(rule(cursor = "2026-03-05"))
        repo.catchUpRecurring(today = d("2026-04-05"))
        val json = repo.exportBackupJson()

        // 模拟换机：清空后从备份恢复
        db.recurringRuleDao().deleteAll()
        db.recordDao().deleteAll()
        repo.restoreFromJson(json)

        val restored = db.recurringRuleDao().getAll().single()
        assertEquals(ruleId, restored.id)
        assertEquals("规则备注", restored.note)
        assertEquals(d("2026-04-05"), restored.lastOccurredEpochDay)
        val rec = db.recordDao().getAllEntities().single()
        assertEquals(ruleId, rec.recurringRuleId)
        assertEquals("规则备注", rec.note)
    }
}
