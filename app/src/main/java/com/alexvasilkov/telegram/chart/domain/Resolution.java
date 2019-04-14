package com.alexvasilkov.telegram.chart.domain;

import java.util.concurrent.TimeUnit;

public enum Resolution {
    DAY(TimeUnit.DAYS.toMillis(1L)),
    HOUR(TimeUnit.HOURS.toMillis(1L)),
    FIVE_MIN(TimeUnit.MINUTES.toMillis(5L));

    public final long duration;

    Resolution(long duration) {
        this.duration = duration;
    }

    public float distance(long from, long to) {
        return ((to - from) / (float) duration);
    }

}