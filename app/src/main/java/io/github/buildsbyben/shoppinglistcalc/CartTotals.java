package io.github.buildsbyben.shoppinglistcalc;

import java.util.List;

final class CartTotals {
    final double subtotal;
    final double tax;
    final double total;

    private CartTotals(double subtotal, double tax) {
        this.subtotal = subtotal;
        this.tax = tax;
        this.total = subtotal + tax;
    }

    static CartTotals calculate(List<ShoppingItem> items, double taxRate) {
        double subtotal = 0;
        for (ShoppingItem item : items) {
            subtotal += item.lineTotal();
        }
        return new CartTotals(subtotal, subtotal * (taxRate / 100.0));
    }
}
