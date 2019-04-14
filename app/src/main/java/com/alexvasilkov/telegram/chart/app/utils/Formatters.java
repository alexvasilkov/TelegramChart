package com.alexvasilkov.telegram.chart.app.utils;

import android.content.Context;
import android.text.format.DateUtils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Formatter;
import java.util.Locale;

public class Formatters {

    private static final String timeZone = "UTC";

    private final Context context;
    private final DecimalFormat yFormat;

    public Formatters(Context context) {
        this.context = context;

        yFormat = (DecimalFormat) NumberFormat.getIntegerInstance();
        final DecimalFormatSymbols symbols = yFormat.getDecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        yFormat.setDecimalFormatSymbols(symbols);
    }


    public String formatNumber(long value) {
        return yFormat.format(value);
    }

    public String formatNumberAbbreviate(int value, long max) {
        if (value == 0) {
            return "0";
        } else if (max >= 10_000_000) {
            return String.format(Locale.US, "%.0fM", value / 1_000_000f);
        } else if (max >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000f);
        } else if (max >= 10_000) {
            return String.format(Locale.US, "%.0fK", value / 1_000f);
        } else if (max >= 1_000) {
            return String.format(Locale.US, "%.1fK", value / 1_000f);
        } else {
            return String.valueOf(value);
        }
    }

    public String formatTime(long timestamp) {
        final int flags = DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_ABBREV_TIME
                | DateUtils.FORMAT_NO_MIDNIGHT
                | DateUtils.FORMAT_NO_NOON;

        return format(timestamp, flags);
    }

    public String formatDateShort(long timestamp) {
        final int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_NO_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH;

        return format(timestamp, flags);
    }

    public String formatDateLong(long timestamp) {
        final int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH
                | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_ABBREV_WEEKDAY;

        return format(timestamp, flags);
    }

    public String formatRangeLong(long from, long to) {
        final int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH;

        final String fromStr = format(from, flags);
        final String toStr = format(to, flags);
        return fromStr.equals(toStr) ? fromStr : fromStr + " – " + toStr;
    }


    private String format(long time, int flags) {
        Formatter f = new Formatter(new StringBuilder(50), Locale.getDefault());
        return DateUtils.formatDateRange(context, f, time, time, flags, timeZone).toString();
    }

}
