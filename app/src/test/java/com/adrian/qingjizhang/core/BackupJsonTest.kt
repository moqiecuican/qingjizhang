package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.BudgetEntity
import com.adrian.qingjizhang.data.local.CategoryEntity
import com.adrian.qingjizhang.data.local.RecordEntity
import com.adrian.qingjizhang.data.local.RecurrencePeriod
import com.adrian.qingjizhang.data.local.RecurringRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** JSON 备份 roundtrip 测试（计划书 T4.1）：build → parse 后数据逐字段一致 */
class BackupJsonTest {

    private val cats = listOf(
        CategoryEntity(id = 1, name = "餐饮", isExpense = true, sortOrder = 0),
        CategoryEntity(id = 9, name = "工资", isExpense = false, sortOrder = 0),
    )
    private val records = listOf(
        RecordEntity(
            id = 5, amountCents = 1234, isExpense = true, categoryId = 1,
            dateEpochDay = 20664, note = "午饭, 加了\"蛋\"", createdAtMillis = 1759000000000,
            clientUid = "uid-aaa", updatedAtMillis = 1759000001000,
        ),
    )
    private val budgets = listOf(BudgetEntity(yearMonthKey = "2026-09", amountCents = 300000, updatedAtMillis = 1))

    @Test fun roundtrip_allFieldsPreserved() {
        val text = BackupJson.build(cats, records, budgets, nowMillis = 1759000099000)
        val parsed = BackupJson.parse(text)
        assertEquals(cats, parsed.categories)
        assertEquals(records, parsed.records)
        assertEquals(budgets, parsed.budgets)
        assertEquals(1759000099000, parsed.exportedAtMillis)
        assertEquals(BackupJson.FORMAT_VERSION, parsed.formatVersion)
    }

    @Test fun build_explicitlyWritesAppAndVersion() {
        // 真机实测教训：encodeDefaults 不开时这两个字段会被省略，校验形同虚设
        val text = BackupJson.build(emptyList(), emptyList(), emptyList())
        assertEquals(true, text.contains("\"app\": \"qingjizhang\""))
        assertEquals(true, text.contains("\"formatVersion\": ${BackupJson.FORMAT_VERSION}"))
    }

    @Test fun parse_rejectsGarbage() {
        assertThrows(IllegalArgumentException::class.java) { BackupJson.parse("not json at all") }
    }

    @Test fun parse_rejectsWrongApp() {
        val text = """{"app":"other","formatVersion":1,"exportedAtMillis":0,"categories":[],"records":[],"budgets":[]}"""
        assertThrows(IllegalArgumentException::class.java) { BackupJson.parse(text) }
    }

    @Test fun parse_rejectsNewerFormat() {
        val text = """{"app":"qingjizhang","formatVersion":99,"exportedAtMillis":0,"categories":[],"records":[],"budgets":[]}"""
        val e = assertThrows(IllegalArgumentException::class.java) { BackupJson.parse(text) }
        assertEquals(true, e.message!!.contains("升级"))
    }

    @Test fun parse_toleratesUnknownFields() {
        // 将来新版本多导出的字段，旧版恢复时应忽略而不是崩
        val text = """{"app":"qingjizhang","formatVersion":1,"exportedAtMillis":0,"categories":[],"records":[],"budgets":[],"futureField":123}"""
        val parsed = BackupJson.parse(text)
        assertEquals(0, parsed.records.size)
    }

    @Test fun emptySnapshot_roundtrip() {
        val text = BackupJson.build(emptyList(), emptyList(), emptyList())
        val parsed = BackupJson.parse(text)
        assertEquals(0, parsed.categories.size)
        assertEquals(0, parsed.records.size)
        assertEquals(0, parsed.budgets.size)
    }

    @Test fun fileNameFormat() {
        val name = BackupJson.fileName(java.time.LocalDateTime.of(2026, 9, 30, 22, 5))
        assertEquals("轻记账备份_20260930_2205.json", name)
    }

    // ---------- v2（V1.3）：周期规则纳入备份 ----------

    @Test fun v2_roundtrip_规则与自动标完整回来() {
        val rules = listOf(
            RecurringRuleEntity(
                id = 3, name = "房租", amountCents = 350_000, isExpense = true, categoryId = 1,
                period = RecurrencePeriod.MONTHLY, dayOfMonth = 5,
                startDateEpochDay = 20673, lastOccurredEpochDay = 20672,
                note = "每月房租（含物业）", isActive = true,
                createdAtMillis = 1759000000000, updatedAtMillis = 1759000000100,
            ),
        )
        val autoRecord = RecordEntity(
            id = 8, amountCents = 350_000, isExpense = true, categoryId = 1,
            dateEpochDay = 20673, note = "每月房租（含物业）",
            createdAtMillis = 1759000000000, clientUid = "uid-auto", updatedAtMillis = 1759000000000,
            recurringRuleId = 3,
        )
        val text = BackupJson.build(emptyList(), listOf(autoRecord), emptyList(), rules, nowMillis = 1)
        val parsed = BackupJson.parse(text)
        assertEquals(rules, parsed.recurringRules)
        assertEquals(3L, parsed.records.single().recurringRuleId)
    }

    @Test fun v2_旧版v1备份无规则字段_可恢复为空() {
        // 兼容性：v1 备份没有 recurringRules 字段，恢复方必须能解析（默认空列表）
        val text = """{"app":"qingjizhang","formatVersion":1,"exportedAtMillis":0,"categories":[],"records":[],"budgets":[]}"""
        val parsed = BackupJson.parse(text)
        assertEquals(0, parsed.recurringRules.size)
        assertEquals(1, parsed.formatVersion)
    }

    @Test fun v2_records缺recurringRuleId字段_默认为null() {
        // 兼容性：v1 备份里的 records 没有 recurringRuleId（V1.3 新列）
        val text = """{"app":"qingjizhang","formatVersion":1,"exportedAtMillis":0,"categories":[],"records":[
            {"id":1,"amountCents":100,"isExpense":true,"categoryId":1,"dateEpochDay":20664,"note":"","createdAtMillis":1,"clientUid":"u","updatedAtMillis":1}
        ],"budgets":[]}"""
        val parsed = BackupJson.parse(text)
        assertEquals(null, parsed.records.single().recurringRuleId)
    }
}
