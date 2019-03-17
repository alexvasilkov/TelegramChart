package com.alexvasilkov.telegram.chart;

import android.content.Context;
import android.content.SharedPreferences;

class Preferences {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private final SharedPreferences prefs;

    Preferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    boolean isInNightMode() {
        return prefs.getBoolean(KEY_NIGHT_MODE, false);
    }

    void setInNightMode(boolean isInNightMode) {
        prefs.edit().putBoolean(KEY_NIGHT_MODE, isInNightMode).apply();
    }

}
