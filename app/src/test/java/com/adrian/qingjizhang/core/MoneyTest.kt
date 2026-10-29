package com.adrian.qingjizhang.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 金额「元↔分」转换单元测试（计划书 V4 验收项）。
 * 这些用例就是记账 app 的第一铁律：永远不用浮点。
 */
class MoneyTest {

    // ---------- parseToCents: 合法输入 ----------

    @Test fun parse_twoDecimals() = assertEquals(1234L, Money.parseToCents("12.34"))

    @Test fun parse_integer() = assertEquals(1200L, Money.parseToCents("12"))

    @Test fun parse_oneDecimal() = assertEquals(1250L, Money.parseToCents("12.5"))

    @Test fun parse_leadingDot() = assertEquals(50L, Money.parseToCents(".5"))

    @Test fun parse_fiveCents() = assertEquals(5L, Money.parseToCents("0.05"))

    @Test fun parse_oneYuan() = assertEquals(100L, Money.parseToCents("1"))

    @Test fun parse_large() = assertEquals(99_999_999L, Money.parseToCents("999999.99"))

    @Test fun parse_trailingZeros() = assertEquals(1200L, Money.parseToCents("12.0"))

    @Test fun parse_surroundingSpaces() = assertEquals(1234L, Money.parseToCents(" 12.34 "))

    // ---------- parseToCents: 非法输入 ----------

    @Test fun parse_empty_isNull() = assertNull(Money.parseToCents(""))

    @Test fun parse_blank_isNull() = assertNull(Money.parseToCents("   "))

    @Test fun parse_zero_isNull() = assertNull(Money.parseToCents("0")) // 金额必须 > 0

    @Test fun parse_zeroDotZero_isNull() = assertNull(Money.parseToCents("0.00"))

    @Test fun parse_threeDecimals_rejected() = assertNull(Money.parseToCents("1.234")) // 拒绝静默四舍五入

    @Test fun parse_negative_rejected() = assertNull(Money.parseToCents("-5"))

    @Test fun parse_letters_rejected() = assertNull(Money.parseToCents("abc"))

    @Test fun parse_doubleDot_rejected() = assertNull(Money.parseToCents("1..2"))

    @Test fun parse_dotOnly_rejected() = assertNull(Money.parseToCents("."))

    @Test fun parse_comma_rejected() = assertNull(Money.parseToCents("1,000"))

    @Test fun parse_overflow_rejected() = assertNull(Money.parseToCents("100000000"))

    @Test fun parse_plusSign_rejected() = assertNull(Money.parseToCents("+5"))

    // ---------- formatYuan ----------

    @Test fun format_twoDecimals() = assertEquals("12.34", Money.formatYuan(1234))

    @Test fun format_roundYuan() = assertEquals("1.00", Money.formatYuan(100))

    @Test fun format_zero() = assertEquals("0.00", Money.formatYuan(0))

    @Test fun format_negative() = assertEquals("-12.34", Money.formatYuan(-1234))

    @Test fun format_singleDigitCent() = assertEquals("0.05", Money.formatYuan(5))

    @Test fun format_tenCents() = assertEquals("0.10", Money.formatYuan(10))

    // ---------- 精度铁律：0.1 + 0.2 必须等于 0.30 ----------

    @Test fun precision_noFloatError() {
        val a = Money.parseToCents("0.1")!!
        val b = Money.parseToCents("0.2")!!
        assertEquals("0.30", Money.formatYuan(a + b)) // 浮点运算这里是 0.30000000000000004
    }
}
