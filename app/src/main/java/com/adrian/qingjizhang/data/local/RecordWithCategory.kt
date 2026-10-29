package com.adrian.qingjizhang.data.local

import androidx.room.Embedded

/** 流水 + 所属分类名（列表显示用，JOIN 查询的返回类型） */
data class RecordWithCategory(
    @Embedded val record: RecordEntity,
    val categoryName: String,
)
