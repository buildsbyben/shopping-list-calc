package io.github.buildsbyben.shoppinglistcalc

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** A user-selected display and input format. No device locale or location is read. */
internal class CurrencyFormat(
    symbol: String?,
    @JvmField val symbolAfter: Boolean,
    @JvmField val decimalSeparator: Char,
    @JvmField val groupingSeparator: Char,
    fractionDigits: Int,
) {
    @JvmField val symbol: String = symbol ?: ""
    @JvmField val fractionDigits: Int = fractionDigits.coerceIn(0, 3)

    fun format(amount: Double): String {
        val symbols = DecimalFormatSymbols.getInstance(Locale.ROOT).apply {
            decimalSeparator = this@CurrencyFormat.decimalSeparator
            groupingSeparator = this@CurrencyFormat.groupingSeparator
        }
        val pattern = "#,##0" + if (fractionDigits > 0) "." + "0".repeat(fractionDigits) else ""
        val formatter = DecimalFormat(pattern, symbols).apply {
            isGroupingUsed = groupingSeparator != '\u0000'
            roundingMode = RoundingMode.HALF_UP
        }
        val number = formatter.format(amount)
        if (symbol.isEmpty()) return number
        return if (symbolAfter) "$number $symbol" else "$symbol$number"
    }

    fun parseDirect(raw: String?, fallback: Double): Double {
        // Match Java String.trim(), not Kotlin's broader Unicode whitespace trimming.
        var cleaned = raw?.trim { it <= ' ' } ?: return fallback
        if (cleaned.isEmpty()) return fallback
        cleaned = cleaned.replace(symbol, "").replace(" ", "")
        if (groupingSeparator != '\u0000') {
            cleaned = cleaned.replace(groupingSeparator.toString(), "")
        }
        cleaned = cleaned.replace(decimalSeparator, '.')
        return try {
            BigDecimal(cleaned).setScale(fractionDigits, RoundingMode.HALF_UP).toDouble()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    fun parseQuick(raw: String?, fallback: Double): Double {
        val digits = raw?.replace(Regex("\\D"), "") ?: ""
        if (digits.isEmpty()) return fallback
        return try {
            BigDecimal(digits).movePointLeft(fractionDigits).toDouble()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    fun displayName(): String = format(1234.56)
}
