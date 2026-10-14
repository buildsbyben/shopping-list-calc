package io.github.buildsbyben.shoppinglistcalc

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CurrencyFormatRegressionTest {
    private val dollars = CurrencyFormat("$", false, '.', ',', 2)

    @Test fun formatsPrefixSuffixAndGroupingChoices() {
        assertEquals("$1,234.56", dollars.format(1234.56))
        assertEquals("1.234,56 EUR", CurrencyFormat("EUR", true, ',', '.', 2).format(1234.56))
        assertEquals("1234,56 EUR", CurrencyFormat("EUR", true, ',', '\u0000', 2).format(1234.56))
        assertEquals("1 234,56", CurrencyFormat("", false, ',', ' ', 2).format(1234.56))
        assertEquals("$-12.50", dollars.format(-12.5))
    }

    @Test fun fractionDigitsAreBoundedAndFormattingUsesHalfUp() {
        assertEquals("2", CurrencyFormat(null, false, '.', ',', -1).format(1.5))
        assertEquals("1.250", CurrencyFormat("", false, '.', ',', 4).format(1.25))
        assertEquals("$1.13", dollars.format(1.125))
        assertEquals("$1,234.56", dollars.displayName())
    }

    @Test fun directEntryHandlesCustomSeparatorsAndRoundsHalfUp() {
        assertEquals(1234.56, dollars.parseDirect(" $1,234.56 ", -1.0), 0.0)
        assertEquals(1234.56, CurrencyFormat("EUR", true, ',', '.', 2).parseDirect("1.234,56 EUR", -1.0), 0.0)
        assertEquals(1234.56, CurrencyFormat("", false, ',', ' ', 2).parseDirect("1 234,56", -1.0), 0.0)
        assertEquals(1.24, dollars.parseDirect("1.235", -1.0), 0.0)
        assertEquals(-1.24, dollars.parseDirect("-1.235", 0.0), 0.0)
    }

    @Test fun invalidDirectInputReturnsCallerFallback() {
        for (raw in listOf(null, "", "  ", "$", "abc", "1.2.3", "NaN", "Infinity")) {
            assertEquals("Input: $raw", 42.5, dollars.parseDirect(raw, 42.5), 0.0)
        }
    }

    @Test fun quickEntryUsesDigitsAndConfiguredFractionCount() {
        assertEquals(12.34, dollars.parseQuick("$12.34", -1.0), 0.0)
        assertEquals(0.05, dollars.parseQuick("5", -1.0), 0.0)
        assertEquals(1234.0, CurrencyFormat("", false, '.', ',', 0).parseQuick("1234", -1.0), 0.0)
        assertEquals(1.234, CurrencyFormat("", false, '.', ',', 3).parseQuick("1234", -1.0), 0.0)
        for (raw in listOf(null, "", "$", "abc")) {
            assertEquals(42.5, dollars.parseQuick(raw, 42.5), 0.0)
        }
    }

    @Test fun selectedFormatIsIndependentOfDeviceLocale() {
        val original = Locale.getDefault()
        try {
            for (locale in listOf(Locale.US, Locale.GERMANY, Locale.FRANCE)) {
                Locale.setDefault(locale)
                assertEquals("$1,234.56", dollars.format(1234.56))
                assertEquals(1234.56, dollars.parseDirect("$1,234.56", -1.0), 0.0)
            }
        } finally {
            Locale.setDefault(original)
        }
    }
}
