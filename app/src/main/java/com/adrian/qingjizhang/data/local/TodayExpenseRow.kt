package com.adrian.qingjizhang.data.local

/**
 * 今日支出聚合行（V1.4 桌面小组件数据源）。
 * 只统计 isExpense = 1 且 dateEpochDay = 今天 的记录：一笔"今日支出"的语义就是当天所有支出。
 */
data class TodayExpenseRow(
    /** 今日支出合计（分） */
    val expenseCents: Long,
    /** 今日支出笔数 */
    val count: Int,
    /** 今日最近一笔支出的创建时刻（毫秒）；当天无支出时为 null */
    val lastMillis: Long?,
)
