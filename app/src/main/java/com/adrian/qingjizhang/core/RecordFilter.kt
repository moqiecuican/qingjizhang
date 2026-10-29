package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.RecordWithCategory

/**
 * 流水筛选条件（V1.2 Phase 3）。全 null/空 = 不过滤。
 * 关键词用 contains 语义（内存过滤），**不用 SQL LIKE**——
 * 因此 `%` `_` 等通配符危害从设计上不存在：搜 "%" 只会命中真含 % 的备注。
 */
data class RecordFilter(
    val keyword: String = "",
    val categoryId: Long? = null,
    val fromDay: Long? = null,
    val toDay: Long? = null,
) {
    val isActive: Boolean
        get() = keyword.isNotBlank() || categoryId != null || fromDay != null || toDay != null
}

object RecordFiltering {

    fun matches(row: RecordWithCategory, f: RecordFilter): Boolean {
        val r = row.record
        if (f.categoryId != null && r.categoryId != f.categoryId) return false
        if (f.fromDay != null && r.dateEpochDay < f.fromDay) return false
        if (f.toDay != null && r.dateEpochDay > f.toDay) return false
        if (f.keyword.isNotBlank()) {
            val kw = f.keyword.trim()
            if (!r.note.contains(kw, ignoreCase = true) &&
                !row.categoryName.contains(kw, ignoreCase = true)
            ) return false
        }
        return true
    }

    fun apply(rows: List<RecordWithCategory>, f: RecordFilter): List<RecordWithCategory> =
        if (!f.isActive) rows else rows.filter { matches(it, f) }

    /** 筛选生效时的汇总（纯函数重算，与 SQL 月汇总同语义） */
    fun statsOf(rows: List<RecordWithCategory>): MonthStats {
        var income = 0L
        var expense = 0L
        for (row in rows) {
            if (row.record.isExpense) expense += row.record.amountCents else income += row.record.amountCents
        }
        return MonthStats(incomeCents = income, expenseCents = expense)
    }
}
