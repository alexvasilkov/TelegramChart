package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.AnimationState;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.Nullable;

public class ChartView extends BaseChartView {

    private static final int Y_GUIDES_COUNT = 6;

    private static final Rect textBounds = new Rect();

    private final float xLabelPadding = dpToPx(10f);

    private final YGuides yGuides = new YGuides(new float[Y_GUIDES_COUNT]);
    private final List<YGuides> yGuidesOld = new ArrayList<>();
    private final Paint yGuidesPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private List<XLabel> xLabels;
    private float xMaxIntervals;

    private float xLabelsLevel;
    private final Paint xLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint xLabelDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private int direction = 1;
    private Function<Long, String> labelCreator;
    private RangeListener xRangeListener;


    public ChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        yGuidesPaint.setStyle(Paint.Style.STROKE);
        yGuidesPaint.setStrokeWidth(dpToPx(1f));
        yGuidesPaint.setColor(Color.parseColor("#F1F1F1"));

        xLabelPaint.setTextSize(dpToPx(12f));
        xLabelPaint.setColor(Color.parseColor("#96a2aa"));

        xLabelDotPaint.setStrokeWidth(dpToPx(2f));
        xLabelDotPaint.setColor(Color.parseColor("#E1E1E1"));
        xLabelDotPaint.setStrokeCap(Paint.Cap.ROUND);

        setInsets(0, 0, 0, (int) dpToPx(18f));
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public void setLabelCreator(Function<Long, String> creator) {
        labelCreator = creator;
    }

    public void setXRangeListener(RangeListener listener) {
        xRangeListener = listener;
    }

    @Override
    public void setChart(Chart newChart) {
        xLabels = null; // Invalidating X labels

        super.setChart(newChart);
    }

    @Override
    protected void doOnReady() {
        prepareXLabels(); // Should be called before super

        super.doOnReady();
    }

    @Override
    protected void onRangeSet(
            float fromX, float toX, float fromY, float toY, boolean animateX, boolean animateY) {

        // Including zero value
        fromY = fromY > 0f ? 0f : fromY;
        toY = toY < 0f ? 0f : toY;

        // Ensure we have minimum possible Y values
        if (toY - fromY + 1 < Y_GUIDES_COUNT) {
            toY = fromY + Y_GUIDES_COUNT - 1;
        }

        // Preparing new Y guides
        for (int i = 0; i < Y_GUIDES_COUNT; i++) {
            yGuides.orig[i] = fromY + (toY - fromY) * i / (Y_GUIDES_COUNT - 1f);
        }

        super.onRangeSet(fromX, toX, fromY, toY, animateX, animateY);
    }

    private void prepareXLabels() {
        if (xLabels != null) {
            return; // Already prepared
        }

        int size = chart.x.length;
        xLabels = new ArrayList<>(size);

        // Preparing titles
        float maxLabelWidth = 0f;
        final float[] widths = new float[size];
        final String[] titles = new String[size];

        for (int i = 0; i < size; i++) {
            titles[i] = labelCreator.call(chart.x[i]);
            widths[i] = measureXLabel(titles[i]);
            maxLabelWidth = Math.max(maxLabelWidth, widths[i]);
        }

        // Computing max number of intervals
        xMaxIntervals = computeMaxIntervals(
                getChartPosition().width(), maxLabelWidth, xLabelPadding);

        final int[] levels = computeLabelsLevels(size, xMaxIntervals);

        for (int i = 0; i < size; i++) {
            // Inverting levels position according to direction
            int levelPos = direction > 0 ? i : size - 1 - i;
            xLabels.add(new XLabel(titles[i], levels[levelPos], widths[i]));
        }
    }

    private float measureXLabel(String title) {
        xLabelPaint.getTextBounds(title, 0, title.length(), textBounds);
        return textBounds.width();
    }


    /**
     * Computing maximum number of intervals that can possibly fit into single screen.
     */
    private static float computeMaxIntervals(float totalWidth, float labelWidth, float padding) {
        float maxIntervals = (totalWidth - labelWidth) / (labelWidth + padding);
        return Math.max(maxIntervals, 2f); // Assuming that screen must fit at least 3 labels
    }

