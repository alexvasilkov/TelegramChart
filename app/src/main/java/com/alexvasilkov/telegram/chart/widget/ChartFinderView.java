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

    private final float frameXWidth = dpToPx(6);
    private final float frameYWidth = dpToPx(1);
    private final float handleTouchOffset = dpToPx(20);
    private final float handlesMinDistance = dpToPx(60);

    private final Range handleRange = new Range();
    private final Paint foregroundPaint = new Paint(PAINT_FLAGS);
    private final Paint framePaint = new Paint(PAINT_FLAGS);
    private Integer selectedHandle; // -1 for left, 1 for right and 0 for both
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

        OnGestureListener listener = new SimpleOnGestureListener() {
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

        setInsets(0, (int) dpToPx(4f), 0, (int) dpToPx(4f));
    }

    public void attachTo(ChartView chartView) {
        this.chartView = chartView;

        chartView.setXRangeListener(this::onAttachedRangeChanged);
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
                    selectedHandle = -1;
                }
            } else {
                if (rightDist <= handleTouchOffset) {
                    selectedHandle = 1;
                }
            }

            if (selectedHandle == null && leftPos < initialX && initialX < rightPos) {
                selectedHandle = 0;
            }

            if (selectedHandle != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
                firstScrollEvent = true;
            }
        }

        return selectedHandle != null;
    }

    private void onUpOrCancelEvent() {
        selectedHandle = null;
    }

    private boolean onScrollEvent(float distanceX) {
        if (selectedHandle == null) {
            return false;
        }

        // Ignoring first scroll event (can be buggy)
        if (firstScrollEvent) {
            firstScrollEvent = false;
            return true;
        }

        float scaleX = ChartMath.getScaleX(matrix);
        float dist = distanceX / scaleX;
        float minDist = handlesMinDistance / scaleX;

        if (selectedHandle == -1) { // Left handle
            float newFrom = handleRange.from - dist;

            if (newFrom > handleRange.to - minDist) {
                newFrom = handleRange.to - minDist;
            }

            handleRange.from = chartRange.fit(newFrom);
        } else if (selectedHandle == 1) { // Right handle
            float newTo = handleRange.to - dist;

            if (newTo < handleRange.from + minDist) {
                newTo = handleRange.from + minDist;
            }

            handleRange.to = chartRange.fit(newTo);
        } else { // Scrolling both
            float newFrom = handleRange.from - dist;
            float newTo = handleRange.to - dist;

            float diff = 0f;
            if (newFrom < chartRange.from) {
                diff = chartRange.from - newFrom;
            } else if (newTo > chartRange.to) {
                diff = chartRange.to - newTo;
            }

            newFrom += diff;
            newTo += diff;

            handleRange.set(newFrom, newTo);
        }

        chartView.setRange(handleRange.from, handleRange.to, false, true);

        invalidate();
        return true;
    }


    private void onAttachedRangeChanged(float fromX, float toX) {
        // Only updating local state if we are not changing it ourselves
        if (selectedHandle == null) {
            handleRange.set(fromX, toX);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Drawing chart
        super.onDraw(canvas);

        if (!isReady()) {
            return;
        }

        // Drawing handle
        float leftPos = ChartMath.mapX(matrix, handleRange.from);
        float rightPos = ChartMath.mapX(matrix, handleRange.to);

        // Foreground
        // Left
        canvas.drawRect(getPaddingLeft(), 0, leftPos, getHeight(), foregroundPaint);
        // Right
        canvas.drawRect(rightPos, 0, getWidth() - getPaddingRight(), getHeight(), foregroundPaint);

        // Frame
        // Left
        canvas.drawRect(leftPos, 0, leftPos + frameXWidth, getHeight(), framePaint);
        // Right
        canvas.drawRect(rightPos - frameXWidth, 0, rightPos, getHeight(), framePaint);
        // Top
        canvas.drawRect(
                leftPos + frameXWidth, 0,
                rightPos - frameXWidth, frameYWidth, framePaint);
        // Bottom
        canvas.drawRect(
                leftPos + frameXWidth, getHeight() - frameYWidth,
                rightPos - frameXWidth, getHeight(), framePaint);
    }

}
