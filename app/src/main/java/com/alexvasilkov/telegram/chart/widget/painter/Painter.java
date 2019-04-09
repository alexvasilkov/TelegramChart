package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Matrix;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

public abstract class Painter {

    final Chart chart;

    Painter(Chart chart) {
        this.chart = chart;
    }


    public abstract void calculateYRange(
            Range yRange,
            int from,
            int to,
            boolean[] sourcesStates
    );

    public abstract void draw(
            Canvas canvas,
            Matrix matrix,
            int from,
            int to,
            float[] sourcesStates,
            int selected,
            boolean simplified
    );


    static int toAlpha(float alpha) {
        return Math.round(255 * alpha);
    }


    public static Painter create(Chart chart, ChartStyle style) {
        switch (chart.type) {
            case LINES:
                return new LinesPainter(chart, style);
            case BARS:
                return new BarsPainter(chart);
            default:
                return new LinesPainter(chart, style); // Fallback to line painter
        }
    }

}
