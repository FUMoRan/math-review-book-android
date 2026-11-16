package com.fumoran.mathreview.ui.components

/**
 * 公式切分，与网页端 lib.js / app.js 的行为保持一致：
 * 1. 优先识别定界符：$$...$$ 与 \[...\] 为行间公式，$...$ 与 \(...\) 为行内公式；
 * 2. 其余文本按「裸 LaTeX」启发式识别（AI 常直接输出 y'' = \frac{dp}{dx} 这类无定界符内容）；
 * 3. 多行环境（matrix / cases / aligned 等）整体作为行间公式；
 * 4. 整行只有公式（允许行尾标点）时按行间公式处理。
 *
 * 纯 Kotlin 实现，不依赖 Android，便于单元测试。
 */
object MathSegments {

    data class Seg(val text: String, val math: Boolean, val display: Boolean = false)

    private val DELIMITER = Regex(
        """\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\$[^$\n]+?\$|\\\([\s\S]+?\\\)"""
    )

    private val ENV = Regex(
        """\\begin\{(pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix|matrix|cases|aligned|alignedat|gathered|split|array|subarray|align\*?|alignat\*?)\}[\s\S]*?\\end\{\1\}"""
    )

    private const val MATH_SYMBOLS = "\\{}^_()[]|+-*/=.,;:'\"<>!?~%&·×÷≤≥≠≈∞∫∑∏√∂±→←⇒⇔∈∀∃αβγδθλμσφωπΩΔ∘∥⊥∠"

