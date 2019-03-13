package com.alexvasilkov.telegram.chart.widget;

class FloatRange {

    float from;
    float to;

    void set(FloatRange range) {
        this.from = range.from;
        this.to = range.to;
    }

    void set(float from, float to) {
        this.from = from;
        this.to = to;
    }

    float fit(float value) {
        return value < from ? from : (value > to ? to : value);
    }

    void interpolate(FloatRange start, FloatRange end, float state) {
        this.from = start.from + (end.from - start.from) * state;
        this.to = start.to + (end.to - start.to) * state;
    }

    float size() {
        return to - from + 1f;
    }

}
