package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.Range;

public class ChartFinderView extends BaseChartView {

    private static final int HANDLE_LEFT = -1;
    private static final int HANDLE_RIGHT = 1;
    private static final int HANDLE_BOTH = 0;

    private final float frameXWidth = dpToPx(4);
    private final float frameYWidth = dpToPx(1);
    private final float handleTouchOffset = dpToPx(20);
    private final float handlesMinDistance = dpToPx(60);

    private final Range handleRange = new Range();
    private final Paint foregroundPaint = new Paint(PAINT_FLAGS);
    private final Paint framePaint = new Paint(PAINT_FLAGS);
    private Integer selectedHandle; // One of HANDLE_* values
    private boolean firstScrollEvent;

    private final GestureDetector gestureDetector;

    private ChartView chartView;


    public ChartFinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.ChartFinderView);
        int foregroundColor =
                arr.getColor(R.styleable.ChartFinderView_chart_foregroundColor, 0x44000000);
        int frameColor = arr.getColor(R.styleable.ChartFinderView_chart_frameColor, 0x88000000);
        arr.recycle();

        foregroundPaint.setStyle(Paint.Style.FILL);
        foregroundPaint.setColor(foregroundColor);

        framePaint.setStyle(Paint.Style.FILL);
        framePaint.setColor(frameColor);

        final OnGestureListener listener = new SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return onDownEvent(e.getX());
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dX, float dY) {
                return onScrollEvent(dX);
            }
        };
        gestureDetector = new GestureDetector(context, listener);
        gestureDetector.setIsLongpressEnabled(false);

        setInsets(0, (int) dpToPx(4f), 0, (int) dpToPx(4f));
        useSimplifiedDrawing(true); // Optimizing preview rendering
    }

    public void attachTo(ChartView chartView) {
        this.chartView = chartView;
    }

    @Override
    public void setChart(Chart chart) {
        super.setChart(chart);
        handleRange.set(chartRange);
        chartView.setChart(chart);
    }

    @Override
    public void setLine(int pos, boolean visible, boolean animate) {
        super.setLine(pos, visible, animate);
        chartView.setLine(pos, visible, animate);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            onUpOrCancelEvent();
        }

        return gestureDetector.onTouchEvent(event);
    }

    private boolean onDownEvent(float initialX) {
        if (selectedHandle == null) {
            float leftPos = ChartMath.mapX(matrix, handleRange.from);
            float rightPos = ChartMath.mapX(matrix, handleRange.to);

            float leftDist = Math.abs(leftPos - initialX);
            float rightDist = Math.abs(rightPos - initialX);

            if (leftDist < rightDist) {
                if (leftDist <= handleTouchOffset) {
                    selectedHandle = HANDLE_LEFT;
                }
            } else {
                if (rightDist <= handleTouchOffset) {
                    selectedHandle = HANDLE_RIGHT;
                }
            }

            if (selectedHandle == null && leftPos < initialX && initialX < rightPos) {
                selectedHandle = HANDLE_BOTH;
            }

            if (selectedHandle != null) {
                firstScrollEvent = true;
                // Optimizing chart drawing while being dragged
                chartView.useSimplifiedDrawing(true);
            }
        }

        return selectedHandle != null;
    }

    private void onUpOrCancelEvent() {
        selectedHandle = null;
        chartView.useSimplifiedDrawing(false);
    }

    private boolean onScrollEvent(float distanceX) {
        if (selectedHandle == null) {
            return false;
        }

        // Ignoring first scroll movement (can be buggy)
        if (firstScrollEvent) {
            firstScrollEvent = false;
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        final float width = getChartPosition().width();
        final float maxIntervals = chartView.xLabelsHelper.getMaxIntervals();

        // Calculating current handles positions
        final float currentScale = width / (xRange.size() - 1f);

        final float fitSize = maxIntervals * currentScale;
        final float minDistance = Math.max(fitSize - 0.5f, handlesMinDistance);

        final float handleFromXOrig = (handleRange.from - xRange.from) * currentScale;
        final float handleToXOrig = width - (xRange.to - handleRange.to) * currentScale;

        float handleFromX = handleFromXOrig;
        float handleToX = handleToXOrig;

        // Applying scrolled distance to handles
        if (selectedHandle == HANDLE_LEFT) {
            // Moving left edge
            handleFromX -= distanceX;

            if (handleFromX < 0f) {
                handleFromX = 0f;
            } else if (handleToX - handleFromX < minDistance) {
                handleFromX = handleToX - minDistance;
            }
        } else if (selectedHandle == HANDLE_RIGHT) {
            // Moving right edge
            handleToX -= distanceX;

            if (handleToX > width) {
                handleToX = width;
            } else if (handleToX - handleFromX < minDistance) {
                handleToX = handleFromX + minDistance;
            }

        } else {
            // Moving both edges
            handleFromX -= distanceX;
            handleToX -= distanceX;

            if (handleFromX < 0f) {
                handleToX += 0f - handleFromX;
                handleFromX = 0f;
            } else if (handleToX > width) {
                handleFromX -= handleToX - width;
                handleToX = width;
            }
        }

        handleRange.from = xRange.from + handleFromX / currentScale;
        handleRange.to = xRange.from + handleToX / currentScale;

        // Setting new range to attached chart view
        chartView.setRange(handleRange.from, handleRange.to, false, true);

        invalidate();
        return true;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); // Drawing chart

        if (!isReady()) {
            return;
        }

        // Drawing handle
        final float leftPos = ChartMath.mapX(matrix, handleRange.from);
        final float rightPos = ChartMath.mapX(matrix, handleRange.to);

        final float start = Math.max(0f, ChartMath.mapX(matrix, chartRange.from));
        final float end = Math.min(getWidth(), ChartMath.mapX(matrix, chartRange.to));

        // Foreground
        // Left
        canvas.drawRect(start, 0f, leftPos, getHeight(), foregroundPaint);
        // Right
        canvas.drawRect(rightPos, 0f, end, getHeight(), foregroundPaint);

        // Frame
        // Left
        canvas.drawRect(leftPos, 0f, leftPos + frameXWidth, getHeight(), framePaint);
        // Right
        canvas.drawRect(rightPos - frameXWidth, 0f, rightPos, getHeight(), framePaint);
        // Top
        canvas.drawRect(
                leftPos + frameXWidth, 0f,
                rightPos - frameXWidth, frameYWidth, framePaint);
        // Bottom
        canvas.drawRect(
                leftPos + frameXWidth, getHeight() - frameYWidth,
                rightPos - frameXWidth, getHeight(), framePaint);
    }

}
