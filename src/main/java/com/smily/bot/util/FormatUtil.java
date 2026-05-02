package com.smily.bot.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class FormatUtil {
    private static final DecimalFormat ONE_DECIMAL;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        ONE_DECIMAL = new DecimalFormat("0.0", symbols);
    }

    private FormatUtil() {
    }

    public static String kg(double value) {
        return ONE_DECIMAL.format(value).replace('.', ',');
    }

    public static String safeName(String firstName, String username) {
        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }
        if (username != null && !username.isBlank()) {
            return "@" + username;
        }
        return "Игрок";
    }
}
