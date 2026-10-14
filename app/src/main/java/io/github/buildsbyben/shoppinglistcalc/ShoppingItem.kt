package io.github.buildsbyben.shoppinglistcalc

// Keep reference identity: MainActivity uses items as editing/reordering targets.
internal class ShoppingItem {
    @JvmField var name: String = ""
    @JvmField var order: Int = 0
    @JvmField var price: Double = 0.0
    @JvmField var qty: Double = 1.0
    @JvmField var byWeight: Boolean = false
    @JvmField var inCart: Boolean = false

    fun lineTotal(): Double = price * qty

    fun isReadyForCart(allowUnnamed: Boolean): Boolean =
        (allowUnnamed || name.trim { it <= ' ' }.isNotEmpty()) && price > 0 && qty > 0
}
