package io.github.buildsbyben.shoppinglistcalc;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShoppingItemTest {

    @Test
    public void toSavedJson_roundTripsNamePriceQtyAndWeightMode() throws JSONException {
        ShoppingItem item = new ShoppingItem();
        item.name = "Milk";
        item.price = 2.5;
        item.qty = 1.5;
        item.byWeight = true;
        item.inCart = true; // checked state must not be persisted

        JSONObject json = item.toSavedJson();
        ShoppingItem restored = ShoppingItem.fromSavedJson(json);

        assertEquals("Milk", restored.name);
        assertEquals(2.5, restored.price, 0);
        assertEquals(1.5, restored.qty, 0);
        assertTrue(restored.byWeight);
        assertFalse("checked/completed state should not be saved", restored.inCart);
    }

    @Test
    public void toSavedJson_keepsSemicolonsInNames() throws JSONException {
        ShoppingItem item = new ShoppingItem();
        item.name = "Bread; Butter";
        item.price = 1;
        item.qty = 1;

        JSONObject json = item.toSavedJson();

        assertEquals("Bread; Butter", json.getString("name"));
    }

    @Test
    public void fromSavedJson_fillsDefaultsForMissingFields() {
        ShoppingItem restored = ShoppingItem.fromSavedJson(new JSONObject());

        assertEquals("", restored.name);
        assertEquals(0, restored.price, 0);
        assertEquals(1, restored.qty, 0);
        assertFalse(restored.byWeight);
    }

    @Test
    public void fromSavedJson_fallsBackToDefaultsForMalformedFields() throws JSONException {
        JSONObject malformed = new JSONObject();
        malformed.put("name", "Eggs");
        malformed.put("price", "not-a-number");
        malformed.put("qty", "also-not-a-number");
        malformed.put("byWeight", "nope");

        ShoppingItem restored = ShoppingItem.fromSavedJson(malformed);

        assertEquals("Eggs", restored.name);
        assertEquals(0, restored.price, 0);
        assertEquals(1, restored.qty, 0);
        assertFalse(restored.byWeight);
    }
}
