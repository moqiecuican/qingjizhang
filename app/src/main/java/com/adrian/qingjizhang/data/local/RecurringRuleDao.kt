package com.adrian.qingjizhang.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    /** 规则管理页列表：按创建顺序稳定排序 */
    @Query("SELECT * FROM recurring_rules ORDER BY createdAtMillis ASC, id ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getAll(): List<RecurringRuleEntity>

    /** 补录用：只看启用中的规则（暂停的不生成） */
    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getActive(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    /**
     * 只推进补录游标（不走 @Update 整行覆盖，避免并发下把用户刚改的金额冲掉）。
     * 正常补录路径：先按区间生成账，再把游标推到今天。
     */
    @Query("UPDATE recurring_rules SET lastOccurredEpochDay = :day WHERE id = :id")
    suspend fun advanceCursor(id: Long, day: Long)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    // ---------- JSON 备份/恢复（V1.3 T2.6）----------

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()

    /** 恢复专用：保留备份里的原始 id（REPLACE 冲突即覆盖同号行） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPreservingId(rules: List<RecurringRuleEntity>)
}
