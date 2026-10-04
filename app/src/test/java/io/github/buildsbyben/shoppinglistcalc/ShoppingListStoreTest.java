package io.github.buildsbyben.shoppinglistcalc;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShoppingListStoreTest {
    private static final String PREFS_KEY = "saved_lists";

    private TestSharedPreferences preferences;
    private ShoppingListStore store;

    @Before
    public void setUp() {
        preferences = new TestSharedPreferences();
        store = new ShoppingListStore(preferences);
    }

    private ShoppingItem detailedItem(String name, double price, double qty, boolean byWeight) {
        ShoppingItem item = new ShoppingItem();
        item.name = name;
        item.price = price;
        item.qty = qty;
        item.byWeight = byWeight;
        return item;
    }

    private ShoppingItem nameOnlyItem(String name) {
        ShoppingItem item = new ShoppingItem();
        item.name = name;
        item.qty = 1;
        return item;
    }

    @Test
    public void nameOnlyList_roundTripsThroughStorageAsPlainStrings() {
        ArrayList<ShoppingItem> items = new ArrayList<>();
        items.add(nameOnlyItem("Milk"));
        items.add(nameOnlyItem("Bread"));
        List<ShoppingList> lists = new ArrayList<>();
        lists.add(new ShoppingList("Groceries", items, false));

        store.saveShoppingLists(lists);

        // Confirms the wire format really is plain strings, not JSON objects,
        // which is what keeps old, pre-detail app versions able to read it.
        assertTrue(preferences.getString(PREFS_KEY, "").contains("\"items\":[\"Milk\",\"Bread\"]"));

        ArrayList<ShoppingList> loaded = store.readSavedShoppingLists();
        assertEquals(1, loaded.size());
        assertEquals("Groceries", loaded.get(0).name);
        assertEquals(2, loaded.get(0).items.size());
        ShoppingItem milk = loaded.get(0).items.get(0);
        assertEquals("Milk", milk.name);
        assertFalse(loaded.get(0).hasDetails);
    }

    @Test
    public void detailedList_roundTripsPriceQtyAndWeightMode() {
        ArrayList<ShoppingItem> items = new ArrayList<>();
        items.add(detailedItem("Apples", 3.49, 2, true));
        List<ShoppingList> lists = new ArrayList<>();
        lists.add(new ShoppingList("Produce", items, true));

        store.saveShoppingLists(lists);
        ArrayList<ShoppingList> loaded = store.readSavedShoppingLists();

        String stored = preferences.getString(PREFS_KEY, "");
        assertTrue(stored.contains("\"hasDetails\":true"));
        assertTrue("detailed items should be stored as JSON objects", stored.contains("\"items\":[{"));

        ShoppingItem apples = loaded.get(0).items.get(0);
        assertEquals("Apples", apples.name);
        assertEquals(3.49, apples.price, 0);
        assertEquals(2, apples.qty, 0);
        assertTrue(apples.byWeight);
        assertTrue(loaded.get(0).hasDetails);
    }

    @Test
    public void nameOnlyList_doesNotLeakPricesEvenIfItemsCarryThem() {
        // The list-level flag, not the item contents, decides the storage format.
        ArrayList<ShoppingItem> items = new ArrayList<>();
        items.add(detailedItem("Eggs", 3, 12, false));
        store.saveShoppingLists(Arrays.asList(new ShoppingList("Basics", items, false)));

        String stored = preferences.getString(PREFS_KEY, "");
        assertTrue(stored.contains("\"items\":[\"Eggs\"]"));
        assertTrue(stored.contains("\"hasDetails\":false"));
        ShoppingItem eggs = store.readSavedShoppingLists().get(0).items.get(0);
        assertEquals(0, eggs.price, 0);
        assertEquals(1, eggs.qty, 0);
    }

    @Test
    public void detailedList_doesNotPersistCheckedState() {
        ShoppingItem inCartItem = detailedItem("Cheese", 4, 1, false);
        inCartItem.inCart = true;
        ArrayList<ShoppingItem> items = new ArrayList<>();
        items.add(inCartItem);
        store.saveShoppingLists(Arrays.asList(new ShoppingList("Deli", items, true)));

        assertFalse(preferences.getString(PREFS_KEY, "").contains("inCart"));
        assertFalse(store.readSavedShoppingLists().get(0).items.get(0).inCart);
    }

    @Test
    public void legacyNameOnlyData_fromBeforeDetailsExisted_stillLoads() {
        preferences.edit()
                .putString(PREFS_KEY, "[{\"name\":\"Legacy List\",\"items\":[\"Old Item 1\",\"Old Item 2\"]}]")
                .apply();

        ArrayList<ShoppingList> loaded = store.readSavedShoppingLists();

        assertEquals(1, loaded.size());
        assertEquals("Legacy List", loaded.get(0).name);
        assertFalse("lists without the flag are name-only", loaded.get(0).hasDetails);
        assertEquals("Old Item 1", loaded.get(0).items.get(0).name);
        assertEquals("Old Item 2", loaded.get(0).items.get(1).name);
    }

    @Test
    public void legacyNameOnlyData_staysNameOnlyWhenResaved() {
        preferences.edit()
                .putString(PREFS_KEY, "[{\"name\":\"Legacy List\",\"items\":[\"Old Item 1\"]}]")
                .apply();

        store.saveShoppingLists(store.readSavedShoppingLists());

        assertTrue(preferences.getString(PREFS_KEY, "").contains("\"items\":[\"Old Item 1\"]"));
    }

    @Test
    public void namesWithSemicolonsAndQuotes_surviveRoundTripInBothFormats() {
        ArrayList<ShoppingItem> items = new ArrayList<>();
        items.add(detailedItem("Salt; pepper \"fine\"", 1.25, 1, false));
        store.saveShoppingLists(Arrays.asList(
                new ShoppingList("Plain", items, false),
                new ShoppingList("Detailed", items, true)));

        ArrayList<ShoppingList> loaded = store.readSavedShoppingLists();

        assertEquals("Salt; pepper \"fine\"", loaded.get(0).items.get(0).name);
        assertEquals("Salt; pepper \"fine\"", loaded.get(1).items.get(0).name);
        assertEquals(1.25, loaded.get(1).items.get(0).price, 0);
    }

    @Test
    public void malformedSavedListsJson_isIgnoredWithoutThrowing() {
        preferences.edit().putString(PREFS_KEY, "not json at all").apply();

        ArrayList<ShoppingList> loaded = store.readSavedShoppingLists();

        assertTrue(loaded.isEmpty());
    }

    @Test
    public void malformedItemEntries_areSkippedOrDefaultedRatherThanCrashing() {
        // A number, a null, an object missing every field, and a blank name
        // mixed in with one valid detailed item and one valid name-only item.
        preferences.edit()
                .putString(PREFS_KEY, "[{\"name\":\"Mixed\",\"items\":["
                        + "42,"
                        + "null,"
                        + "{\"price\":1.5},"
                        + "\"  \","
                        + "{\"name\":\"Valid Detailed\",\"price\":9.99,\"qty\":3,\"byWeight\":false},"
                        + "\"Valid Plain\""
                        + "]}]")
                .apply();

        ArrayList<ShoppingList> loaded = store.readSavedShoppingLists();

        assertEquals(1, loaded.size());
        List<ShoppingItem> loadedItems = loaded.get(0).items;
        List<String> names = new ArrayList<>();
        for (ShoppingItem item : loadedItems) {
            names.add(item.name);
        }
        assertTrue(names.contains("Valid Detailed"));
        assertTrue(names.contains("Valid Plain"));
        assertFalse(names.contains(""));
    }

    @Test
    public void loadingSavedItem_intoCurrentList_restoresDetailsViaCopy() {
        // Both "Add to current" and "Replace current" build the new current-list
        // item with ShoppingItem.copy(); this is the restore logic they share.
        ArrayList<ShoppingItem> items = new ArrayList<>();
        items.add(detailedItem("Rice", 5.25, 2, true));
        store.saveShoppingLists(Arrays.asList(new ShoppingList("Pantry", items, true)));

        ShoppingItem saved = store.readSavedShoppingLists().get(0).items.get(0);
        ShoppingItem restoredIntoCurrentList = saved.copy();
        restoredIntoCurrentList.order = 10;

        assertEquals("Rice", restoredIntoCurrentList.name);
        assertEquals(5.25, restoredIntoCurrentList.price, 0);
        assertEquals(2, restoredIntoCurrentList.qty, 0);
        assertTrue(restoredIntoCurrentList.byWeight);
        assertFalse("a freshly loaded item should not start checked off", restoredIntoCurrentList.inCart);
    }

    @Test
    public void loadingNameOnlySavedItem_intoCurrentList_usesDefaults() {
        ArrayList<ShoppingItem> items = new ArrayList<>();
        items.add(nameOnlyItem("Napkins"));
        store.saveShoppingLists(Arrays.asList(new ShoppingList("Household", items, false)));

        ShoppingItem saved = store.readSavedShoppingLists().get(0).items.get(0);
        ShoppingItem restoredIntoCurrentList = saved.copy();

        assertEquals("Napkins", restoredIntoCurrentList.name);
        assertEquals(0, restoredIntoCurrentList.price, 0);
        assertEquals(1, restoredIntoCurrentList.qty, 0);
        assertFalse(restoredIntoCurrentList.byWeight);
    }
}
