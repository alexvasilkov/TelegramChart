package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.AnimationState;
import com.alexvasilkov.telegram.chart.utils.ChartMath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChartView extends BaseChartView {

    private static final Rect textBounds = new Rect();

    private final int topInset;
    private final int yGuidesCount;

    private final float xLabelPadding = dpToPx(10f);
    private final float yLabelPaddingBottom = dpToPx(5f);

    private final int direction;

    private YGuides yGuides;
    private final List<YGuides> yGuidesOld = new ArrayList<>();
    private final Paint yGuidesPaint = new Paint(PAINT_FLAGS);

    private List<XLabel> xLabels;
    private float xMaxIntervals;
    int xIntervalsNumber;
    private float xLabelsLevel;

    private final Paint xLabelPaint = new Paint(PAINT_FLAGS);
    private final Paint xLabelDotPaint = new Paint(PAINT_FLAGS);
    private final Paint yLabelPaint = new Paint(PAINT_FLAGS);
    private final Paint yLabelStrokePaint = new Paint(PAINT_FLAGS);

    private Function<Long, String> xLabelCreator;
    private Function<Integer, String> yLabelCreator;


    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.ChartView);
        float labelsSize =
                arr.getDimension(R.styleable.ChartView_chart_labelsTextSize, dpToPx(12f));
        int labelsColor = arr.getColor(R.styleable.ChartView_chart_labelsColor, Color.DKGRAY);
        int labelsStrokeColor =
                arr.getColor(R.styleable.ChartView_chart_labelsStrokeColor, Color.TRANSPARENT);
        int labelsDotColor = arr.getColor(R.styleable.ChartView_chart_labelsDotColor, Color.DKGRAY);
        float guidesWidth = arr.getDimension(R.styleable.ChartView_chart_guidesWidth, dpToPx(1f));
        int guidesColor = arr.getColor(R.styleable.ChartView_chart_guidesColor, Color.LTGRAY);
        yGuidesCount = arr.getInt(R.styleable.ChartView_chart_guidesNumber, 6);
        direction = arr.getInt(R.styleable.ChartView_chart_direction, 1);
        arr.recycle();

        yGuidesPaint.setStyle(Paint.Style.STROKE);
        yGuidesPaint.setStrokeWidth(guidesWidth);
        yGuidesPaint.setColor(guidesColor);

        xLabelPaint.setTextSize(labelsSize);
        xLabelPaint.setColor(labelsColor);

        yLabelPaint.setTextSize(labelsSize);
        yLabelPaint.setColor(labelsColor);

        yLabelStrokePaint.set(yLabelPaint);
        yLabelStrokePaint.setStyle(Paint.Style.STROKE);
        yLabelStrokePaint.setStrokeWidth(dpToPx(2f));
        yLabelStrokePaint.setColor(labelsStrokeColor);

        xLabelDotPaint.setStrokeWidth(guidesWidth * 2f);
        xLabelDotPaint.setColor(labelsDotColor);
        xLabelDotPaint.setStrokeCap(Paint.Cap.ROUND);

        topInset = (int) (1.25f * labelsSize + yLabelPaddingBottom);
        int bottomInset = (int) (1.33f * labelsSize);
        setInsets(0, topInset, 0, bottomInset);
    }

    public void setXLabelCreator(Function<Long, String> creator) {
        xLabelCreator = creator;
    }

    public void setYLabelCreator(Function<Integer, String> creator) {
        yLabelCreator = creator;
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
        final int yIntervals = yGuidesCount - 1;
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
        xIntervalsNumber = computeIntervals(size, xMaxIntervals);

        final int[] levels = computeLabelsLevels(size, xIntervalsNumber);

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

    private static int computeIntervals(int xSize, float maxIntervals) {
        // Computing minimum number of steps that each interval should hold
        final int minStepsPerInterval = (int) Math.ceil((xSize - 1) / maxIntervals);

        // Computing number of whole intervals fitting into single screen
        return (xSize - 1) / minStepsPerInterval;
    }

    /**
     * Returns array of levels for each label.
     */
    private static int[] computeLabelsLevels(int size, int intervals) {
        final int[] levels = new int[size];

        fillInitialLabelsLevel(levels, intervals);

        if (levels[0] != levels[size - 1]) {
            throw new AssertionError("Initial labels should include edge points");
        }
        if (levels[0] == 0) {
            throw new AssertionError("Initial labels level is invalid");
        }

        return levels;
    }

    private static void fillInitialLabelsLevel(int[] levels, int intervals) {
        final int size = levels.length;

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
                fillLevelsInHalves(levels, stepsPerInterval, prevPos, pos);
            }
            prevPos = pos;
        }
    }

    private static void fillLevelsInHalves(int[] levels, int prevLevel, int from, int to) {
        final int level = prevLevel / 2;

        if (level <= 1) {
            for (int i = from + 1; i < to; i++) {
                levels[i] = 1;
            }
        } else {
            final int mid = (to + from) / 2;
            levels[mid] = level; // Can't be less than 1
            fillLevelsInHalves(levels, level, from, mid);
            fillLevelsInHalves(levels, level, mid, to);
        }
    }

    private static float computeCurrentLevel(float size, float maxIntervals) {
        return (size - 1f) / maxIntervals;
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
            drawYLabels(canvas, guides, left);
        }
        drawYLabels(canvas, yGuides, left);

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

            if (!label.animation.isSet() || label.animation.getState() == 0f) {
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

    private void drawYLabels(Canvas canvas, YGuides guides, float left) {
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

}
