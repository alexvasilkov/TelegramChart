package com.alexvasilkov.telegram.chart.utils;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.GroupBy;
import com.alexvasilkov.telegram.chart.domain.Resolution;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class LabelsHelper {

    private float maxIntervals;
    private int chartWidth;
    private int labelWidth;
    private float labelsPadding;

    private GroupBy groupBy;

    public void init(int width, int labelWidth, float padding) {
        this.chartWidth = width;
        this.labelWidth = labelWidth;
        this.labelsPadding = padding;

        // Computing maximum number of intervals that can possibly fit into single screen
        maxIntervals = (width - labelWidth) / (labelWidth + padding);
        // Assuming screen must fit at least 3 labels
        maxIntervals = Math.max(maxIntervals, 2f);
    }

    public void setGroupBy(GroupBy groupBy) {
        this.groupBy = groupBy;
    }

    public float computeLevel(float size) {
        return maxIntervals == 0f ? 1f : (size - 1f) / maxIntervals;
    }


    public float[] computeLabelsLevels(Chart chart) {
        final int size = chart.x.length;
        final long fromDate = chart.x[0];
        final long toDate = chart.x[size - 1];
        final Resolution resolution = chart.resolution;
        final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        // If grouping is not specified or chart range does not include at least 1 group
        // then we'll return an array of evenly distributed levels.
        if (groupBy == null || toDate < groupBy.add(cal, fromDate, 1)) {
            return computeEvenlyDistributedLevels(size);
        }

        // To distribute labels we'll extend current chart range so that it includes a whole
        // number of groups and with first and last points are beginning of a group.
        // First we'll distribute groups starting points evenly to fit the whole range.
        // And then we'll use extended range to distribute labels levels in the same way in all the
        // groups.
        // So overall on the first few levels we'll only have groups' starting points,
        // and all the lower levels we'll have a power-of-2 levels distribution.

        // Computing start of the first group, should be earlier than 'fromDate'
        final long fromDateExt = groupBy.getClosestStart(cal, groupBy.add(cal, fromDate, -1), 1);

        // Computing end of the last group, should be later than 'toDate'
        final long toDateExt = groupBy.getClosestStart(cal, groupBy.add(cal, toDate, 1), -1);

        final int sizeExt = Math.round(resolution.distance(fromDateExt, toDateExt)) + 1;

        final List<Integer> groupsStartPos = new ArrayList<>();

        for (int i = 0; i < sizeExt; i++) {
            final long time = resolution.add(cal, fromDateExt, i);
            if (groupBy.isStart(cal, time)) {
                groupsStartPos.add(i);
            }
        }

        final int groupsSizeExt = groupsStartPos.size();

        // We took extra groups starts in the start and in the end when computed
        // 'fromDateExt' and 'toDateExt', but they should not be considered.
        // Always > 0 since we already checked that we have at least 1 group (see above).
        final int groupsSize = groupsSizeExt - 2;

        // Evenly distributing groups starting points, as if there are no other chart points
        final float[] groupsLevels = computeEvenlyDistributedLevels(groupsSize);

        // For extended range we'll set first and last levels to 1 (minimum possible level)
        final float[] groupsLevelsExt = new float[groupsSizeExt];
        System.arraycopy(groupsLevels, 0, groupsLevelsExt, 1, groupsSize);
        groupsLevelsExt[0] = 1f;
        groupsLevelsExt[groupsSizeExt - 1] = 1f;


        // Baseline groups level is the level when we can see all the groups labels on the
        // minimum possible distance from each other (opposite to maxIntervals calculation)
        final float groupsCount = groupBy.distance(fromDate, toDate);
        final float baselineWidth = groupsCount * (labelWidth + labelsPadding) + labelWidth;
        final float levelsMultiplier = computeLevel(size) * chartWidth / baselineWidth;

        final float[] levelsExt = new float[sizeExt];

        for (int i = 0; i < groupsSizeExt; i++) {
            final int pos = groupsStartPos.get(i);
            final int shifted = (groupsSizeExt - 1 - i) % groupsSizeExt;
            levelsExt[pos] = groupsLevelsExt[shifted] * levelsMultiplier;

            if (i != 0) {
                final int prevPos = groupsStartPos.get(i - 1);
                final float level = Math.min(levelsExt[prevPos], levelsExt[pos]);
                fillLevelsInHalves(levelsExt, level, prevPos, pos);
            }
        }

        final float[] levels = new float[size];
        final int daysOffset = Math.round(resolution.distance(fromDateExt, fromDate));
        System.arraycopy(levelsExt, daysOffset, levels, 0, size);
        return levels;
    }


    private float[] computeEvenlyDistributedLevels(int size) {
        final float[] levels = new float[size];

        // Computing number of whole intervals fitting into single screen
        final int intervals = getFitInterval(size);

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

    // Computing number of whole intervals fitting into single screen
    private int getFitInterval(float size) {
        return (int) Math.floor((size - 1f) / Math.ceil((size - 1f) / maxIntervals));
    }

}
