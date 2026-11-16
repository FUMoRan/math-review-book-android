package com.fumoran.mathreview.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AiParsed(
    val questions: List<Question> = emptyList(),
    val knowledge: List<Knowledge> = emptyList()
) {
    val total: Int get() = questions.size + knowledge.size
}

data class OrganizeSuggestion(
    val tags: List<String> = emptyList(),
    val collection: String? = null,
    val relatedIds: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = tags.isEmpty() && collection.isNullOrBlank() && relatedIds.isEmpty()
}

/** 解析 AI 输出：优先严格 JSON，失败时退回「标签：内容」分块文本。 */
object AiParse {

    fun parseItems(text: String, type: LibType, now: Long = System.currentTimeMillis()): AiParsed {
        val raw = JsonCodec.extractJson(text)
        if (raw != null) {
            val el = runCatching { JsonCodec.json.parseToJsonElement(raw) }.getOrNull()
            val fromJson = parseJsonItems(el, type, now)
            if (fromJson.total > 0) return fromJson
        }
        return parseLabeled(text, type, now)
    }

    private fun parseJsonItems(el: JsonElement?, type: LibType, now: Long): AiParsed {
        val array: JsonArray = when (el) {
            is JsonArray -> el
            is JsonObject -> {
                val key = if (type == LibType.Q) "questions" else "knowledge"
                (el[key] as? JsonArray) ?: (el["items"] as? JsonArray) ?: JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }
        return if (type == LibType.Q) {
            AiParsed(questions = array.mapNotNull { (it as? JsonObject)?.let { o -> questionOf(o, now) } })
        } else {
            AiParsed(knowledge = array.mapNotNull { (it as? JsonObject)?.let { o -> knowledgeOf(o, now) } })
        }
    }

    private fun questionOf(o: JsonObject, now: Long): Question? {
        val stem = JsonCodec.textOrEmpty(o, "stem").ifBlank {
            JsonCodec.textOrEmpty(o, "题目")
        }
        if (stem.isBlank()) return null
        return JsonCodec.normalizeQuestion(
            Question(
                id = JsonCodec.uid(),
                stem = stem,
                analysis = JsonCodec.textOrEmpty(o, "analysis"),
                knowledge = JsonCodec.textOrEmpty(o, "knowledge"),
                myAnswer = JsonCodec.textOrEmpty(o, "myAnswer"),
                tags = JsonCodec.stringList(o, "tags"),
                createdAt = now,
                updatedAt = now
            ),
            now
        )
    }

    private fun knowledgeOf(o: JsonObject, now: Long): Knowledge? {
        val title = JsonCodec.textOrEmpty(o, "title").ifBlank {
            JsonCodec.textOrEmpty(o, "知识点")
        }
        if (title.isBlank()) return null
        return JsonCodec.normalizeKnowledge(
            Knowledge(
                id = JsonCodec.uid(),
                title = title,
                content = JsonCodec.textOrEmpty(o, "content"),
                tags = JsonCodec.stringList(o, "tags"),
                createdAt = now,
                updatedAt = now
            ),
            now
        )
    }

    // ---------- 纯文本兜底 ----------

    private val labelRegex = Regex(
        "^\\s*(?:[-*]\\s*)?(?:\\*\\*)?(题目|题干|标题|解析|答案|我的错误答案|我的答案|知识点讲解|知识点|讲解|标签|合集)(?:\\*\\*)?\\s*[：:]\\s*(.*)$"
    )

    private fun parseLabeled(text: String, type: LibType, now: Long): AiParsed {
        val blocks = mutableListOf<MutableMap<String, String>>()
        var cur: MutableMap<String, String>? = null
        var field: String? = null

        fun flush() {
            val c = cur ?: return
            if (c.isNotEmpty()) blocks.add(c)
            cur = null
            field = null
        }

        for (line in text.lines()) {
            val m = labelRegex.find(line)
            if (m != null) {
                val label = m.groupValues[1]
                val value = m.groupValues[2]
                val isStart = when (type) {
                    LibType.Q -> label == "题目" || label == "题干"
                    LibType.K -> label == "标题" || label == "知识点"
                }
                if (isStart) {
                    flush()
                    cur = mutableMapOf()
                }
                val c = cur ?: run { cur = mutableMapOf(); cur!! }
                c[label] = value
                field = label
            } else {
                val c = cur
                val f = field
                if (c != null && f != null) {
                    c[f] = (c[f].orEmpty() + "\n" + line).trim()
                }
            }
        }
        flush()

        val qs = mutableListOf<Question>()
        val ks = mutableListOf<Knowledge>()
        for (b in blocks) {
            if (type == LibType.Q) {
                val stem = (b["题目"] ?: b["题干"]).orEmpty().trim()
                if (stem.isBlank()) continue
                qs.add(
                    JsonCodec.normalizeQuestion(
                        Question(
                            id = JsonCodec.uid(),
                            stem = stem,
                            analysis = (b["解析"] ?: b["答案"]).orEmpty().trim(),
                            knowledge = (b["知识点讲解"] ?: b["知识点"] ?: b["讲解"]).orEmpty().trim(),
                            myAnswer = (b["我的错误答案"] ?: b["我的答案"]).orEmpty().trim(),
                            tags = splitTags(b["标签"]),
                            createdAt = now,
                            updatedAt = now
                        ),
                        now
                    )
                )
            } else {
                val title = (b["标题"] ?: b["知识点"]).orEmpty().trim()
                if (title.isBlank()) continue
                ks.add(
                    JsonCodec.normalizeKnowledge(
                        Knowledge(
                            id = JsonCodec.uid(),
                            title = title,
                            content = (b["讲解"] ?: b["知识点讲解"] ?: b["解析"]).orEmpty().trim(),
                            tags = splitTags(b["标签"]),
                            createdAt = now,
                            updatedAt = now
                        ),
                        now
                    )
                )
            }
        }
        return AiParsed(questions = qs, knowledge = ks)
    }

    // ---------- 整理建议 ----------

    fun parseOrganize(text: String): OrganizeSuggestion {
        val raw = JsonCodec.extractJson(text)
        if (raw != null) {
            val el = runCatching { JsonCodec.json.parseToJsonElement(raw) }.getOrNull()
            if (el is JsonObject) {
                val tags = JsonCodec.stringList(el, "tags")
                val collection = JsonCodec.textOrEmpty(el, "collection").ifBlank {
                    JsonCodec.textOrEmpty(el, "合集")
                }.ifBlank { null }
                val related = JsonCodec.stringList(el, "related").ifEmpty {
                    JsonCodec.stringList(el, "relatedIds")
                }
                if (tags.isNotEmpty() || collection != null || related.isNotEmpty()) {
                    return OrganizeSuggestion(
                        tags = JsonCodec.normalizeTags(tags),
                        collection = collection,
                        relatedIds = related
                    )
                }
            }
        }
        var tags = emptyList<String>()
        var collection: String? = null
        var related = emptyList<String>()
        text.lines().forEach { line ->
            labelRegex.find(line)?.let { m ->
                val v = m.groupValues[2].trim()
                when (m.groupValues[1]) {
                    "标签" -> tags = splitTags(v)
                    "合集" -> if (v.isNotBlank()) collection = v
                    "关联", "关联知识点" -> related = splitTags(v)
                }
            }
        }
        return OrganizeSuggestion(tags = tags, collection = collection, relatedIds = related)
    }

    fun splitTags(raw: String?): List<String> =
        (raw ?: "").split(',', '，', '、', ';', '；', '|')
            .map { it.trim().trim('#') }
            .filter { it.isNotEmpty() }
}
