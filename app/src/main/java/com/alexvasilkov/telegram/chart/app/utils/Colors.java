package com.alexvasilkov.telegram.chart.app.utils;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class Colors {

    private static final float[] hsv = new float[3];


    public final boolean isDark;

    public final int statusBar;
    public final int actionBar;
    public final int navBar;

    public final int window;
    public final int background;

    public final int text;
    public final int textHighlight;

    public final int chartGuides;
    public final int chartLabels;
    public final int chartSelectionMask;

    public final int finderForeground;
    public final int finderFrame;

    public final int popup;


    public enum Type {
        DAY(""), NIGHT("_night");

        final String suffix;

        Type(String suffix) {
            this.suffix = suffix;
        }
    }

    public Colors(Context context, Type type) {
        isDark = type == Type.NIGHT;

        statusBar = color(context, type, "status_bar");
        actionBar = color(context, type, "action_bar");
        navBar = color(context, type, "nav_bar");

        window = color(context, type, "window");
        background = color(context, type, "background");

        text = color(context, type, "text");
        textHighlight = color(context, type, "text_highlight");

        chartGuides = color(context, type, "chart_guides");
        chartLabels = color(context, type, "chart_labels");
        chartSelectionMask = color(context, type, "chart_selection_mask");

        finderForeground = color(context, type, "finder_foreground");
        finderFrame = color(context, type, "finder_frame");

        popup = color(context, type, "popup");
    }


    private static int color(Context context, Type type, String name) {
        final Resources res = context.getResources();
        final String packageName = context.getApplicationInfo().packageName;
        final int resId = res.getIdentifier(name + type.suffix, "color", packageName);
        if (resId == 0) {
            throw new IllegalArgumentException("No color found for name " + (name + type.suffix));
        } else {
            return res.getColor(resId);
        }
    }


    public static void setStatusBarColor(Window window, int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            if (Build.VERSION.SDK_INT >= 23) {
                window.setStatusBarColor(color);

                View decor = window.getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (Colors.isBright(color)) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decor.setSystemUiVisibility(flags);
            } else {
                if (isBright(color)) {
                    color = Colors.addBrightness(color, -0.25f);
                }
                window.setStatusBarColor(color);
            }
        }
    }

    public static void setNavBarColor(Window window, int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            if (Build.VERSION.SDK_INT >= 27) {
                window.setNavigationBarColor(color);

                View decor = window.getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (Colors.isBright(color)) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decor.setSystemUiVisibility(flags);
            } else {
                if (isBright(color)) {
                    color = Colors.addBrightness(color, -0.5f);
                }
                window.setNavigationBarColor(color);
            }
        }
    }

    public static void setActionBarColors(ActionBar actionBar, int background, int textColor) {
        if (actionBar == null) {
            return;
        }

        actionBar.setBackgroundDrawable(new ColorDrawable(background));

        Spannable title = new SpannableString(actionBar.getTitle().toString());
        title.setSpan(new ForegroundColorSpan(textColor), 0, title.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        actionBar.setTitle(title);
    }

    public static void setWindowBackground(Window window, int color) {
        window.setBackgroundDrawable(new ColorDrawable(color));
    }


    private static int addBrightness(int color, float amount) {
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, Math.min(hsv[2] + amount, 1f));
        return Color.HSVToColor(hsv);
    }

    private static boolean isBright(int color) {
        float luminance = (0.299f * Color.red(color) +
                0.587f * Color.green(color) +
                0.114f * Color.blue(color)) / 255f;
        return luminance > 0.5f;
    }

}
