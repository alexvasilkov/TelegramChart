package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
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
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.Calendar;

public class ChartFinderView extends BaseChartView {

    private static final int HANDLE_LEFT = -1;
    private static final int HANDLE_RIGHT = 1;
    private static final int HANDLE_BOTH = 0;

    private final float frameWidth = ChartStyle.dpToPx(getContext(), 10f);
    private final float handleTouchOffset = ChartStyle.dpToPx(getContext(), 20f);
    private final float handlesMinDistance = ChartStyle.dpToPx(getContext(), 20f);

    private final Drawable cornersOverlay;
    private final Drawable handleDrawable;
    private final Drawable handleOverlayDrawable;

    private final Range handleRange = new Range();
    private final Range handleRangeStart = new Range();
    private final Range handleRangeEnd = new Range();
    private final AnimatedState handleState = new AnimatedState();

    private final Paint foregroundPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private Integer selectedHandle; // One of HANDLE_* values
    private boolean firstScrollEvent;

    private final GestureDetector gestureDetector;

    private ChartView chartView;

    private TimeInterval timeInterval;
    private boolean snapToTimeInterval;
    private int initialTimeIntervals;
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

        cornersOverlay = getResources().getDrawable(R.drawable.round_corners_overlay).mutate();

        handleDrawable = getResources().getDrawable(R.drawable.handle).mutate();
        handleOverlayDrawable = getResources().getDrawable(R.drawable.handle_overlay).mutate();

        setColors(foregroundColor, frameColor, chartStyle.backgroundColorHint);

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
    }

    public void setColors(int foreground, int frame, int background) {
        foregroundPaint.setColor(foreground);
        handleDrawable.setColorFilter(frame, PorterDuff.Mode.SRC_IN);
        cornersOverlay.setColorFilter(background, PorterDuff.Mode.SRC_IN);

        invalidate();
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
    public void setSource(int pos, boolean visible, boolean animate) {
        super.setSource(pos, visible, animate);
        chartView.setSource(pos, visible, animate);
    }


    public void setTimeIntervals(
            TimeInterval interval, int min, int max, int initial, boolean snap) {
        timeInterval = interval;
        initialTimeIntervals = initial;
        minTimeIntervals = min;
        maxTimeIntervals = max;
        snapToTimeInterval = snap;
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
                useSimplifiedDrawing(true);
            }
        }

        return selectedHandle != null;
    }

    private void onUpOrCancelEvent() {
        if (selectedHandle != null && timeInterval != null && snapToTimeInterval) {
            snapToInterval(handleRange, handleRangeEnd, timeInterval, selectedHandle);
            animateHandle();
        }

        selectedHandle = null;

        chartView.useSimplifiedDrawing(false);
        useSimplifiedDrawing(false);
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

        chartView.useSimplifiedDrawing(true);
    }

    private void setInitialHandle() {
        handleRange.set(chartRange);

        if (timeInterval != null) {
            // It time interval is set we'll start with max possible range from right side
            handleRange.from = handleRange.to - initialTimeIntervals * timeInterval.steps;
            if (snapToTimeInterval) {
                snapToInterval(handleRange, handleRange, timeInterval, HANDLE_BOTH);
            }
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

            if (handleState.isFinished()) {
                chartView.useSimplifiedDrawing(false);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Rect chartPos = getChartPosition();
        final int left = chartPos.left;
        final int right = chartPos.right;
        final int top = chartPos.top;
        final int bottom = chartPos.bottom;
        final int topExtra = 0;
        final int bottomExtra = getHeight();

        canvas.clipRect(left, topExtra, right, bottomExtra);

        super.onDraw(canvas); // Drawing chart

        if (!isReady()) {
            return;
        }

        // Drawing handle
        final int leftPos = Math.round(ChartMath.mapX(matrix, handleRange.from));
        final int rightPos = Math.round(ChartMath.mapX(matrix, handleRange.to));

        final float start = Math.max(0f, ChartMath.mapX(matrix, chartRange.from));
        final float end = Math.min(getWidth(), ChartMath.mapX(matrix, chartRange.to));

        // Foreground
        canvas.drawRect(start, top, leftPos + frameWidth, bottom, foregroundPaint);
        canvas.drawRect(rightPos - frameWidth, top, end, bottom, foregroundPaint);

        // Drawing fake round corners overlay
        cornersOverlay.setBounds(left, top, right, bottom);
        cornersOverlay.draw(canvas);

        // Handle
        handleDrawable.setBounds(leftPos, topExtra, rightPos, bottomExtra);
        handleDrawable.draw(canvas);

        handleOverlayDrawable.setBounds(leftPos, topExtra, rightPos, bottomExtra);
        handleOverlayDrawable.draw(canvas);
    }

}
