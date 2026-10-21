package com.adrian.qingjizhang.core

/**
 * 月度统计（纯 Kotlin，可测试、可迁移）。
 * 入参是某条流水的原始字段，聚合逻辑放在 SQL（DAO）里做，
 * 这里只做结果组装与结余计算，避免把整表读进内存。
 */
data class MonthStats(
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

/** 一天的汇总（用于流水列表日分组头） */
data class DayTotals(
    val dateEpochDay: Long,
    val incomeCents: Long,
    val expenseCents: Long,
)
