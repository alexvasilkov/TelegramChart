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
import com.alexvasilkov.telegram.chart.utils.AnimationState;
import com.alexvasilkov.telegram.chart.utils.ChartAnimator;
import com.alexvasilkov.telegram.chart.utils.Range;

import androidx.annotation.Nullable;

class BaseChartView extends View {

    protected static final int PAINT_FLAGS = Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG;

    private final ChartAnimator animator;
    private final Path path = new Path();
    private final Paint pathPaint = new Paint(PAINT_FLAGS);
    protected final Matrix matrix = new Matrix();

    private final Range pendingRange = new Range();
    private boolean pendingAnimateX;
    private boolean pendingAnimateY;

    protected final Range xRange = new Range();
    protected final Range xRangeExt = new Range();
    private final Range xRangeStart = new Range();
    protected final Range xRangeEnd = new Range();
    private final AnimationState xRangeState = new AnimationState();

    protected final Range yRange = new Range();
    private final Range yRangeStart = new Range();
    protected final Range yRangeEnd = new Range();
    private final AnimationState yRangeState = new AnimationState();

    protected Chart chart;
    protected final Range chartRange = new Range();

    private final Rect insets = new Rect();
    private final Rect chartPos = new Rect();


    protected BaseChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        animator = new ChartAnimator(this, this::onAnimationStep);

        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeWidth(dpToPx(2f));

        setWillNotDraw(false);
    }


    protected void setInsets(int left, int top, int right, int bottom) {
        insets.set(left, top, right, bottom);
    }

    protected void setStrokeWidth(float strokeDp) {
        pathPaint.setStrokeWidth(dpToPx(strokeDp));
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        notifyReady();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.stop(); // Should not run animation once detached
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        notifyReady();
    }

    protected Rect getChartPosition() {
        chartPos.set(
                getPaddingLeft() + insets.left,
                getPaddingTop() + insets.top,
                getWidth() - getPaddingRight() - insets.right,
                getHeight() - getPaddingBottom() - insets.bottom
        );
        return chartPos;
    }

    /**
     * Sets a chart to be drawn.
     */
    public void setChart(Chart newChart) {
        chart = newChart;
        chartRange.set(0, newChart.x.length - 1);

        pendingRange.set(chartRange);
        pendingAnimateX = false;
        pendingAnimateY = false;

        notifyReady();
    }

    /**
     * Specifies x-range to be shown. Chart should already be set before calling this method.
     */
    public void setRange(float fromX, float toX, boolean animateX, boolean animateY) {
        pendingRange.set(chartRange.fit(fromX), chartRange.fit(toX));
        pendingAnimateX = animateX;
        pendingAnimateY = animateY;

        notifyReady();
    }


    protected boolean isReady() {
        final Rect position = getChartPosition();
        return isAttachedToWindow() && chart != null && chartRange.size() > 1
                && position.width() > 0 && position.height() > 0;
    }

    private void notifyReady() {
        if (isReady()) {
            doOnReady();
        }
    }

    protected void doOnReady() {
        notifyRangeSet();

        boolean animationNeeded = onAnimationStep();
        if (animationNeeded) {
            animator.start();
        }

        invalidate();
    }


    private void notifyRangeSet() {
        if (pendingRange.size() <= 1) {
            return; // No pending range or range is incorrect
        }

        final float fromX = pendingRange.from;
        final float toX = pendingRange.to;

        final int fromXInt = (int) Math.floor(fromX);
        final int toXInt = (int) Math.ceil(toX);

        // Calculating min Y value across all visible lines
        int minY = Integer.MAX_VALUE;
        for (Chart.Line line : chart.lines) {
            for (int i = fromXInt; i <= toXInt; i++) {
                minY = minY > line.y[i] ? line.y[i] : minY;
            }
        }

        // Calculating max Y value across all visible lines
        int maxY = Integer.MIN_VALUE;
        for (Chart.Line line : chart.lines) {
            for (int i = fromXInt; i <= toXInt; i++) {
                maxY = maxY < line.y[i] ? line.y[i] : maxY;
            }
        }

        onRangeSet(fromX, toX, minY, maxY, pendingAnimateX, pendingAnimateY);

        // Clearing pending range
        pendingRange.set(0f, 0f);
        pendingAnimateX = pendingAnimateY = false;
    }

    protected void onRangeSet(
            float fromX, float toX, float fromY, float toY, boolean animateX, boolean animateY) {

        // Ensure Y range has at lest 2 points
        if (fromY >= toY) {
            toY = fromY + 1f;
        }

        if (animateX) {
            // Running animation only if target range is changed
            if (xRangeEnd.from != fromX || xRangeEnd.to != toX) {
                // Starting X range animation
                xRangeStart.set(xRange);
                xRangeState.setTo(0f);
                xRangeState.animateTo(1f);
            }
        } else {
            // Immediately setting final Y range
            xRange.set(fromX, toX);
            xRangeState.reset();
        }

        xRangeEnd.set(fromX, toX);


        if (animateY) {
            // Running animation only if target range is changed
            if (yRangeEnd.from != fromY || yRangeEnd.to != toY) {
                // Starting Y range animation
                yRangeStart.set(yRange);
                yRangeState.setTo(0f);
                yRangeState.animateTo(1f);
            }
        } else {
            // Immediately setting final Y range
            yRange.set(fromY, toY);
            yRangeState.reset();
        }

        yRangeEnd.set(fromY, toY);
    }

    protected boolean onAnimationStep() {
        onUpdateChartState();

        return !xRangeState.isFinished() || !yRangeState.isFinished();
    }

    protected void onUpdateChartState() {
        // Updating X range if animating
        if (!xRangeState.isFinished()) {
            xRangeState.update();
            xRange.interpolate(xRangeStart, xRangeEnd, xRangeState.getState());
        }

        // Updating Y range if animating
        if (!yRangeState.isFinished()) {
            yRangeState.update();
            yRange.interpolate(yRangeStart, yRangeEnd, yRangeState.getState());
        }


        final Rect chartPosition = getChartPosition();

        final float scaleX = chartPosition.width() / (xRange.size() - 1f);
        final float scaleY = chartPosition.height() / (yRange.size() - 1f);

        // We should start at the beginning of current X range and take padding into account
        final float left = chartPosition.left - xRange.from * scaleX;
        // Bottom of the chart should take padding into account
        final float bottom = chartPosition.bottom + yRange.from * scaleY;

        // Setting up transformation matrix
        matrix.setScale(scaleX, -scaleY); // Scale and flip along X axis
        matrix.postTranslate(left, bottom); // Translate to place

        // Adding extra range to continue drawing chart on sides
        int extraLeft = (int) Math.ceil((chartPosition.left) / scaleX);
        int extraRight = (int) Math.ceil((getWidth() - chartPosition.right) / scaleX);

        float fromX = chartRange.fit(xRange.from - extraLeft);
        float toX = chartRange.fit(xRange.to + extraRight);

        xRangeExt.set(fromX, toX);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isReady()) {
            return;
        }

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


    protected float dpToPx(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

}
