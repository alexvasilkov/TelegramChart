package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.content.Context;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.data.ChartsLoader.Type;
import com.alexvasilkov.telegram.chart.domain.Chart;

public class MessagesWidget extends BaseChartWidget {

    private static final Type TYPE = Type.MESSAGES;

    public MessagesWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        main.titleText.setText(R.string.chart_title_messages);

        ChartsLoader.loadChart(context, TYPE, this::setMainChart);
    }

    @Override
    void onRequestDetails(long date) {
        final long[] dates = new long[] { date };
        final int detailsDays = 7;
        ChartsLoader.loadDetails(getContext(), TYPE, dates, detailsDays, this::onDetailsLoaded);
    }

    private void onDetailsLoaded(Chart[] charts) {
        setDetailsChart(charts[0]);
    }

}
