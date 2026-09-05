package io.github.buildsbyben.shoppinglistcalc;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

final class ShoppingListStore {
    private static final String KEY_ITEMS = "items";
    private static final String KEY_TAX_RATE = "tax_rate";
    private static final String KEY_BUDGET = "budget";
    private static final String KEY_CURRENCY_SYMBOL = "currency_symbol";
    private static final String KEY_CURRENCY_POSITION = "currency_position";
    private static final String KEY_DECIMAL_SEPARATOR = "decimal_separator";
    private static final String KEY_GROUPING_SEPARATOR = "grouping_separator";
    private static final String KEY_FRACTION_DIGITS = "fraction_digits";
    private static final String KEY_PRICE_ENTRY_MODE = "price_entry_mode";
    private static final String KEY_QUICK_ENTRY = "quick_entry";
    private static final String KEY_WEIGHT_UNIT = "weight_unit";

    private final SharedPreferences preferences;

    ShoppingListStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    double taxRate() {
        return preferences.getFloat(KEY_TAX_RATE, 0f);
    }

    double budget() {
        return preferences.getFloat(KEY_BUDGET, 0f);
    }

    void saveSettings(double taxRate, double budget) {
        preferences.edit()
                .putFloat(KEY_TAX_RATE, (float) taxRate)
                .putFloat(KEY_BUDGET, (float) budget)
                .apply();
    }

    CurrencyFormat currencyFormat() {
        String symbol = preferences.getString(KEY_CURRENCY_SYMBOL, "$");
        boolean after = preferences.getBoolean(KEY_CURRENCY_POSITION, false);
        String decimal = preferences.getString(KEY_DECIMAL_SEPARATOR, ".");
        String grouping = preferences.getString(KEY_GROUPING_SEPARATOR, ",");
        int digits = preferences.getInt(KEY_FRACTION_DIGITS, 2);
        return new CurrencyFormat(symbol, after,
                decimal == null || decimal.isEmpty() ? '.' : decimal.charAt(0),
                grouping == null || grouping.isEmpty() ? '\0' : grouping.charAt(0), digits);
    }

    void saveCurrencyFormat(CurrencyFormat format) {
        preferences.edit()
                .putString(KEY_CURRENCY_SYMBOL, format.symbol)
                .putBoolean(KEY_CURRENCY_POSITION, format.symbolAfter)
                .putString(KEY_DECIMAL_SEPARATOR, String.valueOf(format.decimalSeparator))
                .putString(KEY_GROUPING_SEPARATOR, format.groupingSeparator == '\0' ? "" : String.valueOf(format.groupingSeparator))
                .putInt(KEY_FRACTION_DIGITS, format.fractionDigits)
                .apply();
    }

    boolean quickCentsEntry() {
        return preferences.getBoolean(KEY_PRICE_ENTRY_MODE, false);
    }

    void saveQuickCentsEntry(boolean enabled) {
        preferences.edit().putBoolean(KEY_PRICE_ENTRY_MODE, enabled).apply();
    }

    boolean quickEntry() {
        return preferences.getBoolean(KEY_QUICK_ENTRY, false);
    }

    void saveQuickEntry(boolean enabled) {
        preferences.edit().putBoolean(KEY_QUICK_ENTRY, enabled).apply();
    }

    String weightUnit() {
        String unit = preferences.getString(KEY_WEIGHT_UNIT, "lb");
        return "kg".equals(unit) || "oz".equals(unit) || "g".equals(unit) ? unit : "lb";
    }

    void saveWeightUnit(String unit) {
        preferences.edit().putString(KEY_WEIGHT_UNIT, unit).apply();
    }

    void loadItems(List<ShoppingItem> destination) {
        destination.clear();
        String raw = preferences.getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                ShoppingItem item = new ShoppingItem();
                item.name = object.optString("name");
                item.order = object.optInt("order", (i + 1) * 10);
                item.price = object.optDouble("price", 0);
                item.qty = object.optDouble("qty", 1);
                item.byWeight = object.optBoolean("byWeight", false);
                item.inCart = object.optBoolean("inCart", false);
                destination.add(item);
            }
        } catch (JSONException ignored) {
            destination.clear();
        }
    }

    void saveItems(List<ShoppingItem> items) {
        JSONArray array = new JSONArray();
        for (ShoppingItem item : items) {
            JSONObject object = new JSONObject();
            try {
                object.put("name", item.name);
                object.put("order", item.order);
                object.put("price", item.price);
                object.put("qty", item.qty);
                object.put("byWeight", item.byWeight);
                object.put("inCart", item.inCart);
                array.put(object);
            } catch (JSONException ignored) {
                // Keep saving the remaining valid items.
            }
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply();
    }
}
