package com.alexvasilkov.telegram.chart.data;

import android.graphics.Color;

import com.alexvasilkov.telegram.chart.domain.Chart;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class ChartParser {

    private ChartParser() {} // No instances

    static List<Chart> parseList(String json) throws JSONException {
        final JSONArray array = new JSONArray(json);
        final List<Chart> charts = new ArrayList<>();
        for (int i = 0, size = array.length(); i < size; i++) {
            charts.add(parse(array.getJSONObject(i)));
        }
        return charts;
    }

    private static Chart parse(JSONObject object) throws JSONException {
        final Object[][] columns = toArrayOfArrays(object.getJSONArray("columns"));
        final Map<String, String> types = toMap(object.getJSONObject("types"));
        final Map<String, String> names = toMap(object.getJSONObject("names"));
        final Map<String, String> colors = toMap(object.getJSONObject("colors"));

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


    private static Map<String, String> toMap(JSONObject object) throws JSONException {
        Map<String, String> map = new HashMap<>();
        for (Iterator<String> iterator = object.keys(); iterator.hasNext(); ) {
            String key = iterator.next();
            map.put(key, object.getString(key));

        }
        return map;
    }

    private static Object[][] toArrayOfArrays(JSONArray array) throws JSONException {
        Object[][] result = new Object[array.length()][];
        for (int i = 0, size = result.length; i < size; i++) {
            result[i] = toArray(array.getJSONArray(i));
        }
        return result;
    }

    private static Object[] toArray(JSONArray array) throws JSONException {
        Object[] result = new Object[array.length()];
        for (int i = 0, size = result.length; i < size; i++) {
            result[i] = array.get(i);
        }
        return result;
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
            result[i - 1] = (long) values[i];
        }
        return result;
    }

    private static int[] getIntValues(Object[] values) {
        int[] result = new int[values.length - 1];
        for (int i = 1, size = values.length; i < size; i++) {
            result[i - 1] = (int) values[i];
        }
        return result;
    }


    private static void checkNotNull(Object value) {
        if (value == null) {
            throw new NullPointerException("Value is not expected to be null");
        }
    }

}
