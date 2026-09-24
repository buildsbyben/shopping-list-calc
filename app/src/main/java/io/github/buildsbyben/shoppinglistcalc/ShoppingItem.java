package io.github.buildsbyben.shoppinglistcalc;

final class ShoppingItem {
    String name = "";
    int order;
    double price;
    double qty = 1;
    boolean byWeight;
    boolean inCart;
    public static final String SEPARATOR = ";"; // csv separator

    double lineTotal() {
        return price * qty;
    }

    boolean isReadyForCart(boolean allowUnnamed) {
        return (allowUnnamed || !name.trim().isEmpty()) && price > 0 && qty > 0;
    }

    String toSavedLine(boolean saveFullData) {
        String name = this.name.trim();
        if (!saveFullData) {
            return name;
        }
        name = name.replace(SEPARATOR, "");
        return name + SEPARATOR + order + SEPARATOR + price + SEPARATOR + qty + SEPARATOR + byWeight + SEPARATOR + inCart;
    }

    static ShoppingItem fromSavedLine(String line) {
        String[] parts = line.split(SEPARATOR);
        ShoppingItem item = new ShoppingItem();
        if (parts.length < 6) {
            item.name = line;
            return item;
        }
        item.name = parts[0];
        try {
            item.order = Integer.parseInt(parts[1]);
            item.price = Double.parseDouble(parts[2]);
            item.qty = Double.parseDouble(parts[3]);
            item.byWeight = Boolean.parseBoolean(parts[4]);
            item.inCart = Boolean.parseBoolean(parts[5]);
        } catch (NumberFormatException ignored) {}
        return item;
    }
}
