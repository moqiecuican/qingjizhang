package com.adrian.qingjizhang.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * 一笔流水。金额用「分」(Long)，日期用 LocalDate.toEpochDay() (Long)。
 * 不用浮点、不用字符串存日期 —— 记账 app 两条铁律。
 *
 * 合并地基三件套（V1.1 引入，为将来跨设备合并铺路）：
 * - clientUid：创建时随机生成，终身不变。多设备导入同一份备份时靠它识别
 *   「同一笔账」——自增 id 在两台设备上必然撞号，时间戳也可能同秒，都不能当身份。
 * - createdAtMillis：创建时刻（UTC 毫秒），UI 显示 HH:mm 用它。
 * - updatedAtMillis：每次编辑刷新，将来冲突裁决「后写的赢」用它。
 *
 * @Serializable 供 JSON 备份用（BackupJson），与 Room 注解互不干扰。
 */
@Serializable
@Entity(
    tableName = "records",
    indices = [
        Index("dateEpochDay"),
        Index("categoryId"),
        Index(value = ["clientUid"], unique = true),
        // 幂等保险（V1.3）：自动账「同规则同日期」至多一笔，从数据库层杜绝重复补录。
        // 用普通唯一索引而非部分索引（Room 注解无法声明部分索引）：
        // SQLite 唯一索引里 NULL 互不相等 → 手动账（recurringRuleId=NULL）永不互撞，
        // 等价于 `WHERE recurringRuleId IS NOT NULL` 的部分唯一索引。
        Index(value = ["recurringRuleId", "dateEpochDay"], unique = true),
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    /** true = 支出, false = 收入 */
    val isExpense: Boolean,
    val categoryId: Long,
    /** java.time.LocalDate.toEpochDay()，本地日期（非 UTC 时刻） */
    val dateEpochDay: Long,
    val note: String = "",
    /** 创建时刻，用于同日排序、UI 时间显示与追溯 */
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** 全局唯一身份（跨设备合并用），创建后不可变 */
    val clientUid: String = UUID.randomUUID().toString(),
    /** 最后编辑时刻；新建时 = createdAtMillis */
    val updatedAtMillis: Long = System.currentTimeMillis(),
    /**
     * 非空 = 该笔由某条周期规则自动生成（V1.3）；null = 手动记账。
     * 规则删除后本列**保留原值**：标「自动」是历史事实，永久可追溯
     * （recurring_rules.id 用 AUTOINCREMENT 永不复用，故不会误领新规则）。
     */
    val recurringRuleId: Long? = null,
)
