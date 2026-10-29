package com.adrian.qingjizhang.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adrian.qingjizhang.core.RecordFilter
import com.adrian.qingjizhang.data.local.CategoryEntity

/**
 * 搜索与筛选条（V1.2 Phase 3，流水/统计共享设计——当前流水页使用）。
 * 关键词 contains 语义（内存过滤），% 等字符按字面匹配，无 LIKE 通配符危害。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    filter: RecordFilter,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    onChange: (RecordFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 修复「搜索框光标残留」：按返回收起键盘后，TextField 仍持焦点、光标继续闪，
    // 观感像没退出输入。这里监听 IME 可见性，键盘收起即释放焦点。
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density) > 0
    androidx.compose.runtime.LaunchedEffect(imeVisible) {
        if (!imeVisible) focusManager.clearFocus()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter.keyword,
                onValueChange = { onChange(filter.copy(keyword = it)) },
                label = { Text("搜索备注或分类") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (filter.keyword.isNotEmpty()) {
                        IconButton(onClick = { onChange(filter.copy(keyword = "")) }) {
                            Icon(Icons.Default.Close, contentDescription = "清空关键词")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (filter.isActive) {
                TextButton(onClick = onClear) { Text("清除筛选") }
            }
        }
        Spacer(Modifier.height(6.dp))
        // 分类 chips（支出+收入合并横滚）
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(expenseCategories + incomeCategories, key = { it.id }) { c ->
                FilterChip(
                    selected = filter.categoryId == c.id,
                    onClick = {
                        onChange(
                            if (filter.categoryId == c.id) filter.copy(categoryId = null)
                            else filter.copy(categoryId = c.id)
                        )
                    },
                    label = { Text(c.name) },
                )
            }
        }
        if (filter.isActive) {
            Text(
                "筛选生效中：汇总与列表均已过滤",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
