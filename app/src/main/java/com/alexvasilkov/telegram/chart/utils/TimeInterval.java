package com.alexvasilkov.telegram.chart.utils;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public enum TimeInterval {

    DAY_BY_HOURS(TimeUnit.HOURS.toMillis(1), 24), // 24, hours
    WEEK_BY_DAYS(TimeUnit.DAYS.toMillis(1), 7), // 7, days
    MONTH_BY_DAYS(TimeUnit.DAYS.toMillis(1), 30); // 30, days

    private final long step;
    private final long duration;
    public final int steps;

    TimeInterval(long step, int count) {
        this.step = step;
        this.duration = step * count;
        this.steps = count;
    }

    public boolean isStart(Calendar cal, long time) {
        cal.setTimeInMillis(time);

        switch (this) {
            case DAY_BY_HOURS:
                return cal.get(Calendar.HOUR_OF_DAY) == 0;
            case WEEK_BY_DAYS:
                return cal.get(Calendar.DAY_OF_WEEK) == cal.getFirstDayOfWeek();
            case MONTH_BY_DAYS:
                return cal.get(Calendar.DAY_OF_MONTH) == 1;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }
    }

    public int count(long from, long to) {
        return (int) Math.round((to - from) / (double) duration);
    }

    public long add(Calendar cal, long time, int count, int direction) {
        cal.setTimeInMillis(time);

        switch (this) {
            case DAY_BY_HOURS:
                cal.add(Calendar.DAY_OF_MONTH, count * direction);
                break;
            case WEEK_BY_DAYS:
                cal.add(Calendar.WEEK_OF_YEAR, count * direction);
                break;
            case MONTH_BY_DAYS:
                cal.add(Calendar.MONTH, count * direction);
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }

        return cal.getTimeInMillis();
    }

    public long getStart(Calendar cal, long time, int direction) {
        if (isStart(cal, time)) {
            return time;
        }

        cal.setTimeInMillis(time);

        switch (this) {
            case DAY_BY_HOURS:
                cal.set(Calendar.HOUR_OF_DAY, 0);
                if (direction > 0) {
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }
                break;
            case WEEK_BY_DAYS:
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                if (direction > 0) {
                    cal.add(Calendar.WEEK_OF_YEAR, 1);
                }
                break;
            case MONTH_BY_DAYS:
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

    public float distance(long from, long to) {
        return ((to - from) / (float) step);
    }

}
