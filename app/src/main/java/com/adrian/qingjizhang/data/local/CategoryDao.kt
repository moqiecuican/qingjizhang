package com.adrian.qingjizhang.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE isExpense = :isExpense ORDER BY sortOrder ASC, id ASC")
    fun observeCategories(isExpense: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isExpense DESC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    /** 同组内重名检查（唯一索引兜底，这里给 UI 友好提示用） */
    @Query("SELECT COUNT(*) FROM categories WHERE isExpense = :isExpense AND name = :name")
    suspend fun countByName(isExpense: Boolean, name: String): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countAll(): Int

    @Query("SELECT MAX(sortOrder) FROM categories WHERE isExpense = :isExpense")
    suspend fun maxSortOrder(isExpense: Boolean): Int?

    // ---------- JSON 备份/恢复（V1.1 T4）----------

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    /** 恢复专用：保留备份里的原始 id（流水的 categoryId 引用依赖它） */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAllPreservingId(categories: List<CategoryEntity>)
}
