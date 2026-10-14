package io.github.buildsbyben.shoppinglistcalc

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShoppingListStoreRegressionTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var store: ShoppingListStore

    @Before fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("shopping_calc", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = ShoppingListStore(preferences)
    }

    @Test fun freshInstallDefaultsRemainStable() {
        assertEquals(0.0, store.taxRate(), 0.0)
        assertEquals(0.0, store.budget(), 0.0)
        assertEquals("$1,234.56", store.currencyFormat().format(1234.56))
        assertEquals("lb", store.weightUnit())
        assertFalse(store.quickCentsEntry())
        assertFalse(store.quickEntry())
        assertTrue(store.savedLists().isEmpty())
        val destination = arrayListOf(ShoppingItem())
        store.loadItems(destination)
        assertTrue(destination.isEmpty())
    }

    @Test fun loadsLiteralLegacyItemDataIncludingMissingFieldDefaults() {
        preferences.edit().putString("items", """[
          {"name":"Apples","order":30,"price":2.4,"qty":1.25,"byWeight":true,"inCart":true},
          {"name":"Milk"}
        ]""").commit()
        val items = arrayListOf(ShoppingItem())
        store.loadItems(items)
        assertEquals(2, items.size)
        assertItem(items[0], "Apples", 30, 2.4, 1.25, true, true)
        assertItem(items[1], "Milk", 20, 0.0, 1.0, false, false)
    }

    @Test fun savingItemsPreservesLegacyKeysAndAllFieldsAcrossReopening() {
        val item = ShoppingItem().apply {
            name = "Crème \"special\"\n牛乳"
            order = 40
            price = 3.75
            qty = 0.5
            byWeight = true
            inCart = true
        }
        store.saveItems(listOf(item))
        val raw = JSONArray(preferences.getString("items", "[]")).getJSONObject(0)
        assertEquals(setOf("name", "order", "price", "qty", "byWeight", "inCart"), raw.keys().asSequence().toSet())
        val loaded = arrayListOf<ShoppingItem>()
        ShoppingListStore(preferences).loadItems(loaded)
        assertEquals(1, loaded.size)
        assertItem(loaded.single(), item.name, 40, 3.75, 0.5, true, true)
        store.saveItems(emptyList())
        store.loadItems(loaded)
        assertTrue(loaded.isEmpty())
    }

    @Test fun malformedItemDataClearsDestinationRatherThanKeepingStaleOrPartialItems() {
        for (raw in listOf("not JSON", "{}", """[{"name":"valid"},42]""")) {
            preferences.edit().putString("items", raw).commit()
            val loaded = arrayListOf(ShoppingItem())
            store.loadItems(loaded)
            assertTrue("Input: $raw", loaded.isEmpty())
        }
    }

    @Test fun loadsLiteralLegacyPreferencesWithoutChangingTypesOrKeys() {
        preferences.edit()
            .putFloat("tax_rate", 8.25f).putFloat("budget", 123.5f)
            .putString("currency_symbol", "EUR").putBoolean("currency_position", true)
            .putString("decimal_separator", ",").putString("grouping_separator", "")
            .putInt("fraction_digits", 3).putBoolean("price_entry_mode", true)
            .putBoolean("quick_entry", true).putString("weight_unit", "g").commit()
        assertEquals(8.25, store.taxRate(), 0.0)
        assertEquals(123.5, store.budget(), 0.0)
        assertEquals("1234,560 EUR", store.currencyFormat().format(1234.56))
        assertTrue(store.quickCentsEntry())
        assertTrue(store.quickEntry())
        assertEquals("g", store.weightUnit())
    }

    @Test fun preferenceWritesRemainReadableByLegacyCode() {
        store.saveSettings(7.5, 100.25)
        store.saveCurrencyFormat(CurrencyFormat("EUR", true, ',', '\u0000', 3))
        store.saveQuickCentsEntry(true)
        store.saveQuickEntry(true)
        store.saveWeightUnit("oz")
        assertEquals(7.5f, preferences.getFloat("tax_rate", -1f), 0f)
        assertEquals(100.25f, preferences.getFloat("budget", -1f), 0f)
        assertEquals("EUR", preferences.getString("currency_symbol", null))
        assertTrue(preferences.getBoolean("currency_position", false))
        assertEquals(",", preferences.getString("decimal_separator", null))
        assertEquals("", preferences.getString("grouping_separator", null))
        assertEquals(3, preferences.getInt("fraction_digits", -1))
        assertTrue(preferences.getBoolean("price_entry_mode", false))
        assertTrue(preferences.getBoolean("quick_entry", false))
        assertEquals("oz", preferences.getString("weight_unit", null))
        val reopened = ShoppingListStore(preferences)
        assertEquals("1234,560 EUR", reopened.currencyFormat().format(1234.56))
        store.saveCurrencyFormat(CurrencyFormat("", false, ',', ' ', 2))
        assertEquals("1 234,56", reopened.currencyFormat().format(1234.56))
    }

    @Test fun supportedUnitsRoundTripAndUnknownUnitsFallBackToPounds() {
        for (unit in listOf("lb", "kg", "oz", "g")) {
            store.saveWeightUnit(unit)
            assertEquals(unit, ShoppingListStore(preferences).weightUnit())
        }
        preferences.edit().putString("weight_unit", "unknown").putString("decimal_separator", "").commit()
        assertEquals("lb", store.weightUnit())
        assertEquals('.', store.currencyFormat().decimalSeparator)
    }

    @Test fun legacySavedListsTrimNamesSkipBlanksAndPreserveOrderAndDuplicates() {
        preferences.edit().putString("saved_lists", """[
          {"name":" Weekly ","items":[" Milk ","", "Bread", "Milk"]},
          {"name":" ","items":["ignored"]}, {"name":"Empty"}
        ]""").commit()
        val lists = store.savedLists()
        assertEquals(listOf("Weekly", "Empty"), lists.map { it.name })
        assertEquals(listOf("Milk", "Bread", "Milk"), lists[0].itemNames)
        assertTrue(lists[1].itemNames.isEmpty())
    }

    @Test fun savedListWritesKeepLegacySchemaAndDoNotModifyCurrentCart() {
        preferences.edit().putString("items", """[{"name":"Current"}]""").commit()
        store.saveSavedLists(listOf(SavedList("Weekly", arrayListOf("Milk", "Bread"))))
        val raw = JSONArray(preferences.getString("saved_lists", "[]")).getJSONObject(0)
        assertEquals(setOf("name", "items"), raw.keys().asSequence().toSet())
        assertEquals("Weekly", raw.getString("name"))
        assertEquals("Milk", raw.getJSONArray("items").getString(0))
        assertEquals(listOf("Milk", "Bread"), ShoppingListStore(preferences).savedLists().single().itemNames)
        store.saveSavedLists(emptyList())
        assertTrue(store.savedLists().isEmpty())
        val current = arrayListOf<ShoppingItem>()
        store.loadItems(current)
        assertEquals("Current", current.single().name)
    }

    @Test fun malformedSavedListsDoNotPreventLoadingCurrentCart() {
        preferences.edit().putString("items", """[{"name":"Current"}]""")
            .putString("saved_lists", "not JSON").commit()
        assertTrue(store.savedLists().isEmpty())
        val current = arrayListOf<ShoppingItem>()
        store.loadItems(current)
        assertEquals("Current", current.single().name)
    }

    @Test fun savedListsKeepJavaTrimRulesAndValidPrefixOnCorruption() {
        preferences.edit().putString("saved_lists", """[
          {"name":"\u0000 Weekly \u001f","items":["\u0000 Milk \u001f","\u2003","\u2003Bread\u2003"]},
          42, {"name":"Not reached"}
        ]""").commit()
        val lists = store.savedLists()
        assertEquals(listOf("Weekly"), lists.map { it.name })
        assertEquals(listOf("Milk", "\u2003", "\u2003Bread\u2003"), lists.single().itemNames)
    }

    @Test fun invalidNumbersSkipOnlyInvalidItemsWhenSaving() {
        val valid = ShoppingItem().apply { name = "Kept"; price = 2.5 }
        val badPrice = ShoppingItem().apply { name = "Bad price"; price = Double.NaN }
        val badQuantity = ShoppingItem().apply { name = "Bad qty"; qty = Double.POSITIVE_INFINITY }
        store.saveItems(listOf(badPrice, valid, badQuantity, valid))
        val loaded = arrayListOf<ShoppingItem>()
        store.loadItems(loaded)
        assertEquals(listOf("Kept", "Kept"), loaded.map { it.name })
    }

    @Test fun settingsRetainFloatPrecisionAndSeparatorsUseFirstCharacter() {
        store.saveSettings(7.123456789, 123.123456789)
        assertEquals(7.123456789.toFloat().toDouble(), store.taxRate(), 0.0)
        assertEquals(123.123456789.toFloat().toDouble(), store.budget(), 0.0)
        preferences.edit().putString("decimal_separator", ",extra")
            .putString("grouping_separator", " extra").commit()
        assertEquals(',', store.currencyFormat().decimalSeparator)
        assertEquals(' ', store.currencyFormat().groupingSeparator)
    }

    private fun assertItem(item: ShoppingItem, name: String, order: Int, price: Double,
                           quantity: Double, byWeight: Boolean, inCart: Boolean) {
        assertEquals(name, item.name)
        assertEquals(order, item.order)
        assertEquals(price, item.price, 0.0)
        assertEquals(quantity, item.qty, 0.0)
        assertEquals(byWeight, item.byWeight)
        assertEquals(inCart, item.inCart)
    }
}
