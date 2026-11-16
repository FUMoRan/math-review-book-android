package com.fumoran.mathreview.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.random.Random

/**
 * 网页版 JSON 的读写与归一化。
 * 兼容：顶层数组（旧版）、v4 备份对象、完整备份（含 images）、忽略 papers。
 */
object JsonCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
        prettyPrint = true
    }

    fun encode(data: LibraryData): String = json.encodeToString(LibraryData.serializer(), data)

    fun decodeLocal(text: String): LibraryData =
        if (text.isBlank()) LibraryData() else json.decodeFromString(LibraryData.serializer(), text)

    fun uid(): String =
        System.currentTimeMillis().toString(36) + (10000 + Random.nextInt(90000)).toString(36)

    // ---------- 备份解析 ----------

    fun parseBackup(text: String): Backup {
        if (text.isBlank()) throw IllegalArgumentException("文件内容为空")
        val root: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("文件不是合法的 JSON")
        }
        val now = System.currentTimeMillis()
        return when (root) {
            is JsonArray -> {
                val qs = parseQuestions(root, now)
                if (qs.isEmpty()) throw IllegalArgumentException("文件中没有可导入的错题")
                Backup(questions = qs)
            }
            is JsonObject -> {
                val qEl = root["questions"]
                val kEl = root["knowledge"]
                if (qEl == null && kEl == null) {
                    throw IllegalArgumentException("文件结构不符合要求：未找到 questions 或 knowledge")
                }
                val qs = if (qEl is JsonArray) parseQuestions(qEl, now) else emptyList()
                val ks = if (kEl is JsonArray) parseKnowledge(kEl, now) else emptyList()
                if (qs.isEmpty() && ks.isEmpty()) throw IllegalArgumentException("文件中没有可导入的条目")
                val version = root["version"]?.jsonPrimitive?.longOrNull?.toInt() ?: 4
                Backup(
                    version = version,
                    questions = qs,
                    knowledge = ks,
                    collections = parseCollections(root["collections"], now),
                    images = parseImages(root["images"])
                )
            }
            else -> throw IllegalArgumentException("文件结构不符合要求：应为 JSON 对象或数组")
        }
    }

    private fun parseQuestions(arr: JsonArray, now: Long): List<Question> = arr.mapNotNull { el ->
        runCatching {
            normalizeQuestion(json.decodeFromJsonElement(Question.serializer(), el), now)
        }.getOrNull()
    }

    private fun parseKnowledge(arr: JsonArray, now: Long): List<Knowledge> = arr.mapNotNull { el ->
        runCatching {
            normalizeKnowledge(json.decodeFromJsonElement(Knowledge.serializer(), el), now)
        }.getOrNull()
    }

    private fun parseCollections(el: JsonElement?, now: Long): Collections {
        val obj = el as? JsonObject ?: return Collections()
        return Collections(
            q = settleCollections(readCollectionList(obj["q"], now)),
            k = settleCollections(readCollectionList(obj["k"], now))
        )
    }

    private fun readCollectionList(el: JsonElement?, now: Long): List<Collection> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            runCatching {
                normalizeCollection(json.decodeFromJsonElement(Collection.serializer(), item), now)
            }.getOrNull()
        }
    }

    private fun parseImages(el: JsonElement?): Map<String, ImageRec> {
        val obj = el as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (k, v) ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val data = o["data"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (data.isBlank()) return@mapNotNull null
            val type = o["type"]?.jsonPrimitive?.content ?: "image/jpeg"
            k to ImageRec(type, data)
        }.toMap()
    }

    // ---------- 归一化 ----------

    fun normalizeTags(tags: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        tags.forEach { t -> t.trim().takeIf { it.isNotEmpty() }?.let { out.add(it) } }
        return out.toList()
    }

    fun mergeTags(existing: List<String>, extra: List<String>): List<String> =
        normalizeTags(existing + extra)

    fun normalizeQuestion(q: Question, now: Long = System.currentTimeMillis()): Question = q.copy(
        id = q.id.ifBlank { uid() },
        stem = q.stem.trim(),
        analysis = q.analysis.trim(),
        knowledge = q.knowledge.trim(),
        myAnswer = q.myAnswer.trim(),
        tags = normalizeTags(q.tags),
        status = if (STATUS_LABEL.containsKey(q.status)) q.status else STATUS_NEW,
        images = q.images.filter { it.isNotBlank() }.distinct(),
        collectionId = q.collectionId?.takeIf { it.isNotBlank() },
        kpIds = q.kpIds.filter { it.isNotBlank() }.distinct(),
        srs = normalizeSrs(q.srs),
        createdAt = if (q.createdAt > 0) q.createdAt else now,
        updatedAt = if (q.updatedAt > 0) q.updatedAt else now
    )

    fun normalizeKnowledge(k: Knowledge, now: Long = System.currentTimeMillis()): Knowledge = k.copy(
        id = k.id.ifBlank { uid() },
        title = k.title.trim(),
        content = k.content.trim(),
        tags = normalizeTags(k.tags),
        status = if (STATUS_LABEL.containsKey(k.status)) k.status else STATUS_NEW,
        images = k.images.filter { it.isNotBlank() }.distinct(),
        collectionId = k.collectionId?.takeIf { it.isNotBlank() },
        srs = normalizeSrs(k.srs),
        createdAt = if (k.createdAt > 0) k.createdAt else now,
        updatedAt = if (k.updatedAt > 0) k.updatedAt else now
    )

    // ---------- 合集树 ----------

    fun normalizeCollection(c: Collection, now: Long = System.currentTimeMillis()): Collection =
        c.copy(
            id = c.id.ifBlank { uid() },
            name = c.name.trim().ifBlank { "未命名合集" },
            parentId = c.parentId?.takeIf { it.isNotBlank() },
            createdAt = if (c.createdAt > 0) c.createdAt else now,
            updatedAt = if (c.updatedAt > 0) c.updatedAt else now
        )

    /** 修正悬空 parentId，断开自环与父链环。 */
    fun settleCollections(list: List<Collection>, now: Long = System.currentTimeMillis()): List<Collection> {
        val cols = list.map { normalizeCollection(it, now) }.toMutableList()
        val byId = cols.associateBy { it.id }
        for (i in cols.indices) {
            val pid = cols[i].parentId
            if (pid != null && (pid == cols[i].id || !byId.containsKey(pid))) {
                cols[i] = cols[i].copy(parentId = null)
            }
        }
        for (i in cols.indices) {
            val seen = mutableSetOf(cols[i].id)
            var p = cols[i].parentId
            while (p != null) {
                if (!seen.add(p)) {
                    cols[i] = cols[i].copy(parentId = null)
                    break
                }
                p = cols.firstOrNull { it.id == p }?.parentId
            }
        }
        return cols
    }

    fun findCollection(cols: List<Collection>, id: String?): Collection? =
        cols.firstOrNull { it.id == id }

    fun collectionChildren(cols: List<Collection>, parentId: String?): List<Collection> =
        cols.filter { it.parentId == parentId }
            .sortedWith(compareBy({ it.createdAt }, { it.name }))

    fun collectionPath(cols: List<Collection>, id: String?): List<Collection> {
        if (id == null) return emptyList()
        val byId = cols.associateBy { it.id }
        val path = ArrayDeque<Collection>()
        val seen = mutableSetOf<String>()
        var cur = id
        while (cur != null && byId.containsKey(cur) && seen.add(cur)) {
            val node = byId.getValue(cur)
            path.addFirst(node)
            cur = node.parentId
        }
        return path.toList()
    }

    fun collectionPathName(cols: List<Collection>, id: String?): String =
        collectionPath(cols, id).joinToString(" / ") { it.name }

    fun flattenCollections(cols: List<Collection>): List<Pair<Collection, Int>> {
        val out = mutableListOf<Pair<Collection, Int>>()
        fun walk(parentId: String?, depth: Int) {
            collectionChildren(cols, parentId).forEach { c ->
                out.add(c to depth)
                walk(c.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    fun subtreeIds(cols: List<Collection>, id: String): Set<String> {
        val out = linkedSetOf(id)
        var changed = true
        while (changed) {
            changed = false
            cols.forEach { c ->
                val pid = c.parentId
                if (pid != null && out.contains(pid) && out.add(c.id)) changed = true
            }
        }
        return out
    }

    /**
     * 按 "A / B / C" 路径查找合集，不存在时逐级创建。
     * @return 新合集树与目标合集 id
     */
    fun resolveCollectionPath(
        cols: List<Collection>,
        path: String,
        now: Long = System.currentTimeMillis()
    ): Pair<List<Collection>, String?> {
        val names = path.split('/', '>', '＞', '／')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (names.isEmpty()) return cols to null
        var list = cols.toMutableList()
        var parentId: String? = null
        for (name in names) {
            val existing = list.firstOrNull { it.parentId == parentId && it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                parentId = existing.id
            } else {
                val node = Collection(
                    id = uid(),
                    name = name,
                    parentId = parentId,
                    createdAt = now,
                    updatedAt = now
                )
                list.add(node)
                parentId = node.id
            }
        }
        return list.toList() to parentId
    }

    /** 合并两棵合集树（按 id 去重）。 */
    fun mergeCollections(existing: List<Collection>, incoming: List<Collection>): List<Collection> {
        val ids = existing.map { it.id }.toHashSet()
        return settleCollections(existing + incoming.filter { ids.add(it.id) })
    }

    fun sanitizeItemCollections(
        questions: List<Question>,
        knowledge: List<Knowledge>,
        cols: Collections
    ): Pair<List<Question>, List<Knowledge>> {
        val validQ = cols.q.map { it.id }.toHashSet()
        val validK = cols.k.map { it.id }.toHashSet()
        return questions.map { q ->
            if (q.collectionId != null && !validQ.contains(q.collectionId)) q.copy(collectionId = null) else q
        } to knowledge.map { k ->
            if (k.collectionId != null && !validK.contains(k.collectionId)) k.copy(collectionId = null) else k
        }
    }

    // ---------- 搜索 / 格式化辅助 ----------

    fun stripLatex(text: String): String =
        text.replace("$", "").replace("\\(", "").replace("\\)", "").replace("\\[", "").replace("\\]", "")

    fun cleanPasted(text: String): String {
        var s = text.trim()
        // 去掉 ```json / ``` 围栏
        val fence = Regex("^```[a-zA-Z]*\\s*|\\s*```$")
        s = s.replace(fence, "").trim()
        return s
    }

    fun extractJson(text: String): String? {
        val s = cleanPasted(text)
        val objStart = s.indexOf('{')
        val arrStart = s.indexOf('[')
        val start = when {
            objStart < 0 -> arrStart
            arrStart < 0 -> objStart
            else -> minOf(objStart, arrStart)
        }
        if (start < 0) return null
        val open = s[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
        }
        return null
    }

    fun textOrEmpty(el: JsonElement?, key: String): String =
        (el as? JsonObject)?.get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: ""

    fun boolOrFalse(el: JsonElement?, key: String): Boolean =
        (el as? JsonObject)?.get(key)?.jsonPrimitive?.booleanOrNull ?: false

    fun numberOrZero(el: JsonElement?, key: String): Double =
        (el as? JsonObject)?.get(key)?.jsonPrimitive?.doubleOrNull ?: 0.0

    fun stringList(el: JsonElement?, key: String): List<String> {
        val arr = (el as? JsonObject)?.get(key) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
    }

    fun objects(el: JsonElement?): List<JsonObject> = when (el) {
        is JsonArray -> el.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(el)
        else -> emptyList()
    }
}
