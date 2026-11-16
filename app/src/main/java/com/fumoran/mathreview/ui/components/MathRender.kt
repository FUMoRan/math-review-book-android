package com.fumoran.mathreview.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.ReplacementSpan
import android.util.LruCache
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * 把 [MathSegments] 的结果渲染为可放进 TextView 的 CharSequence。
 *
 * 与网页端一致：定界符与裸 LaTeX 都交给公式引擎；解析失败时退回纯文本。
 * 公式位图按「字号 + 颜色 + 源码」缓存，避免同一公式在列表/详情里反复排版。
 */
object MathRender {

    private val drawableCache = object : LruCache<String, JLatexMathDrawable>(160) {}
    private val textCache = object : LruCache<String, CharSequence>(64) {}

    fun render(text: String, px: Float, colorArgb: Int): CharSequence {
        val key = "$px|$colorArgb|$text"
        textCache.get(key)?.let { return it }

        val out = SpannableStringBuilder()
        for (seg in MathSegments.split(text)) {
            if (!seg.math) {
                out.append(seg.text)
                continue
            }
            val drawable = try {
                drawableOf(seg.text, px, colorArgb)
            } catch (_: Exception) {
                null
            }
            if (drawable == null) {
                out.append(seg.text)
                continue
            }
            if (seg.display) {
                if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append('\n')
                val start = out.length
                out.append(' ')
                out.setSpan(LatexSpan(drawable), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                out.append('\n')
            } else {
                val start = out.length
                out.append(' ')
                out.setSpan(LatexSpan(drawable), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val result: CharSequence = out
        textCache.put(key, result)
        return result
    }

    private fun drawableOf(tex: String, px: Float, color: Int): JLatexMathDrawable {
        val key = "$px|$color|$tex"
        drawableCache.get(key)?.let { return it }
        val drawable = JLatexMathDrawable.builder(tex)
            .textSize(px)
            .color(color)
            .background(0)
            .padding(0)
            .build()
        drawableCache.put(key, drawable)
        return drawable
    }

    /**
     * JLatexMath 的 drawable 从顶部开始绘制，而文字基线需要扣掉公式深度（下标等），
     * 因此用 ReplacementSpan 按 [JLatexMathDrawable.icon] 的 depth 对齐基线。
     */
    private class LatexSpan(private val drawable: JLatexMathDrawable) : ReplacementSpan() {

        private fun depth(): Int = try {
            drawable.icon().iconDepth
        } catch (_: Exception) {
            0
        }

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val w = drawable.intrinsicWidth
            val h = drawable.intrinsicHeight
            drawable.setBounds(0, 0, w, h)
            if (fm != null) {
                val d = depth()
                val ascent = h - d
                if (-ascent < fm.ascent) fm.ascent = -ascent
                if (d > fm.descent) fm.descent = d
            }
            return w
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val ascent = drawable.bounds.height() - depth()
            canvas.save()
            canvas.translate(x, (y - ascent).toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }
}
