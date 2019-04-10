package com.alexvasilkov.telegram.chart.data;

import android.graphics.Color;
import android.os.Build;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ChartParser {

    private ChartParser() {} // No instances

    static Chart parse(int id, String json) throws JSONException {
        final JSONObject object = new JSONObject(json);
        final Object[][] columns = toArrayOfArrays(object.getJSONArray("columns"));
        final Map<String, String> types = toMap(object.getJSONObject("types"));
        final Map<String, String> names = toMap(object.getJSONObject("names"));
        final Map<String, String> colors = toMap(object.getJSONObject("colors"));
        final boolean yScaled = object.optBoolean("y_scaled", false);

        checkNotNull(columns);
        checkNotNull(types);
        checkNotNull(names);
        checkNotNull(colors);

        String xName = getKeysForValue(types, "x").get(0);
        String type = getFirstValue(types, "x");
        if (type == null) {
            throw new NullPointerException();
        }

        List<String> yNames = getKeysForValue(types, type);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            Collections.sort(yNames); // Old versions does not preserve keys order
        }

        long[] xValues = null;
        for (Object[] values : columns) {
            if (xName.equals(values[0])) {
                xValues = getLongValues(values);
            }
        }
        checkNotNull(xValues);

        List<Source> sources = new ArrayList<>();

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

            sources.add(new Source(name, color, yValues));
        }

        return new Chart(id, parseType(type, yScaled), xValues, sources);
    }

    private static Chart.Type parseType(String type, boolean yScaled) {
        switch (type) {
            case "line":
                return yScaled ? Chart.Type.TWO_LINES : Chart.Type.LINES;
            case "bar":
                return Chart.Type.BARS;
            case "area":
                return Chart.Type.AREA;
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }


    private static Map<String, String> toMap(JSONObject object) throws JSONException {
        Map<String, String> map = new LinkedHashMap<>();
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

    @SuppressWarnings("SameParameterValue")
    private static String getFirstValue(Map<String, String> map, String exclude) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!exclude.equals(entry.getValue())) {
                return entry.getValue();
            }
        }

        return null;
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
            result[i - 1] = ((Number) values[i]).longValue();
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
