package com.alexvasilkov.telegram.chart.utils;

import android.view.View;

public class ChartAnimator {

    private static final boolean DEBUG_FPS = false;

    private static final long FRAME_TIME = 10L;

    private final View view;
    private final StepListener listener;
    private final Fps fps = DEBUG_FPS ? new Fps() : null;

    private final Runnable updateAction = this::update;

    public ChartAnimator(View view, StepListener listener) {
        this.view = view;
        this.listener = listener;
    }

    public void start() {
        if (fps != null) {
            fps.start();
        }

        scheduleNextStep();
    }

    public void stop() {
        scheduleNextStep();
    }

    private void update() {
        boolean continueAnimation = listener.onStep();
        view.invalidate();

        if (fps != null) {
            fps.step();
            if (!continueAnimation) {
                fps.stop();
            }
        }

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
