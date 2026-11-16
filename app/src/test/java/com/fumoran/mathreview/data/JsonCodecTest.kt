package com.fumoran.mathreview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonCodecTest {

    @Test
    fun localRoundTrip() {
        val data = LibraryData(
            questions = listOf(Question(id = "q1", stem = "\$f(x)=x^2\$", tags = listOf("导数"))),
            knowledge = listOf(Knowledge(id = "k1", title = "顶点式", content = "y=a(x-h)^2+k")),
            collections = Collections(q = listOf(Collection(id = "c1", name = "函数")))
        )
        val decoded = JsonCodec.decodeLocal(JsonCodec.encode(data))
        assertEquals(1, decoded.questions.size)
        assertEquals("\$f(x)=x^2\$", decoded.questions[0].stem)
        assertEquals("函数", decoded.collections.q[0].name)
    }

    @Test
    fun legacyTopLevelArray() {
        val text = """[{"stem":"1+1","tags":["算术"]}]"""
        val backup = JsonCodec.parseBackup(text)
        assertEquals(1, backup.questions.size)
        assertEquals("1+1", backup.questions[0].stem)
        assertTrue(backup.questions[0].id.isNotBlank())
    }

    @Test
    fun v4BackupIgnoresPapersAndUnknownFields() {
        val text = """
            {
              "version": 4,
              "questions": [{"id":"q1","stem":"x","status":"reviewing","srs":{"due":1,"interval":1,"ease":2.5,"reps":2},"fromPaper":{"paperId":"p1"}}],
              "knowledge": [{"id":"k1","title":"t","content":"c","collectionId":null}],
              "papers": [{"id":"p1","title":"试卷"}],
              "collections": {"q":[{"id":"c1","name":"A","parentId":null}],"k":[]},
              "images": {"img1":{"type":"image/jpeg","data":"aGVsbG8="}}
            }
        """.trimIndent()
        val backup = JsonCodec.parseBackup(text)
        assertEquals(1, backup.questions.size)
        assertEquals("reviewing", backup.questions[0].status)
        assertEquals(2, backup.questions[0].srs.reps)
        assertEquals(1, backup.images.size)
        assertEquals(1, backup.collections.q.size)
    }

    @Test
    fun invalidJsonThrowsChineseMessage() {
        try {
            JsonCodec.parseBackup("{oops")
            fail("应当抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("JSON"))
        }
    }

    @Test
    fun settleCollectionsBreaksCycle() {
        val cols = listOf(
            Collection(id = "a", name = "A", parentId = "b"),
            Collection(id = "b", name = "B", parentId = "a"),
            Collection(id = "c", name = "C", parentId = "missing")
        )
        val settled = JsonCodec.settleCollections(cols)
        assertEquals(3, settled.size)
        assertTrue(settled.none { it.parentId == "missing" })
        assertTrue(settled.any { it.id == "a" && it.parentId == null } || settled.any { it.id == "b" && it.parentId == null })
    }

    @Test
    fun resolveCollectionPathReusesExisting() {
        val cols = listOf(Collection(id = "c1", name = "函数"))
        val (list, id) = JsonCodec.resolveCollectionPath(cols, "函数 / 二次函数")
        assertEquals(2, list.size)
        val child = list.first { it.id == id }
        assertEquals("二次函数", child.name)
        assertEquals("c1", child.parentId)

        val (again, id2) = JsonCodec.resolveCollectionPath(list, "函数/二次函数")
        assertEquals(2, again.size)
        assertEquals(id, id2)
    }

    @Test
    fun parseAiJsonItems() {
        val text = """
            ```json
            [{"stem":"求 ${'$'}x^2${'$'} 的最小值","analysis":"0","knowledge":"平方非负","tags":["二次函数"]}]
            ```
        """.trimIndent()
        val parsed = AiParse.parseItems(text, LibType.Q)
        assertEquals(1, parsed.questions.size)
        assertEquals(listOf("二次函数"), parsed.questions[0].tags)
    }

    @Test
    fun parseAiLabeledFallback() {
        val text = """
            题目：求 ${'$'}1+1${'$'}
            解析：等于 2
            知识点：加法
            标签：算术, 基础
        """.trimIndent()
        val parsed = AiParse.parseItems(text, LibType.Q)
        assertEquals(1, parsed.questions.size)
        assertEquals(listOf("算术", "基础"), parsed.questions[0].tags)
        assertEquals("等于 2", parsed.questions[0].analysis)
    }

    @Test
    fun parseOrganizeJson() {
        val s = AiParse.parseOrganize("""{"tags":["导数","单调性"],"collection":"函数 / 导数","related":["k1"]}""")
        assertEquals(2, s.tags.size)
        assertEquals("函数 / 导数", s.collection)
        assertEquals(listOf("k1"), s.relatedIds)
    }

    @Test
    fun scheduleSrsMatchesWeb() {
        val first = scheduleSrs(Srs(), STATUS_REVIEWING, now = 1000L)
        assertEquals(1.0, first.interval, 0.0001)
        assertEquals(1, first.reps)

        val second = scheduleSrs(first, STATUS_REVIEWING, now = 1000L)
        assertEquals(2.0, second.interval, 0.0001)

        val mastered = scheduleSrs(Srs(interval = 1.0, ease = 2.5), STATUS_MASTERED, now = 1000L)
        assertEquals(3.0, mastered.interval, 0.0001)

        val reset = scheduleSrs(mastered, STATUS_NEW, now = 1000L)
        assertEquals(0.0, reset.interval, 0.0001)
        assertEquals(2.5, reset.ease, 0.0001)
    }

    @Test
    fun mergeTagsKeepsOrderAndAddsNew() {
        assertEquals(
            listOf("导数", "求导", "极限"),
            JsonCodec.mergeTags(listOf("导数", "求导"), listOf("极限", "导数"))
        )
    }

    @Test
    fun cleanPastedAndExtractJson() {
        val text = "好的，结果如下：\n```json\n{\"tags\":[\"a\"]}\n```\n希望有帮助"
        assertEquals("{\"tags\":[\"a\"]}", JsonCodec.extractJson(text))
        assertEquals("a(b)c", JsonCodec.stripLatex("\$a(b)c\$"))
    }
}
