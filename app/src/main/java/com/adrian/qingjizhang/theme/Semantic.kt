package com.adrian.qingjizhang.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 收支语义色：支出=红、收入=绿；暗色模式用更亮变体保证对比度 */
object Semantic {
    @Composable fun expense(): Color = if (isSystemInDarkTheme()) ExpenseRedDark else ExpenseRed
    @Composable fun income(): Color = if (isSystemInDarkTheme()) IncomeGreenDark else IncomeGreen
}
