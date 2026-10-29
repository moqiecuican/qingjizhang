package com.adrian.qingjizhang.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预算进度纯计算测试（计划书 T3.1） */
class BudgetProgressTest {

    @Test fun remaining_normal() {
        val p = BudgetProgress(budgetCents = 300000, spentCents = 120000)
        assertEquals(180000L, p.remainingCents)
        assertFalse(p.isOver)
        assertEquals(0.4f, p.ratio, 0.001f)
    }

    @Test fun overBudget() {
        val p = BudgetProgress(budgetCents = 300000, spentCents = 350000)
        assertEquals(-50000L, p.remainingCents)
        assertTrue(p.isOver)
        assertTrue("ratio 允许 >1，UI 层 coerce", p.ratio > 1f)
    }

    @Test fun exactBudget_notOver() {
        val p = BudgetProgress(budgetCents = 300000, spentCents = 300000)
        assertEquals(0L, p.remainingCents)
        assertFalse("花光不算超支", p.isOver)
        assertEquals(1f, p.ratio, 0.001f)
    }

    @Test fun zeroBudget_ratioZero() {
        val p = BudgetProgress(budgetCents = 0, spentCents = 100)
        assertEquals(0f, p.ratio, 0.0001f) // 防除零
    }

    @Test fun noSpending_fullRemaining() {
        val p = BudgetProgress(budgetCents = 100000, spentCents = 0)
        assertEquals(100000L, p.remainingCents)
        assertEquals(0f, p.ratio, 0.0001f)
    }
}
