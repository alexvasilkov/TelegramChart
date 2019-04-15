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
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.domain.GroupBy;
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

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

    private GroupBy groupBy;
    private boolean snapToGroup;
    private int minGroupsCount;
    private int maxGroupsCount;
    private int initialGroupsCount;
    private long initialDate;
    private final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));


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
        Chart fixedBaseChart = chart;
        Chart fixedFinderChart = chart;

        if (chart.type == Chart.Type.PIE) {
            // We can't draw PIE type in preview, we'll drawing square area instead where
            // each interval emulates a single value in original chart.
            // Thus we need to add extra point to the end of the chart.

            final int newSize = chart.x.length + 1;
            final long[] x = Arrays.copyOf(chart.x, newSize);
            final Source[] sources = new Source[chart.sources.length];

            for (int i = 0; i < sources.length; i++) {
                final int[] y = Arrays.copyOf(chart.sources[i].y, newSize);
                sources[i] = chart.sources[i].setY(y);
            }

            fixedBaseChart = chart.setX(x).setSources(sources);
            fixedFinderChart = fixedBaseChart.setType(Chart.Type.AREA_SQUARE);
        }

        super.setChart(fixedFinderChart);

        chartView.setChart(fixedBaseChart);

        setInitialHandle();
    }

    @Override
    public void setSourceVisibility(boolean[] visibility, boolean animate) {
        super.setSourceVisibility(visibility, animate);
        chartView.setSourceVisibility(visibility, animate);
    }

    public void groupBy(GroupBy groupBy, int min, int max, int initial, boolean snap) {
        this.groupBy = groupBy;
        this.minGroupsCount = min;
        this.maxGroupsCount = max;
        this.initialGroupsCount = initial;
        this.snapToGroup = snap;
    }

    public void setInitialDate(long initialDate) {
        this.initialDate = initialDate;
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
        if (selectedHandle != null && groupBy != null && snapToGroup) {
            snapToGroups(handleRange, handleRangeEnd, groupBy, selectedHandle);
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

        final float minHandleRange = handlesMinDistance / currentScale;

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
            if (groupBy != null) {
                // Keeping left handle within intervals bounds
                final long toTime = chart.x[Math.round(handleTo)];
                final long minTime = groupBy.add(calendar, toTime, -maxGroupsCount);
                final long maxTime = groupBy.add(calendar, toTime, -minGroupsCount);
                final float min = handleTo + chart.resolution.distance(toTime, minTime);
                final float max = handleTo + chart.resolution.distance(toTime, maxTime);

                handleFrom = handleFrom < min ? min : (handleFrom > max ? max : handleFrom);
            }

        } else if (selectedHandle == HANDLE_RIGHT) {
            // Moving right edge
            handleTo -= distancePos;

            if (handleTo - handleFrom < minHandleRange) {
                handleTo = handleFrom + minHandleRange;
            }

            handleTo = chartRange.fit(handleTo);

            if (groupBy != null) {
                // Keeping right handle within intervals bounds
                final long fromTime = chart.x[Math.round(handleFrom)];
                final long minTime = groupBy.add(calendar, fromTime, minGroupsCount);
                final long maxTime = groupBy.add(calendar, fromTime, maxGroupsCount);
                final float min = handleFrom + chart.resolution.distance(fromTime, minTime);
                final float max = handleFrom + chart.resolution.distance(fromTime, maxTime);

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

        if (groupBy != null) {
            final float initialDistance = initialGroupsCount * groupBy.stepsCount(chart.resolution);
            float from = chart.resolution.distance(chart.x[0], initialDate);
            if (from < 0f) {
                from = 0f;
            }

            float to = from + initialDistance;
            if (to > chartRange.to) {
                to = chartRange.to;
                from = to - initialDistance;
            }

            handleRange.from = chartRange.fit(from);
            handleRange.to = chartRange.fit(to);

            if (snapToGroup) {
                snapToGroups(handleRange, handleRange, groupBy, HANDLE_BOTH);
            }
        }

        chartView.setRange(handleRange.from, handleRange.to, false, false);
    }

    private void snapToGroups(Range range, Range dst, GroupBy groupBy, int selectedHandle) {
        final long[] times = chart.x;

        int newFrom = snapToClosestGroupStart(times, range.from, groupBy);
        int newTo = snapToClosestGroupStart(times, range.to, groupBy);

        final int intervals = Math.round(groupBy.distance(times[newFrom], times[newTo]));
        final int stepsPerGroup = groupBy.stepsCount(chart.resolution);

        // We need to make sure we are occupying a valid number of intervals
        if (intervals < minGroupsCount) {
            if (selectedHandle == HANDLE_LEFT) {
                newFrom = snapToClosestGroupStart(times, newFrom - stepsPerGroup, groupBy);
            } else {
                newTo = snapToClosestGroupStart(times, newTo + stepsPerGroup, groupBy);
            }
        } else if (intervals > maxGroupsCount) {
            if (selectedHandle == HANDLE_LEFT) {
                newFrom = snapToClosestGroupStart(times, newFrom + stepsPerGroup, groupBy);
            } else {
                newTo = snapToClosestGroupStart(times, newTo - stepsPerGroup, groupBy);
            }
        }

        dst.set(newFrom, newTo);
    }

    private int snapToClosestGroupStart(long[] times, float pos, GroupBy groupBy) {
        final int posExact = Math.round(chartRange.fit(pos));
        final int minPos = getNextGroupStart(times[posExact], posExact, groupBy, -1);
        final int maxPos = getNextGroupStart(times[posExact], posExact, groupBy, 1);
        final float state = (pos - minPos) / (float) (maxPos - minPos);
        return state < 0.5f ? minPos : maxPos;
    }

    private int getNextGroupStart(long time, int pos, GroupBy groupBy, int direction) {
        final long nextStart = groupBy.getClosestStart(calendar, time, direction);
        final float nextPos = pos + chart.resolution.distance(time, nextStart);
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
        final int left = Math.round(ChartMath.mapX(matrix, xRange.from));
        final int right = Math.round(ChartMath.mapX(matrix, xRange.to));
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
