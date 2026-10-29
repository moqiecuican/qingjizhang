package com.adrian.qingjizhang.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** 月度汇总纯逻辑测试（计划书 Phase 2） */
class MonthStatsTest {

    @Test fun balance_incomeMinusExpense() {
        assertEquals(300L, MonthStats(incomeCents = 1000, expenseCents = 700).balanceCents)
    }

    @Test fun balance_negativeWhenOverspent() {
        assertEquals(-200L, MonthStats(incomeCents = 800, expenseCents = 1000).balanceCents)
    }

    @Test fun emptyMonth_isZero() {
        val s = MonthStats()
        assertEquals(0L, s.incomeCents + s.expenseCents + s.balanceCents)
    }
}
