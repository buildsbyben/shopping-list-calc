package io.github.buildsbyben.shoppinglistcalc

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

class SettingsActivity : Activity() {
    private val bg = Color.BLACK
    private val text = Color.WHITE
    private val muted = Color.WHITE
    private lateinit var store: ShoppingListStore
    private lateinit var rows: LinearLayout
    private lateinit var customFormat: LinearLayout
    private lateinit var symbol: EditText
    private lateinit var decimal: EditText
    private lateinit var grouping: EditText
    private lateinit var digits: EditText
    private lateinit var taxInput: EditText
    private lateinit var budgetInput: EditText
    private lateinit var symbolPosition: RadioGroup
    private lateinit var currencyChoices: RadioGroup
    private lateinit var priceChoices: RadioGroup
    private lateinit var flowChoices: RadioGroup
    private lateinit var weightChoices: RadioGroup
    private lateinit var budgetFormat: CurrencyFormat

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        getWindow().setStatusBarColor(bg)
        getWindow().setNavigationBarColor(bg)
        store = ShoppingListStore(getSharedPreferences(PREFS, MODE_PRIVATE))
        render()
    }

    private fun render() {
        val screen = column()
        screen.setBackgroundColor(bg)
        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(16), dp(16), dp(8))

        val title = label("Settings", 29, text, true)
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val save = primaryButton("Save")
        save.setOnClickListener { if (saveAll()) finish() }
        header.addView(save, LinearLayout.LayoutParams(-2, -2))
        screen.addView(header)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        rows = column()
        rows.setPadding(dp(16), dp(8), dp(16), dp(28))
        scroll.addView(rows)
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        section("Budget and tax")
        addBudgetAndTaxSettings()

        section("Money")
        addCurrencySettings()

        section("Price entry")
        priceChoices = radios()
        val quickCents = store.quickCentsEntry()

        priceChoices.let { safeChoices ->
            addRadio(
                safeChoices,
                "Direct amount entry",
                "Type an amount normally, such as 12.50 or 12,50.",
                !quickCents
            )
            addRadio(
                safeChoices,
                "Quick cents entry",
                "Digits shift into cents as you type.",
                quickCents
            )
            rows.addView(priceChoices)
        }

        section("New item focus")
        flowChoices = radios()
        val quickEntry = store.quickEntry()

        flowChoices.let { safeChoices ->
            addRadio(
                group = safeChoices,
                title = "Name first",
                summary = "New items start at the item name field.",
                checked = !quickEntry
            )
            addRadio(
                safeChoices,
                "Price first",
                "New items start at the price field. Next adds another item.",
                quickEntry
            )
            rows.addView(flowChoices)
        }

        section("Weight unit")
        weightChoices = radios()

        weightChoices.let { safeChoice ->
            addRadio(
                safeChoice,
                "Pounds (lb)",
                "Common in the United States.",
                "lb" == store.weightUnit()
            )
            addRadio(
                safeChoice,
                "Kilograms (kg)",
                "Common in most countries.",
                "kg" == store.weightUnit()
            )
            addRadio(
                safeChoice,
                "Ounces (oz)",
                "Useful for smaller US measurements.",
                "oz" == store.weightUnit()
            )
            addRadio(
                safeChoice,
                "Grams (g)",
                "Useful for smaller metric measurements.",
                "g" == store.weightUnit()
            )
            rows.addView(weightChoices)
        }

        section("About")
        val about = card()
        about.addView(label("Shopping List Calculator", 17, text, true))
        val version = label("Version " + appVersion(), 13, muted, false)
        version.setPadding(0, dp(3), 0, dp(9))
        about.addView(version)

        val github = button("GitHub repository")
        github.setOnClickListener { openUrl("https://github.com/buildsbyben/shopping-list-calc") }
        about.addView(github)

        val issues = button("Report an issue")
        issues.setOnClickListener { openUrl("https://github.com/buildsbyben/shopping-list-calc/issues") }
        about.addView(issues, top(8))

        val fdroid = button("Get updates on F-Droid")
        fdroid.setOnClickListener { openUrl("https://f-droid.org/packages/io.github.buildsbyben.shoppinglistcalc/") }
        about.addView(fdroid, top(8))
        rows.addView(about)

        screen.setOnApplyWindowInsetsListener { _, insets ->
            val topInset = insets.systemWindowInsetTop
            header.setPadding(dp(16), dp(16) + topInset, dp(16), dp(8))
            insets
        }

        setContentView(screen)
    }

    private fun addBudgetAndTaxSettings() {
        val settingsCard = card()
        val fields = LinearLayout(this)
        fields.orientation = LinearLayout.HORIZONTAL

        val taxField = column()
        taxField.addView(label("Tax rate (%)", 16, text, true))
        taxInput = input("", trimNumber(store.taxRate()), true)
        taxField.addView(taxInput, top(5))

        val format = store.currencyFormat()
        budgetFormat = format
        val budget = store.budget()

        val budgetField = column()
        budgetField.addView(label("Budget", 16, text, true))

        val budgetText = if (budget == 0.0) "" else formatBudget(budget, format)
        budgetInput = input("", budgetText, false)
        // Request a decimal keyboard without filtering formatted currency text.
        // setInputType would install a digits-only listener and strip symbols/separators.
        budgetInput.setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        budgetField.addView(budgetInput, top(5))

        val taxParams = LinearLayout.LayoutParams(0, -2, 1f)
        val budgetParams = LinearLayout.LayoutParams(0, -2, 1f)
        budgetParams.leftMargin = dp(10)

        fields.addView(taxField, taxParams)
        fields.addView(budgetField, budgetParams)
        settingsCard.addView(fields)

        rows.addView(settingsCard)
    }

    private fun addCustomFormat(value: CurrencyFormat, parent: LinearLayout, visible: Boolean) {
        val container = column().apply {
            setPadding(0, dp(8), 0, 0)
            visibility = if (visible) View.VISIBLE else View.GONE
        }
        customFormat = container

        container.addView(label("Custom format", 16, text, true))

        symbol = input("", value.symbol, false)
        decimal = input("", value.decimalSeparator.toString(), false)
        grouping = input(
            "",
            if (value.groupingSeparator == '\u0000') "" else value.groupingSeparator.toString(),
            false
        )
        digits = input("", value.fractionDigits.toString(), true)

        container.addView(inputRow("Currency symbol", symbol, 6))
        container.addView(inputRow("Decimal separator (. or ,)", decimal, 8))
        container.addView(inputRow("Thousands separator (, . space, or blank)", grouping, 8))
        container.addView(inputRow("Decimal places (0–3)", digits, 8))
        container.addView(label("Symbol placement", 14, text, true), top(10))

        val posGroup = radios()
        symbolPosition = posGroup
        addRadio(posGroup, "Before amount", "$1,234.56", !value.symbolAfter)
        addRadio(posGroup, "After amount", "1.234,56 $", value.symbolAfter)
        container.addView(posGroup)

        parent.addView(container)
    }


    private fun addCurrencySettings() {
        val current = store.currencyFormat()
        val money = card()
        money.addView(label("Currency format", 17, text, true))
        val note = label(
            "Choose manually. This app does not read your device region or location.",
            13,
            muted,
            false
        )
        note.setPadding(0, dp(3), 0, dp(8))
        money.addView(note)

        currencyChoices = radios()
        currencyChoices.let { safeChoices ->
            addRadio(
                safeChoices,
                "US Dollar",
                "$1,234.56",
                isFormat(current, "$", false, '.', ',', 2)
            )
            addRadio(
                safeChoices,
                "Euro",
                "€1.234,56",
                isFormat(current, "€", false, ',', '.', 2)
            )
            addRadio(
                safeChoices,
                "British Pound",
                "£1,234.56",
                isFormat(current, "£", false, '.', ',', 2)
            )
            addRadio(
                safeChoices,
                "Japanese Yen",
                "¥1,235",
                isFormat(current, "¥", false, '.', ',', 0)
            )
            val custom = !isPreset(current)
            addRadio(
                safeChoices,
                "Custom format",
                "Choose your own symbol and separators.",
                custom
            )

            money.addView(safeChoices)
            addCustomFormat(current, money, custom)

            safeChoices.setOnCheckedChangeListener { group, _ ->
                val selected = selectedIndex(group)
                customFormat.visibility = if (selected == 4) View.VISIBLE else View.GONE
                refreshBudgetFormat(
                    if (selected < 4) {
                        presetFormat(selected)
                    } else {
                        customFormatPreview()
                    }
                )
            }

            rows.addView(money)
        }
    }

    private fun saveAll(): Boolean {
        val currency = selectedIndex(currencyChoices)
        val format: CurrencyFormat

        if (currency < 4) {
            format = presetFormat(currency)
        } else {
            val preview = customFormatPreview()
            if (preview.decimalSeparator == preview.groupingSeparator && preview.groupingSeparator != '\u0000') {
                grouping.error = "Use a different separator than decimal."
                return false
            }
            format = preview
        }

        store.saveCurrencyFormat(format)

        store.saveQuickCentsEntry(selectedIndex(priceChoices) == 1)
        store.saveQuickEntry(selectedIndex(flowChoices) == 1)

        val weightUnits = arrayOf("lb", "kg", "oz", "g")
        val weightIndex = max(0, selectedIndex(weightChoices))
        store.saveWeightUnit(weightUnits.getOrNull(weightIndex) ?: "lb")

        store.saveSettings(
            parseDouble(taxInput.text.toString()),
            parseBudget(budgetInput.text.toString(), budgetFormat)
        )
        return true
    }

    private fun presetFormat(id: Int): CurrencyFormat {
        if (id == 0) return CurrencyFormat("$", false, '.', ',', 2)
        if (id == 1) return CurrencyFormat("€", false, ',', '.', 2)
        if (id == 2) return CurrencyFormat("£", false, '.', ',', 2)
        return CurrencyFormat("¥", false, '.', ',', 0)
    }

    private fun customFormatPreview(): CurrencyFormat {
        val sym = symbol.text.toString().trim()
        val isAfter = selectedIndex(symbolPosition) == 1
        val dec = decimal.text.toString().trim().firstOrNull() ?: '.'

        // A literal space is a supported separator; only an empty field disables grouping.
        val groupText = grouping.text.toString()
        val group = if (groupText.isEmpty()) '\u0000' else groupText.first()

        val fracDigits = parseInt(digits.text.toString(), 2)

        return CurrencyFormat(sym, isAfter, dec, group, fracDigits)
    }

    private fun refreshBudgetFormat(format: CurrencyFormat) {
        val currentText = budgetInput.text.toString()
        val amount = parseBudget(currentText, budgetFormat)
        budgetFormat = format
        if (currentText.isNotBlank()) {
            budgetInput.setText(formatBudget(amount, format))
        }
    }

    private fun formatBudget(amount: Double, format: CurrencyFormat): String {
        val raw = String.format(Locale.US, "%." + format.fractionDigits + "f", amount)
        val point = raw.indexOf('.')
        var whole = if (point < 0) raw else raw.substring(0, point)
        val fraction = if (point < 0) "" else raw.substring(point + 1)
        if (format.groupingSeparator != '\u0000') {
            val grouped = StringBuilder()
            for (i in 0 until whole.length) {
                if (i > 0 && (whole.length - i) % 3 == 0) grouped.append(format.groupingSeparator)
                grouped.append(whole[i])
            }
            whole = grouped.toString()
        }
        val number = if (format.fractionDigits == 0) whole else whole + format.decimalSeparator + fraction
        return if (format.symbolAfter) number + format.symbol else format.symbol + number
    }

    private fun parseBudget(value: String?, format: CurrencyFormat): Double {
        if (value.isNullOrBlank()) return 0.0
        var raw = value.trim()

        val sym = format.symbol.orEmpty()
        if (sym.isNotEmpty()) {
            raw = raw.replace(sym, "")
        }

        val groupSep = format.groupingSeparator
        if (groupSep != '\u0000') {
            raw = raw.replace(groupSep.toString(), "")
        }

        val decSep = format.decimalSeparator
        if (decSep != '.') {
            raw = raw.replace(decSep, '.')
        }

        raw = raw.replace(Regex("[^0-9.]"), "")
        return parseDouble(raw)
    }

    private fun isPreset(v: CurrencyFormat): Boolean {
        return isFormat(v, "$", false, '.', ',', 2) || isFormat(
            v,
            "€",
            false,
            ',',
            '.',
            2
        ) || isFormat(v, "£", false, '.', ',', 2) || isFormat(v, "¥", false, '.', ',', 0)
    }

    private fun isFormat(
        v: CurrencyFormat,
        s: String?,
        after: Boolean,
        d: Char,
        g: Char,
        places: Int
    ): Boolean {
        return v.symbol == s && v.symbolAfter == after && v.decimalSeparator == d && v.groupingSeparator == g && v.fractionDigits == places
    }

    private fun radios(): RadioGroup {
        val g = RadioGroup(this)
        g.setOrientation(LinearLayout.VERTICAL)
        return g
    }

    private fun addRadio(group: RadioGroup, title: String, summary: String?, checked: Boolean) {
        val radio = RadioButton(this)
        radio.setId(View.generateViewId())
        val content = SpannableString(title + "\n" + summary)
        content.setSpan(
            RelativeSizeSpan(.76f),
            title.length + 1,
            content.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        radio.setText(content)
        radio.setTextSize(17f)
        radio.setTextColor(text)
        radio.setButtonTintList(ColorStateList.valueOf(Color.WHITE))
        radio.setPadding(0, dp(6), 0, dp(6))
        radio.setChecked(checked)
        group.addView(radio)
    }

    private fun selectedIndex(group: RadioGroup): Int {
        val selected = group.findViewById<View?>(group.getCheckedRadioButtonId())
        return if (selected == null) -1 else group.indexOfChild(selected)
    }

    private fun section(title: String?) {
        val v = label(title, 16, text, true)
        v.setPadding(0, dp(24), 0, dp(7))
        rows.addView(v)
    }

    private fun card(): LinearLayout {
        val l = column()
        l.setPadding(0, 0, 0, 0)
        val p = LinearLayout.LayoutParams(-1, -2)
        p.bottomMargin = dp(8)
        l.setLayoutParams(p)
        return l
    }

    private fun button(value: String?): Button {
        val b = Button(this)
        b.setText(value)
        b.setTextColor(text)
        b.setGravity(Gravity.START or Gravity.CENTER_VERTICAL)
        b.setBackgroundColor(Color.TRANSPARENT)
        b.setPadding(dp(8), dp(6), dp(8), dp(6))
        return b
    }

    private fun primaryButton(value: String?): Button {
        val b = Button(this)
        b.setText(value)
        b.setTextColor(Color.BLACK)
        b.setTextSize(15f)
        b.setGravity(Gravity.CENTER)
        b.setBackgroundColor(Color.WHITE)
        b.setPadding(dp(16), dp(8), dp(16), dp(8))
        return b
    }

    private fun column(): LinearLayout {
        val l = LinearLayout(this)
        l.setOrientation(LinearLayout.VERTICAL)
        return l
    }

    private fun inputRow(title: String, field: EditText, marginTop: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = label(title, 14, text, true)
        name.setPadding(0, 0, dp(12), 0)
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(field, LinearLayout.LayoutParams(dp(116), -2))
        val params = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(marginTop)
        }
        row.layoutParams = params
        return row
    }

    private fun label(value: String?, size: Int, color: Int, bold: Boolean): TextView {
        val v = TextView(this)
        v.setText(value)
        v.setTextSize(size.toFloat())
        v.setTextColor(color)
        if (bold) v.setTypeface(null, Typeface.BOLD)
        return v
    }

    private fun input(hint: String?, value: String?, number: Boolean): EditText {
        val e = EditText(this)
        e.setHint(hint)
        e.setText(value)
        e.setSingleLine(true)
        e.setTextColor(text)
        e.setHintTextColor(Color.LTGRAY)
        e.setPadding(dp(10), 0, dp(10), 0)
        val border = GradientDrawable()
        border.setColor(Color.TRANSPARENT)
        border.setStroke(dp(1), Color.WHITE)
        border.setCornerRadius(dp(2).toFloat())
        e.setBackground(border)
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        return e
    }

    private fun top(margin: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(margin)
        return p
    }

    private fun openUrl(url: String?) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun appVersion(): String? {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName
        } catch (e: Exception) {
            return ""
        }
    }

    private fun dp(value: Int): Int {
        return Math.round(value * getResources().getDisplayMetrics().density)
    }

    private fun parseInt(value: String?, fallback: Int): Int {
        val parsed = value?.trim()?.toIntOrNull() ?: return fallback
        return max(0, min(3, parsed))
    }

    private fun parseDouble(value: String?): Double {
        return value?.trim()?.toDoubleOrNull() ?: 0.0
    }


    private fun trimNumber(value: Double): String {
        return if (value == round(value)) value.toLong().toString() else value.toString()
    }

    companion object {
        private const val PREFS = "shopping_calc"
    }
}
