package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.RecordEntity
import com.adrian.qingjizhang.data.local.RecordWithCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvBuilderTest {

    // 用真实转换，避免手写 epochDay 数字出错（DEVLOG 教训）
    private val dayA = LocalDate.of(2026, 7, 24).toEpochDay()
    private val dayB = LocalDate.of(2026, 7, 25).toEpochDay()

    // 时间列期望值也用同一纯函数计算：本测试验证"接线"（列顺序/转义），
    // formatTimeOfDay 本身的正确性在其专属测试里用固定时区注入验证。
    private val timeOf0 = formatTimeOfDay(0)

    private fun row(name: String, cents: Long, expense: Boolean, day: Long, note: String) =
        RecordWithCategory(
            RecordEntity(id = 1, amountCents = cents, isExpense = expense, categoryId = 1, dateEpochDay = day, note = note, createdAtMillis = 0),
            name
        )

    @Test fun headerAndOneRow() {
        val csv = CsvBuilder.build(listOf(row("餐饮", 1234, true, dayA, "午饭")))
        val lines = csv.trim().lines()
        assertEquals("日期,时间,类型,分类,金额(元),备注", lines[0])
        assertEquals("2026-07-24,$timeOf0,支出,餐饮,12.34,午饭", lines[1])
    }

    @Test fun incomeRow() {
        val csv = CsvBuilder.build(listOf(row("工资", 500000, false, dayA, "")))
        assertEquals("2026-07-24,$timeOf0,收入,工资,5000.00,", csv.trim().lines()[1])
    }

    @Test fun noteWithComma_escaped() {
        val csv = CsvBuilder.build(listOf(row("餐饮", 100, true, dayA, "a,b")))
        assertEquals("2026-07-24,$timeOf0,支出,餐饮,1.00,\"a,b\"", csv.trim().lines()[1])
    }

    @Test fun noteWithQuote_escaped() {
        val csv = CsvBuilder.build(listOf(row("餐饮", 100, true, dayA, "他说\"好\"")))
        assertEquals("2026-07-24,$timeOf0,支出,餐饮,1.00,\"他说\"\"好\"\"\"", csv.trim().lines()[1])
    }

    @Test fun crlfLineEndings() {
        val csv = CsvBuilder.build(listOf(row("餐饮", 100, true, dayA, ""), row("工资", 200, false, dayB, "")))
        assertEquals(true, csv.contains("\r\n"))
        assertEquals(3, csv.split("\r\n").size - 1) // header + 2 rows, 末尾空段
    }

    @Test fun fileNameFormat() {
        val name = CsvBuilder.fileName(java.time.LocalDateTime.of(2026, 9, 30, 1, 30))
        assertEquals("轻记账_20260930_0130.csv", name)
    }
}