    /**
     * Returns array of levels for each label.
     */
    private static int[] computeLabelsLevels(int size, float maxIntervals) {
        final int[] levels = new int[size];

        fillInitialLabelsLevel(levels, maxIntervals);

        if (levels[0] != levels[size - 1]) {
            throw new AssertionError("Initial labels should include edge points");
        }
        if (levels[0] == 0) {
            throw new AssertionError("Initial labels level is invalid");
        }

        return levels;
    }

    private static void fillInitialLabelsLevel(int[] levels, float maxIntervals) {
        final int size = levels.length;

        // Computing minimum number of steps that each interval should hold
        final int minStepsPerInterval = (int) Math.ceil((size - 1) / maxIntervals);

        // Computing number of whole intervals fitting into single screen
        final int intervals = (size - 1) / minStepsPerInterval;

        // Computing actual number of steps per interval (can be bigger than min steps above)
        final int stepsPerInterval = (size - 1) / intervals;

        // Computing number of intervals that should hold extra step to span entire size
        final int intervalsWithExtra = (size - 1) % intervals;

        // Dividing first level evenly into intervals and then fill each interval
        // by recursively dividing it into 2 sub-intervals
        int prevPos = -1;

        for (int i = 0; i <= intervals; i++) {
            int pos = i * stepsPerInterval;

            // Adding extra step to last intervals to have a correct total distribution
            pos += Math.max(intervalsWithExtra - intervals + i, 0);
            levels[pos] = stepsPerInterval;

            // Setting up values in-between
            if (prevPos != -1) {
                fillLevelsInHalfs(levels, stepsPerInterval, prevPos, pos);
            }
            prevPos = pos;
        }
    }

    private static void fillLevelsInHalfs(int[] levels, int prevLevel, int from, int to) {
        final int level = prevLevel / 2;

        if (level <= 1) {
            for (int i = from + 1; i < to; i++) {
                levels[i] = 1;
            }
        } else {
            final int mid = (to + from) / 2;
            levels[mid] = level; // Can't be less than 1
            fillLevelsInHalfs(levels, level, from, mid);
            fillLevelsInHalfs(levels, level, mid, to);
        }
    }

    private static float computeCurrentLevel(float size, float maxIntervals) {
        return (size - 1f) / maxIntervals;
    }


    /**
     * Sets an integer range that is closest to current X range.
     */
    public void snapToClosest(boolean animate) {
        final Range range = xRangeEnd;

        if (xLabelsLevel <= 1f) {
            return; // No snapping needed
        }

        final float maxSteps = chartRange.size() / xMaxIntervals;

        final int from = Math.round(range.from);
        int closestFrom = from;
        for (int i = 0; i < maxSteps; i++) {
            if (xLabels.get(from - i).level >= xLabelsLevel) {
                closestFrom = from - i;
                break;
            } else if (xLabels.get(from + i).level >= xLabelsLevel) {
                closestFrom = from + i;
                break;
            }
        }

        final int to = Math.round(range.to);
        int closestTo = to;
        for (int i = 0; i < maxSteps; i++) {
            if (xLabels.get(to + i).level >= xLabelsLevel) {
                closestTo = to + i;
                break;
            } else if (xLabels.get(to - i).level >= xLabelsLevel) {
                closestTo = to - i;
                break;
            }
        }

        // Applying new "nice" range
        if (range.from != closestFrom || range.to != closestTo) {
            setRange(closestFrom, closestTo, animate, animate);
        }
    }


    @Override
    protected boolean onAnimationStep() {
        boolean result = super.onAnimationStep();

        // Checking X labels animations states
        for (XLabel label : xLabels) {
            if (label.animation != null && label.state == label.targetState) {
                label.animation = null;
            }
            result |= label.animation != null;
        }

        return result;
    }

    @Override
    protected void onUpdateChartState() {
        super.onUpdateChartState();

        // Calculating current X labels level
        xLabelsLevel = computeCurrentLevel(xRange.size(), xMaxIntervals);
        setLabelsVisibility();

        if (xRangeListener != null) {
            xRangeListener.onRangeChanged(xRange.from, xRange.to);
        }
    }


