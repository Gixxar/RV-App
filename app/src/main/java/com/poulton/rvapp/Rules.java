package com.poulton.rvapp;

import java.time.LocalDate;

/** Pure rules kept separate from the Android UI. */
public final class Rules {
    private Rules() {}
    public static String validate(String field, String value) {
        if (value.isEmpty()) return null;
        if (field.toLowerCase(java.util.Locale.ROOT).contains("date")) {
            try { LocalDate.parse(value); } catch (RuntimeException e) { return "Use a valid date: YYYY-MM-DD"; }
        }
        if (isNumber(field)) {
            try { double n = Double.parseDouble(value); if (!Double.isFinite(n) || n < 0) return "Enter a positive number or zero"; }
            catch (NumberFormatException e) { return "Enter a number using a decimal point"; }
        }
        return null;
    }
    public static boolean isNumber(String field) { return field.contains("miles") || field.equals("Litres") || field.equals("Cost GBP"); }
    public static boolean due(String date, String miles, double current, LocalDate today) {
        return (!date.isEmpty() && !LocalDate.parse(date).isAfter(today)) || (!miles.isEmpty() && Double.parseDouble(miles) <= current);
    }
}
