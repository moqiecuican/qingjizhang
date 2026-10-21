package com.adrian.qingjizhang.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE yearMonthKey = :key")
    fun observeBudget(key: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets ORDER BY yearMonthKey")
    suspend fun getAll(): List<BudgetEntity>

    /** upsert：同月已有预算则整体替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE yearMonthKey = :key")
    suspend fun delete(key: String)

    // ---------- JSON 备份/恢复（V1.1 T4）----------

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)
}
