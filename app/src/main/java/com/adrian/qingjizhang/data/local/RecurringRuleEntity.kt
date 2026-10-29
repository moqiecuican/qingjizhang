package com.adrian.qingjizhang.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 周期粒度（V1.3）：每周 / 每月 / 每年。
 * Room 原生支持枚举（存 name 为 TEXT），无需 TypeConverter。
 */
@Serializable
enum class RecurrencePeriod { WEEKLY, MONTHLY, YEARLY }

/**
 * 周期记账规则（V1.3）：「每月 5 号 房租 3500 元」这样一条长期节奏。
 *
 * 设计要点（对应计划书）：
 * - `lastOccurredEpochDay` 是**补录游标**：每次打开 app 只补 `(游标, 今天]` 区间内到期的账，
 *   补完把游标推进到今天。好处：用户删掉某条自动账，下次打开不会"复活"（已越过游标）。
 * - `startDateEpochDay` 之前不生成任何账（新规则默认 = 创建后的第一个到期日，不回溯历史）。
 * - `note` 是**规则备注**：每次自动入账时原样复制进生成的那笔账（用户特别要求）。
 * - 主键 autoGenerate 生成 `INTEGER PRIMARY KEY AUTOINCREMENT` → id 永不复用：
 *   删除规则后，历史自动账（records.recurringRuleId）不会因为 id 被新规则复用而误领「自动」标。
 * - 本表不加 FK 约束：规则删除后历史账的 recurringRuleId 保留（悬空但无副作用），
 *   目的是让「这笔是自动账」这个历史事实永久可追溯。
 */
@Serializable
@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 规则名，用户手动输入（不做预设、不做重名约束），必填非空 */
    val name: String,
    /** 金额用「分」(Long)，与流水同一铁律 */
    val amountCents: Long,
    /** true = 支出, false = 收入 */
    val isExpense: Boolean,
    val categoryId: Long,
    val period: RecurrencePeriod,
    /** WEEKLY: 1=周一 … 7=周日；其余周期恒为 0 */
    val dayOfWeek: Int = 0,
    /** MONTHLY / YEARLY: 1..31（当月在 31 号不存在时截断到当月最后一天）；WEEKLY 恒为 0 */
    val dayOfMonth: Int = 0,
    /** YEARLY: 1..12；其余周期恒为 0 */
    val monthOfYear: Int = 0,
    /** 起始日（epochDay，本地日期）：不早于此日生成 */
    val startDateEpochDay: Long,
    /** 补录游标（epochDay）：最后一次已生成到的日期；新规则初始 = startDateEpochDay - 1 */
    val lastOccurredEpochDay: Long,
    /** 规则备注：每次自动入账时自动写入生成账的 note */
    val note: String = "",
    /** 启用（暂停 = false：不再生成新账，历史账保留） */
    val isActive: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)
