package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.ColorUtils;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.Arrays;

public class BarsPainter extends Painter {

    private static final Interpolator INTERPOLATOR = new AccelerateInterpolator(0.75f);
    private static final float SELECTION_BRIGHTNESS_AMOUNT = 0.25f;

    private final Paint barPaint = new Paint(ChartStyle.PAINT_FLAGS);

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
    public void calculateYRange(Range yRange, int from, int to, boolean[] sourcesStates) {
        // Calculating min and max Y values sums across all visible sources

        final int sourcesCount = chart.sources.size();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int i = from; i <= to; i++) {
            int sum = 0;

            for (int s = 0; s < sourcesCount; s++) {
                if (sourcesStates[s]) {
                    sum += chart.sources.get(s).y[i];
                }
            }

            minY = minY > sum ? sum : minY;
            maxY = maxY < sum ? sum : maxY;
        }

        if (minY == Integer.MAX_VALUE) {
            minY = 0;
        }
        if (maxY <= minY) {
            maxY = minY + 1;
        }

        yRange.set(minY, maxY);
    }

    @Override
    public void draw(
            Canvas canvas,
            Matrix matrix,
            int from,
            int to,
            float[] sourcesStates,
            int selected,
            boolean simplified
    ) {

        final float barWidth = Math.abs(ChartMath.mapX(matrix, 1f) - ChartMath.mapX(matrix, 0f));
        barPaint.setStrokeWidth((int) Math.ceil(barWidth + 0.5f)); // Dealing with rounding issues

        final float[] points = pathsPoints;
        final float[] pointsTrans = pathsPointsTransformed;
        Arrays.fill(sums, 0f);

        for (int s = 0, size = chart.sources.size(); s < size; s++) {
            final float state = sourcesStates[s];
            final Source source = chart.sources.get(s);
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
            barPaint.setColor(selected == -1 ? source.color
                    : ColorUtils.changeBrightness(source.color, SELECTION_BRIGHTNESS_AMOUNT));

            final int offset = 4 * from;
            final int count = 2 * (to - from + 1);

            matrix.mapPoints(pointsTrans, offset, points, offset, count);

            canvas.drawLines(pointsTrans, offset, 2 * count, barPaint);

            // Drawing full-color bar, if selected
            if (from <= selected && selected <= to) {
                barPaint.setColor(source.color);
                canvas.drawLine(
                        pointsTrans[4 * selected],
                        pointsTrans[4 * selected + 1],
                        pointsTrans[4 * selected + 2],
                        pointsTrans[4 * selected + 3],
                        barPaint
                );
            }
        }
    }

}
