package com.adrian.qingjizhang.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计时间粒度（V1.2）。SQL 只出按天聚合，周/月/年分桶全在这里做——
 * dateEpochDay 本就是本地日期（项目铁律），纯函数分桶天然无时区问题。
 */
enum class Granularity(val label: String) {
    Week("周"), Month("月"), Year("年"),
}

/** 环形图扇区（含被合并前的原始分类名） */
data class CategorySlice(val name: String, val cents: Long, val ratio: Float)

/** 「其他」合并结果：主扇区 + 被合并明细 */
data class DonutData(
    val mainSlices: List<CategorySlice>,
    val otherItems: List<CategorySlice>,
    val totalCents: Long,
) {
    /** 守恒：主扇区+其他 之和 == 总额 */
    val conserved: Boolean get() = (mainSlices.sumOf { it.cents.toLong() } + otherItems.sumOf { it.cents.toLong() }) == totalCents
}

/** 时间序列柱（周/月/年桶） */
data class TimeBar(
    val key: String,
    val label: String,
    val fromDay: Long,
    val toDay: Long,
    val incomeCents: Long,
    val expenseCents: Long,
)

object ChartModels {

    /** 「其他」合并阈值：占比严格小于 5% 才并入（恰好 5% 保留独立扇区） */
    const val OTHER_THRESHOLD = 0.05f

    /** 柱状图最多显示的桶数（超出取最近 N 个） */
    const val MAX_BARS = 12

    // ---------- 分桶 ----------

    /** 某天的桶起点 epochDay（周=当周周一；月=当月1号；年=当年1月1号） */
    fun bucketStart(epochDay: Long, g: Granularity): Long {
        val d = LocalDate.ofEpochDay(epochDay)
        return when (g) {
            // 显式 MONDAY：不依赖 Locale（实测 JDK 的 Locale.CHINA 一周首日=周日，会把周一归入上一周）
            Granularity.Week -> d.with(java.time.DayOfWeek.MONDAY).toEpochDay()
            Granularity.Month -> d.withDayOfMonth(1).toEpochDay()
            Granularity.Year -> d.withDayOfYear(1).toEpochDay()
        }
    }

    /** 桶的闭区间 [start, end]（周=周一~周日；月=月初~月末；年=元旦~除夕） */
    fun bucketRange(startDay: Long, g: Granularity): Pair<Long, Long> {
        val s = LocalDate.ofEpochDay(startDay)
        val e = when (g) {
            Granularity.Week -> s.plusDays(6)
            Granularity.Month -> s.withDayOfMonth(s.lengthOfMonth())
            Granularity.Year -> s.withDayOfYear(s.lengthOfYear())
        }
        return startDay to e.toEpochDay()
    }

    /** 桶标签：周「12/28-1/4 周」（跨年也这样拼）；月「2026年9月」；年「2026年」 */
    fun bucketLabel(startDay: Long, g: Granularity): String {
        val (s, e) = bucketRange(startDay, g)
        val ds = LocalDate.ofEpochDay(s)
        val de = LocalDate.ofEpochDay(e)
        return when (g) {
            Granularity.Week -> "${ds.monthValue}/${ds.dayOfMonth}-${de.monthValue}/${de.dayOfMonth} 周"
            Granularity.Month -> "${ds.year}年${ds.monthValue}月"
            Granularity.Year -> "${ds.year}年"
        }
    }

    /** 按粒度把按天聚合行分桶累加（输入已按天升序） */
    fun bucketize(
        dayTotals: List<com.adrian.qingjizhang.data.local.DayTotalRow>,
        g: Granularity,
    ): List<TimeBar> {
        val grouped = LinkedHashMap<Long, LongArray>() // start -> [income, expense]
        for (row in dayTotals) {
            val start = bucketStart(row.dateEpochDay, g)
            val acc = grouped.getOrPut(start) { LongArray(2) }
            acc[0] += row.incomeCents
            acc[1] += row.expenseCents
        }
        return grouped.entries.sortedBy { it.key }.map { (start, acc) ->
            val (from, to) = bucketRange(start, g)
            TimeBar(
                key = start.toString(),
                label = bucketLabel(start, g),
                fromDay = from,
                toDay = to,
                incomeCents = acc[0],
                expenseCents = acc[1],
            )
        }
    }

    /** 某天的桶起点平移 delta 个桶（周=±7天；月/年用日历算术） */
    fun shiftBucketStart(startDay: Long, g: Granularity, delta: Int): Long {
        val d = LocalDate.ofEpochDay(startDay)
        return when (g) {
            Granularity.Week -> startDay + 7L * delta
            Granularity.Month -> YearMonth.from(d).plusMonths(delta.toLong()).atDay(1).toEpochDay()
            Granularity.Year -> LocalDate.of(d.year + delta, 1, 1).toEpochDay()
        }
    }

    /** 柱数封顶：超过 MAX_BARS 只保留最近 MAX_BARS 个桶 */
    fun capBars(bars: List<TimeBar>): List<TimeBar> =
        if (bars.size <= MAX_BARS) bars else bars.takeLast(MAX_BARS)

    // ---------- 环形图合并 ----------

    /**
     * 占比 < threshold 的分类并入「其他」。
     * 规则：恰好等于阈值保留独立扇区；「其他」内部按金额倒序；总额守恒。
     */
    fun mergeSmallSlices(
        breakdown: List<com.adrian.qingjizhang.data.local.CategoryBreakdownRow>,
        threshold: Float = OTHER_THRESHOLD,
    ): DonutData {
        val total = breakdown.sumOf { it.cents }
        if (total <= 0) return DonutData(emptyList(), emptyList(), 0)
        val main = mutableListOf<CategorySlice>()
        val other = mutableListOf<CategorySlice>()
        for (row in breakdown) { // 输入已金额倒序
            val ratio = row.cents.toFloat() / total
            val slice = CategorySlice(row.name, row.cents, ratio)
            if (ratio < threshold) other += slice else main += slice
        }
        return DonutData(main, other, total)
    }

    /** 「其他」扇区（有被合并项时才存在） */
    fun otherSlice(data: DonutData): CategorySlice? =
        if (data.otherItems.isEmpty()) null else
            CategorySlice("其他", data.otherItems.sumOf { it.cents },
                data.otherItems.sumOf { it.cents }.toFloat() / data.totalCents)
}
