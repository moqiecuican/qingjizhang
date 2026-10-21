package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.RecurrencePeriod
import com.adrian.qingjizhang.data.local.RecurringRuleEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 周期到期日计算（V1.3 纯函数）。
 *
 * 铁律：dateEpochDay 是**本地日期**（LocalDate.toEpochDay()），本文件全程用 LocalDate 运算，
 * 不碰 UTC 时刻/时区 —— 所以跨年、跨月、闰年边界都能用普通单测锁死，无需仪器化。
 *
 * 区间约定（半开半闭）：`(fromExclusive, toInclusive]`，即 **含 toInclusive（今天当天也入账）**、
 * 不含 fromExclusive（补录游标所在日不重复生成）。
 *
 * 月末截断：dayOfMonth=31 遇到只有 28/29/30 天的月份 → 截断到当月最后一天
 * （如「每月 31 号」在 2 月 → 2/28 或闰年 2/29）。这是周期性账单的通行语义。
 */
object Recurrence {

    /** 安全的月份天数上限，防御非法规则值 */
    private const val MAX_DAY_OF_MONTH = 31

    /**
     * 规则在 `(fromExclusive, toInclusive]` 内的全部到期日（epochDay，升序）。
     *
     * 同时受 `rule.startDateEpochDay` 约束：不早于起始日生成（新建规则默认不回溯历史）。
     */
    fun dueOccurrences(
        rule: RecurringRuleEntity,
        fromExclusive: Long,
        toInclusive: Long,
    ): List<Long> {
        if (toInclusive <= fromExclusive) return emptyList()
        // 有效下界：既要 > fromExclusive，又要 >= startDate（含起始日当天）
        val lowerBound = maxOf(fromExclusive + 1, rule.startDateEpochDay)
        if (lowerBound > toInclusive) return emptyList()
        return when (rule.period) {
            RecurrencePeriod.WEEKLY -> weeklyOccurrences(rule, lowerBound, toInclusive)
            RecurrencePeriod.MONTHLY -> monthlyOccurrences(rule, lowerBound, toInclusive)
            RecurrencePeriod.YEARLY -> yearlyOccurrences(rule, lowerBound, toInclusive)
        }
    }

    /**
     * 规则在 `>= fromInclusive` 的**第一个**到期日（epochDay）；无 → null。
     * 用途：新建规则时算 startDate 默认值（"创建后的第一个到期日"，不回溯历史）。
     * 与 [dueOccurrences] 不同：本函数不受 startDate 约束（调用方拿它来定 startDate）。
     */
    fun firstOccurrenceOnOrAfter(rule: RecurringRuleEntity, fromInclusive: Long): Long? =
        when (rule.period) {
            RecurrencePeriod.WEEKLY -> firstWeekly(rule, fromInclusive)
            RecurrencePeriod.MONTHLY -> firstMonthly(rule, fromInclusive)
            RecurrencePeriod.YEARLY -> firstYearly(rule, fromInclusive)
        }

    // ---------------- 每周 ----------------

    /** dayOfWeek：1=周一 … 7=周日 */
    private fun weeklyOccurrences(rule: RecurringRuleEntity, from: Long, to: Long): List<Long> {
        if (rule.dayOfWeek !in 1..7) return emptyList()
        var d = alignToWeekday(LocalDate.ofEpochDay(from), rule.dayOfWeek)
        val out = mutableListOf<Long>()
        while (d.toEpochDay() <= to) {
            out += d.toEpochDay()
            d = d.plusDays(7)
        }
        return out
    }

    private fun firstWeekly(rule: RecurringRuleEntity, from: Long): Long? {
        if (rule.dayOfWeek !in 1..7) return null
        return alignToWeekday(LocalDate.ofEpochDay(from), rule.dayOfWeek).toEpochDay()
    }

    /** 把日期向后对齐到最近的指定星期几（含当天） */
    private fun alignToWeekday(date: LocalDate, isoDayOfWeek: Int): LocalDate {
        val delta = (isoDayOfWeek - date.dayOfWeek.value + 7) % 7
        return date.plusDays(delta.toLong())
    }

    // ---------------- 每月 ----------------

    private fun monthlyOccurrences(rule: RecurringRuleEntity, from: Long, to: Long): List<Long> {
        if (rule.dayOfMonth !in 1..MAX_DAY_OF_MONTH) return emptyList()
        val first = LocalDate.ofEpochDay(from)
        val last = LocalDate.ofEpochDay(to)
        val out = mutableListOf<Long>()
        var ym: YearMonth = YearMonth.from(first)
        val endYm = YearMonth.from(last)
        while (!ym.isAfter(endYm)) {
            val day = minOf(rule.dayOfMonth, ym.lengthOfMonth()) // 月末截断
            val e = ym.atDay(day).toEpochDay()
            if (e in from..to) out += e
            ym = ym.plusMonths(1)
        }
        return out
    }

    private fun firstMonthly(rule: RecurringRuleEntity, from: Long): Long? {
        if (rule.dayOfMonth !in 1..MAX_DAY_OF_MONTH) return null
        val date = LocalDate.ofEpochDay(from)
        var ym = YearMonth.from(date)
        repeat(13) { // 最多试 13 个月，必然命中
            val day = minOf(rule.dayOfMonth, ym.lengthOfMonth())
            val e = ym.atDay(day).toEpochDay()
            if (e >= from) return e
            ym = ym.plusMonths(1)
        }
        return null
    }

    // ---------------- 每年 ----------------

    private fun yearlyOccurrences(rule: RecurringRuleEntity, from: Long, to: Long): List<Long> {
        if (rule.monthOfYear !in 1..12 || rule.dayOfMonth !in 1..MAX_DAY_OF_MONTH) return emptyList()
        val first = LocalDate.ofEpochDay(from)
        val last = LocalDate.ofEpochDay(to)
        val out = mutableListOf<Long>()
        for (year in first.year..last.year) {
            val ym = YearMonth.of(year, rule.monthOfYear)
            val day = minOf(rule.dayOfMonth, ym.lengthOfMonth()) // 闰年 2/29 → 平年 2/28
            val e = ym.atDay(day).toEpochDay()
            if (e in from..to) out += e
        }
        return out
    }

    private fun firstYearly(rule: RecurringRuleEntity, from: Long): Long? {
        if (rule.monthOfYear !in 1..12 || rule.dayOfMonth !in 1..MAX_DAY_OF_MONTH) return null
        val date = LocalDate.ofEpochDay(from)
        for (year in date.year..date.year + 1) {
            val ym = YearMonth.of(year, rule.monthOfYear)
            val day = minOf(rule.dayOfMonth, ym.lengthOfMonth())
            val e = ym.atDay(day).toEpochDay()
            if (e >= from) return e
        }
        return null
    }

    /** 供 UI/测试用：星期几的中文名（1=周一…7=周日） */
    fun weekdayLabel(isoDayOfWeek: Int): String =
        when (isoDayOfWeek) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; 7 -> "周日"
            else -> "周一"
        }

    /** DayOfWeek 互转（避免调用方重复算） */
    fun toDayOfWeek(isoDayOfWeek: Int): DayOfWeek = DayOfWeek.of(isoDayOfWeek.coerceIn(1, 7))
}
