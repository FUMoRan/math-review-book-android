package com.fumoran.mathreview.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 与网页端 lib.test.js 的公式切分用例保持一致。 */
class MathSegmentsTest {

    private fun mathOf(text: String) = MathSegments.split(text)
        .filter { it.math }
        .map { it.text }

    @Test
    fun inlineDelimiters() {
        val segs = MathSegments.split("求 \\(x^2\\) 的值")
        assertEquals(1, segs.count { it.math })
        val m = segs.first { it.math }
        assertEquals("x^2", m.text)
        assertFalse(m.display)

        val dollar = MathSegments.split("求 \$x^2\$ 的值").filter { it.math }
        assertEquals(1, dollar.size)
        assertEquals("x^2", dollar[0].text)
        assertFalse(dollar[0].display)
    }

    @Test
    fun displayDelimiters() {
        val dd = MathSegments.split("前\n\$\$\na+b\n\$\$\n后").filter { it.math }
        assertEquals(1, dd.size)
        assertTrue(dd[0].display)
        assertTrue(dd[0].text.contains("a+b"))

        val bracket = MathSegments.split("\\[a=b\\]").filter { it.math }
        assertEquals(1, bracket.size)
        assertTrue(bracket[0].display)
        assertEquals("a=b", bracket[0].text)
    }

    @Test
    fun bareInlineInChineseSentence() {
        val text = "由 a_1 与公差 d 可得 x^2 的结果。"
        assertEquals(listOf("a_1", "x^2"), mathOf(text))
        assertTrue(MathSegments.split(text).filter { it.math }.none { it.display })
    }

    @Test
    fun bareWholeLineBecomesDisplay() {
        val segs = MathSegments.split("x^2 + 1 = 0")
        assertEquals(1, segs.count { it.math })
        val m = segs.first { it.math }
        assertTrue(m.display)
        assertEquals("x^2 + 1 = 0", m.text)
    }

    @Test
    fun bareWholeLineWithFullWidthPeriod() {
        val segs = MathSegments.split("\\frac{a}{b} = c。")
        assertEquals(1, segs.count { it.math })
        assertTrue(segs.first { it.math }.display)
    }

    @Test
    fun fullWidthPeriodDoesNotGetSwallowed() {
        val segs = MathSegments.split("因此 y=x^2。")
        assertEquals(listOf("y=x^2"), segs.filter { it.math }.map { it.text })
        assertFalse(segs.first { it.math }.display)
    }

    @Test
    fun derivativePrimeStaysInFormula() {
        assertEquals(listOf("yy' = C_1"), mathOf("第2步：得 yy' = C_1。"))
        assertEquals(listOf("(yy')' = 0"), mathOf("(yy')' = 0"))
    }

    @Test
    fun matrixEnvironmentIsDisplay() {
        val src = "\\begin{cases} x=1 \\\\ y=2 \\end{cases}"
        val segs = MathSegments.split(src)
        assertEquals(1, segs.count { it.math })
        val m = segs.first { it.math }
        assertTrue(m.display)
        assertTrue(m.text.contains("\\begin{cases}"))
    }

    @Test
    fun noTriggerInPlainChinese() {
        assertEquals(emptyList<String>(), mathOf("这道题考查导数的定义与几何意义"))
    }

    @Test
    fun fillBlankUnderscoreIsNotMath() {
        assertEquals(emptyList<String>(), mathOf("特解为_______。"))
        assertTrue(mathOf("则 a=____。").none { it.contains("____") })
    }

    @Test
    fun englishWordNotSwallowed() {
        assertEquals(listOf("s^2"), mathOf("用 MATLAB 绘制 s^2 曲线"))
        assertTrue(mathOf("注意 don't 这类词").isEmpty())
    }

    @Test
    fun mixedTextAndFormulasRoundTrip() {
        val segs = MathSegments.split("设 \$f(x)=x^2\$，则 f'(x)=2x。")
        assertTrue(segs.any { it.math && it.text == "f(x)=x^2" })
        assertTrue(segs.any { it.math && it.text.contains("2x") })
        assertTrue(segs.any { !it.math && it.text.contains("设") })
        assertTrue(segs.none { it.math && it.valueContainsDelimiter() })
    }

    private fun MathSegments.Seg.valueContainsDelimiter() =
        text.contains("\$") || text.contains("\\(") || text.contains("\\[")
}
