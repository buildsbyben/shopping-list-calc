package io.github.buildsbyben.shoppinglistcalc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final String PREFS = "shopping_calc";
    private final int bg = ShoppingStyle.BACKGROUND;
    private final int card = ShoppingStyle.CONTROL_BACKGROUND;
    private final int text = ShoppingStyle.TEXT;
    private final int muted = ShoppingStyle.MUTED_TEXT;
    private final int accent = ShoppingStyle.ACCENT;
    private ShoppingListStore store;
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        store = new ShoppingListStore(getSharedPreferences(PREFS, MODE_PRIVATE));
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        rows = column();
        rows.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(rows);

        TextView title = label("Settings", 28, text, true);
        rows.addView(title);
        section("Money");
        CurrencyFormat format = store.currencyFormat();
        setting("Currency format", format.displayName(), v -> showCurrencyDialog());
        setting("Price entry", store.quickCentsEntry()
                ? "Quick cents entry" : "Direct amount entry", v -> showPriceEntryDialog());

        section("Shopping flow");
        setting("Entry mode", store.quickEntry()
                ? "Quick entry — price first, adds the next item" : "Standard — item name first", v -> showQuickEntryDialog());

        section("Budget and tax");
        setting("Tax rate and budget", "Set your tax percentage and spending target", v -> showBudgetDialog());

        section("About");
        setting("About Shopping List Calculator", "Version " + appVersion() + " · GitHub · F-Droid", v -> showAboutDialog());
        setContentView(scroll);
    }

    private void showCurrencyDialog() {
        String[] choices = {
                "US Dollar — $1,234.56",
                "Euro — €1.234,56",
                "British Pound — £1,234.56",
                "Japanese Yen — ¥1,235",
                "Custom format"
        };
        new AlertDialog.Builder(this)
                .setTitle("Currency format")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) saveFormat(new CurrencyFormat("$", false, '.', ',', 2));
                    if (which == 1) saveFormat(new CurrencyFormat("€", false, ',', '.', 2));
                    if (which == 2) saveFormat(new CurrencyFormat("£", false, '.', ',', 2));
                    if (which == 3) saveFormat(new CurrencyFormat("¥", false, '.', ',', 0));
                    if (which == 4) showCustomFormatDialog();
                })
                .show();
    }

    private void showCustomFormatDialog() {
        CurrencyFormat current = store.currencyFormat();
        LinearLayout wrap = column();
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        EditText symbol = input("Currency symbol", current.symbol, false);
        EditText decimal = input("Decimal separator (. or ,)", String.valueOf(current.decimalSeparator), false);
        EditText grouping = input("Thousands separator (, . space, or blank)", current.groupingSeparator == '\0' ? "" : String.valueOf(current.groupingSeparator), false);
        EditText digits = input("Decimal places (0–3)", String.valueOf(current.fractionDigits), true);
        wrap.addView(symbol);
        wrap.addView(decimal, top(8));
        wrap.addView(grouping, top(8));
        wrap.addView(digits, top(8));
        new AlertDialog.Builder(this)
                .setTitle("Custom currency format")
                .setView(wrap)
                .setSingleChoiceItems(new String[]{"Symbol before amount", "Symbol after amount"}, current.symbolAfter ? 1 : 0, null)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    char decimalChar = singleSeparator(decimal.getText().toString(), '.');
                    char groupingChar = grouping.getText().toString().trim().isEmpty() ? '\0'
                            : singleSeparator(grouping.getText().toString(), ',');
                    int fractionDigits = parseInt(digits.getText().toString(), 2);
                    if (decimalChar == groupingChar && groupingChar != '\0') {
                        new AlertDialog.Builder(this).setMessage("Decimal and thousands separators must be different.")
                                .setPositiveButton("OK", null).show();
                        return;
                    }
                    int checked = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    saveFormat(new CurrencyFormat(symbol.getText().toString().trim(), checked == 1,
                            decimalChar, groupingChar, fractionDigits));
                })
                .show();
    }

    private void showPriceEntryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Price entry")
                .setSingleChoiceItems(new String[]{
                        "Direct amount entry — type 12.50 or 12,50",
                        "Quick cents entry — digits shift into cents as you type"
                }, store.quickCentsEntry() ? 1 : 0, (dialog, which) -> {
                    store.saveQuickCentsEntry(which == 1);
                    dialog.dismiss();
                    render();
                })
                .show();
    }

    private void showQuickEntryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Entry mode")
                .setSingleChoiceItems(new String[]{
                        "Standard — new items start at their name",
                        "Quick entry — new items start at price; Next adds another item"
                }, store.quickEntry() ? 1 : 0, (dialog, which) -> {
                    store.saveQuickEntry(which == 1);
                    dialog.dismiss();
                    render();
                })
                .show();
    }

    private void showBudgetDialog() {
        EditText tax = input("Tax rate %", trimNumber(store.taxRate()), true);
        EditText budget = input("Budget", store.budget() == 0 ? "" : trimNumber(store.budget()), true);
        LinearLayout wrap = column();
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(tax);
        wrap.addView(budget, top(8));
        new AlertDialog.Builder(this).setTitle("Tax rate and budget").setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    store.saveSettings(parseDouble(tax.getText().toString()), parseDouble(budget.getText().toString()));
                    render();
                }).show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Shopping List Calculator")
                .setMessage("Version " + appVersion())
                .setNegativeButton("Close", null)
                .setNeutralButton("F-Droid", (dialog, which) -> openUrl("https://f-droid.org/packages/io.github.buildsbyben.shoppinglistcalc/"))
                .setPositiveButton("GitHub", (dialog, which) -> openUrl("https://github.com/buildsbyben/shopping-list-calc"))
                .show();
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void saveFormat(CurrencyFormat format) {
        store.saveCurrencyFormat(format);
        render();
    }

    private void section(String title) {
        TextView view = label(title, 14, accent, true);
        view.setPadding(0, dp(22), 0, dp(7));
        rows.addView(view);
    }

    private void setting(String title, String summary, View.OnClickListener listener) {
        LinearLayout row = column();
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackgroundColor(card);
        row.setOnClickListener(listener);
        row.addView(label(title, 17, text, true));
        TextView detail = label(summary, 13, muted, false);
        detail.setPadding(0, dp(3), 0, 0);
        row.addView(detail);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        rows.addView(row, params);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, 1);
        return view;
    }

    private EditText input(String hint, String value, boolean decimal) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextColor(text);
        edit.setHintTextColor(muted);
        if (decimal) edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return edit;
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private char singleSeparator(String value, char fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed.charAt(0);
    }

    private int parseInt(String value, int fallback) {
        try { return Math.max(0, Math.min(3, Integer.parseInt(value.trim()))); }
        catch (Exception ignored) { return fallback; }
    }

    private double parseDouble(String value) {
        try { return value.trim().isEmpty() ? 0 : Double.parseDouble(value.trim()); }
        catch (Exception ignored) { return 0; }
    }

    private String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
