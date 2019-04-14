package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.content.Context;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.data.ChartsLoader.Type;

public class InteractionsWidget extends BaseChartWidget {

    public InteractionsWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        main.titleText.setText(R.string.chart_title_interactions);

        ChartsLoader.loadChart(context, Type.INTERACTIONS, this::setMainChart);
    }

}
