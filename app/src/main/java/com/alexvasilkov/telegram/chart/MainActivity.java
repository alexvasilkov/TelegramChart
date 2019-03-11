package com.alexvasilkov.telegram.chart;

import android.os.Bundle;

import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.domain.Chart;
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

        final ChartView chartView = findViewById(R.id.chart_view);
        // TODO: Find a better format
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("MMM d", Locale.US);

        ChartsLoader.loadCharts(getApplicationContext(),
                (List<Chart> charts) -> chartView.setChart(charts.get(0), dateFormatter::format));
    }

}
