package io.github.buildsbyben.shoppinglistcalc

import org.junit.Assert.*
import org.junit.Test

/** Behavior contracts for the Java-to-Kotlin migration; no UI or implementation reflection. */
class CalculationRegressionTest {
    @Test fun emptyCartHasZeroTotals() {
        val totals = CartTotals.calculate(emptyList(), 8.25)
        assertEquals(0.0, totals.subtotal, 0.0)
        assertEquals(0.0, totals.tax, 0.0)
        assertEquals(0.0, totals.total, 0.0)
    }

    @Test fun quantitiesAndWeightsContributeRegardlessOfCompletionState() {
        val units = item("Milk", 3.25, 2.0).apply { inCart = true }
        val weight = item("Apples", 2.40, 1.25).apply { byWeight = true }
        val totals = CartTotals.calculate(listOf(units, weight), 7.5)
        assertEquals(3.0, weight.lineTotal(), 0.000001)
        assertEquals(9.5, totals.subtotal, 0.000001)
        assertEquals(0.7125, totals.tax, 0.000001)
        assertEquals(10.2125, totals.total, 0.000001)
    }

    @Test fun calculationsDoNotRoundEachLineBeforeSumming() {
        val totals = CartTotals.calculate(listOf(item("A", 0.335, 1.0), item("B", 0.335, 1.0)), 0.0)
        assertEquals(0.67, totals.total, 0.000001)
        assertEquals(0.0, totals.tax, 0.0)
    }

    @Test fun newItemDefaultsAndReadinessStayCompatible() {
        val fresh = ShoppingItem()
        assertEquals("", fresh.name)
        assertEquals(1.0, fresh.qty, 0.0)
        assertEquals(0.0, fresh.price, 0.0)
        assertFalse(fresh.byWeight)
        assertFalse(fresh.inCart)
        assertFalse(fresh.isReadyForCart(true))
        fresh.price = 2.0
        fresh.name = "  "
        assertFalse(fresh.isReadyForCart(false))
        assertTrue(fresh.isReadyForCart(true))
        fresh.name = "Milk"
        assertTrue(fresh.isReadyForCart(false))
        for (invalid in listOf(0.0, -1.0)) {
            fresh.qty = invalid
            assertFalse(fresh.isReadyForCart(true))
            fresh.qty = 1.0
            fresh.price = invalid
            assertFalse(fresh.isReadyForCart(true))
            fresh.price = 2.0
        }
    }

    @Test fun savedListCopiesInputNamesAndRetainsDuplicatesAndOrder() {
        val source = arrayListOf("Milk", "Bread", "Milk")
        val saved = SavedList("Weekly", source)
        source.clear()
        assertEquals("Weekly", saved.name)
        assertEquals(listOf("Milk", "Bread", "Milk"), saved.itemNames)
    }

    private fun item(name: String, price: Double, quantity: Double) = ShoppingItem().apply {
        this.name = name
        this.price = price
        qty = quantity
    }
}
