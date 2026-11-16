package com.fumoran.mathreview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.fumoran.mathreview.data.JsonCodec
import com.fumoran.mathreview.data.LibType
import com.fumoran.mathreview.data.OrganizeSuggestion
import com.fumoran.mathreview.ui.components.SectionLabel
import com.fumoran.mathreview.ui.components.TagPill
import com.fumoran.mathreview.ui.components.toast

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiOrganizeScreen(
    vm: AppViewModel,
    type: LibType,
    id: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val data = vm.data
    val title = if (type == LibType.Q) {
        data.questions.firstOrNull { it.id == id }?.stem ?: ""
    } else {
        data.knowledge.firstOrNull { it.id == id }?.title ?: ""
    }

    var suggestion by remember { mutableStateOf<OrganizeSuggestion?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var useTags by remember { mutableStateOf(true) }
    var useCollection by remember { mutableStateOf(true) }
    var useRelated by remember { mutableStateOf(true) }

    val cols = data.collections.forType(type)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 整理") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                JsonCodec.stripLatex(title).ifBlank { "（无标题）" },
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    error = ""
                    suggestion = null
                    vm.aiOrganize(type, id) { s, err ->
                        loading = false
                        if (s != null) suggestion = s else error = err ?: "整理失败"
                    }
                }
            ) {
                Icon(Icons.Default.AutoAwesome, null)
                Text("生成整理建议", modifier = Modifier.padding(start = 6.dp))
            }

            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("正在请求 AI…")
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            val s = suggestion
            if (s != null) {
                if (s.isEmpty) {
                    Text("AI 没有给出可用的建议")
                }
                if (s.tags.isNotEmpty()) {
                    SectionLabel("标签")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = useTags, onCheckedChange = { useTags = it })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                s.tags.forEach { TagPill(it, selected = useTags) }
                            }
                        }
                    }
                }
                val col = s.collection
                if (!col.isNullOrBlank()) {
                    SectionLabel("建议合集")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = useCollection, onCheckedChange = { useCollection = it })
                            Text(col)
                        }
                    }
                }
                if (s.relatedIds.isNotEmpty() && type == LibType.Q) {
                    SectionLabel("建议关联知识点")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = useRelated, onCheckedChange = { useRelated = it })
                                Text("关联以下知识点")
                            }
                            s.relatedIds.forEach { kpId ->
                                val kp = data.knowledge.firstOrNull { it.id == kpId }
                                Text(
                                    "· " + (kp?.title ?: kpId),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 44.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        vm.applyOrganize(
                            type = type,
                            id = id,
                            tags = if (useTags) s.tags else emptyList(),
                            collection = if (useCollection) s.collection else null,
                            related = if (useRelated) s.relatedIds else emptyList()
                        ) { err ->
                            toast(context, err)
                            if (err == null) onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("应用所选建议") }
            }
        }
    }
}
