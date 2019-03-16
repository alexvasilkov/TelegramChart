package com.alexvasilkov.telegram.chart.utils;

import android.os.SystemClock;

public class AnimationState {

    private static final float DURATION = 300f;

    private float targetState = Float.NaN;
    private float state = Float.NaN;
    private long startedAt = 0L;

    public void update() {
        if (isSet() && startedAt != 0L) {
            float animState = (SystemClock.elapsedRealtime() - startedAt) / DURATION;
            animState = animState < 0f ? 0f : (animState > 1f ? 1f : animState);

            state = targetState == 1f ? animState : 1f - animState;
        }
    }

    public float getState() {
        return state;
    }

    public void reset() {
        targetState = Float.NaN;
        state = Float.NaN;
        startedAt = 0L;
    }

    public boolean isSet() {
        return !Float.isNaN(targetState) && !Float.isNaN(state);
    }

    public void setTo(float target) {
        // Setting initial value without animation
        targetState = target;
        state = target;
        startedAt = 0L;
    }

    public void animateTo(float target) {
        if (isSet() && targetState != target) {
            // Starting reversed animation if needed
            targetState = target;

            if (state == target) {
                startedAt = 0L; // No animation needed
            } else {
                float animState = target == 1f ? state : 1f - state;
                startedAt = SystemClock.elapsedRealtime() - (long) (DURATION * animState);
            }
        }
    }

    public boolean isFinished() {
        return !isSet() || targetState == state;
    }

}
