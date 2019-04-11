package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.content.Context;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.data.ChartsLoader.Type;

public class ViewsWidget extends BaseChartWidget {

    public ViewsWidget(Context context, AttributeSet attrs) {
        super(context, attrs, R.layout.chart_widget);

        titleView.setText(R.string.chart_title_views);

        ChartsLoader.loadChart(context, Type.VIEWS, this::setChart);
    }

}
