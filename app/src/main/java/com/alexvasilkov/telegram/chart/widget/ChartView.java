package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.domain.Chart;

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

    private int direction = 1;

    public ChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        yGuidesPaint.setStyle(Paint.Style.STROKE);
        yGuidesPaint.setStrokeWidth(dpToPx(1f));
        yGuidesPaint.setColor(Color.parseColor("#F1F1F1"));

        xLabelPaint.setTextSize(dpToPx(14f));
        xLabelPaint.setColor(Color.BLACK);
        xLabelPaint.setTextAlign(Paint.Align.CENTER);

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


    // TODO: move out X (& Y?)label creator
    public void setChart(Chart chart, Function<Long, String> labelCreator) {
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

        setChart(chart);
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
    public void setRange(int fromX, int toX, boolean animateY) {
        super.setRange(fromX, toX, animateY);

        initRangesIfReady();
    }


    public void snap(boolean animate) {
        Range range = getRangeX();
        int size = getChart().x.length;

        // Calculating new from / to range which will nicely fit entire screen width

        if (range.size() < 2 * xLabelsMinCount) {
            return; // No snapping needed
        }

        // Closest "nice" size
        int newSize = (range.size() / xLabelsLevel) * xLabelsLevel + 1;

        // Closest "nice" from / to range
        int newTo;
        int newFrom;

        if (direction > 0) {
            newFrom = Math.round(range.from / (float) xLabelsLevel) * xLabelsLevel;
            newTo = newFrom + newSize - 1;

            // Switching to other closest position if we went outside of allowed range
            if (newTo >= size) {
                newTo -= xLabelsLevel;
                newFrom -= xLabelsLevel;
            }
        } else {
            int to = size - 1 - range.to;
            newTo = size - 1 - Math.round(to / (float) xLabelsLevel) * xLabelsLevel;
            newFrom = newTo - newSize + 1;

            // Switching to other closest position if we went outside of allowed range
            if (newFrom < 0) {
                newTo += xLabelsLevel;
                newFrom += xLabelsLevel;
            }
        }

        // Applying new "nice" range
        if (range.from != newFrom || range.to != newTo) {
            setRange(newFrom, newTo, animate);
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
        Range rangeY = getRangeY();
        for (int i = 0; i < Y_GUIDES_COUNT; i++) {
            yGuides.orig[i] = rangeY.from + (rangeY.size() - 1f) * i / (Y_GUIDES_COUNT - 1f);
        }

        // Setting up X labels
        xLabelsLevel = calcLabelsLevel(getRangeX().size());
    }

    private int calcLabelsLevel(int size) {
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
            double log2 = Math.log(intervalSize) / Math.log(2f);
            return (int) Math.pow(2f, (int) log2);
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (!isReady()) {
            return;
        }

        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();

        // Drawing Y guides
        yGuides.transform(getChartMatrix());
        for (float guideY : yGuides.transformed) {
            canvas.drawLine(left, guideY, right, guideY, yGuidesPaint);
        }

        // Drawing chart
        super.onDraw(canvas);

        // Drawing X labels
        Range rangeX = getRangeX();
        float labelPosY = getHeight() - getPaddingBottom();
        float dotPosY = yGuides.transformed[0];

        // Measure visible labels (if not measured yet)
        //for (int i = rangeX.from; i <= rangeX.to; i++) {
        //    XLabel label = xLabels.get(i);
        //    boolean show = label.maxLevel >= xLabelsLevel;
        //    if (show && Float.isNaN(label.width)) {
        //        xLabelPaint.getTextBounds(label.title, 0, label.title.length(), textBounds);
        //        label.width = textBounds.width();
        //    }
        //}

        for (int i = rangeX.from; i <= rangeX.to; i++) {
            XLabel label = xLabels.get(i);
            boolean show = label.maxLevel >= xLabelsLevel;
            if (show) {
                float labelPosX = ChartMath.mapX(getChartMatrix(), i);
                canvas.drawText(label.title, labelPosX, labelPosY, xLabelPaint);
                canvas.drawPoint(labelPosX, dotPosY, xLabelDotPaint);
            }
        }
    }

    private static class XLabel {
        final String title;
        final int maxLevel;

        XLabel(String title, int maxLevel) {
            this.title = title;
            this.maxLevel = maxLevel;
        }
    }

    private static class YGuides {

        final float[] orig;
        final float[] transformed;
        long animationStartedAt;
        float state;

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

}
