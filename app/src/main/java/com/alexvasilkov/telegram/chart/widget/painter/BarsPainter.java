package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.ColorUtils;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.Arrays;

class BarsPainter extends Painter {

    private static final Interpolator INTERPOLATOR = new AccelerateInterpolator(0.75f);

    private final Paint barPaint = new Paint(); // No anti-aliasing is needed
    private int selectionMask;

    private float[] pathsPoints;
    private float[] pathsPointsTransformed;
    private float[] sums;

    BarsPainter(Chart chart) {
        super(chart);

        final int points = chart.x.length;
        pathsPoints = new float[4 * points];
        pathsPointsTransformed = new float[4 * points];
        sums = new float[points];


        barPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void applyStyle(ChartStyle style) {
        super.applyStyle(style);

        selectionMask = style.selectionMask;
    }

    @Override
    public void calculateYRange(Range yRange, int from, int to, boolean[] sourcesStates) {
        // Calculating min and max Y values sums across all visible sources

        final int sourcesCount = chart.sources.length;

        final int minY = 0; // Always starting from 0
        int maxY = Integer.MIN_VALUE;

        for (int i = from; i <= to; i++) {
            int sum = 0;

            for (int s = 0; s < sourcesCount; s++) {
                if (sourcesStates[s]) {
                    sum += chart.sources[s].y[i];
                }
            }

            maxY = maxY < sum ? sum : maxY;
        }

        if (maxY <= minY) {
            maxY = minY + 1;
        }

        yRange.set(minY, maxY);
    }

    @Override
    public void draw(
            Canvas canvas,
            Rect chartPos,
            Matrix matrix,
            int from,
            int to,
            float[] sourcesStates,
            int selectedPos,
            boolean simplified
    ) {

        final float barWidth = Math.abs(ChartMath.mapX(matrix, 1f) - ChartMath.mapX(matrix, 0f));
        barPaint.setStrokeWidth(barWidth + 1.1f); // Dealing with rounding issues

        final float[] points = pathsPoints;
        final float[] pointsTrans = pathsPointsTransformed;
        Arrays.fill(sums, 0f);

        for (int s = 0, size = chart.sources.length; s < size; s++) {
            final float state = sourcesStates[s];
            final Source source = chart.sources[s];
            if (state == 0f) {
                continue; // Ignoring invisible sources
            }

            // Bars state should change a bit faster for nicer animations
            final float stateAdjusted = INTERPOLATOR.getInterpolation(state);

            for (int i = from; i <= to; i++) {
                points[4 * i] = i;
                points[4 * i + 1] = sums[i];
                points[4 * i + 2] = i;
                points[4 * i + 3] = sums[i] + source.y[i] * stateAdjusted;

                sums[i] += source.y[i] * stateAdjusted;
            }

            // Bars should be dimmed down if particular bar is selected
            final int color = getSourceColor(s);
            barPaint.setColor(selectedPos == -1 ? color : ColorUtils.overlay(color, selectionMask));

            final int offset = 4 * from;
            final int count = 2 * (to - from + 1);

            matrix.mapPoints(pointsTrans, offset, points, offset, count);

            canvas.drawLines(pointsTrans, offset, 2 * count, barPaint);

            // Drawing full-color bar, if selected
            if (from <= selectedPos && selectedPos <= to) {
                barPaint.setColor(color);
                canvas.drawLine(
                        pointsTrans[4 * selectedPos],
                        pointsTrans[4 * selectedPos + 1],
                        pointsTrans[4 * selectedPos + 2],
                        pointsTrans[4 * selectedPos + 3],
                        barPaint
                );
            }
        }
    }

}
