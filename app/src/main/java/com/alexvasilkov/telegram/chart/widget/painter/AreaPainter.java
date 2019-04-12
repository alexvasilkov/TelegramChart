package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.Arrays;

class AreaPainter extends Painter {

    private static final float OPTIMIZATION_FACTOR = 2f;

    private final Paint pathPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private final Path pathFill = new Path();

    private final Paint selectionPaint = new Paint();

    private final Matrix matrixOptimized = new Matrix();

    private final float[] scales;
    private final float[] sums;

    AreaPainter(Chart chart) {
        super(chart);

        scales = new float[chart.x.length];
        sums = new float[chart.x.length];

        pathPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void applyStyle(ChartStyle style) {
        super.applyStyle(style);

        selectionPaint.setStrokeWidth(style.selectionWidth);
        selectionPaint.setColor(style.selectionColor);
    }


    @Override
    public void calculateYRange(Range yRange, int from, int to, boolean[] sourcesStates) {
        yRange.set(0f, 100f);
    }

    @Override
    public boolean useExactRange() {
        return true;
    }

    @Override
    public void draw(
            Canvas canvas,
            Rect chartPos,
            Matrix matrix,
            int from,
            int to,
            float[] sourcesStates,
            int selected,
            boolean simplified
    ) {

        canvas.save();

        matrixOptimized.set(matrix);
        pathPaint.setFlags(simplified ? 0 : ChartStyle.PAINT_FLAGS);

        if (simplified) {
            // Scaling down paths and scaling up the canvas instead, reducing drawing area.
            // This trick will help old Android versions where path rendering isn't HW accelerated.
            matrixOptimized.postScale(1f / OPTIMIZATION_FACTOR, 1f / OPTIMIZATION_FACTOR);
            canvas.scale(OPTIMIZATION_FACTOR, OPTIMIZATION_FACTOR);
        }

        drawAreas(canvas, matrixOptimized, from, to, sourcesStates);

        canvas.restore();


        // Drawing selected point line if withing visible range
        if (from <= selected && selected <= to) {
            float posX = ChartMath.mapX(matrix, selected);
            canvas.drawLine(posX, chartPos.top, posX, chartPos.bottom, selectionPaint);
        }
    }


    private void drawAreas(
            Canvas canvas,
            Matrix matrix,
            int from,
            int to,
            float[] sourcesStates
    ) {
        final int sourcesCount = chart.sources.length;

        float maxState = 0f;
        for (int i = 0; i < sourcesCount; i++) {
            maxState = maxState < sourcesStates[i] ? sourcesStates[i] : maxState;
        }

        if (maxState == 0f) {
            return; // Nothing to draw
        }

        // Computing Y values scales so that we always have sum = 100%
        final float total = 100f * maxState;

        for (int i = from; i <= to; i++) {
            float sum = 0f;

            for (int s = 0; s < sourcesCount; s++) {
                sum += chart.sources[s].y[i] * sourcesStates[s];
            }

            scales[i] = total / sum;
        }

        // Starting drawing from 100%, top to bottom
        Arrays.fill(sums, total);

        for (int s = sourcesCount - 1; s >= 0; s--) {
            final Source source = chart.sources[s];
            final float state = sourcesStates[s];

            if (state == 0f) {
                continue; // Ignoring invisible sources
            }

            pathFill.reset();

            if (s == sourcesCount - 1) {
                pathFill.moveTo(from, total);
                pathFill.lineTo(to, total);
            } else {
                // Adding next path
                for (int i = from; i <= to; i++) {
                    if (i == from) {
                        pathFill.moveTo(i, sums[i]);
                    } else {
                        pathFill.lineTo(i, sums[i]);
                    }
                }
            }

            // Calculating values for next path
            float minValue = total;
            for (int i = to; from <= i; i--) {
                sums[i] -= source.y[i] * state * scales[i];
                minValue = minValue > sums[i] ? sums[i] : minValue;
            }
            minValue = Math.max(0f, minValue - 10f); // Few more pixels on bottom to prevent issues

            // Using maximum possible value for bottom line to avoid much overdraws
            pathFill.lineTo(to, minValue);
            pathFill.lineTo(from, minValue);

            pathFill.close();

            pathFill.transform(matrix);

            pathPaint.setColor(getSourceColor(s));
            canvas.drawPath(pathFill, pathPaint);
        }
    }

}
