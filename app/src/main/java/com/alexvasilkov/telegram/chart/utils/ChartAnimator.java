package com.alexvasilkov.telegram.chart.utils;

import android.view.View;

public class ChartAnimator {

    private static final long FRAME_TIME = 10L;

    private final View view;
    private final StepListener listener;

    private final Runnable updateAction = this::update;

    public ChartAnimator(View view, StepListener listener) {
        this.view = view;
        this.listener = listener;
    }

    public void start() {
        scheduleNextStep();
    }

    private void update() {
        boolean continueAnimation = listener.onStep();
        view.invalidate();

        if (continueAnimation) {
            scheduleNextStep();
        }
    }

    private void scheduleNextStep() {
        view.removeCallbacks(updateAction);
        view.postOnAnimationDelayed(updateAction, FRAME_TIME);
    }


    public interface StepListener {
        boolean onStep();
    }

}
