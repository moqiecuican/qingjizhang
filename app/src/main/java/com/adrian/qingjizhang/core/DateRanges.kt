package com.adrian.qingjizhang.core

import java.time.LocalDate
import java.time.YearMonth

/** 某自然月的 epochDay 闭区间 [月初, 月末]（月度查询边界统一走这里，避免手滑少一天） */
fun YearMonth.rangeEpochDays(): Pair<Long, Long> =
    atDay(1).toEpochDay() to atEndOfMonth().toEpochDay()

/** 测试里的简写别名 */
fun YearMonth.range(): Pair<Long, Long> = rangeEpochDays()

/** 列表日分组头的显示文本，如 "9月30日 周三" */
fun formatDayHeader(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val week = when (d.dayOfWeek.value) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; else -> "周日"
    }
    return "${d.monthValue}月${d.dayOfMonth}日 $week"
}

/** 月标题，如 "2026年9月" */
fun formatMonth(ym: YearMonth): String = "${ym.year}年${ym.monthValue}月"

/**
 * 流水行的时间显示（时:分），如 "14:23"。
 * 语义 = 系统默认时区的本地时间（记账是「我此刻花了钱」，不是 UTC 时刻）。
 * 纯函数逻辑：Instant → 系统时区 → HH:mm。测试时用固定 ZoneId 注入。
 */
fun formatTimeOfDay(epochMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val t = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "${p(t.hour)}:${p(t.minute)}"
}
