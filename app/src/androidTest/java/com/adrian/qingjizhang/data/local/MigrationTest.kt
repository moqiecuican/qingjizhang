package com.adrian.qingjizhang.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * 迁移测试框架（计划书 T1.2）。
 *
 * 套路（每条迁移一个测试方法）：
 * 1. helper.createDatabase(name, fromVersion) 按旧 schema 建库
 * 2. 用 SupportSQLiteDatabase 手工 INSERT 旧版本格式的测试数据
 * 3. helper.runMigrationsAndValidate(name, toVersion, true, MIGRATION_x_y)
 *    —— 既跑迁移 SQL，又用新 schema JSON 验证表结构/索引完全一致
 * 4. 查询断言：旧数据零丢失、新列语义正确
 *
 * 为什么重要：这是「升级安装不丢账」的机器保险，合并进 main 前必须绿。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val dbName = "migration-test"

    /**
     * 1→1 基线：验证测试框架本身可用（schema JSON 能读、建库能成、数据可查）。
     * 教训记录（DEVLOG）：createDatabase 第二次调用会删库重建，绝不可用来"复用"句柄；
     * 同版本校验用 runMigrationsAndValidate，数据查询就用 createDatabase 返回的句柄。
     */
    @Test
    fun baseline_v1_schemaReads() {
        val db = helper.createDatabase(dbName, 1)
        // 按 v1 schema 手工插一笔（含全部列）
        db.execSQL(
            """INSERT INTO categories (name, isExpense, sortOrder) VALUES ('餐饮', 1, 0)"""
        )
        db.execSQL(
            """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis)
               VALUES (1234, 1, 1, 20664, '基线测试', 1759000000000)"""
        )
        val cur = db.query("SELECT COUNT(*) FROM records")
        cur.moveToFirst()
        assertEquals(1, cur.getInt(0))
        cur.close()

        // 同一句柄直接查回数据（不重新 createDatabase！）
        val cur2 = db.query("SELECT amountCents, note FROM records")
        cur2.moveToFirst()
        assertEquals(1234L, cur2.getLong(0))
        assertEquals("基线测试", cur2.getString(1))
        cur2.close()
        db.close()

        // 结构校验（当前版本→当前版本，不传迁移=只验证 schema）
        helper.runMigrationsAndValidate(dbName, 1, true)
    }

    /** 预置数据生成器：给后续真迁移测试用（随机 clientUid 等） */
    private fun newUid(): String = UUID.randomUUID().toString()

    /**
     * 1→2 真迁移：records 加 clientUid + updatedAtMillis（计划书 T2.2）。
     * 断言：旧数据零丢失、新列非空、updatedAt 回填=createdAt、唯一索引存在。
     */
    @Test
    fun migration_1_2_addsMergeColumns_noDataLoss() {
        val db = helper.createDatabase(dbName, 1)
        // 按 v1 schema 插两笔（v1 没有 clientUid/updatedAtMillis 列）
        db.execSQL("""INSERT INTO categories (name, isExpense, sortOrder) VALUES ('餐饮', 1, 0)""")
        db.execSQL(
            """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis)
               VALUES (1234, 1, 1, 20664, '午饭', 1759000000000)"""
        )
        db.execSQL(
            """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis)
               VALUES (500000, 0, 1, 20665, '工资', 1759000001000)"""
        )
        db.close()

        // 跑迁移并按 v2 schema 全量校验（列/索引/约束一致性）
        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)

        // 旧数据零丢失
        val cur = migrated.query("SELECT amountCents, note, createdAtMillis, clientUid, updatedAtMillis FROM records ORDER BY id")
        cur.moveToFirst()
        assertEquals(1234L, cur.getLong(0))
        assertEquals("午饭", cur.getString(1))
        assertEquals(1759000000000L, cur.getLong(2))
        val uid1 = cur.getString(3)
        assertTrue("clientUid 应已回填", uid1.isNotBlank())
        assertEquals("updatedAt 应回填为 createdAt", 1759000000000L, cur.getLong(4))
        cur.moveToNext()
        assertEquals(500000L, cur.getLong(0))
        val uid2 = cur.getString(3)
        assertTrue(uid2.isNotBlank())
        cur.close()

        // 存量行回填的 clientUid 互不相同（单机场景不撞车）
        assertNotEquals(uid1, uid2)
        migrated.close()
    }

    /**
     * 2→3 真迁移：新增 budgets 表，records/categories 数据零丢失（计划书 T3.2）。
     */
    @Test
    fun migration_2_3_addsBudgetsTable_noDataLoss() {
        val db = helper.createDatabase(dbName, 2)
        db.execSQL("""INSERT INTO categories (name, isExpense, sortOrder) VALUES ('餐饮', 1, 0)""")
        db.execSQL(
            """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis, clientUid, updatedAtMillis)
               VALUES (1234, 1, 1, 20664, '午饭', 1759000000000, 'uid-abc', 1759000000000)"""
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3)

        // 旧数据零丢失
        val cur = migrated.query("SELECT amountCents, note, clientUid FROM records")
        cur.moveToFirst()
        assertEquals(1234L, cur.getLong(0))
        assertEquals("午饭", cur.getString(1))
        assertEquals("uid-abc", cur.getString(2))
        cur.close()

        // budgets 表可写可查
        migrated.execSQL("INSERT INTO budgets (yearMonthKey, amountCents, updatedAtMillis) VALUES ('2026-09', 300000, 1)")
        val cur2 = migrated.query("SELECT amountCents FROM budgets WHERE yearMonthKey = '2026-09'")
        cur2.moveToFirst()
        assertEquals(300000L, cur2.getLong(0))
        cur2.close()
        migrated.close()
    }

    /**
     * 3→4 真迁移（V1.3 T1.3）：新增 recurring_rules 表 + records 加 recurringRuleId 列 + 唯一索引。
     * 断言：旧数据零丢失、存量行新列为 NULL、新表可写、**幂等语义**（手动账同日可重复；
     * 同规则同日期的自动账被唯一索引拒绝 —— 这是"补录不重复入账"的数据库层保险）。
     */
    @Test
    fun migration_3_4_addsRecurringRules_noDataLoss() {
        val db = helper.createDatabase(dbName, 3)
        db.execSQL("""INSERT INTO categories (name, isExpense, sortOrder) VALUES ('餐饮', 1, 0)""")
        db.execSQL(
            """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis, clientUid, updatedAtMillis)
               VALUES (1234, 1, 1, 20664, '午饭', 1759000000000, 'uid-v3', 1759000000000)"""
        )
        db.execSQL("INSERT INTO budgets (yearMonthKey, amountCents, updatedAtMillis) VALUES ('2026-09', 300000, 1)")
        db.close()

        val migrated = helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4)

        // 旧数据零丢失 + 新列默认 NULL（存量行不属于任何规则）
        val cur = migrated.query("SELECT amountCents, note, clientUid, recurringRuleId FROM records")
        cur.moveToFirst()
        assertEquals(1234L, cur.getLong(0))
        assertEquals("午饭", cur.getString(1))
        assertEquals("uid-v3", cur.getString(2))
        assertTrue("存量行 recurringRuleId 应为 NULL", cur.isNull(3))
        cur.close()

        // budgets 表未受影响
        val cur2 = migrated.query("SELECT amountCents FROM budgets WHERE yearMonthKey = '2026-09'")
        cur2.moveToFirst()
        assertEquals(300000L, cur2.getLong(0))
        cur2.close()

        // recurring_rules 表可写可查（period 存枚举名 TEXT）
        migrated.execSQL(
            """INSERT INTO recurring_rules (name, amountCents, isExpense, categoryId, period, dayOfWeek, dayOfMonth, monthOfYear,
                                           startDateEpochDay, lastOccurredEpochDay, note, isActive, createdAtMillis, updatedAtMillis)
               VALUES ('房租', 350000, 1, 1, 'MONTHLY', 0, 5, 0, 20664, 20663, '规则备注', 1, 1, 1)"""
        )
        val cur3 = migrated.query("SELECT name, note, period FROM recurring_rules")
        cur3.moveToFirst()
        assertEquals("房租", cur3.getString(0))
        assertEquals("规则备注", cur3.getString(1))
        assertEquals("MONTHLY", cur3.getString(2))
        cur3.close()

        // 幂等语义 ①：手动账（recurringRuleId=NULL）同日可重复（SQLite 唯一索引 NULL 互不相等）
        migrated.execSQL(
            """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis, clientUid, updatedAtMillis, recurringRuleId)
               VALUES (100, 1, 1, 20700, '手动1', 1, 'uid-m1', 1, NULL)"""
        )
        migrated.execSQL(
            """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis, clientUid, updatedAtMillis, recurringRuleId)
               VALUES (200, 1, 1, 20700, '手动2', 1, 'uid-m2', 1, NULL)"""
        )

        // 幂等语义 ②：同规则同日期的自动账 → 唯一索引拒绝
        migrated.execSQL(
            """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis, clientUid, updatedAtMillis, recurringRuleId)
               VALUES (350000, 1, 1, 20706, '房租', 1, 'uid-a1', 1, 1)"""
        )
        var rejected = false
        try {
            migrated.execSQL(
                """INSERT INTO records (amountCents, isExpense, categoryId, dateEpochDay, note, createdAtMillis, clientUid, updatedAtMillis, recurringRuleId)
                   VALUES (350000, 1, 1, 20706, '房租重复', 1, 'uid-a2', 1, 1)"""
            )
        } catch (e: Exception) {
            // 语句与上面成功的那条同构，异常只可能来自唯一索引冲突
            rejected = true
        }
        assertTrue("同规则同日期的自动账应被唯一索引拒绝", rejected)

        // 反向核验：该 (规则1, 20706) 只有一笔
        val cur4 = migrated.query("SELECT COUNT(*) FROM records WHERE recurringRuleId = 1 AND dateEpochDay = 20706")
        cur4.moveToFirst()
        assertEquals(1, cur4.getInt(0))
        cur4.close()

        migrated.close()
    }
}
