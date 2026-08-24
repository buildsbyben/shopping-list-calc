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
