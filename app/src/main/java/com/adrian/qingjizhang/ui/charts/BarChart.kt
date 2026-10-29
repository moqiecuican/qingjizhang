package com.adrian.qingjizhang.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adrian.qingjizhang.core.TimeBar
import com.adrian.qingjizhang.theme.Semantic

/**
 * 时间序列双柱图（V1.2 T2.3）：每桶两根（收=绿/支=红语义色），Y 轴自适应最大值。
 * 点柱 → onBarTap(bar)，用于跳回流水页对应窗口。
 * 柱下标签按粒度（周标签/月/年），字号 labelSmall，超宽自动省略。
 */
@Composable
fun BarChart(
    bars: List<TimeBar>,
    modifier: Modifier = Modifier,
    onBarTap: ((TimeBar) -> Unit)? = null,
) {
    val maxCents = (bars.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) } ?: 0L).coerceAtLeast(1L)
    val incomeColor = Semantic.income()
    val expenseColor = Semantic.expense()

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(bars) {
                    detectTapGestures { tap ->
                        if (onBarTap == null || bars.isEmpty()) return@detectTapGestures
                        val slot = size.width / bars.size
                        val idx = (tap.x / slot).toInt().coerceIn(0, bars.size - 1)
                        onBarTap(bars[idx])
                    }
                },
        ) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val barW = (slot * 0.28f).coerceAtMost(28f)
            val gap = slot * 0.08f
            for ((i, bar) in bars.withIndex()) {
                val x0 = i * slot + slot / 2f
                val hIn = (bar.incomeCents.toFloat() / maxCents) * (size.height - 8f)
                val hEx = (bar.expenseCents.toFloat() / maxCents) * (size.height - 8f)
                // 收柱在左、支柱在右，居中成对
                drawRect(incomeColor, topLeft = Offset(x0 - barW - gap / 2, size.height - hIn), size = androidx.compose.ui.geometry.Size(barW, hIn))
                drawRect(expenseColor, topLeft = Offset(x0 + gap / 2, size.height - hEx), size = androidx.compose.ui.geometry.Size(barW, hEx))
            }
        }
        // 柱下标签行（与 Canvas 同槽位对齐）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
