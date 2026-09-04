package io.github.buildsbyben.shoppinglistcalc;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.InputType;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private static final String PREFS = "shopping_calc";
    private final int bg = Color.BLACK, text = Color.WHITE, muted = Color.WHITE;
    private ShoppingListStore store;
    private LinearLayout rows, customFormat;
    private EditText symbol, decimal, grouping, digits, taxInput, budgetInput;
    private RadioGroup symbolPosition, currencyChoices, priceChoices, flowChoices;
    private CurrencyFormat budgetFormat;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        store = new ShoppingListStore(getSharedPreferences(PREFS, MODE_PRIVATE)); render();
    }

    private void render() {
        LinearLayout screen = column(); screen.setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL); header.setPadding(dp(16), dp(16), dp(16), dp(8));
        TextView title = label("Settings", 29, text, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button save = primaryButton("Save"); save.setOnClickListener(v -> { if (saveAll()) finish(); });
        header.addView(save, new LinearLayout.LayoutParams(-2, -2));
        screen.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(bg);
        rows = column(); rows.setPadding(dp(16), dp(8), dp(16), dp(28)); scroll.addView(rows);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        section("Money"); addCurrencySettings();
        section("Price entry");
        priceChoices = radios(); addRadio(priceChoices, "Direct amount entry", "Type an amount normally, such as 12.50 or 12,50.", !store.quickCentsEntry()); addRadio(priceChoices, "Quick cents entry", "Digits shift into cents as you type.", store.quickCentsEntry());
        rows.addView(priceChoices);
        section("Shopping flow");
        flowChoices = radios(); addRadio(flowChoices, "Standard", "New items start at their name.", !store.quickEntry()); addRadio(flowChoices, "Quick entry", "New items start at price. Next adds another item.", store.quickEntry());
        rows.addView(flowChoices);
        section("Budget and tax");
        LinearLayout budgetCard = card();
        LinearLayout budgetFields = new LinearLayout(this); budgetFields.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout taxField = column(); taxField.addView(label("Tax rate (%)", 16, text, true)); taxInput = input("", trimNumber(store.taxRate()), true); taxField.addView(taxInput, top(5));
        budgetFormat = store.currencyFormat();
        LinearLayout budgetField = column(); budgetField.addView(label("Budget", 16, text, true)); budgetInput = input("", store.budget() == 0 ? "" : formatBudget(store.budget(), budgetFormat), false); budgetField.addView(budgetInput, top(5));
        LinearLayout.LayoutParams taxParams = new LinearLayout.LayoutParams(0, -2, 1); LinearLayout.LayoutParams budgetParams = new LinearLayout.LayoutParams(0, -2, 1); budgetParams.leftMargin = dp(10);
        budgetFields.addView(taxField, taxParams); budgetFields.addView(budgetField, budgetParams); budgetCard.addView(budgetFields); rows.addView(budgetCard);
        section("About"); LinearLayout about = card(); about.addView(label("Shopping List Calculator", 17, text, true)); TextView version = label("Version " + appVersion(), 13, muted, false); version.setPadding(0, dp(3), 0, dp(9)); about.addView(version);
        Button github = button("GitHub repository"); github.setOnClickListener(v -> openUrl("https://github.com/buildsbyben/shopping-list-calc")); about.addView(github);
        Button issues = button("Report an issue"); issues.setOnClickListener(v -> openUrl("https://github.com/buildsbyben/shopping-list-calc/issues")); about.addView(issues, top(8));
        Button fdroid = button("Get updates on F-Droid"); fdroid.setOnClickListener(v -> openUrl("https://f-droid.org/packages/io.github.buildsbyben.shoppinglistcalc/")); about.addView(fdroid, top(8)); rows.addView(about);
        screen.setOnApplyWindowInsetsListener((view, insets) -> { header.setPadding(dp(16), dp(16) + insets.getSystemWindowInsetTop(), dp(16), dp(8)); return insets; });
        setContentView(screen);
    }

    private void addCurrencySettings() {
        CurrencyFormat current = store.currencyFormat(); LinearLayout money = card(); money.addView(label("Currency format", 17, text, true)); TextView note = label("Choose manually. This app does not read your device region or location.", 13, muted, false); note.setPadding(0, dp(3), 0, dp(8)); money.addView(note);
        currencyChoices = radios(); addRadio(currencyChoices, "US Dollar", "$1,234.56", isFormat(current, "$", false, '.', ',', 2)); addRadio(currencyChoices, "Euro", "€1.234,56", isFormat(current, "€", false, ',', '.', 2)); addRadio(currencyChoices, "British Pound", "£1,234.56", isFormat(current, "£", false, '.', ',', 2)); addRadio(currencyChoices, "Japanese Yen", "¥1,235", isFormat(current, "¥", false, '.', ',', 0)); boolean custom = !isPreset(current); addRadio(currencyChoices, "Custom format", "Choose your own symbol and separators.", custom); money.addView(currencyChoices); addCustomFormat(current, money, custom);
        currencyChoices.setOnCheckedChangeListener((g, id) -> {
            int selected = selectedIndex(currencyChoices);
            customFormat.setVisibility(selected == 4 ? View.VISIBLE : View.GONE);
            refreshBudgetFormat(selected < 4 ? presetFormat(selected) : customFormatPreview());
        }); rows.addView(money);
    }

    private void addCustomFormat(CurrencyFormat value, LinearLayout parent, boolean visible) {
        customFormat = column(); customFormat.setPadding(0, dp(8), 0, 0); customFormat.setVisibility(visible ? View.VISIBLE : View.GONE); customFormat.addView(label("Custom format", 16, text, true));
        symbol = input("", value.symbol, false); decimal = input("", String.valueOf(value.decimalSeparator), false); grouping = input("", value.groupingSeparator == '\0' ? "" : String.valueOf(value.groupingSeparator), false); digits = input("", String.valueOf(value.fractionDigits), true);
        customFormat.addView(inputRow("Currency symbol", symbol, 6));
        customFormat.addView(inputRow("Decimal separator (. or ,)", decimal, 8));
        customFormat.addView(inputRow("Thousands separator (, . space, or blank)", grouping, 8));
        customFormat.addView(inputRow("Decimal places (0–3)", digits, 8));
        customFormat.addView(label("Symbol placement", 14, text, true), top(10));
        symbolPosition = radios(); addRadio(symbolPosition, "Before amount", "$1,234.56", !value.symbolAfter); addRadio(symbolPosition, "After amount", "1.234,56 €", value.symbolAfter); customFormat.addView(symbolPosition); parent.addView(customFormat);
    }

    private boolean saveAll() {
        int currency = selectedIndex(currencyChoices);
        CurrencyFormat format;
        if (currency < 4) format = presetFormat(currency);
        else {
            char d = separator(decimal.getText().toString(), '.');
            char g = grouping.getText().toString().trim().isEmpty() ? '\0' : separator(grouping.getText().toString(), ',');
            if (d == g && g != '\0') { grouping.setError("Use a different separator than decimal."); return false; }
            format = new CurrencyFormat(symbol.getText().toString().trim(), selectedIndex(symbolPosition) == 1, d, g, parseInt(digits.getText().toString(), 2));
        }
        store.saveCurrencyFormat(format);
        store.saveQuickCentsEntry(selectedIndex(priceChoices) == 1);
        store.saveQuickEntry(selectedIndex(flowChoices) == 1);
        store.saveSettings(parseDouble(taxInput.getText().toString()), parseBudget(budgetInput.getText().toString(), budgetFormat));
        return true;
    }
    private CurrencyFormat presetFormat(int id) { if (id == 0) return new CurrencyFormat("$", false, '.', ',', 2); if (id == 1) return new CurrencyFormat("€", false, ',', '.', 2); if (id == 2) return new CurrencyFormat("£", false, '.', ',', 2); return new CurrencyFormat("¥", false, '.', ',', 0); }
    private CurrencyFormat customFormatPreview() { char d = separator(decimal.getText().toString(), '.'); char g = grouping.getText().toString().trim().isEmpty() ? '\0' : separator(grouping.getText().toString(), ','); return new CurrencyFormat(symbol.getText().toString().trim(), selectedIndex(symbolPosition) == 1, d, g, parseInt(digits.getText().toString(), 2)); }
    private void refreshBudgetFormat(CurrencyFormat format) { double amount = parseBudget(budgetInput.getText().toString(), budgetFormat); budgetFormat = format; if (amount != 0 || !budgetInput.getText().toString().trim().isEmpty()) budgetInput.setText(formatBudget(amount, format)); }
    private String formatBudget(double amount, CurrencyFormat format) { String raw = String.format(Locale.US, "%." + format.fractionDigits + "f", amount); int point = raw.indexOf('.'); String whole = point < 0 ? raw : raw.substring(0, point); String fraction = point < 0 ? "" : raw.substring(point + 1); if (format.groupingSeparator != '\0') { StringBuilder grouped = new StringBuilder(); for (int i = 0; i < whole.length(); i++) { if (i > 0 && (whole.length() - i) % 3 == 0) grouped.append(format.groupingSeparator); grouped.append(whole.charAt(i)); } whole = grouped.toString(); } String number = format.fractionDigits == 0 ? whole : whole + format.decimalSeparator + fraction; return format.symbolAfter ? number + format.symbol : format.symbol + number; }
    private double parseBudget(String value, CurrencyFormat format) { String raw = value == null ? "" : value.trim(); if (!format.symbol.isEmpty()) raw = raw.replace(format.symbol, ""); if (format.groupingSeparator != '\0') raw = raw.replace(String.valueOf(format.groupingSeparator), ""); if (format.decimalSeparator != '.') raw = raw.replace(format.decimalSeparator, '.'); return parseDouble(raw.trim()); }
    private boolean isPreset(CurrencyFormat v) { return isFormat(v,"$",false,'.',',',2)||isFormat(v,"€",false,',','.',2)||isFormat(v,"£",false,'.',',',2)||isFormat(v,"¥",false,'.',',',0); }
    private boolean isFormat(CurrencyFormat v, String s, boolean after, char d, char g, int places) { return v.symbol.equals(s) && v.symbolAfter == after && v.decimalSeparator == d && v.groupingSeparator == g && v.fractionDigits == places; }
    private RadioGroup radios() { RadioGroup g = new RadioGroup(this); g.setOrientation(LinearLayout.VERTICAL); return g; }
    private void addRadio(RadioGroup group, String title, String summary, boolean checked) { RadioButton radio = new RadioButton(this); radio.setId(View.generateViewId()); SpannableString content = new SpannableString(title + "\n" + summary); content.setSpan(new RelativeSizeSpan(.76f), title.length() + 1, content.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); radio.setText(content); radio.setTextSize(17); radio.setTextColor(text); radio.setButtonTintList(ColorStateList.valueOf(Color.WHITE)); radio.setPadding(0, dp(6), 0, dp(6)); radio.setChecked(checked); group.addView(radio); }
    private int selectedIndex(RadioGroup group) { View selected = group.findViewById(group.getCheckedRadioButtonId()); return selected == null ? -1 : group.indexOfChild(selected); }
    private void section(String title) { TextView v = label(title,16,text,true); v.setPadding(0,dp(24),0,dp(7)); rows.addView(v); }
    private LinearLayout card() { LinearLayout l = column(); l.setPadding(0,0,0,0); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(8); l.setLayoutParams(p); return l; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setTextColor(text); b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL); b.setBackgroundColor(Color.TRANSPARENT); b.setPadding(dp(8), dp(6), dp(8), dp(6)); return b; }
    private Button primaryButton(String value) { Button b = new Button(this); b.setText(value); b.setTextColor(Color.BLACK); b.setTextSize(15); b.setGravity(android.view.Gravity.CENTER); b.setBackgroundColor(Color.WHITE); b.setPadding(dp(16), dp(8), dp(16), dp(8)); return b; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout inputRow(String title, EditText field, int marginTop) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView name = label(title, 14, text, true); name.setPadding(0, 0, dp(12), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(field, new LinearLayout.LayoutParams(dp(116), -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(marginTop); row.setLayoutParams(params);
        return row;
    }
    private TextView label(String value, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(null, Typeface.BOLD); return v; }
    private EditText input(String hint, String value, boolean number) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true); e.setTextColor(text); e.setHintTextColor(Color.LTGRAY); e.setPadding(dp(10), 0, dp(10), 0); GradientDrawable border = new GradientDrawable(); border.setColor(Color.TRANSPARENT); border.setStroke(dp(1), Color.WHITE); border.setCornerRadius(dp(2)); e.setBackground(border); if(number) e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); return e; }
    private LinearLayout.LayoutParams top(int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(margin); return p; }
    private void openUrl(String url) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    private String appVersion() { try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch(Exception e) { return ""; } }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private char separator(String value, char fallback) { String t = value == null ? "" : value.trim(); return t.isEmpty() ? fallback : t.charAt(0); }
    private int parseInt(String value,int fallback) { try { return Math.max(0,Math.min(3,Integer.parseInt(value.trim()))); } catch(Exception e) { return fallback; } }
    private double parseDouble(String value) { try { return value.trim().isEmpty()?0:Double.parseDouble(value.trim()); } catch(Exception e) { return 0; } }
    private String trimNumber(double value) { return value == Math.rint(value) ? String.valueOf((long)value) : String.valueOf(value); }
}
