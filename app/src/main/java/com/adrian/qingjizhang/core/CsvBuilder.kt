package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.RecordWithCategory
import java.time.LocalDate

/**
 * CSV 生成（纯 Kotlin 逻辑部分，可直接单元测试）。
 * 列：日期,类型,分类,金额(元),备注
 * - UTF-8 BOM 由写文件的调用方负责加（见 ExportCsv 的 Activity 侧代码）
 * - 字段含逗号/引号/换行时按 RFC 4180 转义：双引号包裹、内部引号翻倍
 */
object CsvBuilder {

    private const val HEADER = "日期,时间,类型,分类,金额(元),备注"

    fun escape(field: String): String {
        return if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else field
    }

    fun build(rows: List<RecordWithCategory>): String = buildString {
        append(HEADER).append("\r\n")
        for (row in rows) {
            val r = row.record
            append(LocalDate.ofEpochDay(r.dateEpochDay).toString()).append(',')
            append(formatTimeOfDay(r.createdAtMillis)).append(',')
            append(if (r.isExpense) "支出" else "收入").append(',')
            append(escape(row.categoryName)).append(',')
            append(Money.formatYuan(r.amountCents)).append(',')
            append(escape(r.note)).append("\r\n")
        }
    }

    /** 文件名：轻记账_20260930_0130.csv */
    fun fileName(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String {
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "轻记账_${now.year}${p(now.monthValue)}${p(now.dayOfMonth)}_${p(now.hour)}${p(now.minute)}.csv"
    }
}
