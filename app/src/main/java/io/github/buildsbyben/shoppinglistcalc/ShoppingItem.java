package io.github.buildsbyben.shoppinglistcalc;

import org.json.JSONException;
import org.json.JSONObject;

final class ShoppingItem {
    String name = "";
    int order;
    double price;
    double qty = 1;
    boolean byWeight;
    boolean inCart;

    double lineTotal() {
        return price * qty;
    }

    boolean isReadyForCart(boolean allowUnnamed) {
        return (allowUnnamed || !name.trim().isEmpty()) && price > 0 && qty > 0;
    }

    ShoppingItem copy() {
        ShoppingItem copy = new ShoppingItem();
        copy.name = name;
        copy.order = order;
        copy.price = price;
        copy.qty = qty;
        copy.byWeight = byWeight;
        copy.inCart = inCart;
        return copy;
    }

    JSONObject toSavedJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("name", name.trim());
        object.put("price", price);
        object.put("qty", qty);
        object.put("byWeight", byWeight);
        return object;
    }

    static ShoppingItem fromSavedJson(JSONObject object) {
        ShoppingItem item = new ShoppingItem();
        item.name = object.optString("name", "").trim();
        item.price = object.optDouble("price", 0);
        item.qty = object.optDouble("qty", 1);
        item.byWeight = object.optBoolean("byWeight", false);
        return item;
    }
}
