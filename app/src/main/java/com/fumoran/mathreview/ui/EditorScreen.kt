package com.fumoran.mathreview.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fumoran.mathreview.AppViewModel
import com.fumoran.mathreview.data.JsonCodec
import com.fumoran.mathreview.data.Knowledge
import com.fumoran.mathreview.data.LibType
import com.fumoran.mathreview.data.Question
import com.fumoran.mathreview.data.STATUS_LABEL
import com.fumoran.mathreview.ui.components.SectionLabel
import com.fumoran.mathreview.ui.components.TagPill
import com.fumoran.mathreview.ui.components.toast

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    vm: AppViewModel,
    type: LibType,
    id: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val existingQ = if (type == LibType.Q) id?.let { qid -> vm.data.questions.firstOrNull { it.id == qid } } else null
    val existingK = if (type == LibType.K) id?.let { kid -> vm.data.knowledge.firstOrNull { it.id == kid } } else null

    var stem by remember { mutableStateOf(existingQ?.stem ?: "") }
    var analysis by remember { mutableStateOf(existingQ?.analysis ?: "") }
    var knowledgeText by remember { mutableStateOf(existingQ?.knowledge ?: "") }
    var myAnswer by remember { mutableStateOf(existingQ?.myAnswer ?: "") }
    var title by remember { mutableStateOf(existingK?.title ?: "") }
    var content by remember { mutableStateOf(existingK?.content ?: "") }

    var tags by remember { mutableStateOf(existingQ?.tags ?: existingK?.tags ?: emptyList()) }
    var tagInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(existingQ?.status ?: existingK?.status ?: "new") }
    var star by remember { mutableStateOf(existingQ?.star ?: existingK?.star ?: false) }
    var collectionId by remember { mutableStateOf(existingQ?.collectionId ?: existingK?.collectionId) }
    var kpIds by remember { mutableStateOf(existingQ?.kpIds ?: emptyList()) }
    var images by remember { mutableStateOf(existingQ?.images ?: existingK?.images ?: emptyList()) }

    var showCollection by remember { mutableStateOf(false) }
    var showKp by remember { mutableStateOf(false) }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            vm.addImages(uris) { ids, err ->
                if (ids != null) images = images + ids
                toast(context, err)
            }
        }
    }

    val cols = vm.data.collections.forType(type)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "新建" else "编辑") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val now = System.currentTimeMillis()
                        if (type == LibType.Q) {
                            if (stem.isBlank()) {
                                toast(context, "请填写题目")
                                return@IconButton
                            }
                            val base = existingQ ?: Question(id = JsonCodec.uid(), createdAt = now)
                            vm.saveQuestion(
                                base.copy(
                                    stem = stem,
                                    analysis = analysis,
                                    knowledge = knowledgeText,
                                    myAnswer = myAnswer,
                                    tags = tags,
                                    status = status,
                                    star = star,
                                    collectionId = collectionId,
                                    kpIds = kpIds,
                                    images = images
                                )
                            ) { err ->
                                if (err == null) onSaved() else toast(context, err)
                            }
                        } else {
                            if (title.isBlank()) {
                                toast(context, "请填写标题")
                                return@IconButton
                            }
                            val base = existingK ?: Knowledge(id = JsonCodec.uid(), createdAt = now)
                            vm.saveKnowledge(
                                base.copy(
                                    title = title,
                                    content = content,
                                    tags = tags,
                                    status = status,
                                    star = star,
                                    collectionId = collectionId,
                                    images = images
                                )
                            ) { err ->
                                if (err == null) onSaved() else toast(context, err)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Check, "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (type == LibType.Q) {
                OutlinedTextField(
                    value = stem,
                    onValueChange = { stem = it },
                    label = { Text("题目（公式用 \$...\$）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp)
                )
                OutlinedTextField(
                    value = analysis,
                    onValueChange = { analysis = it },
                    label = { Text("解析") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                )
                OutlinedTextField(
                    value = myAnswer,
                    onValueChange = { myAnswer = it },
                    label = { Text("我的错误答案（可选）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                )
                OutlinedTextField(
                    value = knowledgeText,
                    onValueChange = { knowledgeText = it },
                    label = { Text("知识点讲解") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                )
            } else {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("讲解内容（公式用 \$...\$）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                )
            }

            SectionLabel("状态")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STATUS_LABEL.forEach { (key, label) ->
                    FilterChip(
                        selected = status == key,
                        onClick = { status = key },
                        label = { Text(label) }
                    )
                }
                FilterChip(
                    selected = star,
                    onClick = { star = !star },
                    label = { Text("收藏") }
                )
            }

            SectionLabel("标签")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier.combinedClickable(
                            onClick = { tags = tags - tag },
                            onLongClick = { tags = tags - tag }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TagPill(tag)
                        Icon(
                            Icons.Default.Close,
                            "移除",
                            modifier = Modifier.size(14.dp).padding(start = 2.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("新增标签") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val t = tagInput.trim()
                    if (t.isNotEmpty()) {
                        tags = JsonCodec.mergeTags(tags, listOf(t))
                        tagInput = ""
                    }
                }) { Text("添加") }
            }

            SectionLabel("合集")
            AssistChip(
                onClick = { showCollection = true },
                label = {
                    Text(
                        collectionId?.let { JsonCodec.collectionPathName(cols, it) } ?: "未归档（点击选择）"
                    )
                }
            )

            if (type == LibType.Q) {
                SectionLabel("关联知识点")
                AssistChip(
                    onClick = { showKp = true },
                    label = { Text("已选择 ${kpIds.size} 个") },
                    leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                )
            }

            SectionLabel("配图")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images) { img ->
                    Box {
                        AsyncImage(
                            model = vm.imageFile(img),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { images = images - img },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, "移除", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                item {
                    Button(
                        onClick = { pickImages.launch("image/*") },
                        modifier = Modifier.size(96.dp)
                    ) {
                        Icon(Icons.Default.Add, "添加图片")
                    }
                }
            }
        }
    }

    if (showCollection) {
        CollectionPickDialog(
            title = "选择合集",
            cols = cols,
            currentId = collectionId,
            allowAll = true,
            onDismiss = { showCollection = false },
            onPick = { picked ->
                collectionId = if (picked == "") null else picked
                showCollection = false
            }
        )
    }

    if (showKp) {
        val all = vm.data.knowledge
        val temp = remember { mutableStateOf(kpIds.toSet()) }
        AlertDialog(
            onDismissRequest = { showKp = false },
            title = { Text("关联知识点") },
            text = {
                if (all.isEmpty()) {
                    Text("暂无知识点，请先创建")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(all, key = { it.id }) { k ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            temp.value = if (k.id in temp.value) temp.value - k.id
                                            else temp.value + k.id
                                        },
                                        onLongClick = {}
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = k.id in temp.value,
                                    onCheckedChange = { checked ->
                                        temp.value = if (checked) temp.value + k.id else temp.value - k.id
                                    }
                                )
                                Text(k.title.ifBlank { "（无标题）" })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    kpIds = temp.value.toList()
                    showKp = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showKp = false }) { Text("取消") } }
        )
    }
}
