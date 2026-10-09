package io.github.buildsbyben.shoppinglistcalc

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class ShoppingListStore(private val preferences: SharedPreferences) {
    fun taxRate(): Double = preferences.getFloat(KEY_TAX_RATE, 0f).toDouble()

    fun budget(): Double = preferences.getFloat(KEY_BUDGET, 0f).toDouble()

    fun saveSettings(taxRate: Double, budget: Double) {
        preferences.edit()
            .putFloat(KEY_TAX_RATE, taxRate.toFloat())
            .putFloat(KEY_BUDGET, budget.toFloat())
            .apply()
    }

    fun currencyFormat(): CurrencyFormat {
        val symbol = preferences.getString(KEY_CURRENCY_SYMBOL, "$")
        val after = preferences.getBoolean(KEY_CURRENCY_POSITION, false)
        val decimal = preferences.getString(KEY_DECIMAL_SEPARATOR, ".")
        val grouping = preferences.getString(KEY_GROUPING_SEPARATOR, ",")
        val digits = preferences.getInt(KEY_FRACTION_DIGITS, 2)
        return CurrencyFormat(symbol, after,
            decimal?.firstOrNull() ?: '.', grouping?.firstOrNull() ?: '\u0000', digits)
    }

    fun saveCurrencyFormat(format: CurrencyFormat) {
        preferences.edit()
            .putString(KEY_CURRENCY_SYMBOL, format.symbol)
            .putBoolean(KEY_CURRENCY_POSITION, format.symbolAfter)
            .putString(KEY_DECIMAL_SEPARATOR, format.decimalSeparator.toString())
            .putString(KEY_GROUPING_SEPARATOR,
                if (format.groupingSeparator == '\u0000') "" else format.groupingSeparator.toString())
            .putInt(KEY_FRACTION_DIGITS, format.fractionDigits)
            .apply()
    }

    fun quickCentsEntry(): Boolean = preferences.getBoolean(KEY_PRICE_ENTRY_MODE, false)

    fun saveQuickCentsEntry(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PRICE_ENTRY_MODE, enabled).apply()
    }

    fun quickEntry(): Boolean = preferences.getBoolean(KEY_QUICK_ENTRY, false)

    fun saveQuickEntry(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_QUICK_ENTRY, enabled).apply()
    }

    fun weightUnit(): String {
        val unit = preferences.getString(KEY_WEIGHT_UNIT, "lb")
        return if (unit == "kg" || unit == "oz" || unit == "g") unit else "lb"
    }

    fun saveWeightUnit(unit: String?) {
        preferences.edit().putString(KEY_WEIGHT_UNIT, unit).apply()
    }

    fun loadItems(destination: MutableList<ShoppingItem>) {
        destination.clear()
        val raw = preferences.getString(KEY_ITEMS, "[]")
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val item = ShoppingItem()
                item.name = obj.optString("name")
                item.order = obj.optInt("order", (i + 1) * 10)
                item.price = obj.optDouble("price", 0.0)
                item.qty = obj.optDouble("qty", 1.0)
                item.byWeight = obj.optBoolean("byWeight", false)
                item.inCart = obj.optBoolean("inCart", false)
                destination.add(item)
            }
        } catch (ignored: JSONException) {
            destination.clear()
        }
    }

    fun saveItems(items: List<ShoppingItem>) {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            try {
                obj.put("name", item.name)
                obj.put("order", item.order)
                obj.put("price", item.price)
                obj.put("qty", item.qty)
                obj.put("byWeight", item.byWeight)
                obj.put("inCart", item.inCart)
                array.put(obj)
            } catch (ignored: JSONException) {
                // Keep saving the remaining valid items.
            }
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun readSavedShoppingLists(): ArrayList<ShoppingList> {
        val lists = ArrayList<ShoppingList>()
        val raw = preferences.getString(KEY_SAVED_LISTS, "[]")
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name").trim { it <= ' ' }
                if (name.isEmpty()) continue
                val items = ArrayList<ShoppingItem>()
                val rawItems = obj.optJSONArray("items")
                if (rawItems != null) {
                    for (j in 0 until rawItems.length()) {
                        val item = when (val entry = rawItems.opt(j)) {
                            is JSONObject -> ShoppingItem.fromSavedJson(entry)
                            is String -> ShoppingItem().apply { this.name = entry.trim { it <= ' ' } }
                            else -> null
                        }
                        if (item != null && item.name.isNotEmpty()) items.add(item)
                    }
                }
                lists.add(ShoppingList(name, items, obj.optBoolean("hasDetails", false)))
            }
        } catch (ignored: JSONException) {
            // Invalid saved-list data should not prevent the main list loading.
        }
        return lists
    }

    fun saveShoppingLists(lists: List<ShoppingList>) {
        val array = JSONArray()
        for (list in lists) {
            val obj = JSONObject()
            val items = JSONArray()
            for (item in list.items) {
                val entry: Any = if (list.hasDetails) {
                    try { item.toSavedJson() } catch (ignored: JSONException) { item.name.trim { it <= ' ' } }
                } else item.name.trim { it <= ' ' }
                items.put(entry)
            }
            try {
                obj.put("name", list.name)
                obj.put("items", items)
                obj.put("hasDetails", list.hasDetails)
                array.put(obj)
            } catch (ignored: JSONException) {
                // Keep saving the remaining valid lists.
            }
        }
        preferences.edit().putString(KEY_SAVED_LISTS, array.toString()).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val KEY_TAX_RATE = "tax_rate"
        const val KEY_BUDGET = "budget"
        const val KEY_CURRENCY_SYMBOL = "currency_symbol"
        const val KEY_CURRENCY_POSITION = "currency_position"
        const val KEY_DECIMAL_SEPARATOR = "decimal_separator"
        const val KEY_GROUPING_SEPARATOR = "grouping_separator"
        const val KEY_FRACTION_DIGITS = "fraction_digits"
        const val KEY_PRICE_ENTRY_MODE = "price_entry_mode"
        const val KEY_QUICK_ENTRY = "quick_entry"
        const val KEY_WEIGHT_UNIT = "weight_unit"
        const val KEY_SAVED_LISTS = "saved_lists"
    }
}
