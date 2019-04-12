package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.content.Context;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.data.ChartsLoader.Type;

public class AppsWidget extends BaseChartWidget {

    public AppsWidget(Context context, AttributeSet attrs) {
        super(context, attrs, R.layout.chart_widget);

        titleView.setText(R.string.chart_title_apps);
        chartView.setGuideCount(5);

        ChartsLoader.loadChart(context, Type.APPS, this::setChart);
    }

}
