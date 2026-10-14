package io.github.buildsbyben.shoppinglistcalc

import org.junit.Assert.*
import org.junit.Test

/** Java behavior that can change unintentionally with Kotlin idioms. */
class HelperCompatibilityTest {
    @Test fun duplicateItemsRemainDistinctEditingAndReorderTargets() {
        val first = ShoppingItem().apply { name = "Milk"; price = 2.0 }
        val second = ShoppingItem().apply { name = "Milk"; price = 2.0 }
        val items = arrayListOf(first, second)
        assertNotEquals(first, second)
        assertEquals(1, items.indexOf(second))
        items.remove(second)
        assertSame(first, items.single())
    }

    @Test fun itemNameValidationRetainsJavaWhitespaceRules() {
        val item = ShoppingItem().apply { price = 2.0 }
        item.name = "\t\r\n "
        assertFalse(item.isReadyForCart(false))
        // Java trim leaves an em space intact; Kotlin trim would remove it.
        item.name = "\u2003"
        assertTrue(item.isReadyForCart(false))
    }

    @Test fun currencyInputRetainsJavaWhitespaceAndAsciiQuickEntryRules() {
        val currency = CurrencyFormat("$", false, '.', ',', 2)
        assertEquals(1.25, currency.parseDirect("\t1.25\n", -1.0), 0.0)
        assertEquals(-1.0, currency.parseDirect("\u20031.25\u2003", -1.0), 0.0)
        assertEquals(-1.0, currency.parseQuick("\u0661\u0662\u0663", -1.0), 0.0)
        assertEquals(1.23, currency.parseQuick("-123", -1.0), 0.0)
    }
}
