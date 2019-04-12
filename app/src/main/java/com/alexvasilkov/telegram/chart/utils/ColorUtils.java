package com.alexvasilkov.telegram.chart.utils;

import android.graphics.Color;

public class ColorUtils {

    private static final float[] hsv = new float[3];

    private ColorUtils() {}

    public static int darken(int color) {
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, Math.min(hsv[2] - 0.2f, 1f));
        return Color.HSVToColor(hsv);
    }

    public static int overlay(int base, int overlay) {
        final float alpha = Color.alpha(overlay) / 255f;
        float r = Color.red(base) * (1f - alpha) + Color.red(overlay) * alpha;
        float g = Color.green(base) * (1f - alpha) + Color.green(overlay) * alpha;
        float b = Color.blue(base) * (1f - alpha) + Color.blue(overlay) * alpha;
        return Color.rgb((int) r, (int) g, (int) b);
    }

}
