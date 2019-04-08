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
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.utils.TimeInterval;

import java.util.Calendar;

public class ChartFinderView extends BaseChartView {

    private static final int HANDLE_LEFT = -1;
    private static final int HANDLE_RIGHT = 1;
    private static final int HANDLE_BOTH = 0;

    private final float frameXWidth = dpToPx(4);
    private final float frameYWidth = dpToPx(1);
    private final float handleTouchOffset = dpToPx(20);
    private final float handlesMinDistance = dpToPx(20);

    private final Range handleRange = new Range();
    private final Range handleRangeStart = new Range();
    private final Range handleRangeEnd = new Range();
    private final AnimatedState handleState = new AnimatedState();

    private final Paint foregroundPaint = new Paint(PAINT_FLAGS);
    private final Paint framePaint = new Paint(PAINT_FLAGS);
    private Integer selectedHandle; // One of HANDLE_* values
    private boolean firstScrollEvent;

    private final GestureDetector gestureDetector;

    private ChartView chartView;

    private TimeInterval timeInterval;
    private int minTimeIntervals;
    private int maxTimeIntervals;
    private final Calendar calendar = Calendar.getInstance();


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
        chartView.setChart(chart);

        setInitialHandle();
        chartView.setRange(handleRange.from, handleRange.to, false, false);
    }

    @Override
    public void setLine(int pos, boolean visible, boolean animate) {
        super.setLine(pos, visible, animate);
        chartView.setLine(pos, visible, animate);
    }


    public void setTimeIntervals(TimeInterval interval, int minIntervals, int maxIntervals) {
        timeInterval = interval;
        minTimeIntervals = minIntervals;
        maxTimeIntervals = maxIntervals;
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
            final float leftPos = ChartMath.mapX(matrix, handleRange.from);
            final float rightPos = ChartMath.mapX(matrix, handleRange.to);
            final float midPos = 0.5f * (leftPos + rightPos);

            final float midDist = Math.abs(midPos - initialX);
            final float leftDist = Math.abs(leftPos - initialX);
            final float rightDist = Math.abs(rightPos - initialX);

            if (midDist <= 0.5f * handleTouchOffset) {
                selectedHandle = HANDLE_BOTH;
            } else {
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
            }

            if (selectedHandle != null) {
                firstScrollEvent = true;

                // Stopping animation and applying end value
                if (!handleState.isFinished()) {
                    handleState.reset();
                    handleRange.set(handleRangeEnd);
                }

                // Optimizing chart drawing while being dragged
                chartView.useSimplifiedDrawing(true);
            }
        }

        return selectedHandle != null;
    }

    private void onUpOrCancelEvent() {
        if (selectedHandle != null && timeInterval != null) {
            snapToInterval(handleRange, handleRangeEnd, timeInterval, selectedHandle);
            animateHandle();
        }

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
        final float currentScale = width / (xRange.size() - 1f);
        final float distancePos = distanceX / currentScale;

        final float maxIntervalsPerScreen = chartView.xLabelsHelper.getMaxIntervals();
        final float minHandleRange =
                Math.max(handlesMinDistance / currentScale, maxIntervalsPerScreen);

        float handleFrom = handleRange.from;
        float handleTo = handleRange.to;

        // Applying scrolled distance to handles
        if (selectedHandle == HANDLE_LEFT) {
            // Moving left edge
            handleFrom -= distancePos;

            if (handleTo - handleFrom < minHandleRange) {
                handleFrom = handleTo - minHandleRange;
            }

            handleFrom = chartRange.fit(handleFrom);

            // TODO: Find a way to allow zooming a bit more than allowed to handle edges
            if (timeInterval != null) {
                // Keeping left handle within intervals bounds
                final long toTime = chart.x[(int) handleTo];
                final long minTime = timeInterval.add(calendar, toTime, maxTimeIntervals, -1);
                final long maxTime = timeInterval.add(calendar, toTime, minTimeIntervals, -1);
                final float min = handleTo + timeInterval.distance(toTime, minTime);
                final float max = handleTo + timeInterval.distance(toTime, maxTime);

                handleFrom = handleFrom < min ? min : (handleFrom > max ? max : handleFrom);
            }

        } else if (selectedHandle == HANDLE_RIGHT) {
            // Moving right edge
            handleTo -= distancePos;

            if (handleTo - handleFrom < minHandleRange) {
                handleTo = handleFrom + minHandleRange;
            }

            handleTo = chartRange.fit(handleTo);

            if (timeInterval != null) {
                // Keeping right handle within intervals bounds
                final long fromTime = chart.x[(int) handleFrom];
                final long minTime = timeInterval.add(calendar, fromTime, minTimeIntervals, 1);
                final long maxTime = timeInterval.add(calendar, fromTime, maxTimeIntervals, 1);
                final float min = handleFrom + timeInterval.distance(fromTime, minTime);
                final float max = handleFrom + timeInterval.distance(fromTime, maxTime);

                handleTo = handleTo < min ? min : (handleTo > max ? max : handleTo);
            }

        } else {
            // Moving both edges
            handleFrom -= distancePos;
            handleTo -= distancePos;

            if (handleFrom < chartRange.from) {
                handleTo += chartRange.from - handleFrom;
                handleFrom = chartRange.from;
            } else if (handleTo > chartRange.to) {
                handleFrom -= handleTo - chartRange.to;
                handleTo = chartRange.to;
            }
        }

        handleRange.from = chartRange.fit(handleFrom);
        handleRange.to = chartRange.fit(handleTo);

        // Setting new range to attached chart view
        chartView.setRange(handleRange.from, handleRange.to, false, true);

        invalidate();
        return true;
    }

    private void animateHandle() {
        chartView.setRange(handleRangeEnd.from, handleRangeEnd.to, true, true);

        handleRangeStart.set(handleRange);
        handleState.setTo(0f);
        handleState.animateTo(1f);
        requestAnimation();
    }

    private void setInitialHandle() {
        handleRange.set(chartRange);

        if (timeInterval != null) {
            // It time interval is set we'll start with max possible range from right side
            handleRange.from = handleRange.to - maxTimeIntervals * timeInterval.steps;
            snapToInterval(handleRange, handleRange, timeInterval, HANDLE_BOTH);
        }
    }

    private void snapToInterval(Range range, Range dst, TimeInterval interval, int selectedHandle) {
        final long[] times = chart.x;

        int newFrom = snapToClosestIntervalStart(times, range.from, interval);
        int newTo = snapToClosestIntervalStart(times, range.to, interval);

        final int intervals = interval.count(times[newFrom], times[newTo]);

        // We need to make sure we are occupying a valid number of intervals
        if (intervals < minTimeIntervals) {
            if (selectedHandle == HANDLE_LEFT) {
                newFrom = snapToClosestIntervalStart(times, newFrom - interval.steps, interval);
            } else {
                newTo = snapToClosestIntervalStart(times, newTo + interval.steps, interval);
            }
        } else if (intervals > maxTimeIntervals) {
            if (selectedHandle == HANDLE_LEFT) {
                newFrom = snapToClosestIntervalStart(times, newFrom + interval.steps, interval);
            } else {
                newTo = snapToClosestIntervalStart(times, newTo - interval.steps, interval);
            }
        }

        dst.set(newFrom, newTo);
    }

    private int snapToClosestIntervalStart(long[] times, float pos, TimeInterval interval) {
        final int posExact = Math.round(chartRange.fit(pos));
        final int minPos = getNextIntervalStart(times[posExact], posExact, interval, -1);
        final int maxPos = getNextIntervalStart(times[posExact], posExact, interval, 1);
        final float state = (pos - minPos) / (float) (maxPos - minPos);
        return state < 0.5f ? minPos : maxPos;
    }

    private int getNextIntervalStart(long time, int pos, TimeInterval interval, int direction) {
        final long nextStart = interval.getStart(calendar, time, direction);
        final float nextPos = pos + interval.distance(time, nextStart);
        return Math.round(chartRange.fit(nextPos));
    }

    @Override
    boolean onAnimationStep() {
        return super.onAnimationStep() || !handleState.isFinished();
    }

    @Override
    void onUpdateChartState(long now) {
        super.onUpdateChartState(now);

        if (!handleState.isFinished()) {
            handleState.update(now);
            handleRange.interpolate(handleRangeStart, handleRangeEnd, handleState.get());
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
