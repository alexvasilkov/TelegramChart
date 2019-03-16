package com.alexvasilkov.telegram.chart;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;

import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

import java.util.List;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setNightMode(false);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        final ChartView chartView = findViewById(R.id.chart_view);
        final ChartFinderView chartFinderView = findViewById(R.id.chart_finder_view);

        chartView.setDirection(-1);
        chartView.setXLabelCreator(this::formatDate);
        chartView.setYLabelCreator(String::valueOf);

        chartFinderView.attachTo(chartView);

        ChartsLoader.loadCharts(
                getApplicationContext(),
                (List<Chart> charts) -> chartFinderView.setChart(charts.get(0)),
                Throwable::printStackTrace
        );
    }

    private String formatDate(long timestamp) {
        int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_NO_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH;

        return DateUtils.formatDateTime(this, timestamp, flags);
    }

    private void setNightMode(boolean isNightMode) {
        final int newNightMode = isNightMode
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;

        final Resources res = getResources();
        final Configuration config = new Configuration(res.getConfiguration());
        config.uiMode = newNightMode | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

}
