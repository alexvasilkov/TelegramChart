package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Line;
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.ChartAnimator;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.Range;

abstract class BaseChartView extends View {

    static final int PAINT_FLAGS = Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG;

    private final ChartAnimator animator;

    final Matrix matrix = new Matrix();

    private final Paint pathPaint = new Paint(PAINT_FLAGS);
    private final Path path = new Path();
    private float[] pathsPoints;
    private float[] pathsPointsTransformed;

    private final Paint pointPaint = new Paint(PAINT_FLAGS);
    private final float pointRadius;

    private boolean isAnimating;
    private boolean simplifiedDrawing;

    private final Range pendingRange = new Range();
    private boolean pendingAnimateX;
    private boolean pendingAnimateY;

    private AnimatedState[] linesStates;
    private boolean[] linesVisibility;

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

    private int selectedPointX = -1;


    BaseChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.BaseChartView);
        final float lineWidth =
                arr.getDimension(R.styleable.BaseChartView_chart_lineWidth, dpToPx(2f));
        final int pointColor =
                arr.getColor(R.styleable.BaseChartView_chart_pointColor, Color.WHITE);
        pointRadius = arr.getDimension(R.styleable.BaseChartView_chart_pointRadius, dpToPx(4f));
        arr.recycle();

        animator = new ChartAnimator(this, this::onAnimationStep);

        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeWidth(lineWidth);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(pointColor);

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
        // Resetting old state
        xRange.reset();
        xRangeEnd.reset();
        xRangeState.reset();

        yRange.reset();
        yRangeEnd.reset();
        yRangeState.reset();

        selectedPointX = -1;

        animator.stop();

        // Setting new chart
        chart = newChart;

        final int lines = newChart.lines.size();
        final int points = newChart.x.length;

        chartRange.set(0, points - 1);

        pathsPoints = new float[4 * (points - 1)];
        pathsPointsTransformed = new float[4 * (points - 1)];

        linesStates = new AnimatedState[lines];
        linesVisibility = new boolean[lines];

        for (int i = 0; i < lines; i++) {
            linesStates[i] = new AnimatedState();
            linesStates[i].setTo(1f); // All lines are visible by default
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

    boolean[] getLinesVisibility() {
        for (int i = 0, size = linesStates.length; i < size; i++) {
            linesVisibility[i] = linesStates[i].getTarget() == 1f;
        }
        return linesVisibility;
    }

    boolean hasVisibleLines() {
        for (boolean visible : getLinesVisibility()) {
            if (visible) {
                return true;
            }
        }
        return false;
    }

    void useSimplifiedDrawing(boolean simplified) {
        if (simplifiedDrawing != simplified) {
            simplifiedDrawing = simplified;
            invalidate();
        }
    }

    void setSelectedPointX(int selectedX) {
        selectedPointX = selectedX;
        invalidate();
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

            final Line line = chart.lines.get(l);
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
        isAnimating = result;

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


        final Rect chartPos = getChartPosition();

        final float scaleX = chartPos.width() / (xRange.size() - 1f);
        final float scaleY = chartPos.height() / (yRange.size() - 1f);

        // We should start at the beginning of current X range and take padding into account
        final float left = chartPos.left - xRange.from * scaleX;
        // Bottom of the chart should take padding into account
        final float bottom = chartPos.bottom + yRange.from * scaleY;

        // Setting up transformation matrix
        matrix.setScale(scaleX, -scaleY); // Scale and flip along X axis
        matrix.postTranslate(left, bottom); // Translate to place

        // Adding extra range to continue drawing chart on sides
        int extraLeft = (int) Math.ceil(getExtraLeftSize() / scaleX);
        int extraRight = (int) Math.ceil(getExtraRightSize() / scaleX);

        float fromX = chartRange.fit(xRange.from - extraLeft);
        float toX = chartRange.fit(xRange.to + extraRight);

        xRangeExt.set(fromX, toX);
    }

    float getExtraLeftSize() {
        return getChartPosition().left;
    }

    float getExtraRightSize() {
        return getWidth() - getChartPosition().right;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isReady()) {
            return;
        }

        drawChartLines(canvas);
        drawSelectedPoints(canvas);
    }


    private void drawChartLines(Canvas canvas) {
        final int from = (int) Math.floor(xRangeExt.from);
        final int to = (int) Math.ceil(xRangeExt.to);

        for (int l = 0, size = chart.lines.size(); l < size; l++) {
            final float state = linesStates[l].get();
            final Line line = chart.lines.get(l);
            if (state == 0f) {
                continue; // Ignoring invisible lines
            }

            pathPaint.setColor(line.color);
            pathPaint.setAlpha(toAlpha(state));

            if (isAnimating || simplifiedDrawing) {
                // Drawing a set of lines is much faster than drawing a path
                drawAsLines(canvas, line.y, from, to);
            } else {
                // But a path looks better since it smoothly joins the lines
                drawAsPath(canvas, line.y, from, to);
            }
        }
    }

    private void drawAsPath(Canvas canvas, int[] values, int from, int to) {
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

    private void drawAsLines(Canvas canvas, int[] values, int from, int to) {
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


    private void drawSelectedPoints(Canvas canvas) {
        if (selectedPointX == -1) {
            return;
        }

        for (int l = 0, size = chart.lines.size(); l < size; l++) {
            final float state = linesStates[l].get();
            final Line line = chart.lines.get(l);
            if (state == 0f) {
                continue; // Ignoring invisible lines
            }

            pathPaint.setColor(line.color);
            pathPaint.setAlpha(toAlpha(state));
            // Point's alpha should change much slower than main path
            pointPaint.setAlpha(toAlpha((float) Math.sqrt(Math.sqrt(state))));

            float posX = ChartMath.mapX(matrix, selectedPointX);
            float posY = ChartMath.mapY(matrix, line.y[selectedPointX]);

            canvas.drawCircle(posX, posY, pointRadius, pointPaint);
            canvas.drawCircle(posX, posY, pointRadius, pathPaint);
        }
    }


    float dpToPx(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    static int toAlpha(float alpha) {
        return Math.round(255 * alpha);
    }

}
