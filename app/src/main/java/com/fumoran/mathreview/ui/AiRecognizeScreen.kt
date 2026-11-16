package com.fumoran.mathreview.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fumoran.mathreview.AppViewModel
import com.fumoran.mathreview.data.AiParsed
import com.fumoran.mathreview.data.JsonCodec
import com.fumoran.mathreview.data.LibType
import com.fumoran.mathreview.ui.components.MathText
import com.fumoran.mathreview.ui.components.SectionLabel
import com.fumoran.mathreview.ui.components.TagPill
import com.fumoran.mathreview.ui.components.toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRecognizeScreen(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(LibType.Q) }
    var imageIds by remember { mutableStateOf(emptyList<String>()) }
    var raw by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<AiParsed?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var collectionId by remember { mutableStateOf<String?>(null) }
    var showCollection by remember { mutableStateOf(false) }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            vm.addImages(uris) { ids, err ->
                if (ids != null) imageIds = imageIds + ids
                toast(context, err)
            }
        }
    }

    val cols = vm.data.collections.forType(type)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 识别与整理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
            TabRow(selectedTabIndex = if (type == LibType.Q) 0 else 1) {
                Tab(
                    selected = type == LibType.Q,
                    onClick = {
                        type = LibType.Q
                        parsed = null
                    },
                    text = { Text("识别为错题") }
                )
                Tab(
                    selected = type == LibType.K,
                    onClick = {
                        type = LibType.K
                        parsed = null
                    },
                    text = { Text("识别为知识点") }
                )
            }

            SectionLabel("1. 选择图片")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(imageIds) { img ->
                    androidx.compose.foundation.layout.Box {
                        AsyncImage(
                            model = vm.imageFile(img),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { imageIds = imageIds - img },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, "移除", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { pickImages.launch("image/*") },
                        modifier = Modifier.size(96.dp)
                    ) {
                        Icon(Icons.Default.Add, "添加图片")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = !busy && imageIds.isNotEmpty(),
                    onClick = {
                        busy = true
                        busyText = "正在识别图片…"
                        error = ""
                        vm.aiOcr(type, imageIds) { text, err ->
                            busy = false
                            if (text != null) raw = text else error = err ?: "识别失败"
                        }
                    }
                ) { Text("识别文字") }
                Button(
                    enabled = !busy && raw.isNotBlank(),
                    onClick = {
                        busy = true
                        busyText = "正在整理条目…"
                        error = ""
                        vm.aiSolve(type, raw) { p, err ->
                            busy = false
                            if (p != null) parsed = p else error = err ?: "整理失败"
                        }
                    }
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Text("解析整理", modifier = Modifier.padding(start = 6.dp))
                }
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(busyText, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SectionLabel("2. 原文（可编辑，也可直接粘贴 AI 输出）")
            OutlinedTextField(
                value = raw,
                onValueChange = {
                    raw = it
                    parsed = null
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text("识别结果会出现在这里") }
            )

            val p = parsed
            if (p != null) {
                SectionLabel("3. 解析结果（${p.total} 条）")
                if (p.questions.isNotEmpty()) {
                    p.questions.take(20).forEach { q ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                MathText(q.stem, modifier = Modifier.fillMaxWidth())
                                if (q.tags.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        q.tags.forEach { TagPill(it) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (p.knowledge.isNotEmpty()) {
                    p.knowledge.take(20).forEach { k ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(k.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    JsonCodec.stripLatex(k.content),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                AssistChip(
                    onClick = { showCollection = true },
                    label = {
                        Text("导入到：" + (collectionId?.let { JsonCodec.collectionPathName(cols, it) } ?: "未归档"))
                    }
                )
                Button(
                    onClick = {
                        vm.importParsed(type, p, collectionId, imageIds) { s, err ->
                            if (err != null) {
                                toast(context, err)
                            } else {
                                toast(context, "已导入 ${s?.questions ?: 0} 条错题 / ${s?.knowledge ?: 0} 条知识点")
                                onDone()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导入到复习本") }
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
                collectionId = picked?.takeIf { it.isNotBlank() }
                showCollection = false
            }
        )
    }
}
