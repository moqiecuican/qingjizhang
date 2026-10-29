package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.RecordEntity
import com.adrian.qingjizhang.data.local.RecurrencePeriod
import com.adrian.qingjizhang.data.local.RecurringRuleEntity

/** 一条待补录的到期项：哪条规则、哪一天到期 */
data class DueEntry(
    val rule: RecurringRuleEntity,
    val dateEpochDay: Long,
)

/**
 * 补录计划（V1.3 T2.1 纯函数）。
 *
 * 「打开时补录」的全部决策逻辑都在这里，不碰数据库 → 可纯单测：
 * 1. 只看**启用中**的规则（暂停的不生成）
 * 2. 每规则算 `(lastOccurredEpochDay, 今天]` 内的到期日（含今天，由 [Recurrence] 保证）
 * 3. 输出扁平列表，按 (规则, 日期) 升序 —— 稳定顺序便于测试与幂等插入
 *
 * 真正的落库由仓储层负责（单事务 + IGNORE 冲突策略，数据库层幂等）。
 */
object Catchup {

    fun plan(rules: List<RecurringRuleEntity>, today: Long): List<DueEntry> =
        rules.asSequence()
            .filter { it.isActive }
            .flatMap { rule ->
                Recurrence.dueOccurrences(rule, rule.lastOccurredEpochDay, today)
                    .map { DueEntry(rule, it) }
            }
            .sortedWith(compareBy({ it.rule.id }, { it.dateEpochDay }))
            .toList()

    /**
     * 待补录项 → 实际流水实体。
     * **备注从规则复制**（用户特别要求：每次自动入账都把规则备注写进生成账）。
     * recurringRuleId 标记来源；clientUid 由 RecordEntity 默认值随机生成。
     */
    fun toRecord(entry: DueEntry, nowMillis: Long): RecordEntity = RecordEntity(
        amountCents = entry.rule.amountCents,
        isExpense = entry.rule.isExpense,
        categoryId = entry.rule.categoryId,
        dateEpochDay = entry.dateEpochDay,
        note = entry.rule.note,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
        recurringRuleId = entry.rule.id,
    )

    /** 规则列表页的中文节奏描述，如「每月 5 号」「每周一」「每年 6 月 15 日」 */
    fun describe(rule: RecurringRuleEntity): String = when (rule.period) {
        RecurrencePeriod.WEEKLY -> "每${Recurrence.weekdayLabel(rule.dayOfWeek)}"
        RecurrencePeriod.MONTHLY -> "每月 ${rule.dayOfMonth} 号"
        RecurrencePeriod.YEARLY -> "每年 ${rule.monthOfYear} 月 ${rule.dayOfMonth} 日"
    }
}
