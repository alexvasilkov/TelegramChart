package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.ColorUtils;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.Arrays;

public abstract class Painter {

    final Chart chart;
    final float[] sourcesScales;

    private boolean darken;

    Painter(Chart chart) {
        this.chart = chart;

        sourcesScales = new float[chart.sources.length];
        Arrays.fill(sourcesScales, 1f);
    }

    public void applyStyle(ChartStyle style) {
        this.darken = style.darken;
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
            int selectedPos,
            boolean simplified
    );


    public boolean isAnimating() {
        return false;
    }


    public boolean allowXSelection() {
        return true;
    }

    public int pickSource(Rect chartPos, float posX, float posY) {
        return -1;
    }

    public void setSelectedSource(int selected) {}


    public boolean hasIndependentSources() {
        for (float scale : sourcesScales) {
            if (scale != 1f) {
                return true;
            }
        }
        return false;
    }

    public float[] getSourcesScales() {
        return sourcesScales;
    }

    public int getSourcePopupGravity(int sourceInd) {
        return 0;
    }


    int getSourceColor(int index) {
        final int color = chart.sources[index].color;
        return darken ? ColorUtils.darken(color) : color;
    }

    static int toAlpha(float alpha) {
        return Math.round(255 * alpha);
    }


    public static Painter create(Chart chart) {
        switch (chart.type) {
            case LINES:
                return new LinesPainter(chart);
            case LINES_INDEPENDENT:
                return new LinesPainter(chart, true);
            case BARS:
                return new BarsPainter(chart);
            case AREA:
                return new AreaPainter(chart, false);
            case AREA_SQUARE:
                return new AreaPainter(chart, true);
            case PIE:
                return new PiePainter(chart);
            default:
                return new LinesPainter(chart); // Fallback to line painter
        }
    }

}
