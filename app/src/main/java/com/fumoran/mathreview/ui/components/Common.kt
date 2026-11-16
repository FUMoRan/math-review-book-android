package com.fumoran.mathreview.ui.components

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fumoran.mathreview.data.STATUS_LABEL
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 原生渲染 Markdown + LaTeX（无 WebView）。 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    if (text.isBlank()) return
    val color = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val px = with(density) { style.fontSize.toPx() }
    val appContext = LocalContext.current.applicationContext

    val markwon = remember(px, color, appContext) {
        Markwon.builder(appContext)
            .usePlugin(
                JLatexMathPlugin.create(px) { builder ->
                    builder.theme().textColor(color.toArgb())
                }
            )
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                typeface = Typeface.DEFAULT
            }
        },
        update = { tv ->
            tv.setTextColor(color.toArgb())
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
            markwon.setMarkdown(tv, text)
        }
    )
}

@Composable
fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        "mastered" -> Color(0xFF10B981).copy(alpha = 0.15f) to Color(0xFF047857)
        "reviewing" -> Color(0xFFF59E0B).copy(alpha = 0.18f) to Color(0xFFB45309)
        else -> Color(0xFF64748B).copy(alpha = 0.15f) to Color(0xFF475569)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = STATUS_LABEL[status] ?: "未复习",
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@Composable
fun TagPill(text: String, selected: Boolean = false) {
    Box(
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "#$text",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun EmptyState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🌱", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

fun formatDate(ts: Long): String = if (ts <= 0L) "-" else dateFormat.format(Date(ts))

fun toast(context: android.content.Context, msg: String?) {
    if (!msg.isNullOrBlank()) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
