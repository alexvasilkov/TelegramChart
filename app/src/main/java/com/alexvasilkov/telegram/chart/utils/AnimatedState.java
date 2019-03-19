package com.alexvasilkov.telegram.chart.utils;

import android.os.SystemClock;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

public class AnimatedState {

    private static final Interpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();

    private static final float DURATION = 300f;

    private float targetState = Float.NaN;
    private float state = Float.NaN;
    private long startedAt = 0L;

    public void update(long now) {
        if (isSet() && startedAt != 0L) {
            float animState = (now - startedAt) / DURATION;
            animState = animState < 0f ? 0f : (animState > 1f ? 1f : animState);

            animState = INTERPOLATOR.getInterpolation(animState);

            state = targetState == 1f ? animState : 1f - animState;
        }
    }

    public float get() {
        return state;
    }

    public float getTarget() {
        return targetState;
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
        animateTo(target, now());
    }

    public void animateTo(float target, long now) {
        if (isSet() && targetState != target) {
            // Starting animation from current to target state
            targetState = target;

            if (state == target) {
                startedAt = 0L; // No animation needed
            } else {
                float animState = target == 1f ? state : 1f - state;
                startedAt = now - (long) (DURATION * animState);
            }
        }
    }

    public void reset() {
        targetState = Float.NaN;
        state = Float.NaN;
        startedAt = 0L;
    }

    public boolean isFinished() {
        return !isSet() || targetState == state;
    }

    public static long now() {
        return SystemClock.elapsedRealtime();
    }

}
