package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.ChartAnimator;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.painter.Painter;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

public abstract class BaseChartView extends FrameLayout {

    private final ChartAnimator animator;

    final Matrix matrix = new Matrix();
    private final Matrix matrixExtra = new Matrix();

    private boolean isAnimating;
    private boolean simplifiedDrawing;

    final ChartStyle chartStyle;

    private final Range pendingRange = new Range();
    private boolean pendingAnimateX;
    private boolean pendingAnimateY;

    private AnimatedState[] sourcesStates;
    private boolean[] sourcesVisibility;
    float[] sourcesStatesValues;

    final Range xRange = new Range();
    final Range xRangeExt = new Range();
    private final Range xRangeStart = new Range();
    private final Range xRangeEnd = new Range();
    private final AnimatedState xRangeState = new AnimatedState();

    private final Range yRangeMinMax = new Range();
    private final Range yRange = new Range();
    private final Range yRangeStart = new Range();
    final Range yRangeEnd = new Range();
    private final AnimatedState yRangeState = new AnimatedState();

    Chart chart;
    Painter painter;
    final Range chartRange = new Range();

    private final Rect insets = new Rect();
    private final Rect chartPos = new Rect();

    private int selectedPointX = -1;
    private int selectedSourceInd = -1;

    private OnRangeChangeListener xRangeListener;

    BaseChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        chartStyle = new ChartStyle(context, attrs);
        animator = new ChartAnimator(this, this::onAnimationStepInternal);

        setWillNotDraw(false);
    }

    public void setBaseColors(
            boolean darken, int backgroundHint, int selection, int selectionMask) {
        chartStyle.setColors(darken, backgroundHint, selection, selectionMask);
        if (painter != null) {
            painter.applyStyle(chartStyle);
        }
        invalidate();
    }

    @SuppressWarnings("SameParameterValue")
    void setInsets(int left, int top, int right, int bottom) {
        insets.set(left, top, right, bottom);
    }

    public void setXRangeListener(OnRangeChangeListener listener) {
        xRangeListener = listener;
    }

    public Matrix getChartMatrix() {
        return matrix;
    }

    public void setChartMatrixExtra(Matrix matrixExtra) {
        this.matrixExtra.set(matrixExtra);
        notifyReady();
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
        selectedSourceInd = -1;

        animator.stop();

        // Setting new chart
        chart = newChart;
        painter = Painter.create(chart);
        painter.applyStyle(chartStyle);

        chartRange.set(0, newChart.x.length - 1);

        final int sourcesCount = newChart.sources.length;
        sourcesStates = new AnimatedState[sourcesCount];
        sourcesStatesValues = new float[sourcesCount];
        sourcesVisibility = new boolean[sourcesCount];

        // All sources should be visible by default
        for (int i = 0; i < sourcesCount; i++) {
            sourcesStates[i] = new AnimatedState();
            sourcesStates[i].setTo(1f);
            sourcesStatesValues[i] = 1f;
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

    public void setSourceVisibility(int pos, boolean visible, boolean animate) {
        boolean[] visibilities = getSourcesVisibility();
        visibilities[pos] = visible;
        setSourceVisibility(visibilities, animate);
    }

    public void setSourceVisibility(boolean[] visibility, boolean animate) {
        for (int i = 0; i < visibility.length; i++) {
            final float target = visibility[i] ? 1f : 0f;
            if (animate) {
                sourcesStates[i].animateTo(target);
            } else {
                sourcesStates[i].setTo(target);
            }
        }

        // requesting ranges update
        pendingRange.set(xRangeEnd);
        pendingAnimateX = animate;
        pendingAnimateY = animate;

        notifyReady();
    }

    public boolean[] getSourcesVisibility() {
        for (int i = 0, size = sourcesStates.length; i < size; i++) {
            sourcesVisibility[i] = sourcesStates[i].getTarget() == 1f;
        }
        return sourcesVisibility;
    }

    boolean hasVisibleSources() {
        for (boolean visible : getSourcesVisibility()) {
            if (visible) {
                return true;
            }
        }
        return false;
    }

    void requestAnimation() {
        notifyReady();
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

    void setSelectedSourceInd(int sourceInd) {
        selectedSourceInd = sourceInd;
        invalidate();
    }

    int getSelectedSourceInd() {
        return selectedSourceInd;
    }


    boolean isReady() {
        final Rect position = getChartPosition();

        boolean isAttached = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                || isAttachedToWindow();

        return isAttached && chart != null && chartRange.size() > 1
                && position.width() > 0 && position.height() > 0;
    }

    private void notifyReady() {
        if (isReady()) {
            doOnReady();
        }
    }

    void doOnReady() {
        notifyRangeSet();

        boolean animationNeeded = onAnimationStepInternal();
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

        painter.calculateYRange(yRangeMinMax, fromXInt, toXInt, getSourcesVisibility());

        onRangeSet(
                fromX, toX,
                yRangeMinMax.from, yRangeMinMax.to,
                pendingAnimateX, pendingAnimateY
        );

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

        if (xRangeListener != null) {
            xRangeListener.onRangeChanged(chart, xRangeEnd);
        }
    }

    private boolean onAnimationStepInternal() {
        return isAnimating = onAnimationStep();
    }

    boolean onAnimationStep() {
        onUpdateChartState(AnimatedState.now());

        boolean result = !xRangeState.isFinished() || !yRangeState.isFinished();

        for (AnimatedState state : sourcesStates) {
            result |= !state.isFinished();
        }

        result |= painter.isAnimating();

        return result;
    }

    void onUpdateChartState(long now) {
        // Updating sources visibility states if animating
        for (int i = 0, size = sourcesStates.length; i < size; i++) {
            sourcesStates[i].update(now);
            sourcesStatesValues[i] = sourcesStates[i].get();
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
        matrix.postConcat(matrixExtra);

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

        final int from = (int) Math.floor(xRangeExt.from);
        final int to = (int) Math.ceil(xRangeExt.to);

        painter.draw(
                canvas,
                getChartPosition(),
                matrix,
                from,
                to,
                sourcesStatesValues,
                selectedPointX,
                selectedSourceInd,
                isAnimating || simplifiedDrawing || !matrixExtra.isIdentity()
        );
    }


    public interface OnRangeChangeListener {
        void onRangeChanged(Chart chart, Range range);
    }

}
