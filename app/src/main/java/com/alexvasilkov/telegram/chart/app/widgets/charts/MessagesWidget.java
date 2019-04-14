package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.content.Context;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.data.ChartsLoader.Type;

public class MessagesWidget extends BaseChartWidget {

    public MessagesWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        main.titleText.setText(R.string.chart_title_messages);

        ChartsLoader.loadChart(context, Type.MESSAGES, this::setMainChart);
    }

}
