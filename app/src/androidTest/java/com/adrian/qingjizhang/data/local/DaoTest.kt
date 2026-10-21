package com.adrian.qingjizhang.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adrian.qingjizhang.core.range
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * DAO 仪器测试（在小米平板上跑，计划书 T2.4）。
 * 用内存数据库，每次跑都是干净的建库→预置分类全流程也顺带验证了 PresetCategories。
 */
@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        // Room 2.8 新驱动不再触发旧 SupportSQLite Callback，预置分类改为显式幂等播种
        kotlinx.coroutines.runBlocking { db.seedPresetCategories() }
    }

    @After
    fun teardown() = db.close()

    @Test
    fun presetCategories_insertedOnCreate() = runTest {
        val expense = db.categoryDao().observeCategories(true).first()
        val income = db.categoryDao().observeCategories(false).first()
        assertEquals(8, expense.size)
        assertEquals(4, income.size)
        assertEquals("餐饮", expense.first().name) // 默认分类排第一
        assertEquals("工资", income.first().name)
    }

    @Test
    fun insertAndQueryByMonth() = runTest {
        val food = db.categoryDao().observeCategories(true).first().first { it.name == "餐饮" }.id
        val salary = db.categoryDao().observeCategories(false).first().first { it.name == "工资" }.id
        val sept30 = LocalDate.of(2026, 9, 30).toEpochDay()
        val oct1 = LocalDate.of(2026, 10, 1).toEpochDay()
        db.recordDao().insert(RecordEntity(amountCents = 1234, isExpense = true, categoryId = food, dateEpochDay = sept30))
        db.recordDao().insert(RecordEntity(amountCents = 500000, isExpense = false, categoryId = salary, dateEpochDay = oct1))

        // 九月只应有支出那笔（跨月边界！）
        val (sFrom, sTo) = java.time.YearMonth.of(2026, 9).range()
        val septStats = db.recordDao().observeMonthStats(sFrom, sTo).first()
        assertEquals(0L, septStats.incomeCents)
        assertEquals(1234L, septStats.expenseCents)

        // 十月只有收入
        val (oFrom, oTo) = java.time.YearMonth.of(2026, 10).range()
        val octStats = db.recordDao().observeMonthStats(oFrom, oTo).first()
        assertEquals(500000L, octStats.incomeCents)
        assertEquals(0L, octStats.expenseCents)
    }

    @Test
    fun monthStats_sumEmptyMonth_isZero() = runTest {
        val (from, to) = java.time.YearMonth.of(2030, 1).range()
        val stats = db.recordDao().observeMonthStats(from, to).first()
        assertEquals(0L, stats.incomeCents + stats.expenseCents)
    }

    @Test
    fun deleteRecord_updatesStats() = runTest {
        val food = db.categoryDao().observeCategories(true).first().first().id
        val today = LocalDate.now().toEpochDay()
        val id = db.recordDao().insert(RecordEntity(amountCents = 999, isExpense = true, categoryId = food, dateEpochDay = today))
        val (from, to) = java.time.YearMonth.now().range()
        assertEquals(999L, db.recordDao().observeMonthStats(from, to).first().expenseCents)
        db.recordDao().delete(db.recordDao().getById(id)!!)
        assertEquals(0L, db.recordDao().observeMonthStats(from, to).first().expenseCents)
    }

    @Test
    fun countForCategory_blocksDelete() = runTest {
        val food = db.categoryDao().observeCategories(true).first().first().id
        db.recordDao().insert(RecordEntity(amountCents = 100, isExpense = true, categoryId = food, dateEpochDay = 0L))
        assertEquals(1, db.recordDao().countForCategory(food))
    }

    @Test
    fun recordsOrdered_dateDescThenCreatedDesc() = runTest {
        val food = db.categoryDao().observeCategories(true).first().first().id
        val d1 = LocalDate.of(2026, 9, 1).toEpochDay()
        val d2 = LocalDate.of(2026, 9, 2).toEpochDay()
        db.recordDao().insert(RecordEntity(amountCents = 1, isExpense = true, categoryId = food, dateEpochDay = d1, createdAtMillis = 1))
        db.recordDao().insert(RecordEntity(amountCents = 2, isExpense = true, categoryId = food, dateEpochDay = d2, createdAtMillis = 2))
        db.recordDao().insert(RecordEntity(amountCents = 3, isExpense = true, categoryId = food, dateEpochDay = d2, createdAtMillis = 3))
        val rows = db.recordDao().observeRecordsWithCategory(d1, d2).first()
        assertEquals(listOf(3L, 2L, 1L), rows.map { it.record.amountCents })
    }
}
