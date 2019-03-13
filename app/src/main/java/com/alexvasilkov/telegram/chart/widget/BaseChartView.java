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

    protected final Animator animator;
    private final Path path = new Path();
    private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    protected final Matrix matrix = new Matrix();

    protected final FloatRange xRange = new FloatRange();
    private final FloatRange xRangeExt = new FloatRange();
    private final FloatRange xRangeStart = new FloatRange();
    protected final FloatRange xRangeEnd = new FloatRange();
    private AnimationState xRangeState;

    protected final FloatRange yRange = new FloatRange();
    private final FloatRange yRangeStart = new FloatRange();
    protected final FloatRange yRangeEnd = new FloatRange();
    private AnimationState yRangeState;

    protected Chart chart;
    protected final FloatRange chartRange = new FloatRange();

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


    /**
     * Sets a chart to be drawn.
     */
    public void setChart(Chart chart) {
        this.chart = chart;
        this.chartRange.set(0, chart.x.length - 1);

        // Show entire chart by default
        setRange(0, chart.x.length - 1, false, false);
    }

    /**
     * Specifies x-range to be shown. Chart should already be set before calling this method.
     */
    public void setRange(int fromX, int toX, boolean animateX, boolean animateY) {
        if (chart == null) {
            throw new IllegalStateException("Chart is not set");
        }

        // Fitting X range into available chart range
        fromX = (int) chartRange.fit(fromX);
        toX = (int) chartRange.fit(toX);

        if (fromX >= toX) {
            throw new IllegalArgumentException("'from' should be less than 'to'");
        }

        // Calculating min Y value across all visible lines
        int minY = Integer.MAX_VALUE;
        for (Chart.Line line : chart.lines) {
            for (int i = fromX; i <= toX; i++) {
                minY = minY > line.y[i] ? line.y[i] : minY;
            }
        }

        // Calculating max Y value across all visible lines
        int maxY = Integer.MIN_VALUE;
        for (Chart.Line line : chart.lines) {
            for (int i = fromX; i <= toX; i++) {
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


        if (animateX) {
            // Running animation only if target range is changed
            if (xRangeEnd.from != fromX || xRangeEnd.to != toX) {
                // Starting X range animation
                xRangeStart.set(xRange);

                xRangeState = new AnimationState();
                animator.start();
            }
        } else {
            // Immediately setting final Y range
            xRange.set(fromX, toX);
            xRangeState = null;
        }

        xRangeEnd.set(fromX, toX);


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
        int extraLeft = (int) Math.ceil((getPaddingLeft() + insets.left) / scaleX);
        int extraRight = (int) Math.ceil((getPaddingRight() + insets.right) / scaleX);

        float fromX = chartRange.fit(xRange.from - extraLeft);
        float toX = chartRange.fit(xRange.to + extraRight);

        xRangeExt.set(fromX, toX);

        invalidate();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (isReady()) {

            // Drawing chart lines
            int from = (int) Math.floor(xRangeExt.from);
            int to = (int) Math.ceil(xRangeExt.to);

            for (Chart.Line line : chart.lines) {
                setPath(path, line.y, from, to);
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


    protected boolean onAnimationStep() {
        // Animating X range changes
        if (xRangeState != null) {
            float state = xRangeState.getState();

            xRange.interpolate(xRangeStart, xRangeEnd, state);

            if (state == 1f) { // Animation finished
                xRangeState = null;
            }
        }

        // Animating Y range changes
        if (yRangeState != null) {
            float state = yRangeState.getState();

            yRange.interpolate(yRangeStart, yRangeEnd, state);

            if (state == 1f) { // Animation finished
                yRangeState = null;
            }
        }

        updateMatrixIfReady();

        return xRangeState != null || yRangeState != null;
    }


    protected float dpToPx(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

}
