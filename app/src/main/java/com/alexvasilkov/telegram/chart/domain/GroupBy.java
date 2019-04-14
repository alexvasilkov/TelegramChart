package com.alexvasilkov.telegram.chart.domain;


import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public enum GroupBy {
    DAY(TimeUnit.DAYS.toMillis(1L)),
    MONTH(TimeUnit.DAYS.toMillis(30L));

    private final long duration;

    GroupBy(long duration) {
        this.duration = duration;
    }

    public int stepsCount(Resolution resolution) {
        return Math.round(duration / (float) resolution.duration);
    }

    public float distance(long from, long to) {
        return ((to - from) / (float) duration);
    }

    public boolean isStart(Calendar cal, long time) {
        cal.setTimeInMillis(time);

        switch (this) {
            case DAY:
                return cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0;
            case MONTH:
                return cal.get(Calendar.DAY_OF_MONTH) == 1;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }
    }

    public long getClosestStart(Calendar cal, long time, int direction) {
        if (isStart(cal, time)) {
            return time;
        }

        cal.setTimeInMillis(time);

        switch (this) {
            case DAY:
                cal.set(Calendar.HOUR_OF_DAY, 0);
                if (direction > 0) {
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }
                break;
            case MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                if (direction > 0) {
                    cal.add(Calendar.MONTH, 1);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }

        return cal.getTimeInMillis();
    }

    public long add(Calendar cal, long time, int amount) {
        cal.setTimeInMillis(time);

        switch (this) {
            case DAY:
                cal.add(Calendar.DAY_OF_MONTH, amount);
                break;
            case MONTH:
                cal.add(Calendar.MONTH, amount);
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }

        return cal.getTimeInMillis();
    }


}