    private void setLabelsVisibility() {
        final float fromX = xRangeExt.from;
        final float toX = xRangeExt.to;

        for (int i = 0, size = xLabels.size(); i < size; i++) {
            final XLabel label = xLabels.get(i);

            // Resetting out-of-range labels
            if (i < fromX || i > toX) {
                label.state = 0f;
                label.targetState = Float.NaN;
                label.animation = null;
                continue;
            }

            final boolean show = label.level >= xLabelsLevel;
            final float toState = show ? 1f : 0f;

            if (Float.isNaN(label.targetState)) {
                // Setting initial label state
                label.state = toState;
                label.targetState = toState;
                label.animation = null;
            } else {
                // Setting current state value
                if (label.animation != null) {
                    float animState = label.animation.getState();
                    label.state = label.targetState == 1f ? animState : 1f - animState;
                }

                // Triggering animation if target state is changed
                if (label.targetState != toState) {
                    // Animating to target state
                    label.targetState = toState;
                    label.animation = new AnimationState(show ? label.state : 1f - label.state);
                }
            }
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (!isReady()) {
            super.onDraw(canvas);
            return;
        }

        final Rect chartPosition = getChartPosition();
        final float left = chartPosition.left;
        final float right = chartPosition.right;

        // Drawing Y guides
        yGuides.transform(matrix);
        for (float guideY : yGuides.transformed) {
            canvas.drawLine(left, guideY, right, guideY, yGuidesPaint);
        }

        // Drawing chart
        super.onDraw(canvas);

        // Drawing X labels
        final float fromX = xRange.from;
        final float toX = xRange.to;

        final int fromExtX = (int) Math.ceil(xRangeExt.from);
        final int toExtX = (int) Math.floor(xRangeExt.to);

        final float extraLeft = left - 0f;
        final float extraRight = getWidth() - right;

        final float dotPosY = yGuides.transformed[0];
        final float labelPosY = getHeight() - getPaddingBottom();

        for (int i = fromExtX; i <= toExtX; i++) {
            XLabel label = xLabels.get(i);

            if (label.state > 0f) {
                float alpha = label.state;

                final float dotPosX = ChartMath.mapX(matrix, i);

                // Drawing a dot if it is inside internal range
                if (fromX <= i && i <= toX) {
                    xLabelDotPaint.setAlpha(Math.round(255 * alpha));
                    canvas.drawPoint(dotPosX, dotPosY, xLabelDotPaint);
                }

                // Shifting label's X pos according to its position on screen to fit internal width
                final float labelShift;
                if (i < fromX && extraLeft > 0f) {
                    // Animating label appearance on the left
                    labelShift = 1f - dotPosX / extraLeft;
                    alpha *= 1f - labelShift;
                } else if (i > toX && extraRight > 0f) {
                    // Animating label appearance on the right
                    labelShift = 1f - (dotPosX - right) / extraRight;
                    alpha *= labelShift;
                } else {
                    labelShift = (dotPosX - left) / (right - left);
                }

                final float labelPosX = dotPosX - label.width * labelShift;

                xLabelPaint.setAlpha(Math.round(255 * alpha));
                canvas.drawText(label.title, labelPosX, labelPosY, xLabelPaint);
            }
        }
    }


    private static class XLabel {
        final String title;
        final int level;
        final float width;

        float state;
        float targetState = Float.NaN;
        AnimationState animation;

        XLabel(String title, int level, float width) {
            this.title = title;
            this.level = level;
            this.width = width;
        }
    }

    private static class YGuides {
        final float[] orig;
        final float[] transformed;

        YGuides(float[] values) {
            orig = values;
            transformed = Arrays.copyOf(values, values.length);
        }

        YGuides(YGuides guides) {
            this(Arrays.copyOf(guides.orig, guides.orig.length));
        }

        void transform(Matrix matrix) {
            for (int i = 0, size = orig.length; i < size; i++) {
                transformed[i] = ChartMath.mapY(matrix, orig[i]);
            }
        }
    }


    public interface Function<I, O> {
        O call(I input);
    }

    public interface RangeListener {
        void onRangeChanged(float from, float to);
    }

}
