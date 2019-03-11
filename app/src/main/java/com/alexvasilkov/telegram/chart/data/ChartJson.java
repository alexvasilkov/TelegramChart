package com.alexvasilkov.telegram.chart.data;

import android.graphics.Color;

import com.alexvasilkov.telegram.chart.domain.Chart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Gson will assign all the local fields for us
@SuppressWarnings("unused")
class ChartJson {

    private Object[][] columns;
    private Map<String, String> types;
    private Map<String, String> names;
    private Map<String, String> colors;


    Chart convert() {
        checkNotNull(columns);
        checkNotNull(types);
        checkNotNull(names);
        checkNotNull(colors);

        String xName = getKeysForValue(types, "x").get(0);
        List<String> yNames = getKeysForValue(types, "line");

        long[] xValues = null;
        for (Object[] values : columns) {
            if (xName.equals(values[0])) {
                xValues = getLongValues(values);
            }
        }
        checkNotNull(xValues);

        List<Chart.Line> lines = new ArrayList<>();

        for (String yName : yNames) {
            int[] yValues = null;
            for (Object[] values : columns) {
                if (yName.equals(values[0])) {
                    yValues = getIntValues(values);
                }
            }
            checkNotNull(yValues);

            String name = names.get(yName);
            checkNotNull(name);

            int color = Color.parseColor(colors.get(yName));

            lines.add(new Chart.Line(name, color, yValues));
        }

        return new Chart(xValues, lines);
    }


    private static List<String> getKeysForValue(Map<String, String> map, String value) {
        ArrayList<String> keys = new ArrayList<>();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                keys.add(entry.getKey());
            }
        }

        return keys;
    }

    private static long[] getLongValues(Object[] values) {
        long[] result = new long[values.length - 1];
        for (int i = 1, size = values.length; i < size; i++) {
            result[i - 1] = ((Double) values[i]).longValue();
        }
        return result;
    }

    private static int[] getIntValues(Object[] values) {
        int[] result = new int[values.length - 1];
        for (int i = 1, size = values.length; i < size; i++) {
            result[i - 1] = ((Double) values[i]).intValue();
        }
        return result;
    }

    private static void checkNotNull(Object value) {
        if (value == null) {
            throw new NullPointerException("Value is not expected to be null");
        }
    }

}
