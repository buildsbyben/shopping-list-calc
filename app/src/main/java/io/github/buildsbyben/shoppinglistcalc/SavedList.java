package io.github.buildsbyben.shoppinglistcalc;

import java.util.ArrayList;

final class SavedList {
    final String name;
    final ArrayList<String> items;

    SavedList(String name, ArrayList<String> items) {
        this.name = name;
        this.items = new ArrayList<>(items);
    }
}
