package com.adrian.qingjizhang.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.adrian.qingjizhang.core.MonthStats
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Delete
    suspend fun delete(record: RecordEntity)

    /** 区间流水（含两端），按日期倒序、同日按创建时间倒序 */
    @Query(
        """SELECT r.*, c.name AS categoryName FROM records r
           JOIN categories c ON c.id = r.categoryId
           WHERE r.dateEpochDay BETWEEN :fromDay AND :toDay
           ORDER BY r.dateEpochDay DESC, r.createdAtMillis DESC, r.id DESC"""
    )
    fun observeRecordsWithCategory(fromDay: Long, toDay: Long): Flow<List<RecordWithCategory>>

    /** 全量流水（导出 CSV 用，不分页） */
    @Query(
        """SELECT r.*, c.name AS categoryName FROM records r
           JOIN categories c ON c.id = r.categoryId
           ORDER BY r.dateEpochDay ASC, r.createdAtMillis ASC, r.id ASC"""
    )
    suspend fun getAllWithCategory(): List<RecordWithCategory>

    /** 月度汇总：一条 SQL 出收支两个 SUM（COALESCE 处理空表） */
    @Query(
        """SELECT COALESCE(SUM(CASE WHEN isExpense THEN 0 ELSE amountCents END), 0) AS incomeCents,
                  COALESCE(SUM(CASE WHEN isExpense THEN amountCents ELSE 0 END), 0) AS expenseCents
           FROM records WHERE dateEpochDay BETWEEN :fromDay AND :toDay"""
    )
    fun observeMonthStats(fromDay: Long, toDay: Long): Flow<MonthStats>

    /** 某分类下的流水条数（删除分类前保护检查） */
    @Query("SELECT COUNT(*) FROM records WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: Long): RecordEntity?

    /**
     * 今日支出聚合（V1.4 小组件）：SUM + 笔数 + 最近一笔时刻，一条 SQL 出齐。
     * 只算支出（isExpense = 1）且限定当天（dateEpochDay = :day，本地日期铁律）。
     */
    @Query(
        """SELECT COALESCE(SUM(amountCents), 0) AS expenseCents,
                  COUNT(*) AS count,
                  MAX(createdAtMillis) AS lastMillis
           FROM records
           WHERE isExpense = 1 AND dateEpochDay = :day"""
    )
    suspend fun todayExpense(day: Long): TodayExpenseRow

    /**
     * 周期补录专用（V1.3）：与唯一索引 (recurringRuleId, dateEpochDay) 冲突时**跳过**（返回 -1）。
     * 这是"重复补录不入账"的数据库层保险——幂等不靠调用方小心翼翼，靠约束本身。
     */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflicts(record: RecordEntity): Long

    // ---------- JSON 备份/恢复（V1.1 T4）----------

    /** 全量流水原始实体（备份导出用；保留原 id 与 clientUid） */
    @Query("SELECT * FROM records ORDER BY id ASC")
    suspend fun getAllEntities(): List<RecordEntity>

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    /** 恢复专用：保留备份里的原始 id（REPLACE 冲突即覆盖同号行） */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAllPreservingId(records: List<RecordEntity>)

    // ---------- 统计聚合（V1.2 T1.1）：SQL 只出按天/按分类粒度 ----------

    /** 按天聚合收支（柱状图数据源；周/月/年分桶在 core 纯函数做，天然无时区问题） */
    @Query(
        """SELECT dateEpochDay,
                  COALESCE(SUM(CASE WHEN isExpense THEN 0 ELSE amountCents END), 0) AS incomeCents,
                  COALESCE(SUM(CASE WHEN isExpense THEN amountCents ELSE 0 END), 0) AS expenseCents
           FROM records
           WHERE dateEpochDay BETWEEN :fromDay AND :toDay
           GROUP BY dateEpochDay
           ORDER BY dateEpochDay ASC"""
    )
    fun observeDayTotals(fromDay: Long, toDay: Long): Flow<List<DayTotalRow>>

    /** 按分类聚合支出（环形图数据源，金额倒序；只统计支出） */
    @Query(
        """SELECT c.name AS name, SUM(r.amountCents) AS cents
           FROM records r JOIN categories c ON c.id = r.categoryId
           WHERE r.isExpense = 1 AND r.dateEpochDay BETWEEN :fromDay AND :toDay
           GROUP BY c.id
           ORDER BY cents DESC"""
    )
    fun observeCategoryBreakdown(fromDay: Long, toDay: Long): Flow<List<CategoryBreakdownRow>>
}
