package io.github.buildsbyben.shoppinglistcalc;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "shopping_calc";
    private final ArrayList<ShoppingItem> items = new ArrayList<>();
    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
    private ShoppingListStore store;
    private ScrollView scroll;
    private LinearLayout scrollContent;
    private LinearLayout list;
    private TextView subtotalView;
    private TextView taxView;
    private TextView totalView;
    private TextView remainingView;
    private Button addButton;
    private Button menuButton;
    private boolean rebuilding;
    private ShoppingItem focusAfterRebuild;
    private double taxRate;
    private double budget;
    private boolean formattingPrice;
    private boolean reordering;
    private ArrayList<ShoppingItem> reorderItems;

    private final int bg = ShoppingStyle.BACKGROUND;
    private final int inputBg = ShoppingStyle.INPUT_BACKGROUND;
    private final int panelSoft = ShoppingStyle.CONTROL_BACKGROUND;
    private final int panelIcon = ShoppingStyle.CONTROL_ICON;
    private final int cardBg = ShoppingStyle.CARD_BACKGROUND;
    private final int completedCardBg = ShoppingStyle.COMPLETED_CARD_BACKGROUND;
    private final int text = ShoppingStyle.TEXT;
    private final int muted = ShoppingStyle.MUTED_TEXT;
    private final int danger = ShoppingStyle.DANGER;
    private final int accent = ShoppingStyle.ACCENT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        store = new ShoppingListStore(getSharedPreferences(PREFS, MODE_PRIVATE));
        load();
        buildUi();
        rebuildList();
        recalc();
    }

    private void buildUi() {
        LinearLayout screen = column();
        screen.setBackgroundColor(bg);

        LinearLayout summary = column();
        final int summaryHorizontalPadding = dp(14);
        final int summaryTopPadding = dp(8);
        final int summaryBottomPadding = dp(8);
        final int contentHorizontalPadding = dp(16);
        final int contentTopPadding = dp(12);
        final int contentBottomPadding = dp(24);
        summary.setPadding(summaryHorizontalPadding, summaryTopPadding, summaryHorizontalPadding, summaryBottomPadding);
        summary.setBackgroundColor(altBackground());
        screen.addView(summary, matchWrap(new LinearLayout.LayoutParams(0, 0)));

        LinearLayout totalLine = row();
        totalLine.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(totalLine);

        LinearLayout totalBox = column();
        totalBox.addView(label("Total", 11, muted, false));
        totalView = label("$0.00", 26, accent, true);
        totalBox.addView(totalView);
        totalLine.addView(totalBox, weightWrap(1));

        Button add = button("+ Item", accent, contrastFor(accent));
        totalLine.addView(add, new LinearLayout.LayoutParams(dp(84), dp(ShoppingStyle.SUMMARY_ACTION_SIZE_DP)));
        addButton = add;

        Button menu = button("⋮", panelSoft, panelIcon);
        menu.setTextSize(22);
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                dp(ShoppingStyle.SUMMARY_ACTION_SIZE_DP),
                dp(ShoppingStyle.SUMMARY_ACTION_SIZE_DP)
        );
        menuParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP);
        totalLine.addView(menu, menuParams);
        menuButton = menu;

        LinearLayout summaryGrid = row();
        summaryGrid.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(summaryGrid, matchWrap(top(6)));
        subtotalView = metric(summaryGrid, "Subtotal");
        taxView = metric(summaryGrid, "Tax");
        remainingView = metric(summaryGrid, "Remaining");

        add.setOnClickListener(v -> {
            ShoppingItem item = new ShoppingItem();
            item.order = nextOrder();
            item.qty = 1;
            items.add(item);
            saveItems();
            focusAfterRebuild = item;
            rebuildList();
            recalc();
        });
        menu.setOnClickListener(v -> showActionsMenu(menu));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        scroll.setClipToPadding(false);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        scrollContent = column();
        scrollContent.setPadding(contentHorizontalPadding, contentTopPadding, contentHorizontalPadding, contentBottomPadding);
        scroll.addView(scrollContent);

        list = column();
        scrollContent.addView(list, matchWrap(new LinearLayout.LayoutParams(0, 0)));

        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            int statusTop = 0;
            int bottomInset = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                statusTop = insets.getInsets(WindowInsets.Type.statusBars()).top;
                int navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
                bottomInset = Math.max(navBottom, imeBottom);
            } else {
                statusTop = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            summary.setPadding(
                    summaryHorizontalPadding,
                    summaryTopPadding + statusTop,
                    summaryHorizontalPadding,
                    summaryBottomPadding
            );
            if (scrollContent != null) {
                scrollContent.setPadding(
                        contentHorizontalPadding,
                        contentTopPadding,
                        contentHorizontalPadding,
                        contentBottomPadding + bottomInset
                );
            }
            return insets;
        });

        setContentView(screen);
    }

    private void showActionsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Settings");
        menu.getMenu().add("Edit List");
        menu.getMenu().add("Reorder items");
        menu.getMenu().add("Clear list");
        menu.getMenu().add("Delete list");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Settings".equals(title)) {
                showSettingsDialog();
                return true;
            }
            if ("Edit List".equals(title)) {
                showListViewDialog();
                return true;
            }
            if ("Reorder items".equals(title)) {
                enterReorderMode();
                return true;
            }
            if ("Clear list".equals(title)) {
                clearList();
                return true;
            }
            if ("Delete list".equals(title)) {
                deleteList();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showSettingsDialog() {
        EditText taxInput = input("Tax %", true);
        taxInput.setText(trimNumber(taxRate));

        EditText budgetInput = input("Budget", true);
        budgetInput.setText(budget == 0 ? "" : trimNumber(budget));

        LinearLayout wrap = column();
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(field("Tax rate", taxInput), matchWrap(new LinearLayout.LayoutParams(0, 0)));
        wrap.addView(field("Budget", budgetInput), matchWrap(top(10)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                taxRate = parseDouble(taxInput, 0);
                budget = parseDouble(budgetInput, 0);
                saveSettings();
                dialog.dismiss();
                rebuildUi();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(muted);
        });

        dialog.show();
    }

    private void showListViewDialog() {
        EditText listInput = multilineListInput();
        listInput.setText(currentItemNameList());
        listInput.setSelection(listInput.getText().length());

        LinearLayout wrap = column();
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(listInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit List")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                applyListView(listInput.getText().toString());
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(muted);
            listInput.requestFocus();
            listInput.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(listInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 180);
        });

        dialog.show();
    }

    private void enterReorderMode() {
        sortItems();
        reorderItems = new ArrayList<>(items);
        reordering = true;
        addButton.setText("Save");
        addButton.setOnClickListener(v -> exitReorderMode(true));
        menuButton.setText("Cancel");
        menuButton.setTextSize(12);
        menuButton.setOnClickListener(v -> exitReorderMode(false));
        rebuildList();
    }

    private void exitReorderMode(boolean save) {
        if (save && reorderItems != null) {
            for (int i = 0; i < reorderItems.size(); i++) {
                reorderItems.get(i).order = (i + 1) * 10;
            }
            items.clear();
            items.addAll(reorderItems);
            saveItems();
        }
        reordering = false;
        reorderItems = null;
        addButton.setText("+ Item");
        addButton.setOnClickListener(v -> {
            ShoppingItem item = new ShoppingItem();
            item.order = nextOrder();
            item.qty = 1;
            items.add(item);
            saveItems();
            focusAfterRebuild = item;
            rebuildList();
            recalc();
        });
        menuButton.setText("⋮");
        menuButton.setTextSize(22);
        menuButton.setOnClickListener(v -> showActionsMenu(menuButton));
        rebuildList();
        recalc();
    }

    private void addReorderRow(LinearLayout rows, ArrayList<ShoppingItem> reorderItems, ShoppingItem item) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(6), dp(8));
        row.setBackgroundColor(cardBg);

        LinearLayout details = column();
        TextView name = label(item.name.isEmpty() ? "Unnamed item" : item.name, 16, text, true);
        details.addView(name);
        String quantity = item.byWeight ? trimNumber(item.qty) + " lb" : "Qty " + trimNumber(item.qty);
        details.addView(label(money.format(item.price) + " · " + quantity, 13, muted, false));
        row.addView(details, weightWrap(1));

        TextView handle = label("☰", 27, panelIcon, false);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("Drag to reorder " + item.name);
        row.addView(handle, new LinearLayout.LayoutParams(dp(48), dp(52)));
        rows.addView(row, matchWrap(bottom(ShoppingStyle.ITEM_CARD_GAP_DP)));

        handle.setOnTouchListener(new View.OnTouchListener() {
            private float lastRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    lastRawY = event.getRawY();
                    row.setAlpha(0.72f);
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(event.getRawY() - lastRawY) < dp(12)) {
                        return true;
                    }
                    int currentIndex = rows.indexOfChild(row);
                    int targetIndex = reorderTargetIndex(rows, event.getRawY());
                    if (targetIndex >= 1 && targetIndex != currentIndex) {
                        rows.removeView(row);
                        rows.addView(row, targetIndex > currentIndex ? targetIndex : targetIndex);
                        int itemIndex = currentIndex - 1;
                        int targetItemIndex = targetIndex - 1;
                        ShoppingItem moved = reorderItems.remove(itemIndex);
                        reorderItems.add(targetItemIndex, moved);
                    }
                    lastRawY = event.getRawY();
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    row.setAlpha(1f);
                    return true;
                }
                return true;
            }
        });
    }

    private int reorderTargetIndex(LinearLayout rows, float rawY) {
        for (int i = 1; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            int[] location = new int[2];
            child.getLocationOnScreen(location);
            if (rawY < location[1] + child.getHeight() / 2f) {
                return i;
            }
        }
        return rows.getChildCount() - 1;
    }

    private void rebuildList() {
        rebuilding = true;
        if (reordering) {
            rebuildReorderList();
            rebuilding = false;
            return;
        }
        sortItems();
        list.removeAllViews();
        ArrayList<ItemInput> itemInputs = new ArrayList<>();

        if (items.isEmpty()) {
            TextView empty = label("No items yet.", 16, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
            rebuilding = false;
            return;
        }

        for (ShoppingItem item : items) {
            if (item.inCart) {
                addCompletedItemCard(item);
                continue;
            }

            LinearLayout card = column();
            card.setPadding(
                    dp(ShoppingStyle.ITEM_CARD_HORIZONTAL_PADDING_DP),
                    dp(ShoppingStyle.ITEM_CARD_VERTICAL_PADDING_DP),
                    dp(ShoppingStyle.ITEM_CARD_HORIZONTAL_PADDING_DP),
                    dp(ShoppingStyle.ITEM_CARD_VERTICAL_PADDING_DP)
            );
            card.setBackgroundColor(altBackground());
            list.addView(card, matchWrap(bottom(ShoppingStyle.ITEM_CARD_GAP_DP)));

            LinearLayout topLine = row();
            topLine.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(topLine);

            Button cart = button("☐", cardBg, accent);
            cart.setTextSize(22);
            topLine.addView(cart, new LinearLayout.LayoutParams(
                    dp(ShoppingStyle.CONTROL_HEIGHT_DP),
                    dp(ShoppingStyle.CONTROL_HEIGHT_DP)
            ));

            EditText name = input("Item", false);
            name.setText(item.name);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, dp(ShoppingStyle.CONTROL_HEIGHT_DP), 1);
            nameParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP);
            topLine.addView(name, nameParams);
            itemInputs.add(new ItemInput(item, name, 0));

            Button delete = button("×", cardBg, accent);
            delete.setTextSize(22);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    dp(ShoppingStyle.CONTROL_HEIGHT_DP),
                    dp(ShoppingStyle.CONTROL_HEIGHT_DP)
            );
            deleteParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP);
            topLine.addView(delete, deleteParams);

            LinearLayout fields = row();
            card.addView(fields, matchWrap(top(ShoppingStyle.FIELD_GAP_DP)));

            EditText price = input(item.byWeight ? "Price/lb" : "Price", true);
            price.setText(money.format(item.price));
            fields.addView(inputBox(price), weightWrap(1));
            itemInputs.add(new ItemInput(item, price, 1));

            final EditText weight;
            if (item.byWeight) {
                weight = input("Weight", true);
                weight.setText(item.qty == 0 ? "" : trimNumber(item.qty));
                fields.addView(fieldBox("Lb", weight), weightWrap(1, left(ShoppingStyle.FIELD_GAP_DP)));
                itemInputs.add(new ItemInput(item, weight, 2));
            } else {
                if (item.qty < 1) {
                    item.qty = 1;
                }
                weight = null;
                fields.addView(quantityControl(item), weightWrap(1, left(8)));
            }

            Button mode = button(item.byWeight ? "Lbs" : "Qty", panelSoft, panelIcon);
            mode.setTextSize(12);
            LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(dp(52), dp(ShoppingStyle.CONTROL_HEIGHT_DP));
            modeParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP);
            fields.addView(mode, modeParams);

            price.addTextChangedListener(new SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    formatMoneyAsCents(price);
                }
            });

            SimpleWatcher watcher = new SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (rebuilding) {
                        return;
                    }
                    item.name = name.getText().toString();
                    item.price = parseMoney(price, 0);
                    if (item.byWeight && weight != null) {
                        item.qty = Math.max(0, parseDouble(weight, 0));
                    }
                    saveItems();
                    recalc();
                }
            };
            name.addTextChangedListener(watcher);
            price.addTextChangedListener(watcher);
            if (weight != null) {
                weight.addTextChangedListener(watcher);
            }

            name.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    scrollInputIntoView(name);
                }
            });
            price.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    scrollInputIntoView(price);
                }
            });
            if (weight != null) {
                weight.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        scrollInputIntoView(weight);
                    }
                });
            }
            mode.setOnClickListener(v -> {
                item.byWeight = !item.byWeight;
                if (!item.byWeight && item.qty == 0) {
                    item.qty = 1;
                }
                saveItems();
                rebuildList();
                recalc();
            });
            delete.setOnClickListener(v -> {
                items.remove(item);
                saveItems();
                rebuildList();
                recalc();
            });
            cart.setOnClickListener(v -> {
                item.name = name.getText().toString().trim();
                item.price = parseMoney(price, 0);
                if (item.byWeight && weight != null) {
                    item.qty = Math.max(0, parseDouble(weight, 0));
                }
                if (!item.isReadyForCart()) {
                    if (item.name.isEmpty()) {
                        name.setError("Add an item name");
                    } else if (item.price <= 0) {
                        price.setError("Add a price");
                    } else if (item.byWeight && weight != null) {
                        weight.setError("Add a weight");
                    }
                    return;
                }
                item.inCart = true;
                saveItems();
                rebuildList();
                recalc();
            });

            if (item == focusAfterRebuild) {
                focusAfterRebuild = null;
                name.postDelayed(() -> focusAndShowKeyboard(name), 120);
            }
        }

        wireItemFieldNavigation(itemInputs);

        rebuilding = false;
    }

    private void rebuildReorderList() {
        list.removeAllViews();
        list.addView(label("Drag the handle to move an item.", 14, muted, false), matchWrap(bottom(8)));
        if (reorderItems == null || reorderItems.isEmpty()) {
            TextView empty = label("No items yet.", 16, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
            return;
        }
        for (ShoppingItem item : reorderItems) {
            addReorderRow(list, reorderItems, item);
        }
    }

    private void addCompletedItemCard(ShoppingItem item) {
        LinearLayout card = column();
        card.setPadding(dp(10), dp(7), dp(10), dp(7));
        card.setBackgroundColor(completedCardBg);
        list.addView(card, matchWrap(bottom(8)));

        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(line);

        Button cart = button("☑", completedCardBg, accent);
        cart.setTextSize(21);
        line.addView(cart, new LinearLayout.LayoutParams(
                dp(ShoppingStyle.COMPLETED_CONTROL_SIZE_DP),
                dp(ShoppingStyle.COMPLETED_CONTROL_SIZE_DP)
        ));

        TextView summary = label(item.name, 16, text, true);
        line.addView(summary, weightWrap(1));

        TextView details = label(completedItemDetails(item), 13, muted, false);
        card.addView(details, matchWrap(top(1)));

        cart.setOnClickListener(v -> {
            item.inCart = false;
            saveItems();
            focusAfterRebuild = item;
            rebuildList();
            recalc();
        });
    }

    private String completedItemDetails(ShoppingItem item) {
        if (item.byWeight) {
            return trimNumber(item.qty) + " lb × " + money.format(item.price) + "/lb = "
                    + money.format(item.price * item.qty);
        }
        return trimNumber(item.qty) + " × " + money.format(item.price) + " = "
                + money.format(item.price * item.qty);
    }

    private void wireItemFieldNavigation(ArrayList<ItemInput> itemInputs) {
        for (int i = 0; i < itemInputs.size(); i++) {
            ItemInput current = itemInputs.get(i);
            current.input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            final int index = i;
            current.input.setOnEditorActionListener((v, actionId, event) -> {
                if (!isEnterAction(actionId, event)) {
                    return false;
                }
                if (current.field == 1) {
                    formatPriceInput(current.input);
                }
                if (current.field == 2 && index == itemInputs.size() - 1) {
                    ShoppingItem item = addItemAfter(current.item);
                    focusAfterRebuild = item;
                    rebuildList();
                    recalc();
                    return true;
                }
                if (index < itemInputs.size() - 1) {
                    focusAndShowKeyboard(itemInputs.get(index + 1).input);
                    return true;
                }
                return true;
            });
        }
    }

    private ShoppingItem addItemAfter(ShoppingItem after) {
        sortItems();
        ShoppingItem item = new ShoppingItem();
        item.qty = 1;
        int index = items.indexOf(after);
        if (index < 0 || index >= items.size() - 1) {
            items.add(item);
        } else {
            items.add(index + 1, item);
        }
        for (int i = 0; i < items.size(); i++) {
            items.get(i).order = (i + 1) * 10;
        }
        saveItems();
        return item;
    }

    private boolean isEnterAction(int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
            return true;
        }
        return event != null
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && event.getAction() == KeyEvent.ACTION_UP;
    }

    private void recalc() {
        CartTotals totals = CartTotals.calculate(items, taxRate);
        subtotalView.setText(money.format(totals.subtotal));
        taxView.setText(money.format(totals.tax));
        totalView.setText(money.format(totals.total));
        remainingView.setText(budget > 0 ? money.format(budget - totals.total) : "--");
        remainingView.setTextColor(budget > 0 && budget - totals.total < 0 ? danger : text);
    }

    private TextView metric(LinearLayout parent, String label) {
        LinearLayout box = column();
        TextView value = label("$0.00", 16, text, true);
        box.addView(label(label, 12, muted, false));
        box.addView(value);
        parent.addView(box, weightWrap(1));
        return value;
    }

    private void clearList() {
        for (ShoppingItem item : items) {
            item.price = 0;
            item.qty = 1;
            item.inCart = false;
        }
        saveItems();
        rebuildList();
        recalc();
    }

    private void deleteList() {
        items.clear();
        saveItems();
        rebuildList();
        recalc();
    }

    private void rebuildUi() {
        buildUi();
        rebuildList();
        recalc();
    }

    private LinearLayout field(String label, EditText input) {
        LinearLayout wrap = column();
        wrap.addView(label(label, 12, muted, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(ShoppingStyle.CONTROL_HEIGHT_DP)
        );
        params.topMargin = dp(4);
        wrap.addView(input, params);
        return wrap;
    }

    private LinearLayout inputBox(EditText input) {
        LinearLayout wrap = column();
        wrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(ShoppingStyle.CONTROL_HEIGHT_DP)
        ));
        return wrap;
    }

    private LinearLayout fieldBox(String prefixText, EditText input) {
        LinearLayout wrap = row();
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(ShoppingStyle.INPUT_HORIZONTAL_PADDING_DP), 0, dp(ShoppingStyle.INPUT_HORIZONTAL_PADDING_DP), 0);
        wrap.setMinimumHeight(dp(ShoppingStyle.CONTROL_HEIGHT_DP));
        wrap.setBackgroundColor(inputBg);

        TextView prefix = label(prefixText + ":", 15, text, false);
        wrap.addView(prefix);

        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(6), 0, 0, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                0,
                dp(ShoppingStyle.CONTROL_HEIGHT_DP),
                1
        ));
        return wrap;
    }

    private LinearLayout quantityControl(ShoppingItem item) {
        LinearLayout wrap = row();
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(3), 0, dp(3), 0);
        wrap.setMinimumHeight(dp(ShoppingStyle.CONTROL_HEIGHT_DP));
        wrap.setBackgroundColor(inputBg);

        Button minus = button("−", panelSoft, panelIcon);
        minus.setTextSize(20);
        wrap.addView(minus, new LinearLayout.LayoutParams(dp(30), dp(ShoppingStyle.CONTROL_HEIGHT_DP)));

        TextView value = label("Qty\n" + trimNumber(item.qty), 13, text, true);
        value.setGravity(Gravity.CENTER);
        value.setLines(2);
        wrap.addView(value, new LinearLayout.LayoutParams(0, dp(ShoppingStyle.CONTROL_HEIGHT_DP), 1));

        Button plus = button("+", panelSoft, panelIcon);
        plus.setTextSize(20);
        wrap.addView(plus, new LinearLayout.LayoutParams(dp(30), dp(ShoppingStyle.CONTROL_HEIGHT_DP)));

        minus.setEnabled(item.qty > 1);
        minus.setTextColor(minus.isEnabled() ? panelIcon : muted);
        minus.setOnClickListener(v -> {
            if (item.qty > 1) {
                item.qty--;
                value.setText("Qty\n" + trimNumber(item.qty));
                minus.setEnabled(item.qty > 1);
                minus.setTextColor(minus.isEnabled() ? panelIcon : muted);
                saveItems();
                recalc();
            }
        });
        plus.setOnClickListener(v -> {
            item.qty++;
            value.setText("Qty\n" + trimNumber(item.qty));
            minus.setEnabled(true);
            minus.setTextColor(panelIcon);
            saveItems();
            recalc();
        });
        return wrap;
    }

    private EditText input(String hint, boolean number) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(ShoppingStyle.INPUT_HINT);
        input.setTextColor(text);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setIncludeFontPadding(false);
        input.setPadding(dp(ShoppingStyle.INPUT_HORIZONTAL_PADDING_DP), 0, dp(ShoppingStyle.INPUT_HORIZONTAL_PADDING_DP), 0);
        input.setMinHeight(0);
        input.setMinimumHeight(0);
        input.setBackgroundColor(inputBg);
        if (number) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        return input;
    }

    private Button button(String textValue, int background, int foreground) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackgroundColor(background);
        return button;
    }

    private void focusAndShowKeyboard(EditText input) {
        input.requestFocus();
        input.setSelection(input.getText().length());
        scrollInputIntoView(input);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void scrollInputIntoView(EditText input) {
        if (scroll != null) {
            scroll.postDelayed(() -> {
                Rect rect = new Rect();
                input.getDrawingRect(rect);
                scroll.offsetDescendantRectToMyCoords(input, rect);
                scroll.smoothScrollTo(0, Math.max(0, rect.top - dp(12)));
            }, 260);
        }
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(color);
        label.setTextSize(size);
        label.setIncludeFontPadding(true);
        if (bold) {
            label.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return label;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private LinearLayout.LayoutParams matchWrap(LinearLayout.LayoutParams margins) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin);
        return params;
    }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        return weightWrap(weight, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout.LayoutParams weightWrap(float weight, LinearLayout.LayoutParams margins) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin);
        return params;
    }

    private LinearLayout.LayoutParams top(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0);
        params.topMargin = dp(value);
        return params;
    }

    private LinearLayout.LayoutParams bottom(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0);
        params.bottomMargin = dp(value);
        return params;
    }

    private LinearLayout.LayoutParams left(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0);
        params.leftMargin = dp(value);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int altBackground() {
        return cardBg;
    }

    private int contrastFor(int color) {
        double brightness = (Color.red(color) * 0.299) + (Color.green(color) * 0.587) + (Color.blue(color) * 0.114);
        return brightness > 150 ? Color.rgb(7, 20, 28) : text;
    }

    private void sortItems() {
        Collections.sort(items, (first, second) -> {
            if (first.inCart != second.inCart) {
                return first.inCart ? 1 : -1;
            }
            return Integer.compare(first.order, second.order);
        });
    }

    private void applyListView(String rawList) {
        sortItems();
        ArrayList<ShoppingItem> originalItems = new ArrayList<>(items);
        ArrayList<ShoppingItem> updatedItems = new ArrayList<>();
        boolean[] used = new boolean[originalItems.size()];
        ArrayList<String> names = cleanListNames(rawList);

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            ShoppingItem item = findUnusedItemByName(originalItems, used, name);
            if (item == null && i < originalItems.size() && !used[i]) {
                item = originalItems.get(i);
                used[i] = true;
            }
            if (item == null) {
                item = new ShoppingItem();
                item.qty = 1;
            }
            item.name = name;
            item.order = (updatedItems.size() + 1) * 10;
            updatedItems.add(item);
        }

        items.clear();
        items.addAll(updatedItems);
        saveItems();
        rebuildList();
        recalc();
    }

    private ShoppingItem findUnusedItemByName(ArrayList<ShoppingItem> originalItems, boolean[] used, String name) {
        for (int i = 0; i < originalItems.size(); i++) {
            ShoppingItem item = originalItems.get(i);
            if (!used[i] && item.name.equals(name)) {
                used[i] = true;
                return item;
            }
        }
        return null;
    }

    private String currentItemNameList() {
        sortItems();
        StringBuilder builder = new StringBuilder();
        for (ShoppingItem item : items) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(item.name);
        }
        return builder.toString();
    }

    private ArrayList<String> cleanListNames(String rawList) {
        ArrayList<String> names = new ArrayList<>();
        String[] lines = rawList.replace('\r', '\n').split("\n");
        for (String line : lines) {
            String name = cleanListItemName(line);
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private String cleanListItemName(String rawName) {
        String name = rawName.trim();
        name = name.replaceAll("^[\\u2022\\u2023\\u25E6\\u2043\\u2219*+-]\\s+", "");
        name = name.replaceAll("^\\d+[.)]\\s+", "");
        name = name.replaceAll("^\\[[ xX]\\]\\s+", "");
        return name.trim().replaceAll("\\s+", " ");
    }

    private int nextOrder() {
        int max = 0;
        for (ShoppingItem item : items) {
            max = Math.max(max, item.order);
        }
        return max + 10;
    }

    private EditText multilineListInput() {
        EditText input = new EditText(this);
        input.setHint("One item per line");
        input.setHintTextColor(ShoppingStyle.INPUT_HINT);
        input.setTextColor(text);
        input.setTextSize(16);
        input.setMinLines(8);
        input.setMaxLines(14);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(inputBg);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return input;
    }

    private void saveSettings() {
        store.saveSettings(taxRate, budget);
    }

    private void saveItems() {
        store.saveItems(items);
    }

    private void load() {
        taxRate = store.taxRate();
        budget = store.budget();
        store.loadItems(items);
    }

    private double parseDouble(EditText input, double fallback) {
        try {
            String raw = input.getText().toString().trim();
            if (raw.isEmpty()) {
                return fallback;
            }
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double parseMoney(EditText input, double fallback) {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        String cleaned = raw.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) {
            return fallback;
        }
        try {
            if (!cleaned.contains(".")) {
                return Long.parseLong(cleaned) / 100.0;
            }
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void formatPriceInput(EditText input) {
        double value = parseMoney(input, 0);
        String formatted = money.format(value);
        if (!formatted.equals(input.getText().toString())) {
            input.setText(formatted);
            input.setSelection(input.getText().length());
        }
    }

    private void formatMoneyAsCents(EditText input) {
        if (formattingPrice) {
            return;
        }
        String digits = input.getText().toString().replaceAll("\\D", "");
        long cents = 0;
        try {
            if (!digits.isEmpty()) {
                cents = Long.parseLong(digits);
            }
        } catch (NumberFormatException ignored) {
            // Keep the input usable if an unusually long number is pasted.
        }
        String formatted = money.format(cents / 100.0);
        if (!formatted.equals(input.getText().toString())) {
            formattingPrice = true;
            input.setText(formatted);
            input.setSelection(formatted.length());
            formattingPrice = false;
        }
    }

    private String trimNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static class ItemInput {
        final ShoppingItem item;
        final EditText input;
        final int field;

        ItemInput(ShoppingItem item, EditText input, int field) {
            this.item = item;
            this.input = input;
            this.field = field;
        }
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
