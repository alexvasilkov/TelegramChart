package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.alexvasilkov.telegram.chart.domain.Chart;

import androidx.annotation.Nullable;

class BaseChartView extends View {

    private final Animator animator;
    private final Path path = new Path();
    private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();

    private final Range xRange = new Range();
    private final Range xRangeExt = new Range();
    private final FloatRange yRange = new FloatRange();

    private final FloatRange yRangeStart = new FloatRange();
    private final Range yRangeEnd = new Range();
    private AnimationState yRangeState;

    private Chart chart;

    private boolean includeZeroY;
    private int minSizeY;

    private final Rect insets = new Rect();


    protected BaseChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        animator = new Animator(this, this::onAnimationStep);

        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeWidth(dpToPx(2f));

        setWillNotDraw(false);
    }


    protected void setIncludeZeroY(boolean includeZeroY) {
        this.includeZeroY = includeZeroY;
    }

    protected void setMinSizeY(int minSizeY) {
        this.minSizeY = minSizeY;
    }

    protected void setInsets(int left, int top, int right, int bottom) {
        insets.set(left, top, right, bottom);
    }

    protected Chart getChart() {
        return chart;
    }

    protected Matrix getChartMatrix() {
        return matrix;
    }

    protected Range getRangeX() {
        return xRange;
    }

    protected Range getRangeY() {
        return yRangeEnd;
    }


    /**
     * Sets a chart to be drawn.
     */
    public void setChart(Chart chart) {
        this.chart = chart;

        // Show entire chart by default
        setRange(0, chart.x.length - 1, false);
    }

    /**
     * Specifies x-range to be shown. Chart should already be set before calling this method.
     */
    public void setRange(int fromX, int toX, boolean animateY) {
        if (chart == null) {
            throw new IllegalStateException("Chart is not set");
        }

        // Setting X range withing available range
        xRange.set(fromX, toX);
        xRange.clamp(0, chart.x.length - 1);

        // Calculating min Y value across all visible lines
        int minY = Integer.MAX_VALUE;
        for (Chart.Line line : chart.lines) {
            for (int i = xRange.from; i <= xRange.to; i++) {
                minY = minY > line.y[i] ? line.y[i] : minY;
            }
        }

        // Calculating max Y value across all visible lines
        int maxY = Integer.MIN_VALUE;
        for (Chart.Line line : chart.lines) {
            for (int i = xRange.from; i <= xRange.to; i++) {
                maxY = maxY < line.y[i] ? line.y[i] : maxY;
            }
        }

        // Setting target Y range, including zero if requested
        minY = includeZeroY && minY > 0 ? 0 : minY;
        maxY = includeZeroY && maxY < 0 ? 0 : maxY;

        // Ensure we have minimum required Y values
        if (maxY - minY + 1 < minSizeY) {
            maxY = minY + minSizeY - 1;
        }


        if (animateY) {
            // Running animation only if target range is changed
            if (yRangeEnd.from != minY || yRangeEnd.to != maxY) {
                // Starting Y range animation
                yRangeStart.set(yRange);

                yRangeState = new AnimationState();
                animator.start();
            }
        } else {
            // Immediately setting final Y range
            yRange.set(minY, maxY);
            yRangeState = null;
        }

        yRangeEnd.set(minY, maxY);

        updateMatrixIfReady();
    }


    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        updateMatrixIfReady();
    }

    protected boolean isReady() {
        if (chart == null) {
            return false; // No chart info is set
        }

        int width = getWidth() - getPaddingLeft() - getPaddingRight() - insets.width();
        int height = getHeight() - getPaddingTop() - getPaddingBottom() - insets.height();

        return width > 0 && height > 0 && xRange.size() > 1;
    }


    private void updateMatrixIfReady() {
        if (!isReady()) {
            return;
        }

        final int width = getWidth() - getPaddingLeft() - getPaddingRight() - insets.width();
        final int height = getHeight() - getPaddingTop() - getPaddingBottom() - insets.height();

        final float scaleX = width / (xRange.size() - 1f);
        final float scaleY = height / (yRange.size() - 1f);

        // We should start at the beginning of current X range and take padding into account
        final float left = -xRange.from * scaleX + getPaddingLeft() + insets.left;
        // Bottom of the chart should take padding into account
        final float bottom = getHeight() - getPaddingBottom() - insets.bottom;

        matrix.setScale(scaleX, -scaleY); // Scale and flip along X axis
        matrix.postTranslate(left, bottom); // Translate to place

        // Adding extra range to continue drawing chart on sides
        int extraLeft = (int) ((getPaddingLeft() + insets.left + scaleX * 0.5) / scaleX);
        int extraRight = (int) ((getPaddingRight() + insets.right + scaleX * 0.5) / scaleX);
        xRangeExt.set(xRange.from - extraLeft, xRange.to + extraRight);
        xRangeExt.clamp(0, chart.x.length - 1);

        invalidate();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (isReady()) {

            // Drawing chart lines
            for (Chart.Line line : chart.lines) {
                setPath(path, line.y, xRangeExt.from, xRangeExt.to);
                path.transform(matrix);
                pathPaint.setColor(line.color);
                canvas.drawPath(path, pathPaint);
            }

        }
    }

    private void setPath(Path path, int[] y, int from, int to) {
        path.rewind(); // TODO: Or reset? Test it?
        for (int i = from; i <= to; i++) {
            if (i == from) {
                path.moveTo(i, y[i]);
            } else {
                path.lineTo(i, y[i]);
            }
        }
    }


    private boolean onAnimationStep() {
        if (yRangeState != null) {
            float state = yRangeState.getState();

            yRange.interpolate(yRangeStart, yRangeEnd, state);

            if (state == 1f) {
                yRangeState = null;
            }
        }

        updateMatrixIfReady();

        return yRangeState != null;
    }


    protected float dpToPx(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }


    static class Range {
        int from; // Including
        int to; // Excluding // TODO: update code

        void set(int from, int to) {
            if (from >= to) {
                throw new IllegalArgumentException("'from' should be less than 'to'");
            }

            this.from = from;
            this.to = to;
        }

        void clamp(int min, int max) {
            from = Math.max(from, min);
            to = Math.min(to, max);
        }

        int size() {
            return to - from + 1;
        }
    }

    static class FloatRange {
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

        void interpolate(FloatRange start, Range end, float state) {
            this.from = start.from + (end.from - start.from) * state;
            this.to = start.to + (end.to - start.to) * state;
        }

        void clamp(float min, float max) {
            from = Math.max(from, min);
            to = Math.min(to, max);
        }

        float size() {
            return to - from + 1f;
        }
    }

}