    fun split(text: String): List<Seg> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<Seg>()
        var last = 0
        for (m in DELIMITER.findAll(text)) {
            if (m.range.first > last) appendBare(text.substring(last, m.range.first), out)
            val token = m.value
            val display = token.startsWith("$$") || token.startsWith("\\[")
            val body = when {
                token.startsWith("$$") -> token.substring(2, token.length - 2)
                token.startsWith("\\[") || token.startsWith("\\(") -> token.substring(2, token.length - 2)
                else -> token.substring(1, token.length - 1)
            }
            out.add(Seg(body, math = true, display = display))
            last = m.range.last + 1
        }
        if (last < text.length) appendBare(text.substring(last), out)
        return out
    }

    // ---------- 裸 LaTeX 切分（对应网页端 bareMathSegments） ----------

    private fun isAlnum(c: Char) = c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'

    private fun isLetterAscii(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    private fun isMathChar(c: Char) = isAlnum(c) || MATH_SYMBOLS.indexOf(c) >= 0

    private fun isBlank(c: Char) = c == ' ' || c == '\t'

    private fun isBlankUnderscore(s: String, i: Int): Boolean {
        if (i < 0 || i >= s.length || s[i] != '_') return false
        return (i + 1 < s.length && s[i + 1] == '_') || (i > 0 && s[i - 1] == '_')
    }

    /** 强信号：\命令、上下标、求导撇、等号左侧有操作数 */
    private fun mathTrigger(s: String, i: Int): Boolean {
        val c = s[i]
        if (c == '\\') return i + 1 < s.length && isLetterAscii(s[i + 1])
        if (c == '^' || c == '_') {
            if (i + 1 >= s.length) return false
            val n = s[i + 1]
            return isAlnum(n) || n == '{' || n == '(' || n == '['
        }
        if (c == '\'') {
            var j = i
            while (j > 0 && isLetterAscii(s[j - 1])) j--
            val run = i - j
            return run in 1..2 || (i > 0 && s[i - 1] == ')')
        }
        if (c == '=') {
            if (i == 0) return false
            val p = s[i - 1]
            return isAlnum(p) || p == ')' || p == ']' || p == '}' || p == '\''
        }
        return false
    }

    private fun tokenLeft(s: String, end: Int): String {
        var i = end
        while (i > 0 && isMathChar(s[i - 1])) i--
        return s.substring(i, end)
    }

    private fun tokenRight(s: String, from: Int): String {
        var i = from
        while (i < s.length && isMathChar(s[i])) i++
        return s.substring(from, i)
    }

    /** 像数学的词：含 \命令、数字/上下标/括号/运算符；变量要求一两个字母或带撇 */
    private fun mathToken(tok: String): Boolean {
        if (tok.isEmpty()) return false
        if (Regex("""\\[A-Za-z]+""").containsMatchIn(tok)) return true
        for (c in tok) {
            if (c in "0123456789^_{}[]()=+-*/<>") return true
        }
        var run = 0
        var maxRun = 0
        for (c in tok) {
            if (isLetterAscii(c)) {
                run++
                if (run > maxRun) maxRun = run
            } else {
                run = 0
            }
        }
        if (maxRun >= 3) return false
        if (!tok.matches(Regex("""[A-Za-z][A-Za-z']*"""))) return false
        return tok.contains('\'') || tok.length <= 2
    }

    private fun expandLeft(s: String, start: Int): Int {
        var i = start
        while (i > 0) {
            if (isBlankUnderscore(s, i - 1)) break
            if (isMathChar(s[i - 1])) {
                i--
                continue
            }
            if (isBlank(s[i - 1])) {
                var j = i
                while (j > 0 && isBlank(s[j - 1])) j--
                if (mathToken(tokenLeft(s, j)) && mathToken(tokenRight(s, i))) {
                    i = j
                    continue
                }
            }
            break
        }
        return i
    }

    private fun expandRight(s: String, start: Int): Int {
        val n = s.length
        var i = start
        while (i < n) {
            val c = s[i]
            if (c == '\n' || c == '\r') break
            if (isBlankUnderscore(s, i)) break
            if (isMathChar(c)) {
                i++
                continue
            }
            if (isBlank(c)) {
                var j = i
                while (j < n && isBlank(s[j])) j++
                if (j < n && s[j] != '\n' && s[j] != '\r' &&
                    mathToken(tokenLeft(s, i)) && mathToken(tokenRight(s, j))
                ) {
                    i = j
                    continue
                }
            }
            break
        }
        return i
    }

    private fun isTrailingPunct(c: Char) = c.isWhitespace() || c in "。，；：、,;:."

    private fun appendBare(s: String, out: MutableList<Seg>) {
        val n = s.length
        var textStart = 0

        fun flush(end: Int) {
            if (end > textStart) out.add(Seg(s.substring(textStart, end), math = false))
        }

        val envEnd = HashMap<Int, Int>()
        for (m in ENV.findAll(s)) envEnd[m.range.first] = m.range.last + 1

        var i = 0
        while (i < n) {
            val envB = envEnd[i]
            if (envB != null) {
                var a = expandLeft(s, i)
                while (a < envB && s[a].isWhitespace()) a++
                flush(a)
                out.add(Seg(s.substring(a, envB), math = true, display = true))
                textStart = envB
                i = envB
                continue
            }
            if (!mathTrigger(s, i)) {
                i++
                continue
            }
            var a = expandLeft(s, i)
            var b = expandRight(s, i + 1)
            while (a < b && s[a].isWhitespace()) a++
            while (b > a && s[b - 1].isWhitespace()) b--
            while (b > a && (s[b - 1] == '.' || s[b - 1] == ',' || s[b - 1] == ';' || s[b - 1] == ':')) b--
            while (a < b && (s[a] == '.' || s[a] == ',' || s[a] == ';' || s[a] == ':')) a++
            if (b <= a) {
                i++
                continue
            }

            val lineStart = if (a == 0) 0 else s.lastIndexOf('\n', a - 1) + 1
            var lineEnd = s.indexOf('\n', b)
            if (lineEnd < 0) lineEnd = n
            var tail = lineEnd
            while (tail > b && isTrailingPunct(s[tail - 1])) tail--
            val display = s.substring(lineStart, a).isBlank() && tail == b

            flush(a)
            out.add(Seg(s.substring(a, b), math = true, display = display))
            textStart = b
            i = b
        }
        flush(n)
    }
}
