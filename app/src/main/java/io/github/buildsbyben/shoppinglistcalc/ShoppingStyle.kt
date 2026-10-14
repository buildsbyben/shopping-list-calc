package io.github.buildsbyben.shoppinglistcalc

import android.graphics.Color

/** Central visual tokens for the programmatic shopping UI. */
internal object ShoppingStyle {
    @JvmField val BACKGROUND: Int = Color.BLACK
    @JvmField val INPUT_BACKGROUND: Int = Color.rgb(23, 23, 23)
    @JvmField val CONTROL_BACKGROUND: Int = Color.rgb(138, 138, 138)
    @JvmField val CONTROL_ICON: Int = Color.rgb(201, 201, 201)
    @JvmField val CARD_BACKGROUND: Int = Color.rgb(89, 89, 89)
    @JvmField val COMPLETED_CARD_BACKGROUND: Int = Color.rgb(39, 50, 43)
    @JvmField val TEXT: Int = Color.rgb(242, 245, 248)
    @JvmField val MUTED_TEXT: Int = Color.rgb(158, 169, 184)
    @JvmField val DANGER: Int = Color.rgb(248, 113, 113)
    @JvmField val ACCENT: Int = Color.rgb(125, 211, 252)
    @JvmField val INPUT_HINT: Int = Color.rgb(112, 124, 141)

    const val CONTROL_HEIGHT_DP: Int = 36
    const val COMPLETED_CONTROL_SIZE_DP: Int = 32
    const val SUMMARY_ACTION_SIZE_DP: Int = 44
    const val ITEM_CARD_HORIZONTAL_PADDING_DP: Int = 12
    const val ITEM_CARD_VERTICAL_PADDING_DP: Int = 8
    const val ITEM_CARD_GAP_DP: Int = 10
    const val FIELD_GAP_DP: Int = 8
    const val INPUT_HORIZONTAL_PADDING_DP: Int = 8
}
