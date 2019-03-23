package com.alexvasilkov.telegram.chart.app;

import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.domain.Chart;

import java.util.List;

public class ChartsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.charts_activity);
        setTitle(R.string.charts_title);
        requireActionBar().setDisplayShowTitleEnabled(true);

        final ViewGroup list = findViewById(R.id.charts_list);
        final ChartsAdapter adapter = new ChartsAdapter();

        ChartsLoader.loadCharts(
                this,
                (List<Chart> charts) -> addCharts(list, adapter, charts),
                (Throwable ex) -> Log.e("Charts", "Can't read charts", ex)
        );
    }

    private static void addCharts(ViewGroup layout, ChartsAdapter adapter, List<Chart> charts) {
        for (int i = 0, size = charts.size(); i < size; i++) {
            layout.addView(adapter.createView(layout, charts.get(i), i));
        }
    }

}
