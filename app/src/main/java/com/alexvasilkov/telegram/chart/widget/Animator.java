package com.alexvasilkov.telegram.chart.widget;

import android.view.View;

class Animator {

    private static final long FRAME_TIME = 10L;

    private final View view;
    private final StepListener listener;

    private final Runnable updateAction = this::update;

    Animator(View view, StepListener listener) {
        this.view = view;
        this.listener = listener;
    }

    void start() {
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


    interface StepListener {
        boolean onStep();
    }

}
