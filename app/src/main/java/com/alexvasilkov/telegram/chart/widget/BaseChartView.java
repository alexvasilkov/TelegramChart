package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.ChartAnimator;
import com.alexvasilkov.telegram.chart.utils.Range;

abstract class BaseChartView extends View {

    static final int PAINT_FLAGS = Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG;

    private static final float OPTIMIZATION_FACTOR = 2f;

    private final ChartAnimator animator;
    private final Path path = new Path();
    private final Paint pathPaint = new Paint(PAINT_FLAGS);
    final Matrix matrix = new Matrix();

    private final Paint pathPaintOptimized = new Paint(PAINT_FLAGS);
    private final Matrix matrixOptimized = new Matrix();
    private boolean optimizeDrawing;

    private final Range pendingRange = new Range();
    private boolean pendingAnimateX;
    private boolean pendingAnimateY;

    private AnimatedState[] linesStates;

    final Range xRange = new Range();
    final Range xRangeExt = new Range();
    private final Range xRangeStart = new Range();
    private final Range xRangeEnd = new Range();
    private final AnimatedState xRangeState = new AnimatedState();

    private final Range yRange = new Range();
    private final Range yRangeStart = new Range();
    final Range yRangeEnd = new Range();
    private final AnimatedState yRangeState = new AnimatedState();

    Chart chart;
    final Range chartRange = new Range();

    private final Rect insets = new Rect();
    private final Rect chartPos = new Rect();

    BaseChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.BaseChartView);
        float lineWidth =
                arr.getDimension(R.styleable.BaseChartView_chart_lineWidth, dpToPx(2f));
        arr.recycle();

        animator = new ChartAnimator(this, this::onAnimationStep);

        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeWidth(lineWidth);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);

        setWillNotDraw(false);
    }


    @SuppressWarnings("SameParameterValue")
    void setInsets(int left, int top, int right, int bottom) {
        insets.set(left, top, right, bottom);
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

    Rect getChartPosition() {
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

        linesStates = new AnimatedState[chart.lines.size()];
        for (int i = 0, size = linesStates.length; i < size; i++) {
            linesStates[i] = new AnimatedState();
            linesStates[i].setTo(1f); // Visible by default
        }

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

    public void setLine(int pos, boolean visible, boolean animate) {
        final float target = visible ? 1f : 0f;
        if (animate) {
            linesStates[pos].animateTo(target);
        } else {
            linesStates[pos].setTo(target);
        }

        // requesting ranges update
        pendingRange.set(xRangeEnd);
        pendingAnimateX = animate;
        pendingAnimateY = animate;

        notifyReady();
    }


    boolean isReady() {
        final Rect position = getChartPosition();
        return isAttachedToWindow() && chart != null && chartRange.size() > 1
                && position.width() > 0 && position.height() > 0;
    }

    private void notifyReady() {
        if (isReady()) {
            doOnReady();
        }
    }

    void doOnReady() {
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

        // Calculating min and max Y value across all visible lines
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int l = 0, size = chart.lines.size(); l < size; l++) {
            if (linesStates[l].getTarget() != 1f) {
                continue; // Ignoring invisible lines
            }

            final Chart.Line line = chart.lines.get(l);
            for (int i = fromXInt; i <= toXInt; i++) {
                minY = minY > line.y[i] ? line.y[i] : minY;
                maxY = maxY < line.y[i] ? line.y[i] : maxY;
            }
        }

        if (minY == Integer.MAX_VALUE) {
            minY = 0;
        }
        if (maxY == Integer.MIN_VALUE) {
            maxY = minY + 1;
        }

        onRangeSet(fromX, toX, minY, maxY, pendingAnimateX, pendingAnimateY);

        // Clearing pending range
        pendingRange.set(0f, 0f);
        pendingAnimateX = pendingAnimateY = false;
    }

    void onRangeSet(
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

    boolean onAnimationStep() {
        onUpdateChartState();

        boolean result = !xRangeState.isFinished() || !yRangeState.isFinished();

        for (AnimatedState state : linesStates) {
            result |= !state.isFinished();
        }

        // We'll optimizing path drawing if animating, see onDraw method.
        optimizeDrawing = result;

        return result;
    }

    void onUpdateChartState() {
        final long now = AnimatedState.now();

        // Updating lines visibility states if animating
        for (AnimatedState state : linesStates) {
            state.update(now);
        }

        // Updating X range if animating
        if (!xRangeState.isFinished()) {
            xRangeState.update(now);
            xRange.interpolate(xRangeStart, xRangeEnd, xRangeState.get());
        }

        // Updating Y range if animating
        if (!yRangeState.isFinished()) {
            yRangeState.update(now);
            yRange.interpolate(yRangeStart, yRangeEnd, yRangeState.get());
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

        canvas.save();

        matrixOptimized.set(matrix);
        pathPaintOptimized.set(pathPaint);

        // Optimizing paths drawing by drawing them with less precision.
        // It is especially important when animating Y range since path drawing may become slow if
        // path size is too big (bigger than canvas) which is usually the case during Y animation.
        if (optimizeDrawing) {
            final float reversedFactor = 1f / OPTIMIZATION_FACTOR;
            // Scaling down paths
            matrixOptimized.postScale(reversedFactor, reversedFactor);
            // Scaling down stroke width
            pathPaintOptimized.setStrokeWidth(reversedFactor * pathPaintOptimized.getStrokeWidth());
            // Scaling up the canvas instead
            canvas.scale(OPTIMIZATION_FACTOR, OPTIMIZATION_FACTOR);
        }

        // Drawing chart lines
        int from = (int) Math.floor(xRangeExt.from);
        int to = (int) Math.ceil(xRangeExt.to);

        for (int l = 0, size = chart.lines.size(); l < size; l++) {
            final float state = linesStates[l].get();
            if (state == 0f) {
                continue; // Ignoring invisible lines
            }

            final Chart.Line line = chart.lines.get(l);
            setPath(path, line.y, from, to);
            path.transform(matrixOptimized);

            pathPaintOptimized.setColor(line.color);
            pathPaintOptimized.setAlpha(Math.round(255 * state));

            canvas.drawPath(path, pathPaintOptimized);
        }

        canvas.restore();
    }


    private static void setPath(Path path, int[] y, int from, int to) {
        path.reset();
        for (int i = from; i <= to; i++) {
            if (i == from) {
                path.moveTo(i, y[i]);
            } else {
                path.lineTo(i, y[i]);
            }
        }
    }


    float dpToPx(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

}
