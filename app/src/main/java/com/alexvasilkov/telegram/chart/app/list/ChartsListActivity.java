package com.alexvasilkov.telegram.chart.app.list;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.app.BaseActivity;
import com.alexvasilkov.telegram.chart.app.chart.ChartActivity;

import java.util.List;

public class ChartsListActivity extends BaseActivity {

    private ViewGroup chartsGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.charts_list_activity);
        setTitle(R.string.charts_list_title);

        chartsGroup = findViewById(R.id.charts_list);

        ChartsLoader.loadCharts(
                getApplicationContext(),
                this::setChartsList,
                (Throwable ex) -> Log.e("Charts", "Can't read charts", ex)

        );
    }

    private void setChartsList(List<Chart> charts) {
        chartsGroup.removeAllViews();

        for (int i = 0, size = charts.size(); i < size; i++) {
            final Chart chart = charts.get(i);
            final String title = getString(R.string.chart_name, i);

            TextView item = (TextView) getLayoutInflater()
                    .inflate(R.layout.charts_list_item, chartsGroup, false);

            item.setText(title);

            item.setOnClickListener((View view) ->
                    startActivity(ChartActivity.createIntent(this, chart, title)));

            chartsGroup.addView(item);
        }
    }

}
