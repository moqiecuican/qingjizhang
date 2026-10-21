package com.adrian.qingjizhang.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** formatTimeOfDay 专属测试：固定时区注入，断言精确值（CsvBuilderTest 只验接线） */
class FormatTimeOfDayTest {

    private val utc = ZoneId.of("UTC")
    private val cst = ZoneId.of("Asia/Shanghai")

    @Test fun epoch0_utc() {
        assertEquals("00:00", formatTimeOfDay(0, utc))
    }

    @Test fun epoch0_shanghai() {
        // 1970-01-01T00:00Z = 北京时间 08:00
        assertEquals("08:00", formatTimeOfDay(0, cst))
    }

    @Test fun specificTime_shanghai() {
        // 2026-09-30T14:23 北京时间 = 06:23 UTC；用 UTC 毫秒构造：
        // 06:23 UTC → 毫秒 = (6*3600+23*60)*1000 相对当日零点
        val dayStartUtc = java.time.LocalDate.of(2026, 9, 30).atStartOfDay(utc).toInstant().toEpochMilli()
        val millis = dayStartUtc + ((6 * 3600 + 23 * 60) * 1000L)
        assertEquals("14:23", formatTimeOfDay(millis, cst))
    }

    @Test fun singleDigitPadded() {
        val dayStartUtc = java.time.LocalDate.of(2026, 9, 30).atStartOfDay(utc).toInstant().toEpochMilli()
        val millis = dayStartUtc + (1 * 3600 + 5 * 60) * 1000L // 01:05 UTC
        assertEquals("01:05", formatTimeOfDay(millis, utc))
    }
}
