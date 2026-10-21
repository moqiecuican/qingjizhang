package com.adrian.qingjizhang.core

/**
 * 金额工具：全 app 金额一律以「分」(Long) 存储与计算，只在 UI 显示时转「元」字符串。
 * 纯 Kotlin，禁止 import 任何 Android API —— 为将来 KMP 迁移共享这部分逻辑。
 */
object Money {

    /** 单笔金额上限（分）：99,999,999.99 元，足够个人记账用一辈子 */
    const val MAX_CENTS = 99_999_999L

    /**
     * 把用户输入的「元」字符串解析为「分」。
     * 规则：只允许数字与最多一个小数点、小数最多 2 位；不接受负号/空格/其它符号。
     * 例："12.34"->1234  "12"->1200  ".5"->50  "0.05"->5
     * 非法输入（空串、"abc"、"1.234"、"-5"、超大值）返回 null。
     */
    fun parseToCents(input: String): Long? {
        val s = input.trim()
        if (s.isEmpty()) return null
        // 手动校验格式：整数部分可选（".5" 合法），小数部分最多 2 位
        val parts = s.split('.')
        if (parts.size > 2) return null
        val intPart = parts[0]
        val fracPart = if (parts.size == 2) parts[1] else ""
        if (intPart.isEmpty() && fracPart.isEmpty()) return null
        if (!intPart.all { it.isDigit() }) return null
        if (!fracPart.all { it.isDigit() }) return null
        if (fracPart.length > 2) return null
        val yuan = intPart.ifEmpty { "0" }.toLongOrNull() ?: return null
        val cents = when (fracPart.length) {
            0 -> 0L
            1 -> (fracPart[0] - '0') * 10L
            else -> (fracPart[0] - '0') * 10L + (fracPart[1] - '0')
        }
        val total = yuan * 100 + cents
        if (total <= 0L || total > MAX_CENTS) return null // 金额 0 视为非法（必填且必须 > 0）
        return total
    }

    /**
     * 把「分」格式化为「元」显示串：1234->"12.34" 100->"1.00" 0->"0.00" -1234->"-12.34"
     */
    fun formatYuan(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val yuan = abs / 100
        val frac = abs % 100
        return buildString {
            if (negative) append('-')
            append(yuan)
            append('.')
            append(if (frac < 10) "0" else "").append(frac)
        }
    }
}
