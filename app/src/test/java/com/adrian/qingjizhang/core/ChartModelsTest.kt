package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.CategoryBreakdownRow
import com.adrian.qingjizhang.data.local.DayTotalRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** ChartModels 纯函数测试（V1.2 T1.2 硬验收） */
class ChartModelsTest {

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    // ---------- 分桶 ----------

    @Test fun weekBucket_mondayStart() {
        // 2026-12-28 是周一；2027-01-03 是周日 → 同桶
        val mon = day(2026, 12, 28)
        val sun = day(2027, 1, 3)
        assertEquals(ChartModels.bucketStart(mon, Granularity.Week), ChartModels.bucketStart(sun, Granularity.Week))
        assertEquals(mon, ChartModels.bucketStart(sun, Granularity.Week))
    }

    @Test fun crossYearWeek_sameBucket_andLabel() {
        val dec30 = day(2026, 12, 30)
        val jan1 = day(2027, 1, 1)
        assertEquals(ChartModels.bucketStart(dec30, Granularity.Week), ChartModels.bucketStart(jan1, Granularity.Week))
        assertEquals("12/28-1/3 周", ChartModels.bucketLabel(day(2026, 12, 28), Granularity.Week))
    }

    @Test fun monthBucket_endOfMonthBelongsToSameMonth() {
        val last = day(2026, 9, 30)
        val first = day(2026, 9, 1)
        assertEquals(ChartModels.bucketStart(first, Granularity.Month), ChartModels.bucketStart(last, Granularity.Month))
    }

    @Test fun yearBucket() {
        assertEquals(day(2026, 1, 1), ChartModels.bucketStart(day(2026, 7, 19), Granularity.Year))
        assertEquals("2026年", ChartModels.bucketLabel(day(2026, 7, 19), Granularity.Year))
    }

    // ---------- bucketize ----------

    @Test fun bucketize_weekAccumulatesAcrossDays() {
        val rows = listOf(
            DayTotalRow(day(2026, 12, 28), 0, 100),
            DayTotalRow(day(2026, 12, 30), 0, 200),
            DayTotalRow(day(2027, 1, 3), 0, 50),   // 同一跨年周
            DayTotalRow(day(2027, 1, 5), 0, 70),   // 下一周
        )
        val bars = ChartModels.bucketize(rows, Granularity.Week)
        assertEquals(2, bars.size)
        assertEquals(350L, bars[0].expenseCents)
        assertEquals(70L, bars[1].expenseCents)
    }

    @Test fun bucketize_emptyInput() {
        assertTrue(ChartModels.bucketize(emptyList(), Granularity.Month).isEmpty())
    }

    @Test fun capBars_twoYearsWeekly_capsAt12_mostRecent() {
        // 2 年 ≈ 105 周桶
        val rows = (0 until 730).map { DayTotalRow(day(2025, 1, 1) + it, 0, 10) }
        val bars = ChartModels.bucketize(rows, Granularity.Week)
        assertTrue(bars.size > 12)
        val capped = ChartModels.capBars(bars)
        assertEquals(12, capped.size)
        assertEquals(bars.takeLast(12), capped) // 保留最近 12 个
    }

    @Test fun capBars_underLimit_unchanged() {
        val bars = ChartModels.bucketize(listOf(DayTotalRow(day(2026, 9, 1), 1, 2)), Granularity.Month)
        assertEquals(bars, ChartModels.capBars(bars))
    }

    // ---------- 环形图合并 ----------

    private fun bd(vararg cents: Long) = cents.mapIndexed { i, c -> CategoryBreakdownRow("cat$i", c) }

    @Test fun mergeSmallSlices_thresholdBoundary() {
        // 总额 10000：500 = 恰好 5% 保留；499 = 4.99% 并入
        val data = ChartModels.mergeSmallSlices(bd(9001, 500, 499))
        assertEquals(listOf("cat0", "cat1"), data.mainSlices.map { it.name })
        assertEquals(listOf("cat2"), data.otherItems.map { it.name })
        assertTrue(data.conserved)
    }

    @Test fun mergeSmallSlices_emptyOther_noOtherSlice() {
        val data = ChartModels.mergeSmallSlices(bd(6000, 4000))
        assertTrue(data.otherItems.isEmpty())
        assertNull(ChartModels.otherSlice(data))
        assertTrue(data.conserved)
    }

    @Test fun mergeSmallSlices_otherInternalDescByCents() {
        // 输入已倒序：9000, 300, 200, 100 → 后三个 <5% 并入，内部保持金额倒序
        val data = ChartModels.mergeSmallSlices(bd(9000, 300, 200, 100))
        assertEquals(listOf(300L, 200L, 100L), data.otherItems.map { it.cents })
        val other = ChartModels.otherSlice(data)!!
        assertEquals(600L, other.cents)
        assertEquals(0.0625f, other.ratio, 0.0001f) // 600/9600，别手算成 0.06（教训）
        assertTrue(data.conserved)
    }

    @Test fun mergeSmallSlices_zeroTotal_empty() {
        val data = ChartModels.mergeSmallSlices(emptyList())
        assertEquals(0L, data.totalCents)
        assertNull(ChartModels.otherSlice(data))
    }

    @Test fun mergeSmallSlices_conservationLaw() {
        val data = ChartModels.mergeSmallSlices(bd(5000, 2500, 1500, 600, 300, 100))
        val allSlices = data.mainSlices.sumOf { it.cents } + (ChartModels.otherSlice(data)?.cents ?: 0)
        assertEquals(data.totalCents, allSlices)
        val ratioSum = data.mainSlices.sumOf { it.ratio.toDouble() } + (ChartModels.otherSlice(data)?.ratio?.toDouble() ?: 0.0)
        assertTrue(ratioSum in 0.999..1.001)
    }
}
