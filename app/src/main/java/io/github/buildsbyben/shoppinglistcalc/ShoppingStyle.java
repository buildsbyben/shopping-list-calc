package io.github.buildsbyben.shoppinglistcalc;

import android.graphics.Color;

/** Central visual tokens for the programmatic shopping UI. */
final class ShoppingStyle {
    static final int BACKGROUND = Color.BLACK;
    static final int INPUT_BACKGROUND = Color.rgb(23, 23, 23);
    static final int CONTROL_BACKGROUND = Color.rgb(138, 138, 138);
    static final int CONTROL_ICON = Color.rgb(201, 201, 201);
    static final int CARD_BACKGROUND = Color.rgb(89, 89, 89);
    static final int COMPLETED_CARD_BACKGROUND = Color.rgb(39, 50, 43);
    static final int TEXT = Color.rgb(242, 245, 248);
    static final int MUTED_TEXT = Color.rgb(158, 169, 184);
    static final int DANGER = Color.rgb(248, 113, 113);
    static final int ACCENT = Color.rgb(125, 211, 252);
    static final int INPUT_HINT = Color.rgb(112, 124, 141);

    static final int CONTROL_HEIGHT_DP = 36;
    static final int COMPLETED_CONTROL_SIZE_DP = 32;
    static final int SUMMARY_ACTION_SIZE_DP = 44;
    static final int ITEM_CARD_HORIZONTAL_PADDING_DP = 12;
    static final int ITEM_CARD_VERTICAL_PADDING_DP = 8;
    static final int ITEM_CARD_GAP_DP = 10;
    static final int FIELD_GAP_DP = 8;
    static final int INPUT_HORIZONTAL_PADDING_DP = 8;

    private ShoppingStyle() {
    }
}
