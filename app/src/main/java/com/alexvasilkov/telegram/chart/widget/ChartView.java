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

    private final float xLabelMaxWidth = dpToPx(50f);

    private final YGuides yGuides = new YGuides(new float[Y_GUIDES_COUNT]);
    private final List<YGuides> yGuidesOld = new ArrayList<>();
    private final Paint yGuidesPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private List<XLabel> xLabels;
    private int xLabelsLevel;
    private int xLabelsMinCount;
    private final Paint xLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint xLabelDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private boolean animateLabels;

    private int direction = 1;
    private Function<Long, String> labelCreator;
    private RangeListener rangeListener;


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

        setIncludeZeroY(true);
        setMinSizeY(Y_GUIDES_COUNT);
        setInsets(0, 0, 0, (int) dpToPx(18f));
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public void setLabelCreator(Function<Long, String> labelCreator) {
        this.labelCreator = labelCreator;
    }

    public void setRangeListener(RangeListener rangeListener) {
        this.rangeListener = rangeListener;
    }


    public void setChart(Chart chart) {
        // Computing X labels
        int size = chart.x.length;
        xLabels = new ArrayList<>(size);

        // Computing max levels for all label positions
        for (int i = 0; i < size; i++) {
            String title = labelCreator.call(chart.x[i]);
            int pos = direction > 0 ? i : size - 1 - i;
            int level = getLabelMaxLevel(pos);
            xLabels.add(new XLabel(title, level));
        }

        animateLabels = false; // Do not animate on first start

        // Should be called in the end after X labels are initialized
        super.setChart(chart);
    }

    /**
     * Computes max level (powers of 2) at which label on this position should still be shown.
     */
    private int getLabelMaxLevel(int ind) {
        if (ind == 0) {
            return Integer.MAX_VALUE; // First label is always shown
        }

        // Label's max possible level is a biggest power of 2 which divides label position.
        // E.g. max level for 12 is 4, max level for 8 is 8.
        // This can be computed as a number of trailing zeros in index binary representation.
        return 1 << ChartMath.countTrailingZeroBits(ind);
    }

    @Override
    public void setRange(float fromX, float toX, boolean animateX, boolean animateY) {
        super.setRange(fromX, toX, animateX, animateY);

        initRangesIfReady();
    }


    public void snap(boolean animate) {
        // Calculating new from / to range which will nicely fit entire screen width
        final Range range = xRangeEnd;

        if (range.size() < 2 * xLabelsMinCount) {
            return; // No snapping needed
        }

        final int targetLevel = calcLabelsLevel(range.size());

        // Closest "nice" size
        final int newSize = ((int) range.size() / targetLevel) * targetLevel + 1;

        // Closest "nice" from / to range
        int newTo;
        int newFrom;

        final int chartSize = (int) chartRange.size();

        if (direction > 0) {
            newFrom = Math.round(range.from / targetLevel) * targetLevel;
            newTo = newFrom + newSize - 1;

            // Switching to other closest position if we went outside of allowed range
            if (newTo >= chartSize) {
                newTo -= targetLevel;
                newFrom -= targetLevel;
            }
        } else {
            float to = chartSize - 1 - range.to;
            newTo = chartSize - 1 - Math.round(to / targetLevel) * targetLevel;
            newFrom = newTo - newSize + 1;

            // Switching to other closest position if we went outside of allowed range
            if (newFrom < 0) {
                newTo += targetLevel;
                newFrom += targetLevel;
            }
        }

        // Applying new "nice" range
        if (range.from != newFrom || range.to != newTo) {
            setRange(newFrom, newTo, animate, animate);
        }
    }


    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        // Calculating minimum number of labels which should fit view's width.
        // E.g. if width = 320 and max width = 50 then min labels count = 3.
        xLabelsMinCount = ((int) (width / xLabelMaxWidth) + 1) / 2;
        // Min labels number cannot be less than 2
        xLabelsMinCount = Math.max(2, xLabelsMinCount);

        initRangesIfReady();
    }


    private void initRangesIfReady() {
        if (!isReady()) {
            return;
        }

        // Preparing new Y guides
        final Range yRange = yRangeEnd;
        for (int i = 0; i < Y_GUIDES_COUNT; i++) {
            yGuides.orig[i] = yRange.from + (yRange.size() - 1f) * i / (Y_GUIDES_COUNT - 1f);
        }

        // Calculating current X labels level
        xLabelsLevel = calcLabelsLevel(xRange.size());

        toggleLabelsVisibility(animateLabels);
        animateLabels = true;

        if (rangeListener != null) {
            rangeListener.onRangeChanged(xRange);
        }
    }


    private void toggleLabelsVisibility(boolean animate) {
        final int fromX = (int) Math.floor(xRange.from);
        final int toX = (int) Math.floor(xRange.to);

        for (int i = fromX; i <= toX; i++) {
            XLabel label = xLabels.get(i);

            final boolean show = label.maxLevel >= xLabelsLevel;
            final float toState = show ? 1f : 0f;

            if (label.targetState != toState) {
                label.targetState = toState;

                if (animate) {
                    label.animation = new AnimationState(show ? label.state : 1f - label.state);
                    animator.start();
                } else {
                    label.animation = null;
                    label.state = toState;
                }
            }
        }
    }


    @Override
    protected boolean onAnimationStep() {
        boolean result = super.onAnimationStep();

        // X range may change, so need to re-calc current labels level and animate them
        xLabelsLevel = calcLabelsLevel(xRange.size());
        toggleLabelsVisibility(true);

        // Calculating X labels animation states
        for (XLabel label : xLabels) {
            if (label.animation != null) {
                float animState = label.animation.getState();
                label.state = label.targetState == 1f ? animState : 1f - animState;

                if (animState == 1f) {
                    label.animation = null;
                } else {
                    result = true;
                }
            }
        }

        if (rangeListener != null) {
            rangeListener.onRangeChanged(xRange);
        }

        return result;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (!isReady()) {
            return;
        }

        final float left = getPaddingLeft();
        final float right = getWidth() - getPaddingRight();

        // Drawing Y guides
        yGuides.transform(matrix);
        for (float guideY : yGuides.transformed) {
            canvas.drawLine(left, guideY, right, guideY, yGuidesPaint);
        }

        // Drawing chart
        super.onDraw(canvas);

        // Drawing X labels
        final int fromX = (int) Math.floor(xRange.from);
        final int toX = (int) Math.floor(xRange.to);

        final float labelPosY = getHeight() - getPaddingBottom();
        final float dotPosY = yGuides.transformed[0];

        for (int i = fromX; i <= toX; i++) {
            XLabel label = xLabels.get(i);

            if (label.state > 0f) {
                int alpha = (int) (255 * label.state);
                xLabelPaint.setAlpha(alpha);
                xLabelDotPaint.setAlpha(alpha);

                float dotPosX = ChartMath.mapX(matrix, i);
                float width = getLabelWidth(label);

                // Shifting label X pos according to their position on screen to fit in chart width
                float labelShift = (dotPosX - left) / (right - left);
                float labelPosX = dotPosX - width * labelShift;

                canvas.drawText(label.title, labelPosX, labelPosY, xLabelPaint);
                canvas.drawPoint(dotPosX, dotPosY, xLabelDotPaint);
            }
        }
    }


    private int calcLabelsLevel(float size) {
        // Number of labels we can shown at the same time without hiding them is
        // [xLabelsMinCount, 2 * xLabelsMinCount - 1).
        // If there are more x values needs to be shown then we have to hide some of the labels.
        // Overall we will only show every 2^x (== xLabelsLevel) labels, so we need to find out
        // what level should be used for given number of x values.

        if (size < 2 * xLabelsMinCount) {
            // Maximum number of labels we can show without hiding them is (2 * xLabelsMinCount - 1)
            return 1;
        } else {
            // Current level can be calculated as a maximum power of 2 that is less or equal to
            // the number of items in maximum possible interval.
            double intervalSize = (size - 2.0) / (xLabelsMinCount - 1.0);
            double log2 = Math.log(intervalSize) / Math.log(2.0);
            return (int) Math.pow(2.0, (int) log2);
        }
    }

    private float getLabelWidth(XLabel label) {
        // Measuring requested label (if not measured yet)
        if (Float.isNaN(label.width)) {
            xLabelPaint.getTextBounds(label.title, 0, label.title.length(), textBounds);
            label.width = textBounds.width();
        }
        return label.width;
    }


    private static class XLabel {
        final String title;
        final int maxLevel;

        float width = Float.NaN;

        float state;
        float targetState;
        AnimationState animation;

        XLabel(String title, int maxLevel) {
            this.title = title;
            this.maxLevel = maxLevel;
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
        void onRangeChanged(Range range);
    }

}
