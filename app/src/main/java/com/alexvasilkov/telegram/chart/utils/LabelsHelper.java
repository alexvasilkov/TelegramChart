package com.alexvasilkov.telegram.chart.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class LabelsHelper {

    private float maxIntervals;

    public void init(float maxIntervals) {
        this.maxIntervals = maxIntervals;
    }

    public float getMaxIntervals() {
        return maxIntervals;
    }

    public int getFitIntervals(float size) {
        // Computing number of whole intervals fitting into single screen
        return (int) Math.floor((size - 1) / Math.ceil((size - 1) / maxIntervals));
    }

    public float computeLevel(float size) {
        return (size - 1f) / maxIntervals;
    }

    /**
     * Returns array of evenly distributed levels for each label.
     */
    public float[] computeLabelsLevels(int size) {
        return computeLevels(size, getFitIntervals(size));
    }

    public float[] computeLabelsLevels(final long fromDate, final long toDate) {
        // Computing total days count
        final int size = countDays(fromDate, toDate) + 1;

        final Calendar cal = Calendar.getInstance();

        // Computing beginning of the first month
        cal.setTimeInMillis(fromDate);
        cal.add(Calendar.DAY_OF_MONTH, -1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        final long fromDateExt = cal.getTimeInMillis();

        cal.setTimeInMillis(toDate);
        cal.add(Calendar.MONTH, 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        final long toDateExt = cal.getTimeInMillis();

        final int sizeExt = countDays(fromDateExt, toDateExt) + 1;

        final List<Integer> monthsPos = new ArrayList<>();

        for (int i = 0; i < sizeExt; i++) {
            cal.setTimeInMillis(fromDateExt);
            cal.add(Calendar.DAY_OF_MONTH, i);

            if (cal.get(Calendar.DAY_OF_MONTH) == 1) {
                monthsPos.add(i);
            }
        }

        final int monthsSize = monthsPos.size();
        final int monthSizeMin = monthsSize - 2;

        final int monthsIntervals = getFitIntervals(monthSizeMin);
        final float[] monthsLevelsTmp = computeLevels(monthSizeMin, monthsIntervals);
        final float[] monthsLevels = new float[monthsSize];
        System.arraycopy(monthsLevelsTmp, 0, monthsLevels, 1, monthSizeMin);
        monthsLevels[0] = 1f;
        monthsLevels[monthsSize - 1] = 1f;


        final float maxMonths = size / 30f;
        final float extraFit = maxMonths < maxIntervals ? maxIntervals / maxMonths : 1f;
        final float monthsLevelsMultiplier = computeLevel(size) / monthsLevels[1] * extraFit;

        final float[] levels = new float[sizeExt];

        for (int i = 0; i < monthsSize; i++) {
            int pos = monthsPos.get(i);
            int shifted = (monthsSize - 1 - i) % monthsSize;
            levels[pos] = monthsLevels[shifted] * monthsLevelsMultiplier;

            if (i != 0) {
                int prevPos = monthsPos.get(i - 1);
                float level = Math.min(levels[prevPos], levels[pos]);
                fillLevelsInHalves(levels, level, prevPos, pos);
            }
        }

        final float[] result = new float[size];
        final int daysOffset = countDays(fromDateExt, fromDate);
        System.arraycopy(levels, daysOffset, result, 0, size);
        return result;
    }

    private static float[] computeLevels(int size, int intervals) {
        final float[] levels = new float[size];

        // Computing actual number of steps per interval (can be bigger than min steps above)
        final int stepsPerInterval = (size - 1) / intervals;

        // Computing number of intervals that should hold extra step to span entire size
        final int intervalsWithExtra = (size - 1) % intervals;

        // Dividing first level evenly into intervals and then fill each interval
        // by recursively dividing it into 2 sub-intervals
        int prevPos = -1;

        for (int i = 0; i <= intervals; i++) {
            int pos = i * stepsPerInterval;

            // Adding extra step to last intervals to have a correct total distribution
            pos += Math.max(intervalsWithExtra - intervals + i, 0);
            levels[pos] = stepsPerInterval;

            // Setting up values in-between
            if (prevPos != -1) {
                fillLevelsInHalves(levels, stepsPerInterval, prevPos, pos);
            }
            prevPos = pos;
        }

        return levels;
    }

    private static void fillLevelsInHalves(float[] levels, float prevLevel, int from, int to) {
        final float level = 0.5f * prevLevel;

        if (to - from <= 3 || level <= 1f) {
            for (int i = from + 1; i < to; i++) {
                levels[i] = 1f;
            }
        } else {
            final int mid = (to + from) / 2;
            levels[mid] = level; // Can't be less than 1
            fillLevelsInHalves(levels, level, from, mid);
            fillLevelsInHalves(levels, level, mid, to);
        }
    }


    private static int countDays(long from, long to) {
        return (int) Math.round((to - from) / (24 * 60 * 60 * 1000.0));
    }

}
