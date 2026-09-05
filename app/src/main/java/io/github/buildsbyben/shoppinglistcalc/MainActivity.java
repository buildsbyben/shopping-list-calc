package io.github.buildsbyben.shoppinglistcalc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.DragEvent;
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

import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends Activity {
    private static final String PREFS = "shopping_calc";
    private final ArrayList<ShoppingItem> items = new ArrayList<>();
    private CurrencyFormat money;
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
    private boolean quickCentsEntry;
    private boolean quickEntry;
    private boolean reordering;
    private ArrayList<ShoppingItem> reorderItems;
    private String weightUnit;
    private int reorderAutoScrollDirection;
    private final Runnable reorderAutoScroller = new Runnable() {
        @Override public void run() {
            if (scroll == null || reorderAutoScrollDirection == 0) {
                return;
            }
            int before = scroll.getScrollY();
            scroll.scrollBy(0, reorderAutoScrollDirection * dp(12));
            if (scroll.getScrollY() != before) {
                scroll.postDelayed(this, 16);
            }
        }
    };

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

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            load();
            if (list != null) {
                rebuildList();
                recalc();
            }
        }
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
                startActivity(new Intent(this, SettingsActivity.class));
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
        String quantity = item.byWeight ? trimNumber(item.qty) + " " + weightUnit : "Qty " + trimNumber(item.qty);
        details.addView(label(money.format(item.price) + " · " + quantity, 13, muted, false));
        row.addView(details, weightWrap(1));

        TextView handle = label("☰", 27, panelIcon, false);
        handle.setGravity(Gravity.CENTER);
        row.setContentDescription("Press and hold to reorder " + item.name);
        handle.setContentDescription("Press and hold anywhere on this item to reorder " + item.name);
        row.addView(handle, new LinearLayout.LayoutParams(dp(48), dp(52)));
        rows.addView(row, matchWrap(bottom(ShoppingStyle.ITEM_CARD_GAP_DP)));

        View.OnLongClickListener startDrag = v -> startReorderDrag(rows, row, item);
        // The entire item is the drag target. Keep the same listener on the
        // handle too: a child view can receive the long-press before its row.
        row.setOnLongClickListener(startDrag);
        handle.setOnLongClickListener(startDrag);
        // A drag event is delivered to the view directly under the finger first.
        // Handle it here as well as on the list so crossing a row always updates
        // the insertion marker instead of leaving the drag stranded on that row.
        row.setOnDragListener((v, event) -> handleReorderDragEvent(
                rows, (View) v, event, ((View) v).getTop()));
    }

    private boolean startReorderDrag(LinearLayout rows, View row, ShoppingItem item) {
        ReorderDrag drag = new ReorderDrag(row, item);
        drag.placeholder = reorderPlaceholder();
        ClipData data = ClipData.newPlainText("shopping-item", item.name);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(row);
        boolean started;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            started = row.startDragAndDrop(data, shadow, drag, 0);
        } else {
            started = row.startDrag(data, shadow, drag, 0);
        }
        // startDragAndDrop requires an attached source view.  The old code
        // removed it before starting, which could leave only empty markers.
        if (started) {
            int index = rows.indexOfChild(row);
            rows.removeView(row);
            rows.addView(drag.placeholder, index);
        }
        return started;
    }

    private void updateReorderAutoScroll(View target, DragEvent event) {
        if (scroll == null) {
            return;
        }
        int[] targetLocation = new int[2];
        int[] scrollLocation = new int[2];
        target.getLocationOnScreen(targetLocation);
        scroll.getLocationOnScreen(scrollLocation);
        float yInViewport = targetLocation[1] + event.getY() - scrollLocation[1];
        int edge = dp(56);
        int direction = yInViewport < edge ? -1
                : yInViewport > scroll.getHeight() - edge ? 1 : 0;
        if (direction == reorderAutoScrollDirection) {
            return;
        }
        reorderAutoScrollDirection = direction;
        scroll.removeCallbacks(reorderAutoScroller);
        if (direction != 0) {
            scroll.post(reorderAutoScroller);
        }
    }

    private void stopReorderAutoScroll() {
        reorderAutoScrollDirection = 0;
        if (scroll != null) {
            scroll.removeCallbacks(reorderAutoScroller);
        }
    }

    private View reorderPlaceholder() {
        View placeholder = new View(this);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke(dp(1), accent);
        placeholder.setBackground(outline);
        placeholder.setOnDragListener((v, event) -> handleReorderDragEvent(
                list, (View) v, event, ((View) v).getTop()));
        LinearLayout.LayoutParams params = matchWrap(bottom(ShoppingStyle.ITEM_CARD_GAP_DP));
        params.height = dp(68);
        placeholder.setLayoutParams(params);
        return placeholder;
    }

    private void wireReorderDragTarget(LinearLayout rows, ArrayList<ShoppingItem> reorderItems) {
        rows.setOnDragListener((v, event) -> handleReorderDragEvent(rows, rows, event, 0));
    }

    private boolean handleReorderDragEvent(LinearLayout rows, View target, DragEvent event, int targetTop) {
        if (!(event.getLocalState() instanceof ReorderDrag)) {
            return false;
        }
        ReorderDrag drag = (ReorderDrag) event.getLocalState();
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            return true;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
            updateReorderAutoScroll(target, event);
            movePlaceholder(rows, drag.placeholder, targetTop + event.getY());
            return true;
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            stopReorderAutoScroll();
            if (drag.finished) {
                return true;
            }
            int destination = rows.indexOfChild(drag.placeholder) - 1;
            rows.removeView(drag.placeholder);
            rows.addView(drag.row, destination + 1);
            reorderItems.remove(drag.item);
            reorderItems.add(destination, drag.item);
            drag.dropped = true;
            drag.finished = true;
            return true;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            stopReorderAutoScroll();
            if (!drag.finished && rows.indexOfChild(drag.placeholder) >= 0) {
                int destination = rows.indexOfChild(drag.placeholder);
                rows.removeView(drag.placeholder);
                rows.addView(drag.row, destination);
                drag.finished = true;
            }
            return true;
        }
        return true;
    }

    private void movePlaceholder(LinearLayout rows, View placeholder, float y) {
        rows.removeView(placeholder);
        rows.addView(placeholder, reorderTargetIndex(rows, y));
    }

    private int reorderTargetIndex(LinearLayout rows, float y) {
        for (int i = 1; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            if (y < child.getTop() + child.getHeight() / 2f) {
                return i;
            }
        }
        return rows.getChildCount();
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

            boolean blankPrice = item.price == 0 && item.name.trim().isEmpty() && !item.inCart;
            EditText price = input(blankPrice ? "" : (item.byWeight ? "Price/" + weightUnit : "Price"), true);
            if (blankPrice) {
                price.setText(priceStartText());
            } else {
                price.setText(money.format(item.price));
            }
            fields.addView(inputBox(price), weightWrap(1));
            itemInputs.add(new ItemInput(item, price, 1));

            final EditText weight;
            if (item.byWeight) {
                weight = input("Weight", true);
                weight.setText(item.qty == 0 ? "" : trimNumber(item.qty));
                fields.addView(fieldBox(weightUnit, weight), weightWrap(1, left(ShoppingStyle.FIELD_GAP_DP)));
                itemInputs.add(new ItemInput(item, weight, 2));
            } else {
                if (item.qty < 1) {
                    item.qty = 1;
                }
                weight = null;
                fields.addView(quantityControl(item), weightWrap(1, left(8)));
            }

            Button mode = button(item.byWeight ? weightUnit : "Qty", panelSoft, panelIcon);
            mode.setTextSize(12);
            LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(dp(52), dp(ShoppingStyle.CONTROL_HEIGHT_DP));
            modeParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP);
            fields.addView(mode, modeParams);

            price.addTextChangedListener(new SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (quickCentsEntry) {
                        formatMoneyAsCents(price);
                    }
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
                } else if (!quickCentsEntry) {
                    formatPriceInput(price);
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
                if (item.byWeight) {
                    // A measured amount is intentionally unknown until entered.
                    item.qty = 0;
                } else if (item.qty == 0) {
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
                if (!item.isReadyForCart(quickEntry)) {
                    if (item.name.isEmpty() && !quickEntry) {
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
                EditText focus = quickEntry ? price : name;
                focus.postDelayed(() -> focusAndShowKeyboard(focus), 120);
            }
        }

        wireItemFieldNavigation(itemInputs);

        rebuilding = false;
    }

    private void rebuildReorderList() {
        list.removeAllViews();
        list.addView(label("Press and hold an item, then drag it to a new position.", 14, muted, false), matchWrap(bottom(8)));
        if (reorderItems == null || reorderItems.isEmpty()) {
            TextView empty = label("No items yet.", 16, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
            return;
        }
        wireReorderDragTarget(list, reorderItems);
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
            return trimNumber(item.qty) + " " + weightUnit + " × " + money.format(item.price) + "/" + weightUnit + " = "
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
                    // Finishing the price on the final item always starts the next item.
                    // The saved new-item focus preference decides whether that item opens
                    // at its name or price field; quick cents only changes price formatting.
                    if (isLastIncompleteItem(current.item)) {
                        ShoppingItem item = addItemAfter(current.item);
                        focusAfterRebuild = item;
                        rebuildList();
                        recalc();
                        return true;
                    }
                }
                if (current.field == 2 && isLastIncompleteItem(current.item)) {
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

    private boolean isLastIncompleteItem(ShoppingItem candidate) {
        ShoppingItem last = null;
        for (ShoppingItem item : items) {
            if (!item.inCart) {
                last = item;
            }
        }
        return candidate == last;
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
                View itemCard = itemCardFor(input);
                itemCard.getDrawingRect(rect);
                scroll.offsetDescendantRectToMyCoords(itemCard, rect);
                int padding = dp(12);
                int viewportHeight = scroll.getHeight();
                int visibleHeight = viewportHeight - padding * 2;
                int targetTop = rect.height() <= visibleHeight
                        ? rect.top - padding
                        : rect.top;
                scroll.smoothScrollTo(0, Math.max(0, targetTop));
            }, 260);
        }
    }

    private View itemCardFor(View input) {
        View current = input;
        while (current.getParent() instanceof View && current.getParent() != list) {
            current = (View) current.getParent();
        }
        return current;
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
        money = store.currencyFormat();
        quickCentsEntry = store.quickCentsEntry();
        quickEntry = store.quickEntry();
        weightUnit = store.weightUnit();
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
        String raw = input.getText().toString();
        return quickCentsEntry ? money.parseQuick(raw, fallback) : money.parseDirect(raw, fallback);
    }

    private void formatPriceInput(EditText input) {
        if (priceHasNoAmount(input)) {
            if (!priceStartText().equals(input.getText().toString())) {
                input.setText(priceStartText());
            }
            return;
        }
        double value = parseMoney(input, 0);
        String formatted = money.format(value);
        if (!formatted.equals(input.getText().toString())) {
            input.setText(formatted);
            input.setSelection(input.getText().length());
        }
    }

    private String priceStartText() {
        return money.symbol;
    }

    private boolean priceHasNoAmount(EditText input) {
        String raw = input.getText().toString().replace(money.symbol, "").trim();
        return raw.replaceAll("\\D", "").isEmpty();
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
        String formatted = digits.isEmpty() ? priceStartText() : money.format(cents / 100.0);
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

    private static class ReorderDrag {
        final View row;
        final ShoppingItem item;
        View placeholder;
        boolean dropped;
        boolean finished;

        ReorderDrag(View row, ShoppingItem item) {
            this.row = row;
            this.item = item;
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
