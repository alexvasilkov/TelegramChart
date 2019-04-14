package com.alexvasilkov.telegram.chart.domain;

import java.util.Calendar;
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

    public long add(Calendar cal, long time, int amount) {
        cal.setTimeInMillis(time);

        switch (this) {
            case DAY:
                cal.add(Calendar.DAY_OF_MONTH, amount);
                break;
            case HOUR:
                cal.add(Calendar.HOUR_OF_DAY, amount);
                break;
            case FIVE_MIN:
                cal.add(Calendar.MINUTE, 5 * amount);
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }

        return cal.getTimeInMillis();
    }

}
