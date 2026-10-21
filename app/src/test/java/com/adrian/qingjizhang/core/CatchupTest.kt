package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.RecurrencePeriod
import com.adrian.qingjizhang.data.local.RecurringRuleEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Catchup] 补录计划单测（V1.3 T2.1）。
 * 重点：游标区间语义、暂停不生成、备注复制、排序稳定、多笔区间。
 */
class CatchupTest {

    private fun d(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    private fun rule(
        id: Long = 1,
        period: RecurrencePeriod = RecurrencePeriod.MONTHLY,
        dayOfMonth: Int = 0,
        dayOfWeek: Int = 0,
        monthOfYear: Int = 0,
        start: String = "2026-01-01",
        cursor: String = "2026-01-01",
        active: Boolean = true,
        note: String = "规则备注",
        amount: Long = 350_00,
        categoryId: Long = 7,
    ) = RecurringRuleEntity(
        id = id,
        name = "房租",
        amountCents = amount,
        isExpense = true,
        categoryId = categoryId,
        period = period,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        startDateEpochDay = d(start),
        lastOccurredEpochDay = d(cursor),
        note = note,
        isActive = active,
    )

    private fun days(entries: List<DueEntry>): List<String> =
        entries.map { LocalDate.ofEpochDay(it.dateEpochDay).toString() }

    @Test
    fun plan_游标到今天的区间() {
        val r = rule(dayOfMonth = 5, start = "2026-01-01", cursor = "2026-03-05")
        val plan = Catchup.plan(listOf(r), today = d("2026-04-05"))
        // (3/5, 4/5] → 4/5 一笔（含今天）
        assertEquals(listOf("2026-04-05"), days(plan))
    }

    @Test
    fun plan_今天没到期则为空() {
        val r = rule(dayOfMonth = 20, start = "2026-01-01", cursor = "2026-04-20")
        assertEquals(emptyList<DueEntry>(), Catchup.plan(listOf(r), today = d("2026-05-19")))
    }

    @Test
    fun plan_补多期_跨月() {
        val r = rule(dayOfMonth = 5, start = "2026-01-01", cursor = "2026-01-05")
        val plan = Catchup.plan(listOf(r), today = d("2026-04-05"))
        assertEquals(listOf("2026-02-05", "2026-03-05", "2026-04-05"), days(plan))
    }

    @Test
    fun plan_每周规则多笔() {
        val r = rule(period = RecurrencePeriod.WEEKLY, dayOfWeek = 1, start = "2026-01-01", cursor = "2026-01-04")
        val plan = Catchup.plan(listOf(r), today = d("2026-01-19"))
        assertEquals(listOf("2026-01-05", "2026-01-12", "2026-01-19"), days(plan))
    }

    @Test
    fun plan_暂停的规则不生成() {
        val paused = rule(id = 1, dayOfMonth = 5, cursor = "2026-03-05", active = false)
        val enabled = rule(id = 2, dayOfMonth = 6, cursor = "2026-03-06", active = true)
        val plan = Catchup.plan(listOf(paused, enabled), today = d("2026-04-06"))
        assertEquals(listOf("2026-04-06"), days(plan))
    }

    @Test
    fun plan_新建规则起始日当天生成() {
        // 新建时：startDate = 第一个到期日，cursor = start - 1
        val r = rule(dayOfMonth = 8, start = "2026-05-08", cursor = "2026-05-07")
        val plan = Catchup.plan(listOf(r), today = d("2026-05-08"))
        assertEquals(listOf("2026-05-08"), days(plan))
    }

    @Test
    fun plan_起始日在未来_不生成() {
        val r = rule(dayOfMonth = 8, start = "2026-06-08", cursor = "2026-06-07")
        assertEquals(emptyList<DueEntry>(), Catchup.plan(listOf(r), today = d("2026-05-20")))
    }

    @Test
    fun plan_多条规则排序稳定() {
        val r2 = rule(id = 2, dayOfMonth = 10, cursor = "2026-03-10")
        val r1 = rule(id = 1, dayOfMonth = 5, cursor = "2026-03-05")
        val plan = Catchup.plan(listOf(r2, r1), today = d("2026-04-10"))
        // 按 (规则 id, 日期) 升序
        assertEquals(listOf(1L, 2L), plan.map { it.rule.id })
        assertEquals(listOf("2026-04-05", "2026-04-10"), days(plan))
    }

    @Test
    fun plan_空规则集返回空() {
        assertEquals(emptyList<DueEntry>(), Catchup.plan(emptyList(), today = d("2026-04-05")))
    }

    /** 用户特别要求：每次自动入账都把**规则备注**写进生成账 */
    @Test
    fun toRecord_备注从规则复制_且标记来源() {
        val r = rule(id = 9, dayOfMonth = 5, cursor = "2026-03-05", note = "每月房租（含物业）", amount = 350_000, categoryId = 3)
        val entry = Catchup.plan(listOf(r), today = d("2026-04-05")).single()
        val rec = Catchup.toRecord(entry, nowMillis = 1_700_000_000_000)

        assertEquals("每月房租（含物业）", rec.note)
        assertEquals(9L, rec.recurringRuleId)
        assertEquals(350_000L, rec.amountCents)
        assertEquals(3L, rec.categoryId)
        assertEquals(d("2026-04-05"), rec.dateEpochDay)
        assertTrue(rec.isExpense)
        assertEquals(1_700_000_000_000, rec.createdAtMillis)
        assertEquals(1_700_000_000_000, rec.updatedAtMillis)
        // clientUid 自动生成且非空（跨设备合并地基）
        assertTrue(rec.clientUid.isNotBlank())
    }

    @Test
    fun toRecord_空备注也正常() {
        val r = rule(dayOfMonth = 5, cursor = "2026-03-05", note = "")
        val entry = Catchup.plan(listOf(r), today = d("2026-04-05")).single()
        assertEquals("", Catchup.toRecord(entry, nowMillis = 1).note)
    }

    @Test
    fun describe_三种粒度文案() {
        assertEquals("每月 5 号", Catchup.describe(rule(dayOfMonth = 5)))
        assertEquals("每周一", Catchup.describe(rule(period = RecurrencePeriod.WEEKLY, dayOfWeek = 1)))
        assertEquals("每周日", Catchup.describe(rule(period = RecurrencePeriod.WEEKLY, dayOfWeek = 7)))
        assertEquals("每年 6 月 15 日", Catchup.describe(rule(period = RecurrencePeriod.YEARLY, monthOfYear = 6, dayOfMonth = 15)))
    }
}
