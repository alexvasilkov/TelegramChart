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

    public final float lineWidth;
    public final float pointRadius;
    public final int pointColor;

    public ChartStyle(Context context, AttributeSet attrs) {
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.BaseChartView);

        lineWidth = arr.getDimension(R.styleable.BaseChartView_chart_lineWidth,
                dpToPx(context, 2f));

        pointColor = arr.getColor(R.styleable.BaseChartView_chart_pointColor, Color.WHITE);

        pointRadius = arr.getDimension(R.styleable.BaseChartView_chart_pointRadius,
                dpToPx(context, 4f));

        arr.recycle();
    }


    public static float dpToPx(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

}
