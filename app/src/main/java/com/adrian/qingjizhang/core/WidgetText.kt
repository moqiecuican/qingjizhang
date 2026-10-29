package com.adrian.qingjizhang.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 桌面小组件的文案（V1.4 §0.1 规格），纯函数、可单测：
 * - 主数字：「¥126.50」（Money.formatYuan）
 * - 标签行：「今日支出 · 10月18日」（**必须带日期**：跨零点时用户能判断数字属于哪天）
 * - 辅助行（2×2）：「共 4 笔 · 最近 21:35」；当天无支出 → 「今日还没有记账」
 */
object WidgetText {

    /** 「今日支出 · M月d日」 */
    fun dateLabel(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return "今日支出 · ${d.monthValue}月${d.dayOfMonth}日"
    }

    /**
     * 2×2 辅助行。空态（没有支出）返回「今日还没有记账」；
     * 有支出时「共 N 笔 · 最近 HH:mm」（时间按系统时区格式化）。
     */
    fun auxLine(count: Int, lastMillis: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (count <= 0 || lastMillis == null) return "今日还没有记账"
        val t = Instant.ofEpochMilli(lastMillis).atZone(zone).toLocalTime()
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "共 $count 笔 · 最近 ${p(t.hour)}:${p(t.minute)}"
    }

    /** 主数字；空态仍给 ¥0.00 占位（数字不消失，避免 widget 布局跳动） */
    fun amountLine(expenseCents: Long): String = "¥" + Money.formatYuan(expenseCents)
}
