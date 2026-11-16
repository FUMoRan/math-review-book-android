package com.fumoran.mathreview

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fumoran.mathreview.ai.AiClient
import com.fumoran.mathreview.ai.AiConfig
import com.fumoran.mathreview.ai.AiPresets
import com.fumoran.mathreview.ai.AiPrompts
import com.fumoran.mathreview.ai.Prefs
import com.fumoran.mathreview.data.AiParse
import com.fumoran.mathreview.data.AiParsed
import com.fumoran.mathreview.data.Collection
import com.fumoran.mathreview.data.ImportSummary
import com.fumoran.mathreview.data.JsonCodec
import com.fumoran.mathreview.data.Knowledge
import com.fumoran.mathreview.data.LibType
import com.fumoran.mathreview.data.LibraryData
import com.fumoran.mathreview.data.LibraryRepository
import com.fumoran.mathreview.data.OrganizeSuggestion
import com.fumoran.mathreview.data.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = LibraryRepository(app)
    val prefs = Prefs(app)
    val ai = AiClient()

    var theme by mutableStateOf(prefs.theme)
        private set

    var aiConfig by mutableStateOf(prefs.loadAiConfig())
        private set

    var data by mutableStateOf(LibraryData())
        private set

    var loading by mutableStateOf(true)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                repo.load()
                data = repo.data
            } catch (e: Exception) {
                loadError = e.message ?: "数据读取失败"
            } finally {
                loading = false
            }
        }
    }

    private fun refresh() {
        data = repo.data
    }

    private fun action(done: (String?) -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
                done(null)
            } catch (e: Exception) {
                done(e.message ?: "操作失败")
            }
        }
    }

    // ---------- 设置 ----------

    fun updateTheme(value: String) {
        theme = value
        prefs.theme = value
    }

    fun updateAiConfig(cfg: AiConfig) {
        aiConfig = cfg
        prefs.saveAiConfig(cfg)
    }

    fun resetAiPreset(presetId: String): AiConfig {
        val p = AiPresets.of(presetId)
        val cfg = AiConfig(
            preset = p.id,
            protocol = p.protocol,
            baseUrl = p.baseUrl,
            model = p.model,
            apiKey = aiConfig.apiKey,
            maxTokens = aiConfig.maxTokens
        )
        updateAiConfig(cfg)
        return cfg
    }

    // ---------- 条目 ----------

    fun question(id: String): Question? = repo.question(id)
    fun knowledge(id: String): Knowledge? = repo.knowledge(id)
    fun imageFile(id: String): File = repo.imageFile(id)

    fun saveQuestion(q: Question, done: (String?) -> Unit = {}) =
        action(done) { repo.saveQuestion(q) }

    fun saveKnowledge(k: Knowledge, done: (String?) -> Unit = {}) =
        action(done) { repo.saveKnowledge(k) }

    fun deleteItems(type: LibType, ids: List<String>, done: (String?) -> Unit = {}) =
        action(done) { repo.deleteItems(type, ids) }

    fun setStatus(type: LibType, ids: List<String>, status: String, done: (String?) -> Unit = {}) =
        action(done) { repo.setStatus(type, ids, status) }

    fun toggleStar(type: LibType, ids: List<String>, done: (String?) -> Unit = {}) =
        action(done) { repo.toggleStar(type, ids) }

    fun addTag(type: LibType, ids: List<String>, tag: String, done: (String?) -> Unit = {}) =
        action(done) { repo.addTag(type, ids, tag) }

    // ---------- 合集 ----------

    fun collectionPaths(type: LibType): List<String> =
        JsonCodec.flattenCollections(data.collections.forType(type)).map { (c, _) ->
            JsonCodec.collectionPathName(data.collections.forType(type), c.id)
        }

    fun saveCollection(type: LibType, c: Collection, done: (String?) -> Unit = {}) =
        action(done) { repo.saveCollection(type, c) }

    fun deleteCollection(type: LibType, id: String, done: (String?) -> Unit = {}) =
        action(done) { repo.deleteCollection(type, id) }

    fun moveCollection(type: LibType, id: String, parentId: String?, done: (String?) -> Unit = {}) =
        action(done) { repo.moveCollection(type, id, parentId) }

    fun moveItems(type: LibType, ids: List<String>, collectionId: String?, done: (String?) -> Unit = {}) =
        action(done) { repo.moveItems(type, ids, collectionId) }

    fun resolveCollection(type: LibType, path: String, done: (String?, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val id = repo.resolveCollection(type, path)
                refresh()
                done(id, null)
            } catch (e: Exception) {
                done(null, e.message ?: "创建合集失败")
            }
        }
    }

    // ---------- 图片 ----------

    fun addImages(uris: List<Uri>, done: (List<String>?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val ids = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching { repo.addImageFromUri(uri) }.getOrNull()
                    }
                }
                if (ids.isEmpty()) done(null, "图片读取失败") else done(ids, null)
            } catch (e: Exception) {
                done(null, e.message ?: "图片读取失败")
            }
        }
    }

    // ---------- 导入 / 导出 ----------

    fun importParsed(
        type: LibType,
        parsed: AiParsed,
        collectionId: String?,
        imageIds: List<String> = emptyList(),
        done: (ImportSummary?, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val qs = parsed.questions.mapIndexed { index, q ->
                    if (index == 0 && imageIds.isNotEmpty()) q.copy(images = imageIds) else q
                }
                val ks = parsed.knowledge.mapIndexed { index, k ->
                    if (index == 0 && imageIds.isNotEmpty()) k.copy(images = imageIds) else k
                }
                val summary = repo.importParsed(qs, ks, collectionId)
                refresh()
                done(summary, null)
            } catch (e: Exception) {
                done(null, e.message ?: "导入失败")
            }
        }
    }

    fun importBackup(text: String, replace: Boolean, done: (ImportSummary?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val summary = repo.importBackup(text, replace)
                refresh()
                done(summary, null)
            } catch (e: Exception) {
                done(null, e.message ?: "导入失败")
            }
        }
    }

    fun exportFull(done: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                done(repo.exportFull(), null)
            } catch (e: Exception) {
                done(null, e.message ?: "导出失败")
            }
        }
    }

    // ---------- AI ----------

    fun testConnection(done: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val reply = ai.test(aiConfig)
                done(true, reply.take(60))
            } catch (e: Exception) {
                done(false, e.message ?: "连接失败")
            }
        }
    }

    fun aiOcr(type: LibType, imageIds: List<String>, done: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val images = withContext(Dispatchers.IO) {
                    imageIds.mapNotNull { id ->
                        val f = repo.imageFile(id)
                        if (f.exists()) f.readBytes() else null
                    }
                }
                if (images.isEmpty()) throw IllegalStateException("请先选择图片")
                val text = ai.complete(aiConfig, "", AiPrompts.ocr(type), images)
                done(text, null)
            } catch (e: Exception) {
                done(null, e.message ?: "识别失败")
            }
        }
    }

    fun aiSolve(type: LibType, raw: String, done: (AiParsed?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val text = ai.complete(
                    aiConfig,
                    "",
                    AiPrompts.solve(type, collectionPaths(type)) + "\n\n原始内容：\n" + raw
                )
                val parsed = AiParse.parseItems(text, type)
                if (parsed.total == 0) throw IllegalStateException("未能解析出条目，请检查 AI 输出")
                done(parsed, null)
            } catch (e: Exception) {
                done(null, e.message ?: "解析失败")
            }
        }
    }

    fun aiOrganize(type: LibType, id: String, done: (OrganizeSuggestion?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val content = if (type == LibType.Q) {
                    val q = repo.question(id) ?: throw IllegalStateException("条目不存在")
                    listOf(q.stem, q.analysis, q.knowledge, q.myAnswer).filter { it.isNotBlank() }
                        .joinToString("\n")
                } else {
                    val k = repo.knowledge(id) ?: throw IllegalStateException("条目不存在")
                    listOf(k.title, k.content).filter { it.isNotBlank() }.joinToString("\n")
                }
                val kps = data.knowledge.map { it.id to it.title }
                val text = ai.complete(
                    aiConfig,
                    "",
                    AiPrompts.organize(type, content, collectionPaths(type), kps)
                )
                done(AiParse.parseOrganize(text), null)
            } catch (e: Exception) {
                done(null, e.message ?: "AI 整理失败")
            }
        }
    }

    fun applyOrganize(
        type: LibType,
        id: String,
        tags: List<String>,
        collection: String?,
        related: List<String>,
        done: (String?) -> Unit = {}
    ) {
        action(done) {
            tags.forEach { tag -> repo.addTag(type, listOf(id), tag) }
            if (!collection.isNullOrBlank()) {
                val colId = repo.resolveCollection(type, collection)
                if (colId != null) repo.moveItems(type, listOf(id), colId)
            }
            if (related.isNotEmpty() && type == LibType.Q) {
                repo.linkKnowledge(id, related)
            }
        }
    }
}
