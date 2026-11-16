package com.fumoran.mathreview.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ImportSummary(
    val questions: Int,
    val knowledge: Int,
    val images: Int,
    val replaced: Boolean
)

/**
 * 单文件持久化：library.json + Mutex + 临时文件原子替换。
 * 所有写操作都会先落盘成功，再更新内存快照。
 */
class LibraryRepository(private val context: Context) {

    private val mutex = Mutex()
    private val file = File(context.filesDir, "library.json")
    private val imageDir = File(context.filesDir, "images")

    @Volatile
    var data: LibraryData = LibraryData()
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            imageDir.mkdirs()
            if (file.exists()) {
                val text = file.readText()
                data = runCatching { JsonCodec.decodeLocal(text) }
                    .getOrElse { throw IllegalStateException("本地数据读取失败：${it.message}") }
            }
        }
    }

    private suspend fun update(transform: (LibraryData) -> LibraryData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(data)
            persistLocked(next)
            data = next
            cleanupLocked(next)
        }
    }

    private fun persistLocked(d: LibraryData) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(JsonCodec.encode(d))
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }

    // ---------- 读取 ----------

    fun question(id: String): Question? = data.questions.firstOrNull { it.id == id }
    fun knowledge(id: String): Knowledge? = data.knowledge.firstOrNull { it.id == id }

    // ---------- 写入 ----------

    suspend fun saveQuestion(q: Question) = update { d ->
        val now = System.currentTimeMillis()
        val item = JsonCodec.normalizeQuestion(q.copy(updatedAt = now), now)
        val list = d.questions.toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(0, item)
        d.copy(questions = list)
    }

    suspend fun saveKnowledge(k: Knowledge) = update { d ->
        val now = System.currentTimeMillis()
        val item = JsonCodec.normalizeKnowledge(k.copy(updatedAt = now), now)
        val list = d.knowledge.toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(0, item)
        d.copy(knowledge = list)
    }

    suspend fun deleteItems(type: LibType, ids: List<String>) = update { d ->
        val idSet = ids.toHashSet()
        when (type) {
            LibType.Q -> d.copy(questions = d.questions.filterNot { it.id in idSet })
            LibType.K -> d.copy(
                knowledge = d.knowledge.filterNot { it.id in idSet },
                questions = d.questions.map { q ->
                    if (q.kpIds.any { it in idSet }) {
                        q.copy(kpIds = q.kpIds.filterNot { it in idSet })
                    } else q
                }
            )
        }
    }

    suspend fun setStatus(type: LibType, ids: List<String>, status: String) = update { d ->
        val now = System.currentTimeMillis()
        val idSet = ids.toHashSet()
        when (type) {
            LibType.Q -> d.copy(questions = d.questions.map {
                if (it.id in idSet) it.copy(status = status, srs = scheduleSrs(it.srs, status, now), updatedAt = now) else it
            })
            LibType.K -> d.copy(knowledge = d.knowledge.map {
                if (it.id in idSet) it.copy(status = status, srs = scheduleSrs(it.srs, status, now), updatedAt = now) else it
            })
        }
    }

    suspend fun toggleStar(type: LibType, ids: List<String>) = update { d ->
        val idSet = ids.toHashSet()
        val now = System.currentTimeMillis()
        when (type) {
            LibType.Q -> {
                val allStarred = d.questions.filter { it.id in idSet }.all { it.star }
                d.copy(questions = d.questions.map {
                    if (it.id in idSet) it.copy(star = !allStarred, updatedAt = now) else it
                })
            }
            LibType.K -> {
                val allStarred = d.knowledge.filter { it.id in idSet }.all { it.star }
                d.copy(knowledge = d.knowledge.map {
                    if (it.id in idSet) it.copy(star = !allStarred, updatedAt = now) else it
                })
            }
        }
    }

    suspend fun addTag(type: LibType, ids: List<String>, tag: String) = update { d ->
        val idSet = ids.toHashSet()
        val now = System.currentTimeMillis()
        when (type) {
            LibType.Q -> d.copy(questions = d.questions.map {
                if (it.id in idSet) it.copy(tags = JsonCodec.mergeTags(it.tags, listOf(tag)), updatedAt = now) else it
            })
            LibType.K -> d.copy(knowledge = d.knowledge.map {
                if (it.id in idSet) it.copy(tags = JsonCodec.mergeTags(it.tags, listOf(tag)), updatedAt = now) else it
            })
        }
    }

    suspend fun saveCollection(type: LibType, c: Collection) = update { d ->
        val now = System.currentTimeMillis()
        val list = d.collections.forType(type).toMutableList()
        val item = JsonCodec.normalizeCollection(
            if (c.id.isBlank()) c.copy(id = JsonCodec.uid()) else c,
            now
        )
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(item)
        d.copy(collections = d.collections.withType(type, JsonCodec.settleCollections(list, now)))
    }

    suspend fun deleteCollection(type: LibType, id: String) = update { d ->
        val now = System.currentTimeMillis()
        val list = d.collections.forType(type)
            .filterNot { it.id == id }
            .map { if (it.parentId == id) it.copy(parentId = null, updatedAt = now) else it }
        val cleaned = JsonCodec.settleCollections(list, now)
        when (type) {
            LibType.Q -> d.copy(
                collections = d.collections.withType(type, cleaned),
                questions = d.questions.map { if (it.collectionId == id) it.copy(collectionId = null) else it }
            )
            LibType.K -> d.copy(
                collections = d.collections.withType(type, cleaned),
                knowledge = d.knowledge.map { if (it.collectionId == id) it.copy(collectionId = null) else it }
            )
        }
    }

    suspend fun moveCollection(type: LibType, id: String, parentId: String?) = update { d ->
        val now = System.currentTimeMillis()
        val list = d.collections.forType(type)
        if (parentId != null) {
            val blocked = JsonCodec.subtreeIds(list, id)
            if (parentId in blocked) return@update d
        }
        val moved = list.map { if (it.id == id) it.copy(parentId = parentId, updatedAt = now) else it }
        d.copy(collections = d.collections.withType(type, JsonCodec.settleCollections(moved, now)))
    }

    suspend fun moveItems(type: LibType, ids: List<String>, collectionId: String?) = update { d ->
        val idSet = ids.toHashSet()
        val now = System.currentTimeMillis()
        when (type) {
            LibType.Q -> d.copy(questions = d.questions.map {
                if (it.id in idSet) it.copy(collectionId = collectionId, updatedAt = now) else it
            })
            LibType.K -> d.copy(knowledge = d.knowledge.map {
                if (it.id in idSet) it.copy(collectionId = collectionId, updatedAt = now) else it
            })
        }
    }

    /** 把 "A / B" 形式的路径落实为合集，不存在则创建。 */
    suspend fun resolveCollection(type: LibType, path: String): String? {
        var result: String? = null
        update { d ->
            val (list, id) = JsonCodec.resolveCollectionPath(d.collections.forType(type), path)
            result = id
            d.copy(collections = d.collections.withType(type, list))
        }
        return result
    }

    suspend fun linkKnowledge(questionId: String, kpIds: List<String>) = update { d ->
        val now = System.currentTimeMillis()
        d.copy(
            questions = d.questions.map {
                if (it.id == questionId) {
                    it.copy(kpIds = (it.kpIds + kpIds).distinct(), updatedAt = now)
                } else it
            }
        )
    }

    // ---------- 图片 ----------

    fun imageFile(id: String): File = File(imageDir, "$id.jpg")

    suspend fun addImageFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val id = JsonCodec.uid()
        ImageStore.fromUri(context, uri, imageFile(id))
        id
    }

    suspend fun addImage(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val id = JsonCodec.uid()
        ImageStore.fromBytes(bytes, imageFile(id))
        id
    }

    suspend fun cleanupImages() = withContext(Dispatchers.IO) {
        mutex.withLock { cleanupLocked(data) }
    }

    private fun cleanupLocked(d: LibraryData) {
        val referenced = (d.questions.flatMap { it.images } + d.knowledge.flatMap { it.images }).toHashSet()
        imageDir.listFiles()?.forEach { f ->
            val id = f.name.substringBeforeLast('.')
            if (!referenced.contains(id)) f.delete()
        }
    }

    // ---------- 导入 / 导出 ----------

    suspend fun importBackup(text: String, replace: Boolean): ImportSummary = withContext(Dispatchers.IO) {
        val backup = JsonCodec.parseBackup(text)
        mutex.withLock {
            val now = System.currentTimeMillis()
            var imageCount = 0
            backup.images.forEach { (id, rec) ->
                runCatching {
                    val bytes = Base64.decode(rec.data, Base64.DEFAULT)
                    ImageStore.fromBytes(bytes, imageFile(id))
                    imageCount++
                }
            }

            val base = if (replace) LibraryData() else data
            val cols = Collections(
                q = if (replace) backup.collections.q
                else JsonCodec.mergeCollections(base.collections.q, backup.collections.q),
                k = if (replace) backup.collections.k
                else JsonCodec.mergeCollections(base.collections.k, backup.collections.k)
            )

            val qMap = LinkedHashMap<String, Question>()
            base.questions.forEach { qMap[it.id] = it }
            backup.questions.forEach { qMap[it.id] = it }

            val kMap = LinkedHashMap<String, Knowledge>()
            base.knowledge.forEach { kMap[it.id] = it }
            backup.knowledge.forEach { kMap[it.id] = it }

            val (qs, ks) = JsonCodec.sanitizeItemCollections(
                qMap.values.sortedByDescending { it.createdAt },
                kMap.values.sortedByDescending { it.createdAt },
                cols
            )

            val next = LibraryData(questions = qs, knowledge = ks, collections = cols)
            persistLocked(next)
            data = next
            cleanupLocked(next)

            ImportSummary(
                questions = backup.questions.size,
                knowledge = backup.knowledge.size,
                images = imageCount,
                replaced = replace
            )
        }
    }

    suspend fun importParsed(
        questions: List<Question>,
        knowledge: List<Knowledge>,
        collectionId: String? = null
    ): ImportSummary = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val base = data
            val newQ = questions.map {
                JsonCodec.normalizeQuestion(
                    it.copy(collectionId = it.collectionId ?: collectionId, createdAt = now, updatedAt = now),
                    now
                )
            }
            val newK = knowledge.map {
                JsonCodec.normalizeKnowledge(
                    it.copy(collectionId = it.collectionId ?: collectionId, createdAt = now, updatedAt = now),
                    now
                )
            }
            val next = base.copy(
                questions = newQ + base.questions,
                knowledge = newK + base.knowledge
            )
            persistLocked(next)
            data = next
            ImportSummary(newQ.size, newK.size, 0, replaced = false)
        }
    }

    suspend fun exportFull(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ids = (data.questions.flatMap { it.images } + data.knowledge.flatMap { it.images }).toHashSet()
            val images = ids.mapNotNull { id ->
                val f = imageFile(id)
                if (!f.exists()) null
                else id to ImageRec("image/jpeg", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }.toMap()
            val backup = Backup(
                version = 4,
                questions = data.questions,
                knowledge = data.knowledge,
                collections = data.collections,
                images = images
            )
            JsonCodec.json.encodeToString(Backup.serializer(), backup)
        }
    }
}
