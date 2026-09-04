package io.github.buildsbyben.shoppinglistcalc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** A user-selected display and input format. No device locale or location is read. */
final class CurrencyFormat {
    final String symbol;
    final boolean symbolAfter;
    final char decimalSeparator;
    final char groupingSeparator;
    final int fractionDigits;

    CurrencyFormat(String symbol, boolean symbolAfter, char decimalSeparator,
                   char groupingSeparator, int fractionDigits) {
        this.symbol = symbol == null ? "" : symbol;
        this.symbolAfter = symbolAfter;
        this.decimalSeparator = decimalSeparator;
        this.groupingSeparator = groupingSeparator;
        this.fractionDigits = Math.max(0, Math.min(3, fractionDigits));
    }

    String format(double amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setDecimalSeparator(decimalSeparator);
        symbols.setGroupingSeparator(groupingSeparator);
        StringBuilder pattern = new StringBuilder("#,##0");
        if (fractionDigits > 0) {
            pattern.append('.');
            for (int i = 0; i < fractionDigits; i++) {
                pattern.append('0');
            }
        }
        DecimalFormat formatter = new DecimalFormat(pattern.toString(), symbols);
        formatter.setGroupingUsed(groupingSeparator != '\0');
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        String number = formatter.format(amount);
        if (symbol.isEmpty()) {
            return number;
        }
        return symbolAfter ? number + " " + symbol : symbol + number;
    }

    double parseDirect(String raw, double fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        String cleaned = raw.trim().replace(symbol, "").replace(" ", "");
        if (groupingSeparator != '\0') {
            cleaned = cleaned.replace(String.valueOf(groupingSeparator), "");
        }
        cleaned = cleaned.replace(decimalSeparator, '.');
        try {
            return new BigDecimal(cleaned).setScale(fractionDigits, RoundingMode.HALF_UP).doubleValue();
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    double parseQuick(String raw, double fallback) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return fallback;
        }
        try {
            return new BigDecimal(digits).movePointLeft(fractionDigits).doubleValue();
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    String displayName() {
        return format(1234.56);
    }
}
