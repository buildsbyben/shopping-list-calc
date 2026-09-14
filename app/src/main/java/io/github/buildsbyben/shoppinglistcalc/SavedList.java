package io.github.buildsbyben.shoppinglistcalc;

import java.util.ArrayList;

final class SavedList {
    final String name;
    final ArrayList<String> itemNames;

    SavedList(String name, ArrayList<String> itemNames) {
        this.name = name;
        this.itemNames = new ArrayList<>(itemNames);
    }
}
