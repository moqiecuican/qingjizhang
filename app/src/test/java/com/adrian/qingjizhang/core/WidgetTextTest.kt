package com.adrian.qingjizhang.core

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** 小组件文案纯函数单测（V1.4 T4.2：跨零点/空表/多笔边界） */
class WidgetTextTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun dateLabel_当天日期() {
        assertEquals("今日支出 · 10月18日", WidgetText.dateLabel(LocalDate.of(2026, 10, 18).toEpochDay()))
        assertEquals("今日支出 · 1月1日", WidgetText.dateLabel(LocalDate.of(2027, 1, 1).toEpochDay()))
    }

    /** 跨零点：不同 epochDay → 标签必须不同（用户据此识别数字属于哪天） */
    @Test
    fun dateLabel_跨天变化() {
        val d1 = LocalDate.of(2026, 10, 18).toEpochDay()
        val d2 = LocalDate.of(2026, 10, 19).toEpochDay()
        assertEquals(false, WidgetText.dateLabel(d1) == WidgetText.dateLabel(d2))
        assertEquals("今日支出 · 10月19日", WidgetText.dateLabel(d2))
    }

    @Test
    fun auxLine_空态() {
        assertEquals("今日还没有记账", WidgetText.auxLine(0, null, zone))
        assertEquals("今日还没有记账", WidgetText.auxLine(0, 1759000000000, zone))
    }

    @Test
    fun auxLine_多笔带最近时间() {
        // 2026-10-18 21:35 上海时间
        val millis = java.time.LocalDateTime.of(2026, 10, 18, 21, 35)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals("共 4 笔 · 最近 21:35", WidgetText.auxLine(4, millis, zone))
        assertEquals("共 1 笔 · 最近 09:05",
            WidgetText.auxLine(1, java.time.LocalDateTime.of(2026, 10, 18, 9, 5)
                .atZone(zone).toInstant().toEpochMilli(), zone))
    }

    @Test
    fun amountLine_格式与空占位() {
        assertEquals("¥0.00", WidgetText.amountLine(0))
        assertEquals("¥126.50", WidgetText.amountLine(12650))
        assertEquals("¥1234.00", WidgetText.amountLine(123400))
    }
}
