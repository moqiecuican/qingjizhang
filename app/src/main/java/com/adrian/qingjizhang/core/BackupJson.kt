package com.adrian.qingjizhang.core

import com.adrian.qingjizhang.data.local.BudgetEntity
import com.adrian.qingjizhang.data.local.CategoryEntity
import com.adrian.qingjizhang.data.local.RecordEntity
import com.adrian.qingjizhang.data.local.RecurringRuleEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON 备份格式（V1.1 T4.1；V1.3 升 v2）。
 *
 * 设计决策：
 * - 与 CSV 分工：CSV 给人看/给 Excel，JSON 是完整机器可读备份，恢复用它。
 * - formatVersion：将来格式演进时的兼容开关（恢复方拒绝更高版本，提示升级 app）。
 * - 全量快照：categories + records + budgets + recurringRules 四表整体导出；恢复=清空重灌，
 *   保留原始 id，表间引用（records.categoryId / records.recurringRuleId）天然一致。
 * - clientUid/createdAt/updatedAt 全带上——这就是跨设备合并的数据地基。
 * - **v2（1.3）新增 recurringRules**：不纳入备份会导致恢复后周期规则全丢、
 *   且历史自动账的 recurringRuleId 变成悬空引用。旧版 v1 备份没有该字段 → 默认空列表。
 */
object BackupJson {

    const val FORMAT_VERSION = 2

    @Serializable
    data class File(
        val app: String = "qingjizhang",
        val formatVersion: Int = FORMAT_VERSION,
        val exportedAtMillis: Long,
        val categories: List<CategoryEntity>,
        val records: List<RecordEntity>,
        val budgets: List<BudgetEntity>,
        /** v2（1.3）：周期记账规则；旧版备份缺失时默认空（保持可恢复） */
        val recurringRules: List<RecurringRuleEntity> = emptyList(),
    )

    // encodeDefaults=true：app/formatVersion 必须显式写进备份文件——
    // 否则缺省值被省略，恢复方的「app 标识/版本」校验形同虚设（真机实测发现）
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun build(
        categories: List<CategoryEntity>,
        records: List<RecordEntity>,
        budgets: List<BudgetEntity>,
        recurringRules: List<RecurringRuleEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
    ): String = json.encodeToString(
        File.serializer(),
        File(
            exportedAtMillis = nowMillis,
            categories = categories,
            records = records,
            budgets = budgets,
            recurringRules = recurringRules,
        )
    )

    /** 解析失败抛 IllegalArgumentException（带用户可读原因），成功返回快照 */
    fun parse(text: String): File {
        val f = try {
            json.decodeFromString(File.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("文件不是有效的轻记账备份（JSON 解析失败）")
        }
        if (f.app != "qingjizhang") throw IllegalArgumentException("文件不是轻记账的备份（app 标识不符）")
        if (f.formatVersion > FORMAT_VERSION) {
            throw IllegalArgumentException("备份来自更新版本的 app（格式 v${f.formatVersion}），请先升级轻记账")
        }
        return f
    }

    fun fileName(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String {
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "轻记账备份_${now.year}${p(now.monthValue)}${p(now.dayOfMonth)}_${p(now.hour)}${p(now.minute)}.json"
    }
}
