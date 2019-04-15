package com.alexvasilkov.telegram.chart.widget.style;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.alexvasilkov.telegram.chart.R;

public class ChartStyle {

    public static final int PAINT_FLAGS = Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG;

    public boolean darken;

    public final float lineWidth;
    public final float pointRadius;

    public final float selectionWidth;
    public int selectionColor;
    public int selectionMask;
    public int backgroundColorHint;

    public final float pieMinTextSize;
    public final float pieMaxTextSize;


    public ChartStyle(Context context, AttributeSet attrs) {
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.BaseChartView);

        darken = arr.getBoolean(R.styleable.BaseChartView_chart_darken, false);

        lineWidth = arr.getDimension(R.styleable.BaseChartView_chart_lineWidth,
                dpToPx(context, 2f));

        pointRadius = arr.getDimension(R.styleable.BaseChartView_chart_pointRadius,
                dpToPx(context, 4f));

        selectionWidth = arr.getDimension(R.styleable.BaseChartView_chart_selectionWidth,
                dpToPx(context, 2f));

        selectionColor = arr.getColor(R.styleable.BaseChartView_chart_selectionColor,
                Color.TRANSPARENT);

        selectionMask = arr.getColor(R.styleable.BaseChartView_chart_selectionMask,
                Color.TRANSPARENT);

        backgroundColorHint = arr.getColor(R.styleable.BaseChartView_chart_backgroundHint,
                Color.TRANSPARENT);

        pieMinTextSize = arr.getDimension(R.styleable.BaseChartView_chart_pie_minTextSize,
                dpToPx(context, 12f));

        pieMaxTextSize = arr.getDimension(R.styleable.BaseChartView_chart_pie_maxTextSize,
                dpToPx(context, 24f));

        arr.recycle();
    }

    public void setColors(boolean darken, int backgroundHint, int selection, int selectionMask) {
        this.darken = darken;
        this.selectionColor = selection;
        this.backgroundColorHint = backgroundHint;
        this.selectionMask = selectionMask;
    }


    public static float dpToPx(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

}
