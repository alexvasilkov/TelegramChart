package com.alexvasilkov.telegram.chart.utils;

import android.graphics.Color;

public class ColorUtils {

    private static final float[] hsv = new float[3];

    private ColorUtils() {}

    public static int changeBrightness(int color, float amount) {
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(hsv[1] - amount, 1f));
        hsv[2] = Math.max(0f, Math.min(hsv[2] + amount, 1f));
        return Color.HSVToColor(hsv);
    }

}
