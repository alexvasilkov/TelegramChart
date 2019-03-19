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

    private final float frameXWidth = dpToPx(6);
    private final float frameYWidth = dpToPx(1);
    private final float handleTouchOffset = dpToPx(20);
    private final float handlesMinDistance = dpToPx(80);

    private boolean useDynamicRange;

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
        useDynamicRange = arr.getBoolean(R.styleable.ChartFinderView_chart_useDynamicRange, true);
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
    }

    public void setUseDynamicRange(boolean useDynamicRange) {
        this.useDynamicRange = useDynamicRange;
        if (chart != null) {
            setChart(chart); // Resetting chart
        }
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

            firstScrollEvent = selectedHandle != null;
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
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        final float width = getChartPosition().width();

        // Calculating current handles positions
        final float currentScale = width / (xRange.size() - 1f);

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
            } else if (handleToX - handleFromX < handlesMinDistance) {
                handleFromX = handleToX - handlesMinDistance;
            }
        } else if (selectedHandle == HANDLE_RIGHT) {
            // Moving right edge
            handleToX -= distanceX;

            if (handleToX > width) {
                handleToX = width;
            } else if (handleToX - handleFromX < handlesMinDistance) {
                handleToX = handleFromX + handlesMinDistance;
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

        if (useDynamicRange) {
            setHandlesWithDynamicRange(
                    handleFromX, handleToX,
                    handleFromXOrig, handleToXOrig,
                    width, currentScale
            );
        } else {
            setHandlesWithStaticRange(handleFromX, handleToX, currentScale);
        }

        // Setting new range to attached chart view
        chartView.setRange(handleRange.from, handleRange.to, false, true);

        invalidate();
        return true;
    }


    /**
     * Sets handles according to their on-screen position without changing X range.
     * It may not be possible to zoom to revel all chart X values since we have to preserve
     * minimum distance between the handles.
     */
    private void setHandlesWithStaticRange(float handleFromX, float handleToX, float currentScale) {
        handleRange.from = xRange.from + handleFromX / currentScale;
        handleRange.to = xRange.from + handleToX / currentScale;
    }


    /**
     * Calculates and applies new handles range and new X range at the same time so that
     * it is possible to zoom to maximum possible details while preserving min distance
     * between the handles.
     */
    private void setHandlesWithDynamicRange(
            float handleFromX, float handleToX,
            float handleFromXOrig, float handleToXOrig,
            float width, float currentScale) {

        final float totalIntervals = chartRange.size() - 1f;
        final float minIntervals = chartView.xIntervalsNumber;

        float rangeFrom;
        float rangeTo;

        if (selectedHandle == HANDLE_LEFT) {
            // Left handle is moving. Right handle's position and value should stay unchanged.

            // Calculating state that changes 0 -> 1 when left handle position is
            // changing from left-most to the maximum possible position to the right
            float state = handleFromX / (handleToX - handlesMinDistance);

            // Left handle value should change at the same pace from 0 to max possible value
            handleRange.from = state * (handleRange.to - minIntervals);

            // Calculating new X range knowing that right handle's value and position are unchanged
            float scale = (handleToX - handleFromX) / (handleRange.to - handleRange.from);
            rangeFrom = handleRange.from - handleFromX / scale;
            rangeTo = rangeFrom + width / scale;
        } else if (selectedHandle == HANDLE_RIGHT) {
            // Right handle is moving. Left handle's position and value should stay unchanged.

            // Calculating state that changes 0 -> 1 when right handle position is
            // changing from right-most to the maximum possible position to the left
            float state = (width - handleToX) / (width - handleFromX - handlesMinDistance);

            // Right handle value should change at the same pace from total to min possible value
            handleRange.to = state * (handleRange.from + minIntervals)
                    + (1f - state) * totalIntervals;

            // Calculating new X range knowing that left handle's value and position are unchanged
            float scale = (handleToX - handleFromX) / (handleRange.to - handleRange.from);
            rangeFrom = handleRange.to - handleToX / scale;
            rangeTo = rangeFrom + width / scale;
        } else {
            // Moving both handles in same direction
            if (handleFromXOrig > handleFromX && handleFromX > 0f) { // If moving to left
                // Calculating new 'from' value so that left invisible part is moving proportionally
                rangeFrom = xRange.from * handleFromX / handleFromXOrig;
                rangeTo = rangeFrom + width / currentScale;
            } else if (handleToXOrig < handleToX && handleToX < width) { // If moving to right
                // Calculating new 'to' value so that right invisible part is moving proportionally
                rangeTo = totalIntervals - (totalIntervals - xRange.to)
                        * (width - handleToX) / (width - handleToXOrig);
                rangeFrom = rangeTo - width / currentScale;
            } else {
                // No movement, means we reached either of the sides
                if (handleFromX <= 0f) {
                    rangeFrom = 0f;
                    rangeTo = rangeFrom + width / currentScale;
                } else if (handleToX >= width) {
                    rangeTo = totalIntervals;
                    rangeFrom = rangeTo - width / currentScale;
                } else {
                    rangeFrom = xRange.from;
                    rangeTo = xRange.to;
                }
            }

            // Calculating handles values from their on-screen positions
            handleRange.from = rangeFrom + handleFromX / currentScale;
            handleRange.to = rangeFrom + handleToX / currentScale;
        }

        // Updating X range if changed
        if (xRange.from != rangeFrom || xRange.to != rangeTo) {
            setRange(rangeFrom, rangeTo, false, true);
        }
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
