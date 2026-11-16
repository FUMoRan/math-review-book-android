package com.fumoran.mathreview.data

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToLong

/** 与网页版完全一致的数据结构（v4）。字段名大小写敏感，勿改。 */

@Serializable
data class Srs(
    val due: Long = 0L,
    val interval: Double = 0.0,
    val ease: Double = 2.5,
    val reps: Int = 0
)

@Serializable
data class Question(
    val id: String = "",
    val stem: String = "",
    val analysis: String = "",
    val knowledge: String = "",
    val myAnswer: String = "",
    val tags: List<String> = emptyList(),
    val status: String = STATUS_NEW,
    val star: Boolean = false,
    val images: List<String> = emptyList(),
    val collectionId: String? = null,
    val kpIds: List<String> = emptyList(),
    val srs: Srs = Srs(),
    val order: Double? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class Knowledge(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val status: String = STATUS_NEW,
    val star: Boolean = false,
    val images: List<String> = emptyList(),
    val collectionId: String? = null,
    val srs: Srs = Srs(),
    val order: Double? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class Collection(
    val id: String = "",
    val name: String = "",
    val parentId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class Collections(
    val q: List<Collection> = emptyList(),
    val k: List<Collection> = emptyList()
) {
    fun forType(type: LibType): List<Collection> = if (type == LibType.Q) q else k

    fun withType(type: LibType, list: List<Collection>): Collections =
        if (type == LibType.Q) copy(q = list) else copy(k = list)
}

@Serializable
data class ImageRec(
    val type: String = "image/jpeg",
    val data: String = ""
)

@Serializable
data class Backup(
    val version: Int = 4,
    val questions: List<Question> = emptyList(),
    val knowledge: List<Knowledge> = emptyList(),
    val collections: Collections = Collections(),
    val images: Map<String, ImageRec> = emptyMap()
)

/** 本地库文件（不含图片二进制）。 */
@Serializable
data class LibraryData(
    val questions: List<Question> = emptyList(),
    val knowledge: List<Knowledge> = emptyList(),
    val collections: Collections = Collections()
)

enum class LibType(val key: String) {
    Q("q"), K("k");

    companion object {
        fun of(key: String): LibType = if (key == "k") K else Q
    }
}

const val STATUS_NEW = "new"
const val STATUS_REVIEWING = "reviewing"
const val STATUS_MASTERED = "mastered"

val STATUS_LABEL: Map<String, String> = linkedMapOf(
    STATUS_NEW to "未复习",
    STATUS_REVIEWING to "复习中",
    STATUS_MASTERED to "已掌握"
)

private const val DAY_MS = 86_400_000L

fun nextStatus(status: String): String = when (status) {
    STATUS_NEW -> STATUS_REVIEWING
    STATUS_REVIEWING -> STATUS_MASTERED
    else -> STATUS_NEW
}

fun normalizeSrs(s: Srs): Srs = Srs(
    due = max(0L, s.due),
    interval = max(0.0, s.interval),
    ease = if (s.ease > 0) s.ease.coerceIn(1.3, 3.0) else 2.5,
    reps = max(0, s.reps)
)

/** 与网页版 scheduleSrs 保持一致。 */
fun scheduleSrs(current: Srs, newStatus: String, now: Long = System.currentTimeMillis()): Srs {
    val s = normalizeSrs(current)
    var interval = s.interval
    var ease = s.ease
    when (newStatus) {
        STATUS_NEW -> {
            interval = 0.0
            ease = 2.5
        }
        STATUS_MASTERED -> {
            interval = if (interval > 0) (interval * ease).roundToLong().toDouble() else 3.0
        }
        else -> {
            interval = if (interval > 0) max(1.0, (interval * 1.5).roundToLong().toDouble()) else 1.0
        }
    }
    val due = if (interval > 0) now + (interval * DAY_MS).toLong() else now
    return Srs(due = due, interval = interval, ease = ease, reps = s.reps + 1)
}

fun isDue(srs: Srs, now: Long = System.currentTimeMillis()): Boolean = srs.due <= now

fun dueLabel(srs: Srs, now: Long = System.currentTimeMillis()): String {
    val due = srs.due
    if (due <= 0L) return "待复习"
    val diff = due - now
    if (diff <= 0L) return "待复习"
    if (diff < DAY_MS) return "今天稍后"
    return "${Math.round(diff.toDouble() / DAY_MS)} 天后复习"
}
