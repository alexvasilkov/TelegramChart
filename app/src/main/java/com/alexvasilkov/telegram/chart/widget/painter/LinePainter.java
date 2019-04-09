package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

public class LinePainter extends Painter {

    private final Paint pathPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private final Paint pointPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private float pointRadius;

    private final Path path = new Path();
    private float[] pathsPoints;
    private float[] pathsPointsTransformed;


    LinePainter(Chart chart, ChartStyle style) {
        super(chart);

        final int points = chart.x.length;
        pathsPoints = new float[4 * (points - 1)];
        pathsPointsTransformed = new float[4 * (points - 1)];


        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setStyle(Paint.Style.FILL);

        applyStyle(style);
    }

    private void applyStyle(ChartStyle style) {
        pathPaint.setStrokeWidth(style.lineWidth);
        pointPaint.setColor(style.pointColor);
        pointRadius = style.pointRadius;
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

        for (int l = 0, size = chart.sources.size(); l < size; l++) {
            final float state = sourcesStates[l];
            final Chart.Source source = chart.sources.get(l);
            if (state == 0f) {
                continue; // Ignoring invisible sources
            }

            pathPaint.setColor(source.color);
            pathPaint.setAlpha(toAlpha(state));

            if (simplified) {
                // Drawing a set of lines is much faster than drawing a path
                drawAsLines(canvas, matrix, source.y, from, to);
            } else {
                // But a path looks better since it smoothly joins the lines
                drawAsPath(canvas, matrix, source.y, from, to);
            }
        }

        if (selected == -1) {
            return;
        }

        for (int l = 0, size = chart.sources.size(); l < size; l++) {
            final float state = sourcesStates[l];
            final Chart.Source source = chart.sources.get(l);
            if (state == 0f) {
                continue; // Ignoring invisible sources
            }

            pathPaint.setColor(source.color);
            pathPaint.setAlpha(toAlpha(state));
            // Point's alpha should change much slower than main path
            pointPaint.setAlpha(toAlpha((float) Math.sqrt(Math.sqrt(state))));

            drawSelected(canvas, matrix, selected, source.y[selected]);
        }
    }

    private void drawAsPath(Canvas canvas, Matrix matrix, int[] values, int from, int to) {
        path.reset();
        for (int i = from; i <= to; i++) {
            if (i == from) {
                path.moveTo(i, values[i]);
            } else {
                path.lineTo(i, values[i]);
            }
        }

        path.transform(matrix);

        canvas.drawPath(path, pathPaint);
    }

    private void drawAsLines(Canvas canvas, Matrix matrix, int[] values, int from, int to) {
        final float[] points = pathsPoints;

        for (int i = from; i < to; i++) {
            points[4 * i] = i;
            points[4 * i + 1] = values[i];
            points[4 * i + 2] = i + 1;
            points[4 * i + 3] = values[i + 1];
        }

        final int offset = 4 * from;
        final int count = 2 * (to - from);

        matrix.mapPoints(pathsPointsTransformed, offset, points, offset, count);

        canvas.drawLines(pathsPointsTransformed, offset, 2 * count, pathPaint);
    }


    private void drawSelected(Canvas canvas, Matrix matrix, int x, int y) {
        float posX = ChartMath.mapX(matrix, x);
        float posY = ChartMath.mapY(matrix, y);

        canvas.drawCircle(posX, posY, pointRadius, pointPaint);
        canvas.drawCircle(posX, posY, pointRadius, pathPaint);
    }

}
