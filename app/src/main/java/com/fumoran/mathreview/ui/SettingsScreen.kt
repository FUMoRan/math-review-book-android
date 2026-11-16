package com.fumoran.mathreview.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fumoran.mathreview.AppViewModel
import com.fumoran.mathreview.ai.AiPreset
import com.fumoran.mathreview.ai.AiPresets
import com.fumoran.mathreview.data.Backup
import com.fumoran.mathreview.data.JsonCodec
import com.fumoran.mathreview.ui.components.SectionLabel
import com.fumoran.mathreview.ui.components.toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(vm.aiConfig) }
    var showKey by remember { mutableStateOf(false) }
    var showPreset by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var previewBackup by remember { mutableStateOf<Backup?>(null) }
    var testResult by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                toast(context, "读取文件失败")
            } else {
                runCatching { JsonCodec.parseBackup(text) }
                    .onSuccess {
                        previewBackup = it
                        pendingImport = text
                    }
                    .onFailure { toast(context, it.message) }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.exportFull { text, err ->
                if (text == null) {
                    toast(context, err)
                } else {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(text.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("无法写入")
                    }.onSuccess { toast(context, "已导出完整备份") }
                        .onFailure { toast(context, it.message ?: "导出失败") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            SectionLabel("外观")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (key, label) ->
                    FilterChip(
                        selected = vm.theme == key,
                        onClick = { vm.updateTheme(key) },
                        label = { Text(label) }
                    )
                }
            }

            SectionLabel("AI 服务")
            TextButton(onClick = { showPreset = true }) {
                Text("预设：" + AiPresets.of(config.preset).name)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("gemini" to "Gemini", "anthropic" to "Claude", "openai" to "OpenAI 兼容").forEach { (key, label) ->
                    FilterChip(
                        selected = config.protocol == key,
                        onClick = { config = config.copy(protocol = key) },
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = { config = config.copy(baseUrl = it) },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = config.model,
                onValueChange = { config = config.copy(model = it) },
                label = { Text("模型名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { config = config.copy(apiKey = it) },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = config.maxTokens.toString(),
                onValueChange = { v ->
                    v.filter { it.isDigit() }.take(6).toIntOrNull()?.let {
                        config = config.copy(maxTokens = it)
                    }
                },
                label = { Text("max tokens") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    vm.updateAiConfig(config)
                    testResult = "测试中…"
                    vm.testConnection { ok, msg ->
                        testResult = if (ok) "连接正常：$msg" else "连接失败：$msg"
                    }
                }) { Text("测试连接") }
                Button(onClick = {
                    vm.updateAiConfig(config)
                    toast(context, "已保存")
                }) { Text("保存设置") }
            }
            if (testResult.isNotBlank()) {
                Text(testResult, style = MaterialTheme.typography.bodySmall)
            }

            SectionLabel("数据")
            Button(
                onClick = { importLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导入网页版 JSON") }
            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                    exportLauncher.launch("数学复习本-完整备份-$stamp.json")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导出完整备份（含配图）") }
            Text(
                "支持网页版导出的普通备份与完整备份；文件中的试卷（papers）会被忽略。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel("关于")
            Text("数学复习本 Android 1.0.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                "原生实现，不使用 WebView；公式由 Markwon + JLatexMath 本地渲染。\n" +
                    "数据保存在应用私有目录，API Key 仅存本机，不上传任何服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPreset) {
        var selected by remember { mutableStateOf(config.preset) }
        AlertDialog(
            onDismissRequest = { showPreset = false },
            title = { Text("选择 AI 预设") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AiPresets.all.forEach { p: AiPreset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            FilterChip(
                                selected = selected == p.id,
                                onClick = { selected = p.id },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    config = vm.resetAiPreset(selected)
                    showPreset = false
                }) { Text("应用") }
            },
            dismissButton = { TextButton(onClick = { showPreset = false }) { Text("取消") } }
        )
    }

    val backup = previewBackup
    val textToImport = pendingImport
    if (backup != null && textToImport != null) {
        AlertDialog(
            onDismissRequest = {
                previewBackup = null
                pendingImport = null
            },
            title = { Text("导入预览") },
            text = {
                Text(
                    "错题 ${backup.questions.size} 条\n" +
                        "知识点 ${backup.knowledge.size} 条\n" +
                        "合集 ${backup.collections.q.size + backup.collections.k.size} 个\n" +
                        "配图 ${backup.images.size} 张\n\n" +
                        "「追加」会保留现有数据并按 id 去重；「替换」会清空现有数据。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = textToImport
                    previewBackup = null
                    pendingImport = null
                    vm.importBackup(text, replace = false) { s, err ->
                        toast(
                            context,
                            err ?: "已追加：错题 ${s?.questions ?: 0} / 知识点 ${s?.knowledge ?: 0} / 配图 ${s?.images ?: 0}"
                        )
                    }
                }) { Text("追加") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val text = textToImport
                        previewBackup = null
                        pendingImport = null
                        vm.importBackup(text, replace = true) { s, err ->
                            toast(
                                context,
                                err ?: "已替换：错题 ${s?.questions ?: 0} / 知识点 ${s?.knowledge ?: 0} / 配图 ${s?.images ?: 0}"
                            )
                        }
                    }) { Text("替换", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = {
                        previewBackup = null
                        pendingImport = null
                    }) { Text("取消") }
                }
            }
        )
    }
}
