package io.github.buildsbyben.shoppinglistcalc

import org.json.JSONException
import org.json.JSONObject

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
    fun copy(): ShoppingItem = ShoppingItem().also {
        it.name = name
        it.order = order
        it.price = price
        it.qty = qty
        it.byWeight = byWeight
        it.inCart = inCart
    }

    @Throws(JSONException::class)
    fun toSavedJson(): JSONObject = JSONObject().apply {
        put("name", name.trim { it <= ' ' })
        put("price", price)
        put("qty", qty)
        put("byWeight", byWeight)
    }

    companion object {
        @JvmStatic fun fromSavedJson(obj: JSONObject): ShoppingItem = ShoppingItem().apply {
            name = (obj.opt("name") as? String ?: "").trim { it <= ' ' }
            price = obj.optDouble("price", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0
            qty = obj.optDouble("qty", 1.0).takeIf { it.isFinite() && it >= 0 } ?: 1.0
            byWeight = obj.optBoolean("byWeight", false)
        }
    }
}
