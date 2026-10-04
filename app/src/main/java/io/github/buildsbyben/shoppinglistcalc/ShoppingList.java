package io.github.buildsbyben.shoppinglistcalc;

import java.util.ArrayList;

final class ShoppingList {
    final String name;
    boolean hasDetails;
    final ArrayList<ShoppingItem> items;

    ShoppingList(String name, ArrayList<ShoppingItem> items, boolean hasDetails) {
        this.name = name;
        this.hasDetails = hasDetails;
        this.items = new ArrayList<>(items);
    }
}
