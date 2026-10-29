package com.adrian.qingjizhang.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrian.qingjizhang.data.local.CategoryEntity
import com.adrian.qingjizhang.theme.Semantic

/**
 * 分类管理页：支出/收入两组，增/改名/删（有流水引用的分类删除会被拒绝并提示）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: LedgerViewModel,
    wide: Boolean,
    contentPadding: PaddingValues,
) {
    var isExpense by rememberSaveable { mutableStateOf(true) }
    val expenseCats by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCats by viewModel.incomeCategories.collectAsStateWithLifecycle()
    val cats = if (isExpense) expenseCats else incomeCats

    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }

    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        Column(
            // widthIn 先于 fillMaxSize，理由同 LedgerScreen（限宽顺序铁律）
            modifier = Modifier.widthIn(max = if (wide) 600.dp else 640.dp).fillMaxSize().padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.padding(top = 8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = isExpense, onClick = { isExpense = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出分类") }
                SegmentedButton(selected = !isExpense, onClick = { isExpense = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入分类") }
            }
            Spacer(Modifier.padding(top = 8.dp))
            FilledTonalButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("新增分类")
            }
            Spacer(Modifier.padding(top = 4.dp))
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(cats, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renaming = c }) {
                                    Icon(Icons.Default.Edit, contentDescription = "改名",
                                        tint = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(onClick = { deleting = c }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除",
                                        tint = Semantic.expense())
                                }
                            }
                        },
                    )
                }
                item { Spacer(Modifier.padding(bottom = 80.dp)) }
            }
        }
    }

    // 新增
    if (showAdd) {
        NameDialog(
            title = if (isExpense) "新增支出分类" else "新增收入分类",
            initial = "",
            onDismiss = { showAdd = false },
            onConfirm = { name -> viewModel.addCategory(name, isExpense); showAdd = false },
        )
    }
    // 改名
    renaming?.let { c ->
        NameDialog(
            title = "重命名「${c.name}」",
            initial = c.name,
            onDismiss = { renaming = null },
            onConfirm = { name -> viewModel.renameCategory(c.id, name); renaming = null },
        )
    }
    // 删除确认
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分类「${c.name}」？") },
            text = { Text("若该分类下已有流水则无法删除。") },
            confirmButton = {
                TextButton(onClick = { deleting = null; viewModel.deleteCategory(c.id) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

/** 分类名输入对话框 */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= 20) value = it },
                label = { Text("分类名") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
