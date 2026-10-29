package com.adrian.qingjizhang.data.local

/** 按天聚合行（统计柱状图数据源；SQL 只出按天粒度，周/月/年分桶在 core 纯函数做） */
data class DayTotalRow(
    val dateEpochDay: Long,
    val incomeCents: Long,
    val expenseCents: Long,
)

/** 分类支出聚合行（环形图数据源，金额倒序） */
data class CategoryBreakdownRow(
    val name: String,
    val cents: Long,
)
