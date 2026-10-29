package com.adrian.qingjizhang.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.adrian.qingjizhang.core.CategorySlice
import com.adrian.qingjizhang.theme.ChartPalette
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 环形图（V1.2 T2.1）：手写 Canvas，零依赖。
 * - 扇区从 -90°（12 点方向）顺时针铺开，sweep = ratio * 360
 * - 「其他」扇区用中性灰，永远画在最后
 * - 点击命中：触点→圆心向量，半径落在 [innerR, outerR] 且角度落入某弧段 → onSectorTap
 *   （index = -1 表示「其他」扇区；同时回传触点局部坐标供气泡锚定）
 */
@Composable
fun DonutChart(
    slices: List<CategorySlice>,
    otherSlice: CategorySlice?,
    centerText: String,
    modifier: Modifier = Modifier,
    onSectorTap: ((index: Int, tap: Offset) -> Unit)? = null,
) {
    val dark = isSystemInDarkTheme()
    // 绘制序列 = 主扇区 + 其他（命中索引与之一致：其他 = slices.size）
    val drawList = slices + listOfNotNull(otherSlice)
    val colors = drawList.mapIndexed { i, _ ->
        if (i < slices.size) ChartPalette.color(i, dark) else ChartPalette.other(dark)
    }

    Box(modifier = modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .pointerInput(drawList) {
                    detectTapGestures { tap ->
                        if (onSectorTap == null) return@detectTapGestures
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = tap.x - cx
                        val dy = tap.y - cy
                        val r = sqrt(dx * dx + dy * dy)
                        val outer = minOf(cx, cy) - 8f
                        val inner = outer * 0.62f
                        if (r < inner || r > outer) return@detectTapGestures
                        // 角度：atan2 得 (-π, π]，0=右；转为从 12 点顺时针 [0,360)
                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (deg < 0) deg += 360f
                        var acc = 0f
                        for ((i, s) in drawList.withIndex()) {
                            val sweep = s.ratio * 360f
                            if (deg >= acc && deg < acc + sweep) {
                                onSectorTap(if (i < slices.size) i else -1, tap)
                                return@detectTapGestures
                            }
                            acc += sweep
                        }
                    }
                },
        ) {
            val outer = minOf(size.width, size.height) / 2f - 8f
            val inner = outer * 0.62f
            val strokeW = outer - inner
            val midR = (outer + inner) / 2f
            var start = -90f
            for ((i, s) in drawList.withIndex()) {
                val sweep = s.ratio * 360f
                drawArc(
                    color = colors[i],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeW),
                    topLeft = Offset(size.width / 2f - midR, size.height / 2f - midR),
                    size = androidx.compose.ui.geometry.Size(midR * 2, midR * 2),
                )
                start += sweep
            }
        }
        // 中心文字：窗口总支出（叠在 Box 中心，比 Canvas 画字可靠）
        androidx.compose.material3.Text(
            text = centerText,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        )
    }
}
