package com.alexvasilkov.telegram.chart.utils;

public class Range {

    public float from = 0f;
    public float to = -1f;

    public void set(Range range) {
        this.from = range.from;
        this.to = range.to;
    }

    public void set(float from, float to) {
        this.from = from;
        this.to = to;
    }

    public void reset() {
        this.from = 0f;
        this.to = -1f;
    }

    public float fit(float value) {
        return value < from ? from : (value > to ? to : value);
    }

    public void interpolate(Range start, Range end, float state) {
        this.from = start.from + (end.from - start.from) * state;
        this.to = start.to + (end.to - start.to) * state;
    }

    public float size() {
        return to - from + 1f;
    }

}
