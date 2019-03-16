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
import java.util.Iterator;
import java.util.List;

public class ChartView extends BaseChartView {

    private static final int Y_GUIDES_COUNT = 6;

    private static final Rect textBounds = new Rect();

    private final int topInset = (int) dpToPx(20f);
    private final int bottomInset = (int) dpToPx(16f);

    private final float xLabelPadding = dpToPx(10f);
    private final float yLabelPaddingBottom = dpToPx(5f);

    private YGuides yGuides;
    private final List<YGuides> yGuidesOld = new ArrayList<>();
    private final Paint yGuidesPaint = new Paint(PAINT_FLAGS);

    private List<XLabel> xLabels;
    private float xMaxIntervals;

    private float xLabelsLevel;
    private final Paint xLabelPaint = new Paint(PAINT_FLAGS);
    private final Paint xLabelDotPaint = new Paint(PAINT_FLAGS);
    private final Paint yLabelPaint = new Paint(PAINT_FLAGS);
    private final Paint yLabelStrokePaint = new Paint(PAINT_FLAGS);

    private int direction = 1;
    private Function<Long, String> xLabelCreator;
    private Function<Integer, String> yLabelCreator;
    private RangeListener xRangeListener;


    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        yGuidesPaint.setStyle(Paint.Style.STROKE);
        yGuidesPaint.setStrokeWidth(dpToPx(1f));
        yGuidesPaint.setColor(Color.parseColor("#F1F1F1"));

        xLabelPaint.setTextSize(dpToPx(12f));
        xLabelPaint.setColor(Color.parseColor("#96a2aa"));

        yLabelPaint.setTextSize(dpToPx(12f));
        yLabelPaint.setColor(Color.parseColor("#96a2aa"));

        yLabelStrokePaint.set(yLabelPaint);
        yLabelStrokePaint.setStyle(Paint.Style.STROKE);
        yLabelStrokePaint.setStrokeWidth(dpToPx(2f));
        yLabelStrokePaint.setColor(Color.parseColor("#F1F1F1"));

        xLabelDotPaint.setStrokeWidth(dpToPx(2f));
        xLabelDotPaint.setColor(Color.parseColor("#E1E1E1"));
        xLabelDotPaint.setStrokeCap(Paint.Cap.ROUND);

        setInsets(0, topInset, 0, bottomInset);
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public void setXLabelCreator(Function<Long, String> creator) {
        xLabelCreator = creator;
    }

    public void setYLabelCreator(Function<Integer, String> creator) {
        yLabelCreator = creator;
    }

    public void setXRangeListener(RangeListener listener) {
        xRangeListener = listener;
    }

