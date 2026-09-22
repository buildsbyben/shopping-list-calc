package io.github.buildsbyben.shoppinglistcalc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ShoppingListStore(getSharedPreferences("shopping_calc", MODE_PRIVATE))

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    RenderSettingsScreen(
                        store = store,
                        onSave = { finish() }
                    )
                }
            }
        }
    }

    // Data classes and Lists outside  the composable for performance
    data class CurrencyOption(val symbol: String, val label: String, val subtitle: String)

    val currencyOptions = listOf(
        CurrencyOption("$", "US Dollar", "Common in the United States(e.g. $10.0)."),
        CurrencyOption("€", "Euro", "Common in most European countries(e.g. €10.0)."),
        CurrencyOption("£", "British Pound", "Common in the UK(e.g. £10.0)."),
        CurrencyOption("¥", "Japanese Yen", "Common in Japan(e.g. ¥10.0)."),
    )

    // weightOptions
    data class WeightOption(val code: String, val label: String)

    val weightOptions = listOf(
        WeightOption("lb", "Pounds (lb) - Common in the United States."),
        WeightOption("kg", "Kilograms (kg) - Common in most countries."),
        WeightOption("oz", "Ounces (oz) - Useful for smaller US measurements."),
    )


    //For performance
    @Composable
    private fun BudgetTaxSection(
        taxRate: String,
        onTaxChange: (String) -> Unit,
        budget: String,
        onBudgetChange: (String) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Tax rate (%)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                CustomOutlinedTextField(value = taxRate, onValueChange = onTaxChange)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Budget", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                CustomOutlinedTextField(value = budget, onValueChange = onBudgetChange)
            }
        }
    }


    @Composable
    private fun RenderSettingsScreen(
        store: ShoppingListStore,
        onSave: () -> Unit
    ) {
        val context = LocalContext.current

        // Load initial state
        val scrollState = rememberLazyListState()
        var taxRate by remember { mutableStateOf(store.taxRate().toString()) }
        var budget by remember { mutableStateOf(store.budget().toString()) }
        var quickCents by remember { mutableStateOf(store.quickCentsEntry()) }
        var quickEntry by remember { mutableStateOf(store.quickEntry()) }
        var weightUnit by remember { mutableStateOf(store.weightUnit()) }
        val currentFormat = store.currencyFormat()
        var currencySymbol by remember { mutableStateOf(currentFormat.symbol) }
        var symbolAfter by remember { mutableStateOf(currentFormat.symbolAfter) }

        // Currency


        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                //.verticalScroll(rememberScrollState()) fn
                .padding(16.dp)
        ) {
            item{
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            // Save changes back into ShoppingListStore
                            store.saveSettings(
                                taxRate.toDoubleOrNull() ?: 0.0,
                                budget.toDoubleOrNull() ?: 0.0
                            )
                            store.saveQuickCentsEntry(quickCents)
                            store.saveQuickEntry(quickEntry)
                            store.saveWeightUnit(weightUnit)
                            val updatedFormat = CurrencyFormat(
                                currencySymbol,
                                symbolAfter,
                                currentFormat.decimalSeparator,
                                currentFormat.groupingSeparator,
                                currentFormat.fractionDigits
                            )
                            store.saveCurrencyFormat(updatedFormat)
                            onSave()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(text = "SAVE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // Budget and tax Section
            item(key = "budget_tax_section") {
                BudgetTaxSection(
                    taxRate = taxRate,
                    onTaxChange = { taxRate = it },
                    budget = budget,
                    onBudgetChange = { budget = it }
                )
            }

            // Currency Section
            item {
                SectionHeader(title = "Currency symbol")
            }


            items(currencyOptions, key = { it.symbol }) { option ->
                RadioOptionRow(
                    title = option.label,
                    subtitle = option.subtitle,
                    selected = currencySymbol == option.symbol,
                    onClick = { currencySymbol = option.symbol }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {

                SectionHeader(title = "Currency position")
            }
            item {
                RadioOptionRow(
                    title = "Before amount (e.g. $10)",
                    subtitle = "",
                    selected = !symbolAfter,
                    onClick = { symbolAfter = false }
                )
            }
            item {
                RadioOptionRow(
                    title = "After amount (e.g. 10 $)",
                    subtitle = "",
                    selected = symbolAfter,
                    onClick = { symbolAfter = true }
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Price entry Section
            item {
                SectionHeader(title = "Price entry")
            }
            item {
                RadioOptionRow(
                    title = "Direct amount entry",
                    subtitle = "Type an amount normally, such as 12.50 or 12,50.",
                    selected = !quickCents,
                    onClick = { quickCents = false }
                )
            }
            item {
                RadioOptionRow(
                    title = "Quick cents entry",
                    subtitle = "Digits shift into cents as you type.",
                    selected = quickCents,
                    onClick = { quickCents = true }
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Item entry Section (Quick entry mapping)
            item {
                SectionHeader(title = "Item entry mode")
            }

            item {
                RadioOptionRow(
                    title = "Name first",
                    subtitle = "New items start at the item name field.",
                    selected = !quickEntry,
                    onClick = { quickEntry = false }
                )
            }
            item {
                RadioOptionRow(
                    title = "Price first",
                    subtitle = "New items start at the price field. Next adds another item.",
                    selected = quickEntry,
                    onClick = { quickEntry = true }
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Weight unit Section
            item {
                SectionHeader(title = "Weight unit")
            }

            items(
                items = weightOptions,
                key = { it.code }
            ) { (code, label) ->
                RadioOptionRow(
                    title = label,
                    subtitle = "",
                    selected = weightUnit == code,
                    onClick = { weightUnit = code }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // About Section
            item {
                SectionHeader(title = "About")
            }

            item {
                Text(
                    "Shopping List Calculator",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Text(
                    "Version 2.0",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }


            item {
                TextLinkButton(text = "GITHUB REPOSITORY") {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/buildsbyben/shopping-list-calculator")
                        )
                    )
                }
            }

            item {

                TextLinkButton(text = "REPORT AN ISSUE") {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/buildsbyben/shopping-list-calculator/issues")
                        )
                    )
                }
            }

            item {
                TextLinkButton(text = "GET UPDATES ON F-DROID") {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://f-droid.org")
                        )
                    )
                }
            }
            item{

            Spacer(modifier = Modifier.height(32.dp))}
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun RadioOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.White,
                unselectedColor = Color.White
            ),
            modifier = Modifier.padding(end = 12.dp)
        )
        Column {
            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(Color.White),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun TextLinkButton(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    )
}