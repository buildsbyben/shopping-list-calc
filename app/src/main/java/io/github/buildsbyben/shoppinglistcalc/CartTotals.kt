package io.github.buildsbyben.shoppinglistcalc

internal class CartTotals private constructor(
    @JvmField val subtotal: Double,
    @JvmField val tax: Double,
) {
    @JvmField val total: Double = subtotal + tax

    companion object {
        @JvmStatic
        fun calculate(items: List<ShoppingItem>, taxRate: Double): CartTotals {
            var subtotal = 0.0
            for (item in items) subtotal += item.lineTotal()
            return CartTotals(subtotal, subtotal * (taxRate / 100.0))
        }
    }
}
