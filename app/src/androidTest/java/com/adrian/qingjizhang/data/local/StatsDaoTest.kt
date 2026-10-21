package com.adrian.qingjizhang.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** V1.2 T1.1：统计聚合 SQL 真机断言（按天 GROUP BY + 按分类 GROUP BY） */
@RunWith(AndroidJUnit4::class)
class StatsDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        kotlinx.coroutines.runBlocking { db.seedPresetCategories() }
    }

    @After
    fun teardown() = db.close()

    @Test
    fun dayTotals_groupsByDay_withIncomeExpenseSplit() = runTest {
        val food = db.categoryDao().observeCategories(true).first().first { it.name == "餐饮" }.id
        val salary = db.categoryDao().observeCategories(false).first().first { it.name == "工资" }.id
        val d1 = LocalDate.of(2026, 9, 10).toEpochDay()
        val d2 = LocalDate.of(2026, 9, 11).toEpochDay()
        db.recordDao().insert(RecordEntity(amountCents = 100, isExpense = true, categoryId = food, dateEpochDay = d1))
        db.recordDao().insert(RecordEntity(amountCents = 250, isExpense = true, categoryId = food, dateEpochDay = d1))
        db.recordDao().insert(RecordEntity(amountCents = 700, isExpense = true, categoryId = food, dateEpochDay = d2))
        db.recordDao().insert(RecordEntity(amountCents = 5000, isExpense = false, categoryId = salary, dateEpochDay = d1))

        val days = db.recordDao().observeDayTotals(d1, d2).first()
        assertEquals(2, days.size)
        assertEquals(d1, days[0].dateEpochDay)
        assertEquals(5000L, days[0].incomeCents)
        assertEquals(350L, days[0].expenseCents)
        assertEquals(0L, days[1].incomeCents)
        assertEquals(700L, days[1].expenseCents)
    }

    @Test
    fun categoryBreakdown_expenseOnly_descByCents() = runTest {
        val food = db.categoryDao().observeCategories(true).first().first { it.name == "餐饮" }.id
        val transport = db.categoryDao().observeCategories(true).first().first { it.name == "交通" }.id
        val salary = db.categoryDao().observeCategories(false).first().first { it.name == "工资" }.id
        val d1 = LocalDate.of(2026, 9, 10).toEpochDay()
        db.recordDao().insert(RecordEntity(amountCents = 350, isExpense = true, categoryId = food, dateEpochDay = d1))
        db.recordDao().insert(RecordEntity(amountCents = 700, isExpense = true, categoryId = transport, dateEpochDay = d1))
        db.recordDao().insert(RecordEntity(amountCents = 5000, isExpense = false, categoryId = salary, dateEpochDay = d1))

        val cats = db.recordDao().observeCategoryBreakdown(d1, d1).first()
        // 收入不进环形图；金额倒序
        assertEquals(listOf("交通" to 700L, "餐饮" to 350L), cats.map { it.name to it.cents })
    }

    @Test
    fun dayTotals_emptyRange_emptyList() = runTest {
        val days = db.recordDao().observeDayTotals(0, 1).first()
        assertEquals(0, days.size)
    }
}
