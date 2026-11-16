package com.fumoran.mathreview.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fumoran.mathreview.AppViewModel
import com.fumoran.mathreview.data.JsonCodec
import com.fumoran.mathreview.data.Knowledge
import com.fumoran.mathreview.data.LibType
import com.fumoran.mathreview.data.Question
import com.fumoran.mathreview.data.STATUS_LABEL
import com.fumoran.mathreview.data.dueLabel
import com.fumoran.mathreview.data.isDue
import com.fumoran.mathreview.data.nextStatus
import com.fumoran.mathreview.ui.components.EmptyState
import com.fumoran.mathreview.ui.components.StatusBadge
import com.fumoran.mathreview.ui.components.TagPill
import kotlinx.coroutines.launch

private data class RowItem(
    val id: String,
    val main: String,
    val tags: List<String>,
    val status: String,
    val star: Boolean,
    val due: Boolean,
    val dueText: String,
    val collectionId: String?,
    val updatedAt: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    vm: AppViewModel,
    onOpen: (LibType, String) -> Unit,
    onAdd: (LibType) -> Unit,
    onCollections: () -> Unit,
    onSettings: () -> Unit,
    onAiRecognize: () -> Unit,
    onOrganize: (LibType, String) -> Unit
) {
    var tab by remember { mutableStateOf(LibType.Q) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var starOnly by remember { mutableStateOf(false) }
    var dueOnly by remember { mutableStateOf(false) }
    var collectionFilter by remember { mutableStateOf<String?>(null) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(msg: String?) {
        if (msg != null) scope.launch { snackbar.showSnackbar(msg) }
    }

    fun clearSelection() {
        selectMode = false
        selected = emptySet()
    }

    val cols = vm.data.collections.forType(tab)
    val allowedIds = remember(cols, collectionFilter) {
        when (val cf = collectionFilter) {
            null -> null
            "" -> emptySet()
            else -> JsonCodec.subtreeIds(cols, cf)
        }
    }

    val rows: List<RowItem> = when (tab) {
        LibType.Q -> vm.data.questions.filter { q ->
            filterText(query, listOf(q.stem, q.analysis, q.knowledge, q.myAnswer, q.tags.joinToString(" ")), q.tags, q.status, q.star, q.srs) &&
                (statusFilter.isBlank() || q.status == statusFilter) &&
                (!starOnly || q.star) &&
                (!dueOnly || isDue(q.srs)) &&
                (collectionFilter == null || when (collectionFilter) {
                    "" -> q.collectionId == null
                    else -> q.collectionId != null && allowedIds!!.contains(q.collectionId)
                })
        }.map { q ->
            RowItem(
                q.id,
                q.stem,
                q.tags,
                q.status,
                q.star,
                isDue(q.srs),
                dueLabel(q.srs),
                q.collectionId,
                q.updatedAt
            )
        }
        LibType.K -> vm.data.knowledge.filter { k ->
            filterText(query, listOf(k.title, k.content, k.tags.joinToString(" ")), k.tags, k.status, k.star, k.srs) &&
                (statusFilter.isBlank() || k.status == statusFilter) &&
                (!starOnly || k.star) &&
                (!dueOnly || isDue(k.srs)) &&
                (collectionFilter == null || when (collectionFilter) {
                    "" -> k.collectionId == null
                    else -> k.collectionId != null && allowedIds!!.contains(k.collectionId)
                })
        }.map { k ->
            RowItem(
                k.id,
                k.title,
                k.tags,
                k.status,
                k.star,
                isDue(k.srs),
                dueLabel(k.srs),
                k.collectionId,
                k.updatedAt
            )
        }
    }.sortedByDescending { it.updatedAt }

    val firstStatus = when (tab) {
        LibType.Q -> vm.data.questions.firstOrNull { it.id in selected }?.status
        LibType.K -> vm.data.knowledge.firstOrNull { it.id in selected }?.status
    } ?: "new"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数学复习本") },
                actions = {
                    IconButton(onClick = onCollections) { Icon(Icons.Default.Folder, "合集") }
                    IconButton(onClick = onAiRecognize) { Icon(Icons.Default.AutoAwesome, "AI 识别") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(tab) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(if (tab == LibType.Q) "新建错题" else "新建知识点") }
            )
        },
        bottomBar = {
            if (selectMode) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            vm.setStatus(tab, selected.toList(), nextStatus(firstStatus)) { toast(it) }
                            clearSelection()
                        }) { Text("状态") }
                        TextButton(onClick = { showTagDialog = true }) { Text("标签") }
                        TextButton(onClick = { showMoveDialog = true }) { Text("移动") }
                        if (selected.size == 1) {
                            TextButton(onClick = {
                                val id = selected.first()
                                clearSelection()
                                onOrganize(tab, id)
                            }) { Text("AI 整理") }
                        }
                        TextButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = if (tab == LibType.Q) 0 else 1) {
                Tab(selected = tab == LibType.Q, onClick = {
                    tab = LibType.Q
                    clearSelection()
                }, text = { Text("错题 ${vm.data.questions.size}") })
                Tab(selected = tab == LibType.K, onClick = {
                    tab = LibType.K
                    clearSelection()
                }, text = { Text("知识点 ${vm.data.knowledge.size}") })
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("搜索题目 / 标签，支持 tag: 与 status:") },
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statusFilter.isBlank(),
                    onClick = { statusFilter = "" },
                    label = { Text("全部") }
                )
                STATUS_LABEL.forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = if (statusFilter == key) "" else key },
                        label = { Text(label) }
                    )
                }
                FilterChip(
                    selected = starOnly,
                    onClick = { starOnly = !starOnly },
                    label = { Text("收藏") },
                    leadingIcon = if (starOnly) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = dueOnly,
                    onClick = { dueOnly = !dueOnly },
                    label = { Text("待复习") }
                )
                FilterChip(
                    selected = collectionFilter != null,
                    onClick = { showCollectionPicker = true },
                    label = {
                        Text(
                            when (val cf = collectionFilter) {
                                null -> "全部合集"
                                "" -> "未归档"
                                else -> JsonCodec.collectionPathName(cols, cf)
                            }
                        )
                    }
                )
            }

            if (rows.isEmpty()) {
                EmptyState(if (query.isBlank()) "还没有内容，点右下角新建" else "没有匹配的条目")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rows, key = { it.id }) { row ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectMode) {
                                            val next = if (row.id in selected) selected - row.id else selected + row.id
                                            selected = next
                                            if (next.isEmpty()) selectMode = false
                                        } else {
                                            onOpen(tab, row.id)
                                        }
                                    },
                                    onLongClick = {
                                        selectMode = true
                                        selected = selected + row.id
                                    }
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                if (selectMode) {
                                    Checkbox(
                                        checked = row.id in selected,
                                        onCheckedChange = {
                                            selected = if (it) selected + row.id else selected - row.id
                                            if (selected.isEmpty()) selectMode = false
                                        }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = JsonCodec.stripLatex(row.main).ifBlank { "（空）" },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (row.tags.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.padding(top = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            row.tags.take(3).forEach { TagPill(it) }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StatusBadge(row.status)
                                        if (row.due) {
                                            Text(
                                                "待复习",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Text(
                                            row.collectionId?.let { JsonCodec.collectionPathName(cols, it) } ?: "未归档",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.weight(1f))
                                        if (row.star) {
                                            Icon(
                                                Icons.Default.Star,
                                                null,
                                                modifier = Modifier.size(16.dp),
                                                tint = androidx.compose.ui.graphics.Color(0xFFF59E0B)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("添加标签") },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    singleLine = true,
                    label = { Text("标签") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val tag = tagInput.trim()
                    showTagDialog = false
                    tagInput = ""
                    if (tag.isNotEmpty()) {
                        vm.addTag(tab, selected.toList(), tag) { toast(it) }
                        clearSelection()
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showTagDialog = false }) { Text("取消") } }
        )
    }

    if (showMoveDialog) {
        CollectionPickDialog(
            title = "移动到合集",
            cols = cols,
            currentId = null,
            allowAll = false,
            onDismiss = { showMoveDialog = false },
            onPick = { id ->
                showMoveDialog = false
                vm.moveItems(tab, selected.toList(), id) { toast(it) }
                clearSelection()
            }
        )
    }

    if (showCollectionPicker) {
        CollectionPickDialog(
            title = "按合集筛选",
            cols = cols,
            currentId = collectionFilter,
            allowAll = true,
            onDismiss = { showCollectionPicker = false },
            onPick = { id ->
                showCollectionPicker = false
                collectionFilter = id
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除条目") },
            text = { Text("将删除选中的 ${selected.size} 条内容，且无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val ids = selected.toList()
                    clearSelection()
                    vm.deleteItems(tab, ids) { toast(it) }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
fun CollectionPickDialog(
    title: String,
    cols: List<com.fumoran.mathreview.data.Collection>,
    currentId: String?,
    allowAll: Boolean,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    allLabel: String = "全部合集"
) {
    val flat = JsonCodec.flattenCollections(cols)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (allowAll) {
                    PickRow(allLabel, currentId == null) { onPick(null) }
                }
                PickRow("未归档", currentId == "") { onPick("") }
                flat.forEach { (c, depth) ->
                    PickRow(
                        text = "　".repeat(depth) + c.name,
                        selected = currentId == c.id
                    ) { onPick(c.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
    }
}

private fun filterText(
    query: String,
    haystack: List<String>,
    tags: List<String>,
    status: String,
    star: Boolean,
    srs: com.fumoran.mathreview.data.Srs
): Boolean {
    val text = haystack.joinToString(" ").lowercase()
    query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { token ->
        val t = token.lowercase()
        when {
            t.startsWith("tag:") -> {
                val v = t.removePrefix("tag:")
                if (tags.none { it.lowercase().contains(v) }) return false
            }
            t.startsWith("status:") -> {
                val v = t.removePrefix("status:")
                val mapped = when (v) {
                    "未复习" -> "new"
                    "复习中" -> "reviewing"
                    "已掌握" -> "mastered"
                    else -> v
                }
                if (status != mapped) return false
            }
            t == "star" || t == "收藏" -> if (!star) return false
            t == "due" || t == "待复习" -> if (!isDue(srs)) return false
            else -> if (!text.contains(t)) return false
        }
    }
    return true
}
