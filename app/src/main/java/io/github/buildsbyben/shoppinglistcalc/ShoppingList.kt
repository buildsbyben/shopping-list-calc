package io.github.buildsbyben.shoppinglistcalc

// Based on StrongTheDev's per-list detail model from PR #8.
internal class ShoppingList(
    @JvmField val name: String,
    items: List<ShoppingItem>,
    @JvmField val hasDetails: Boolean
) {
    @JvmField val items: ArrayList<ShoppingItem> = ArrayList(items.map { it.copy().apply {
        inCart = false
        if (!hasDetails) { price = 0.0; qty = 1.0; byWeight = false }
    } })
}
