package com.adrian.qingjizhang.core

/**
 * 预算进度纯计算（可测试、可迁移）。
 * remaining<0 即超支；ratio 封顶显示用（进度条最多 100%）。
 */
data class BudgetProgress(
    val budgetCents: Long,
    val spentCents: Long,
) {
    val remainingCents: Long get() = budgetCents - spentCents
    val isOver: Boolean get() = remainingCents < 0
    /** 0f..1f+，UI 进度条用 coerceAtMost(1f) */
    val ratio: Float get() = if (budgetCents <= 0) 0f else spentCents.toFloat() / budgetCents
}
