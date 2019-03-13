package com.alexvasilkov.telegram.chart;

import android.os.Bundle;

import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        setContentView(R.layout.activity_main);

        // TODO: Find a better format
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("MMM d", Locale.US);

        final ChartView chartView = findViewById(R.id.chart_view);
        final ChartFinderView chartFinderView = findViewById(R.id.chart_finder_view);

        chartView.setDirection(-1);
        chartView.setLabelCreator(dateFormatter::format);

        chartFinderView.attachTo(chartView);

        ChartsLoader.loadCharts(getApplicationContext(),
                (List<Chart> charts) -> chartFinderView.setChart(charts.get(0)));
    }

}
