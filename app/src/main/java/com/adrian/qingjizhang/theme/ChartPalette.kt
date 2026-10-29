package com.adrian.qingjizhang.theme

import androidx.compose.ui.graphics.Color

/**
 * 统计图表调色板（V1.2）：8 色可区分色组，明/暗各一套。
 * 取色原则：相邻色相拉开、暗底提亮、与语义色（收绿/支红）不混淆——
 * 图表色只用于"分类身份"，收支语义色另走 Semantic。
 */
object ChartPalette {
    val light = listOf(
        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF6C00), Color(0xFF7E57C2),
        Color(0xFFD4A017), Color(0xFF00897B), Color(0xFFC2185B), Color(0xFF546E7A),
    )
    val dark = listOf(
        Color(0xFF9FA8DA), Color(0xFF80CBC4), Color(0xFFFFB74D), Color(0xFFB39DDB),
        Color(0xFFFFD54F), Color(0xFF4DB6AC), Color(0xFFF48FB1), Color(0xFF90A4AE),
    )
    /** 「其他」扇区：中性灰，明示"这是合并项" */
    val otherLight = Color(0xFF9E9E9E)
    val otherDark = Color(0xFF757575)

    fun color(index: Int, isDark: Boolean): Color =
        (if (isDark) dark else light)[index % 8]

    fun other(isDark: Boolean): Color = if (isDark) otherDark else otherLight
}
