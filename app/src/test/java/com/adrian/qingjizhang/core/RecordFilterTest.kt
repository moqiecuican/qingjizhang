package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.RecordEntity
import com.adrian.qingjizhang.data.local.RecordWithCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFilterTest {

    private fun row(note: String, cat: String, cents: Long, expense: Boolean = true, day: Long = 100L) =
        RecordWithCategory(
            RecordEntity(id = 1, amountCents = cents, isExpense = expense, categoryId = if (cat == "餐饮") 1L else 2L, dateEpochDay = day, note = note),
            cat,
        )

    private val rows = listOf(
        row("午饭 50% off", "餐饮", 1234),
        row("地铁", "交通", 500),
        row("", "餐饮", 800, day = 200L),
        row("工资", "工资", 9000, expense = false),
    )

    @Test fun inactive_returnsAll() {
        assertEquals(4, RecordFiltering.apply(rows, RecordFilter()).size)
    }

    @Test fun keyword_matchesNoteAndCategory() {
        assertEquals(2, RecordFiltering.apply(rows, RecordFilter(keyword = "餐饮")).size) // 分类名命中两条
        assertEquals(1, RecordFiltering.apply(rows, RecordFilter(keyword = "地铁")).size)
        assertEquals(0, RecordFiltering.apply(rows, RecordFilter(keyword = "不存在")).size)
    }

    @Test fun keyword_percentIsLiteral_notWildcard() {
        // LIKE 语义下 "%" 会命中全部；contains 语义只命中真含 % 的备注
        val hit = RecordFiltering.apply(rows, RecordFilter(keyword = "%"))
        assertEquals(1, hit.size)
        assertEquals("午饭 50% off", hit[0].record.note)
    }

    @Test fun categoryAndDateRange() {
        assertEquals(2, RecordFiltering.apply(rows, RecordFilter(categoryId = 1L)).size)
        assertEquals(1, RecordFiltering.apply(rows, RecordFilter(fromDay = 150L)).size)
        assertEquals(3, RecordFiltering.apply(rows, RecordFilter(toDay = 150L)).size)
    }

    @Test fun statsOf_recomputes() {
        val s = RecordFiltering.statsOf(RecordFiltering.apply(rows, RecordFilter(categoryId = 1L)))
        assertEquals(2034L, s.expenseCents)
        assertEquals(0L, s.incomeCents)
    }
}
