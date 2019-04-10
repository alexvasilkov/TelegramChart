package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

public abstract class Painter {

    final Chart chart;

    Painter(Chart chart) {
        this.chart = chart;
    }

    /**
     * Stores desired Y values range into 'yRange'.
     */
    public abstract void calculateYRange(
            Range yRange,
            int from,
            int to,
            boolean[] sourcesStates
    );

    /**
     * Returns true if caller should use exact Y values range without modifications.
     *
     * @see #calculateYRange(Range, int, int, boolean[])
     */
    public boolean useExactRange() {
        return false;
    }

    public abstract void draw(
            Canvas canvas,
            Rect chartPos,
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
            case AREA:
                return new AreaPainter(chart, style);
            default:
                return new LinesPainter(chart, style); // Fallback to line painter
        }
    }

}
