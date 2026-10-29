package com.adrian.qingjizhang.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 月度预算（V1.1）：每个 YearMonth 至多一条支出总预算。
 * yearMonthKey = "${year}-${month}"（如 "2026-09"），字符串主键天然唯一、可读、排序正确。
 * amountCents 用「分」，与流水同一铁律。@Serializable 供 JSON 备份用。
 */
@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val yearMonthKey: String,
    val amountCents: Long,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

/** YearMonth ↔ 预算主键互转（纯函数，可测试） */
fun java.time.YearMonth.toBudgetKey(): String = "$year-${monthValue.toString().padStart(2, '0')}"

