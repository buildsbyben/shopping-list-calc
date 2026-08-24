package io.github.buildsbyben.shoppinglistcalc;

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

    boolean isReadyForCart() {
        return !name.trim().isEmpty() && price > 0 && qty > 0;
    }
}