    @Override
    public void setChart(Chart newChart) {
        // Invalidating X labels
        xLabels = null;

        // Invalidating Y guides
        yGuides = null;
        yGuidesOld.clear();

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

        // Adjusting Y range to better fit chart height + extra inset on top.
        // Final range will be divisible by the number of intervals, to have integer guide values.
        final int yIntervals = Y_GUIDES_COUNT - 1;
        final int chartHeight = getChartPosition().height();
        final float minToY = toY - (toY - fromY) * topInset / (chartHeight + topInset);

        fromY = (int) Math.floor(fromY / yIntervals) * yIntervals;
        toY = (int) Math.ceil(minToY / yIntervals) * yIntervals;

        // Setting up new Y guides if Y range is changed
        if (yRangeEnd.from != fromY || yRangeEnd.to != toY) {
            // We wont animate the very first guides
            boolean animate = yGuides != null;

            if (yGuides != null) {
                // Animating out old Y guides
                yGuidesOld.add(yGuides);
                yGuides.animation.animateTo(0f);
            }

            // Preparing new Y guides
            yGuides = new YGuides(yIntervals + 1);
            for (int i = 0; i <= yIntervals; i++) {
                final int value = (int) (fromY + (toY - fromY) * i / yIntervals);
                yGuides.orig[i] = value;
                yGuides.titles[i] = yLabelCreator == null
                        ? String.valueOf(value) : yLabelCreator.call(value);
            }

            if (animate) {
                yGuides.animation.setTo(0f); // Setting initial hidden state
                yGuides.animation.animateTo(1f); // Animating to visible state
            } else {
                yGuides.animation.setTo(1f); // Setting initial visible state
            }
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
            titles[i] = xLabelCreator == null
                    ? String.valueOf(chart.x[i]) : xLabelCreator.call(chart.x[i]);
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

        // Applying new closes range
        if (range.from != closestFrom || range.to != closestTo) {
            setRange(closestFrom, closestTo, animate, animate);
        }
    }


    @Override
    protected boolean onAnimationStep() {
        boolean result = super.onAnimationStep();

        // Checking X labels animations states
        for (XLabel label : xLabels) {
            result |= !label.animation.isFinished();
        }

        // Checking Y guides animations states
        result |= !yGuides.animation.isFinished();

        for (Iterator<YGuides> iterator = yGuidesOld.iterator(); iterator.hasNext(); ) {
            boolean finished = iterator.next().animation.isFinished();
            if (finished) {
                iterator.remove();
            }
            result |= !finished;
        }

        return result;
    }

    @Override
    protected void onUpdateChartState() {
        super.onUpdateChartState();

        // Calculating current X labels level
        xLabelsLevel = computeCurrentLevel(xRange.size(), xMaxIntervals);
        setXLabelsVisibility();

        yGuides.transform(matrix);
        for (YGuides guides : yGuidesOld) {
            guides.transform(matrix);
        }
        setYGuidesVisibility();

        if (xRangeListener != null) {
            xRangeListener.onRangeChanged(xRange.from, xRange.to);
        }
    }


    private void setXLabelsVisibility() {
        final float fromX = xRangeExt.from;
        final float toX = xRangeExt.to;

        for (int i = 0, size = xLabels.size(); i < size; i++) {
            final XLabel label = xLabels.get(i);

            // Resetting out-of-range labels
            if (i < fromX || i > toX) {
                label.animation.reset();
                continue;
            }

            final boolean show = label.level >= xLabelsLevel;

            if (label.animation.isSet()) {
                label.animation.update();
                label.animation.animateTo(show ? 1f : 0f);
            } else {
                label.animation.setTo(show ? 1f : 0f);
            }
        }
    }

    private void setYGuidesVisibility() {
        yGuides.animation.update();
        for (YGuides guides : yGuidesOld) {
            guides.animation.update();
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
        for (YGuides guides : yGuidesOld) {
            drawYGuides(canvas, guides, left, right);
        }
        drawYGuides(canvas, yGuides, left, right);

        // Drawing chart
        super.onDraw(canvas);

        // Drawing Y labels
        for (YGuides guides : yGuidesOld) {
            drawYLabels(canvas, guides, left, right);
        }
        drawYLabels(canvas, yGuides, left, right);

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

            if (!label.animation.isSet()) {
                continue;
            }

            // Ignore labels from deeper levels, to avoid labels stacking
            if (label.level < xLabelsLevel / 2) {
                continue;
            }

            float alpha = label.animation.getState();

            final float dotPosX = ChartMath.mapX(matrix, i);

            // Drawing a dot if it is inside internal range
            if (fromX <= i && i <= toX) {
                xLabelDotPaint.setAlpha(toAlpha(alpha));
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

            xLabelPaint.setAlpha(toAlpha(alpha));
            canvas.drawText(label.title, labelPosX, labelPosY, xLabelPaint);
        }
    }

    private void drawYGuides(Canvas canvas, YGuides guides, float left, float right) {
        yGuidesPaint.setAlpha(toAlpha(guides.animation.getState()));

        for (int i = 0, size = guides.size(); i < size; i++) {
            final float posY = guides.transformed[i];
            canvas.drawLine(left, posY, right, posY, yGuidesPaint);
        }
    }

    private void drawYLabels(Canvas canvas, YGuides guides, float left, float right) {
        yLabelStrokePaint.setAlpha(toAlpha(guides.animation.getState()));
        yLabelPaint.setAlpha(toAlpha(guides.animation.getState()));

        for (int i = 0, size = guides.size(); i < size; i++) {
            final float posY = guides.transformed[i] - yLabelPaddingBottom;
            canvas.drawText(guides.titles[i], left, posY, yLabelStrokePaint);
            canvas.drawText(guides.titles[i], left, posY, yLabelPaint);
        }
    }


    private static int toAlpha(float alpha) {
        return Math.round(255 * alpha);
    }


    private static class XLabel {
        final String title;
        final int level;
        final float width;

        final AnimationState animation = new AnimationState();

        XLabel(String title, int level, float width) {
            this.title = title;
            this.level = level;
            this.width = width;
        }
    }

    private static class YGuides {
        final String[] titles;
        final float[] orig;
        final float[] transformed;
        final AnimationState animation = new AnimationState();

        YGuides(int size) {
            orig = new float[size];
            transformed = new float[size];
            titles = new String[size];
        }

        void transform(Matrix matrix) {
            for (int i = 0, size = orig.length; i < size; i++) {
                transformed[i] = ChartMath.mapY(matrix, orig[i]);
            }
        }

        int size() {
            return orig.length;
        }
    }


    public interface Function<I, O> {
        O call(I input);
    }

    public interface RangeListener {
        void onRangeChanged(float from, float to);
    }

}
