package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.alexvasilkov.telegram.chart.domain.Chart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.Nullable;

public class ChartView extends View {

    private static final int yGuidesCount = 6;

    private static final float[] tmpFloatPoint = new float[2];


    private final float xLabelMaxWidth = dpToPx(50f);

    private Paint pathPaintTemplate = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    {
        pathPaintTemplate.setStyle(Paint.Style.STROKE);
        pathPaintTemplate.setStrokeWidth(dpToPx(2f));
    }

    private Paint yGuidesPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    {
        yGuidesPaint.setStyle(Paint.Style.STROKE);
        yGuidesPaint.setStrokeWidth(dpToPx(1f));
        yGuidesPaint.setColor(Color.parseColor("#F1F1F1"));
    }


    private Chart chart;
    private List<XLabel> xLabels;
    private List<Path> pathsOrig;
    private List<Path> pathsTransformed;
    private List<Paint> pathsPaints;

    private final Range xRange = new Range(0, 0);
    private final Range yRange = new Range(0, 0);
    private YGuides yGuidesOrig;
    private YGuides yGuidesTransformed;

    private int xLabelsLevel;
    private int xLabelsMinCount;

    private final Matrix chartMatrix = new Matrix();


    public ChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(false);
    }


    public void setChart(Chart chart, Function<Long, String> labelCreator) {
        this.chart = chart;

        xLabels = new ArrayList<>();
        for (int i = 0, size = chart.x.length; i < size; i++) {
            String title = labelCreator.call(chart.x[i]);
            // Computing max levels in reversed order to ensure last value is always shown
            int maxLevel = getLabelMaxLevel(size - 1 - i);
            xLabels.add(new XLabel(title, maxLevel));
        }

        pathsOrig = new ArrayList<>();
        pathsTransformed = new ArrayList<>();
        for (Chart.Line line : chart.lines) {
            Path path = new Path();
            for (int i = 0, size = line.y.length; i < size; i++) {
                if (i == 0) {
                    path.moveTo(0f, line.y[0]);
                } else {
                    path.lineTo(i, line.y[i]);
                }
            }

            pathsOrig.add(path);
            pathsTransformed.add(new Path());
        }

        pathsPaints = new ArrayList<>();
        for (Chart.Line line : chart.lines) {
            Paint paint = new Paint(pathPaintTemplate);
            paint.setColor(line.color);
            pathsPaints.add(paint);
        }

        // Show entire chart by default
        showRange(0, chart.x.length - 1);
    }


    /**
     * Specifies x-range to be shown. Chart should already be set before calling this method.
     */
    public void showRange(int from, int to) {
        xRange.set(from, to);

        // TODO: Get max in given interval only
        int maxY = Integer.MIN_VALUE;
        for (Chart.Line line : chart.lines) {
            for (int y : line.y) {
                maxY = maxY < y ? y : maxY;
            }
        }

        yRange.set(0, maxY);

        // Ensure we have enough values to show guides
        if (yRange.size() < yGuidesCount) {
            yRange.to = yRange.from + yGuidesCount;
        }

        float[] guides = new float[yGuidesCount];
        for (int i = 0; i < yGuidesCount; i++) {
            guides[i] = yRange.from + yRange.size() * i / (yGuidesCount - 1f);
        }

        yGuidesOrig = new YGuides(guides);
        yGuidesTransformed = new YGuides(Arrays.copyOf(guides, guides.length));

        prepareChartIfReady();
    }


    /**
     * Computes max level at which label on this position is still shown.
     * Where levels are powers of 2.
     */
    private int getLabelMaxLevel(int ind) {
        if (ind == 0) {
            return Integer.MAX_VALUE; // First label is always shown
        }

        // Label's max possible level is a biggest power of 2 which divides label position.
        // E.g. max level for 12 is 4, max level for 8 is 8.
        // This can be computed as a number of trailing zeros in label index binary representation.
        return ChartMath.countTrailingZeroBits(ind);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Calculating minimum number of labels which should fit view's width.
        // E.g. if width = 320 and max width = 50 then min labels count = 3.
        xLabelsMinCount = ((int) (getMeasuredWidth() / xLabelMaxWidth) + 1) / 2;
        // Min labels number cannot be less than 2
        xLabelsMinCount = Math.max(2, xLabelsMinCount);

        prepareChartIfReady();
    }


    private void prepareChartIfReady() {
        if (xLabelsMinCount == 0) {
            return; // Not measured yet
        }

        int xSize = xRange.size();
        if (xSize <= 1) {
            return; // No valid x range is set
        }

        int ySize = yRange.size();
        if (ySize <= 1) {
            return; // No valid y range is set
        }

        // Number of labels we can shown at the same time without hiding them is
        // [xLabelsMinCount, 2 * xLabelsMinCount - 1).
        // If there are more x values needs to be shown then we have to hide some of the labels.
        // Overall we will only show every 2^x (== xLabelsLevel) labels, so we need to find out
        // what level should be used for given number of x values.

        if (xSize < 2 * xLabelsMinCount) {
            // Maximum number of labels we can show without hiding them is (2 * xLabelsMinCount - 1)
            xLabelsLevel = 1;
        } else {
            // Current level can be calculated as a maximum power of 2 that is less or equal to
            // the number of items in maximum possible interval.
            double intervalSize = (xSize - 2.0) / (xLabelsMinCount - 1.0);
            double log2 = Math.log(intervalSize) / Math.log(2f);
            xLabelsLevel = (int) Math.pow(2f, (int) log2);
        }

        float chartScaleX = getMeasuredWidth() / (xSize - 1f);
        float chartScaleY = getMeasuredHeight() / (ySize - 1f);
        float left = -xRange.from * chartScaleX;

        chartMatrix.setScale(chartScaleX, chartScaleY);
        chartMatrix.postScale(1f, -1f); // Flip along X axis
        chartMatrix.postTranslate(left, getMeasuredHeight()); // Translate to place

        // Transforming paths for drawing
        for (int i = 0, size = pathsOrig.size(); i < size; i++) {
            pathsOrig.get(i).transform(chartMatrix, pathsTransformed.get(i));
        }

        for (int i = 0, size = yGuidesOrig.values.length; i < size; i++) {
            tmpFloatPoint[0] = 0f;
            tmpFloatPoint[1] = yGuidesOrig.values[i];
            chartMatrix.mapPoints(tmpFloatPoint);
            yGuidesTransformed.values[i] = tmpFloatPoint[1];
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (chart != null) { // Chart is initialized

            // Drawing guides
            for (float guideY : yGuidesTransformed.values) {
                canvas.drawLine(0f, guideY, getWidth(), guideY, yGuidesPaint);
            }

            for (int i = 0, size = pathsTransformed.size(); i < size; i++) {
                canvas.drawPath(pathsTransformed.get(i), pathsPaints.get(i));
            }
        }
    }


    private float dpToPx(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }


    private static class XLabel {
        final String title;
        final int maxLevel;

        XLabel(String title, int maxLevel) {
            this.title = title;
            this.maxLevel = maxLevel;
        }
    }

    private class YGuides {
        final float[] values;

        YGuides(float[] values) {
            this.values = values;
        }
    }

    private class Range {
        int from;
        int to;

        Range(int from, int to) {
            set(from, to);
        }

        void set(int from, int to) {
            this.from = from;
            this.to = to;
        }

        int size() {
            return to - from;
        }
    }


    public interface Function<I, O> {
        O call(I input);
    }

}
