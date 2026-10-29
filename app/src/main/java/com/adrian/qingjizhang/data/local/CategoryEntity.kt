package com.adrian.qingjizhang.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 分类。isExpense 区分支出组/收入组；name 在同组内唯一（由 Repository 层校验）。@Serializable 供 JSON 备份用。 */
@Serializable
@Entity(tableName = "categories", indices = [Index(value = ["isExpense", "name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isExpense: Boolean,
    val sortOrder: Int,
)
