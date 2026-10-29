package com.adrian.qingjizhang.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adrian.qingjizhang.data.LedgerRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * JSON 备份/恢复仪器测试（计划书 T4.4）：
 * roundtrip（导出→清库→恢复→逐字段一致）+ 非法输入拒绝且不损库。
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        runBlocking { db.seedPresetCategories() }
        repo = LedgerRepository(db)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun roundtrip_exportWipeRestore_identical() = runTest {
        // 造数据：两笔流水 + 一条预算
        val cats = db.categoryDao().getAll()
        val food = cats.first { it.name == "餐饮" }
        val salary = cats.first { it.name == "工资" }
        repo.addRecord(
            RecordEntity(amountCents = 1234, isExpense = true, categoryId = food.id,
                dateEpochDay = LocalDate.of(2026, 9, 30).toEpochDay(), note = "午饭"),
        )
        repo.addRecord(
            RecordEntity(amountCents = 500000, isExpense = false, categoryId = salary.id,
                dateEpochDay = LocalDate.of(2026, 9, 1).toEpochDay(), note = ""),
        )
        repo.setBudget(java.time.YearMonth.of(2026, 9), 300000)

        val before = db.recordDao().getAllEntities().sortedBy { it.id }
        val backup = repo.exportBackupJson()

        // 清库（模拟换机后的空库）
        db.recordDao().deleteAll()
        db.categoryDao().deleteAll()
        db.budgetDao().deleteAll()
        assertEquals(0, db.recordDao().getAllEntities().size)

        // 恢复
        repo.restoreFromJson(backup)

        // 逐字段一致：流水实体全等（含原 id / clientUid / 两个时间戳）
        val after = db.recordDao().getAllEntities().sortedBy { it.id }
        assertEquals(before, after)
        // 分类 12 个预置全回来，id 不变（categoryId 引用完整）
        assertEquals(12, db.categoryDao().getAll().size)
        assertEquals(food.id, db.categoryDao().getAll().first { it.name == "餐饮" }.id)
        // 预算回来
        assertEquals(300000L, db.budgetDao().getAll().first { it.yearMonthKey == "2026-09" }.amountCents)
    }

    @Test
    fun restore_rejectsGarbage_dbUntouched() = runTest {
        repo.addRecord(
            RecordEntity(amountCents = 999, isExpense = true,
                categoryId = db.categoryDao().getAll().first().id,
                dateEpochDay = LocalDate.now().toEpochDay()),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.restoreFromJson("this is not a backup {{{") }
        }
        // 库未被动过
        assertEquals(1, db.recordDao().getAllEntities().size)
        assertEquals(12, db.categoryDao().getAll().size)
    }
}
