package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.RecurrencePeriod
import com.adrian.qingjizhang.data.local.RecurringRuleEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Recurrence] 边界单测（V1.3 T1.2）。
 *
 * 全部用本地日期（LocalDate.parse）断言，覆盖：
 * 月末 31→30/28/29、闰年 2/29、跨年、跨周、区间半开半闭语义、startDate 不回溯、非法值防御。
 */
class RecurrenceTest {

    private fun d(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    private fun rule(
        period: RecurrencePeriod,
        dayOfMonth: Int = 0,
        dayOfWeek: Int = 0,
        monthOfYear: Int = 0,
        start: String = "2026-01-01",
    ) = RecurringRuleEntity(
        name = "测试规则",
        amountCents = 100_00,
        isExpense = true,
        categoryId = 1,
        period = period,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        startDateEpochDay = d(start),
        lastOccurredEpochDay = d(start) - 1,
    )

    private fun days(list: List<Long>): List<String> =
        list.map { LocalDate.ofEpochDay(it).toString() }

    // ---------------- 每月 ----------------

    @Test
    fun monthly_basic_每5号() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 5, start = "2026-01-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2026-01-01"), toInclusive = d("2026-03-31"))
        assertEquals(listOf("2026-01-05", "2026-02-05", "2026-03-05"), days(got))
    }

    @Test
    fun monthly_31号遇到短月_截断到月末() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 31, start = "2025-12-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2025-12-31"), toInclusive = d("2026-04-30"))
        // 12/31、1/31、2/28（2026 平年）、3/31、4/30（只有 30 天）
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), days(got))
    }

    @Test
    fun monthly_31号闰年2月_截断到29() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 31, start = "2028-01-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2028-01-31"), toInclusive = d("2028-02-29"))
        assertEquals(listOf("2028-02-29"), days(got))
    }

    @Test
    fun monthly_30号遇到2月_截断到28() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 30, start = "2026-01-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2026-01-30"), toInclusive = d("2026-03-30"))
        assertEquals(listOf("2026-02-28", "2026-03-30"), days(got))
    }

    // ---------------- 每年 ----------------

    @Test
    fun yearly_2月29日_平年落到2月28日() {
        val r = rule(RecurrencePeriod.YEARLY, dayOfMonth = 29, monthOfYear = 2, start = "2024-01-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2024-01-01"), toInclusive = d("2025-12-31"))
        assertEquals(listOf("2024-02-29", "2025-02-28"), days(got))
    }

    @Test
    fun yearly_每年固定日() {
        val r = rule(RecurrencePeriod.YEARLY, dayOfMonth = 15, monthOfYear = 6, start = "2025-01-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2025-01-01"), toInclusive = d("2027-06-15"))
        assertEquals(listOf("2025-06-15", "2026-06-15", "2027-06-15"), days(got))
    }

    // ---------------- 每周 ----------------

    @Test
    fun weekly_每周一() {
        // 2026-01-01 是周四 → 首个周一是 2026-01-05
        val r = rule(RecurrencePeriod.WEEKLY, dayOfWeek = 1, start = "2026-01-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2025-12-31"), toInclusive = d("2026-01-20"))
        assertEquals(listOf("2026-01-05", "2026-01-12", "2026-01-19"), days(got))
    }

    @Test
    fun weekly_每周日_跨月跨年() {
        val r = rule(RecurrencePeriod.WEEKLY, dayOfWeek = 7, start = "2025-12-25")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2025-12-27"), toInclusive = d("2026-01-11"))
        assertEquals(listOf("2025-12-28", "2026-01-04", "2026-01-11"), days(got))
    }

    // ---------------- 区间语义（半开半闭）----------------

    @Test
    fun range_含今天不含游标日() {
        // 用户拍板：到期日当天打开 app 就入账（toInclusive 含今天）
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 10, start = "2026-01-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2026-03-10"), toInclusive = d("2026-04-10"))
        // 3/10 是不含的边界（游标所在日）；4/10 含（今天到期就入账）
        assertEquals(listOf("2026-04-10"), days(got))
    }

    @Test
    fun range_空区间返回空() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 10)
        assertEquals(emptyList<Long>(), Recurrence.dueOccurrences(r, fromExclusive = d("2026-03-10"), toInclusive = d("2026-03-10")))
        assertEquals(emptyList<Long>(), Recurrence.dueOccurrences(r, fromExclusive = d("2026-03-11"), toInclusive = d("2026-03-10")))
    }

    @Test
    fun range_to早于startDate_返回空() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 5, start = "2026-06-01")
        assertEquals(
            emptyList<Long>(),
            Recurrence.dueOccurrences(r, fromExclusive = d("2026-01-01"), toInclusive = d("2026-05-31")),
        )
    }

    // ---------------- startDate 不回溯 ----------------

    @Test
    fun startDate_之前不生成() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 5, start = "2026-03-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2026-01-01"), toInclusive = d("2026-04-30"))
        assertEquals(listOf("2026-03-05", "2026-04-05"), days(got))
    }

    @Test
    fun startDate_当天即到期日_包含当天() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 8, start = "2026-05-08")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2026-05-07"), toInclusive = d("2026-05-10"))
        assertEquals(listOf("2026-05-08"), days(got))
    }

    // ---------------- 跨年 ----------------

    @Test
    fun 跨年_每月15号() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 15, start = "2025-11-01")
        val got = Recurrence.dueOccurrences(r, fromExclusive = d("2025-12-01"), toInclusive = d("2026-01-31"))
        assertEquals(listOf("2025-12-15", "2026-01-15"), days(got))
    }

    // ---------------- 非法值防御 ----------------

    @Test
    fun 非法值_返回空() {
        val badMonthly = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 0)
        val badWeekly = rule(RecurrencePeriod.WEEKLY, dayOfWeek = 0)
        val badYearly = rule(RecurrencePeriod.YEARLY, dayOfMonth = 5, monthOfYear = 13)
        val r1 = Recurrence.dueOccurrences(badMonthly, d("2026-01-01"), d("2026-12-31"))
        val r2 = Recurrence.dueOccurrences(badWeekly, d("2026-01-01"), d("2026-12-31"))
        val r3 = Recurrence.dueOccurrences(badYearly, d("2026-01-01"), d("2026-12-31"))
        assertEquals(emptyList<Long>(), r1)
        assertEquals(emptyList<Long>(), r2)
        assertEquals(emptyList<Long>(), r3)
    }

    // ---------------- 首次到期日（新建规则算 startDate 用）----------------

    @Test
    fun firstOccurrence_每月5号() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 5, start = "2026-01-01")
        assertEquals("2026-01-05", LocalDate.ofEpochDay(Recurrence.firstOccurrenceOnOrAfter(r, d("2026-01-01"))!!).toString())
        assertEquals("2026-01-05", LocalDate.ofEpochDay(Recurrence.firstOccurrenceOnOrAfter(r, d("2026-01-05"))!!).toString())
        assertEquals("2026-02-05", LocalDate.ofEpochDay(Recurrence.firstOccurrenceOnOrAfter(r, d("2026-01-06"))!!).toString())
    }

    @Test
    fun firstOccurrence_每月31号在2月落到28() {
        val r = rule(RecurrencePeriod.MONTHLY, dayOfMonth = 31, start = "2026-01-01")
        assertEquals("2026-02-28", LocalDate.ofEpochDay(Recurrence.firstOccurrenceOnOrAfter(r, d("2026-02-01"))!!).toString())
    }

    @Test
    fun firstOccurrence_每周一从周四算起() {
        val r = rule(RecurrencePeriod.WEEKLY, dayOfWeek = 1, start = "2026-01-01")
        assertEquals("2026-01-05", LocalDate.ofEpochDay(Recurrence.firstOccurrenceOnOrAfter(r, d("2026-01-01"))!!).toString())
    }

    @Test
    fun firstOccurrence_每年2月29日_平年落28() {
        val r = rule(RecurrencePeriod.YEARLY, dayOfMonth = 29, monthOfYear = 2, start = "2024-01-01")
        assertEquals("2024-02-29", LocalDate.ofEpochDay(Recurrence.firstOccurrenceOnOrAfter(r, d("2024-01-01"))!!).toString())
        assertEquals("2026-02-28", LocalDate.ofEpochDay(Recurrence.firstOccurrenceOnOrAfter(r, d("2025-03-01"))!!).toString())
    }

    @Test
    fun firstOccurrence_非法值返回null() {
        assertNull(Recurrence.firstOccurrenceOnOrAfter(rule(RecurrencePeriod.MONTHLY, dayOfMonth = 0), d("2026-01-01")))
        assertNull(Recurrence.firstOccurrenceOnOrAfter(rule(RecurrencePeriod.WEEKLY, dayOfWeek = 9), d("2026-01-01")))
        assertNull(Recurrence.firstOccurrenceOnOrAfter(rule(RecurrencePeriod.YEARLY, dayOfMonth = 5, monthOfYear = 0), d("2026-01-01")))
    }

    @Test
    fun weekdayLabel_七个值() {
        assertEquals("周一", Recurrence.weekdayLabel(1))
        assertEquals("周日", Recurrence.weekdayLabel(7))
        assertEquals("周一", Recurrence.weekdayLabel(0)) // 兜底
    }
}
