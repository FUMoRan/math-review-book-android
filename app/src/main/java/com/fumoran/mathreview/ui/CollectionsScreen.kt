package com.fumoran.mathreview.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fumoran.mathreview.AppViewModel
import com.fumoran.mathreview.data.Collection
import com.fumoran.mathreview.data.JsonCodec
import com.fumoran.mathreview.data.LibType
import com.fumoran.mathreview.ui.components.EmptyState
import com.fumoran.mathreview.ui.components.toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(LibType.Q) }

    var editing by remember { mutableStateOf<Collection?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var showParent by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Collection?>(null) }
    var moving by remember { mutableStateOf<Collection?>(null) }
    var showMove by remember { mutableStateOf(false) }

    val cols = vm.data.collections.forType(tab)
    val flat = JsonCodec.flattenCollections(cols)

    fun openEdit(c: Collection?) {
        editing = c
        name = c?.name ?: ""
        parentId = c?.parentId
        showEdit = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("合集管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openEdit(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新建合集") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = if (tab == LibType.Q) 0 else 1) {
                Tab(
                    selected = tab == LibType.Q,
                    onClick = { tab = LibType.Q },
                    text = { Text("错题合集") }
                )
                Tab(
                    selected = tab == LibType.K,
                    onClick = { tab = LibType.K },
                    text = { Text("知识点合集") }
                )
            }

            if (flat.isEmpty()) {
                EmptyState("还没有合集，点右下角新建")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(flat, key = { it.first.id }) { (c, depth) ->
                        val count = when (tab) {
                            LibType.Q -> vm.data.questions.count { it.collectionId == c.id }
                            LibType.K -> vm.data.knowledge.count { it.collectionId == c.id }
                        }
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (12 + depth * 16).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = (if (depth > 0) "↳ " else "") + c.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = {
                                        moving = c
                                        showMove = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) { Icon(Icons.Default.DriveFileMove, "移动", modifier = Modifier.size(18.dp)) }
                                IconButton(onClick = { openEdit(c) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Edit, "重命名", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { pendingDelete = c }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Default.Delete,
                                        "删除",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text(if (editing == null) "新建合集" else "编辑合集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true
                    )
                    TextButton(onClick = { showParent = true }) {
                        Text(
                            "上级：" + (parentId?.let { JsonCodec.collectionPathName(cols, it) } ?: "无（顶级）")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) {
                        toast(context, "请填写名称")
                        return@TextButton
                    }
                    val base = editing ?: Collection(id = "", createdAt = System.currentTimeMillis())
                    vm.saveCollection(tab, base.copy(name = n, parentId = parentId)) { err ->
                        toast(context, err)
                    }
                    showEdit = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("取消") } }
        )
    }

    if (showParent) {
        val blocked = editing?.let { JsonCodec.subtreeIds(cols, it.id) } ?: emptySet()
        val available = cols.filterNot { it.id in blocked }
        CollectionPickDialog(
            title = "选择上级合集",
            cols = available,
            currentId = parentId,
            allowAll = true,
            allLabel = "无（顶级）",
            onDismiss = { showParent = false },
            onPick = { picked ->
                parentId = picked?.takeIf { it.isNotBlank() }
                showParent = false
            }
        )
    }

    val del = pendingDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除合集") },
            text = { Text("「${del.name}」将被删除；其中条目会移到「未归档」，子合集会升为顶级。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.deleteCollection(tab, del.id) { toast(context, it) }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    if (showMove) {
        val target = moving
        val blocked = target?.let { JsonCodec.subtreeIds(cols, it.id) } ?: emptySet()
        val available = cols.filterNot { it.id in blocked }
        CollectionPickDialog(
            title = "移动到",
            cols = available,
            currentId = target?.parentId,
            allowAll = true,
            allLabel = "无（顶级）",
            onDismiss = {
                showMove = false
                moving = null
            },
            onPick = { picked ->
                val c = target
                showMove = false
                moving = null
                if (c != null) {
                    vm.moveCollection(tab, c.id, picked?.takeIf { it.isNotBlank() }) { toast(context, it) }
                }
            }
        )
    }
}
