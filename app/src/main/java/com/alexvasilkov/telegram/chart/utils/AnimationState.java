package com.alexvasilkov.telegram.chart.utils;

import android.os.SystemClock;

public class AnimationState {

    private static final float DURATION = 300f;

    private final long startedAt;

    public AnimationState() {
        this(0f);
    }

    public AnimationState(float state) {
        startedAt = SystemClock.elapsedRealtime() - (long) (DURATION * state);
    }

    public float getState() {
        float state = (SystemClock.elapsedRealtime() - startedAt) / DURATION;
        return state < 0f ? 0f : (state > 1f ? 1f : state);
    }

}
