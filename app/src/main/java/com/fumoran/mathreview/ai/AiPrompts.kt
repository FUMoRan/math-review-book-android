package com.fumoran.mathreview.ai

import com.fumoran.mathreview.data.LibType

object AiPrompts {

    private val LATEX_RULES = """
公式一律使用 LaTeX：行内用 §...§，块级用 §§...§§；不要用 Markdown 代码块包裹公式。
""".trim().replace('§', '$')

    fun ocr(type: LibType): String {
        val what = if (type == LibType.Q) "数学题目" else "数学笔记与知识点"
        return """
你是数学题识别助手。$LATEX_RULES
请逐字识别图片中的全部$what，保留题号、选项、条件与符号。
输出纯文本，每道题/每段笔记之间空一行，不要输出任何解释、标题或 Markdown 围栏。
""".trimIndent()
    }

    fun solve(type: LibType, collectionPaths: List<String>): String {
        val schema = if (type == LibType.Q) {
            """[{"stem":"题目原文","analysis":"解题解析","knowledge":"知识点讲解","myAnswer":"我的错误答案（可为空）","tags":["标签1","标签2"],"collection":"可选：给出的合集路径之一"}]"""
        } else {
            """[{"title":"知识点标题","content":"讲解内容","tags":["标签1"],"collection":"可选：给出的合集路径之一"}]"""
        }
        val colHint = if (collectionPaths.isEmpty()) "" else buildString {
            append("\n可从下列合集路径中选择最合适的一项写入 collection（必须原样使用，否则省略该字段）：\n")
            collectionPaths.take(40).forEach { append("- ").append(it).append('\n') }
        }
        return """
你是数学复习本的整理助手。$LATEX_RULES

请把用户提供的原始内容整理成条目，只输出 JSON 数组，不要解释、不要代码块。
字段格式：
$schema

要求：
1. 公式用 §...§ 包裹；
2. 每道错题都要给出 analysis 与 knowledge，无法确定时写「暂无」；
3. 标签为 1-4 个简短中文词，不要带 #；
4. 不要合并不同题目，保持原意。$colHint
""".trimIndent().replace('§', '$')
    }

    fun organize(
        type: LibType,
        content: String,
        collectionPaths: List<String>,
        knowledge: List<Pair<String, String>>
    ): String {
        val kind = if (type == LibType.Q) "错题" else "知识点"
        val cols = if (collectionPaths.isEmpty()) "（暂无合集）" else
            collectionPaths.take(60).joinToString("\n") { "- $it" }
        val kps = if (knowledge.isEmpty()) "（暂无知识点）" else
            knowledge.take(200).joinToString("\n") { (id, title) -> "- $id | $title" }
        return """
你是数学复习本的整理助手。
请为下面这条${kind}给出整理建议，只输出 JSON 对象，不要解释、不要代码块：
{"tags":["标签"],"collection":"合集路径","related":["知识点id"]}

规则：
1. 标签 1-4 个，简短中文，不要带 #；
2. collection 必须从下面给出的路径中原样选择；都不合适则输出空字符串；
3. related 最多 3 个，必须从下面给出的知识点 id 中选择；都不相关则输出空数组；
4. 不要修改或复述条目内容。

可选合集路径：
$cols

可选知识点（id | 标题）：
$kps

条目内容：
$content
""".trimIndent()
    }

    fun itemsJson(type: LibType): String = if (type == LibType.Q) {
        """[{"stem":"","analysis":"","knowledge":"","myAnswer":"","tags":[]}]"""
    } else {
        """[{"title":"","content":"","tags":[]}]"""
    }
}
