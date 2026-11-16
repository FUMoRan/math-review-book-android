package com.fumoran.mathreview.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.fumoran.mathreview.AppViewModel
import com.fumoran.mathreview.data.JsonCodec
import com.fumoran.mathreview.data.Knowledge
import com.fumoran.mathreview.data.LibType
import com.fumoran.mathreview.data.Question
import com.fumoran.mathreview.data.Srs
import com.fumoran.mathreview.data.STATUS_LABEL
import com.fumoran.mathreview.data.dueLabel
import com.fumoran.mathreview.ui.components.MathText
import com.fumoran.mathreview.ui.components.MetaRow
import com.fumoran.mathreview.ui.components.SectionLabel
import com.fumoran.mathreview.ui.components.StatusBadge
import com.fumoran.mathreview.ui.components.TagPill
import com.fumoran.mathreview.ui.components.formatDate
import com.fumoran.mathreview.ui.components.toast

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    vm: AppViewModel,
    type: LibType,
    id: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOrganize: () -> Unit,
    onOpenKnowledge: (String) -> Unit
) {
    val context = LocalContext.current
    val data = vm.data
    val question: Question? = if (type == LibType.Q) data.questions.firstOrNull { it.id == id } else null
    val knowledge: Knowledge? = if (type == LibType.K) data.knowledge.firstOrNull { it.id == id } else null
    val meta = when {
        question != null -> DetailMeta(
            question.images, question.tags, question.status, question.star,
            question.srs, question.collectionId, question.createdAt, question.updatedAt
        )
        knowledge != null -> DetailMeta(
            knowledge.images, knowledge.tags, knowledge.status, knowledge.star,
            knowledge.srs, knowledge.collectionId, knowledge.createdAt, knowledge.updatedAt
        )
        else -> null
    }

    var confirmDelete by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<String?>(null) }

    val cols = data.collections.forType(type)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (type == LibType.Q) "错题详情" else "知识点详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOrganize) { Icon(Icons.Default.AutoAwesome, "AI 整理") }
                    IconButton(onClick = onEdit, enabled = meta != null) {
                        Icon(Icons.Default.Edit, "编辑")
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = meta != null) {
                        Icon(Icons.Default.Delete, "删除")
                    }
                }
            )
        }
    ) { padding ->
        if (meta == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("条目已删除", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                STATUS_LABEL.forEach { (key, label) ->
                    AssistChip(
                        onClick = {
                            vm.setStatus(type, listOf(id), key) { toast(context, it) }
                        },
                        label = { Text(label) },
                        leadingIcon = if (meta.status == key) {
                            { Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
                IconButton(onClick = {
                    vm.toggleStar(type, listOf(id)) { toast(context, it) }
                }) {
                    if (meta.star) {
                        Icon(Icons.Default.Star, "取消收藏", tint = Color(0xFFF59E0B))
                    } else {
                        Icon(Icons.Outlined.StarBorder, "收藏")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(meta.status)
                Text(
                    "🕒 ${dueLabel(meta.srs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    meta.collectionId?.let { JsonCodec.collectionPathName(cols, it) } ?: "未归档",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (question != null) {
                if (question.stem.isNotBlank()) {
                    SectionLabel("题目")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        MathText(question.stem, modifier = Modifier.fillMaxWidth().padding(14.dp))
                    }
                }
                if (question.analysis.isNotBlank()) {
                    SectionLabel("解析")
                    MathText(question.analysis)
                }
                if (question.myAnswer.isNotBlank()) {
                    SectionLabel("我的错误答案")
                    MathText(question.myAnswer)
                }
                if (question.knowledge.isNotBlank()) {
                    SectionLabel("知识点讲解")
                    MathText(question.knowledge)
                }
            } else if (knowledge != null) {
                SectionLabel("标题")
                Text(knowledge.title, style = MaterialTheme.typography.headlineSmall)
                if (knowledge.content.isNotBlank()) {
                    SectionLabel("讲解")
                    MathText(knowledge.content)
                }
            }

            val images = meta.images
            if (images.isNotEmpty()) {
                SectionLabel("配图")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images) { imgId ->
                        AsyncImage(
                            model = vm.imageFile(imgId),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { previewImage = imgId },
                                    onLongClick = { previewImage = imgId }
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            if (meta.tags.isNotEmpty()) {
                SectionLabel("标签")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meta.tags.forEach { TagPill(it) }
                }
            }

            if (question != null && question.kpIds.isNotEmpty()) {
                SectionLabel("关联知识点")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    question.kpIds.forEach { kpId ->
                        val kp = data.knowledge.firstOrNull { it.id == kpId }
                        Text(
                            text = kp?.title ?: "（已删除）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { if (kp != null) onOpenKnowledge(kpId) },
                                    onLongClick = { if (kp != null) onOpenKnowledge(kpId) }
                                )
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            SectionLabel("信息")
            MetaRow("合集", meta.collectionId?.let { JsonCodec.collectionPathName(cols, it) } ?: "未归档")
            MetaRow("创建时间", formatDate(meta.createdAt))
            MetaRow("更新时间", formatDate(meta.updatedAt))
            MetaRow("复习次数", meta.srs.reps.toString())
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除") },
            text = { Text("删除后无法恢复，确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteItems(type, listOf(id)) { toast(context, it) }
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }

    val preview = previewImage
    if (preview != null) {
        Dialog(onDismissRequest = { previewImage = null }) {
            AsyncImage(
                model = vm.imageFile(preview),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private data class DetailMeta(
    val images: List<String>,
    val tags: List<String>,
    val status: String,
    val star: Boolean,
    val srs: Srs,
    val collectionId: String?,
    val createdAt: Long,
    val updatedAt: Long
)
