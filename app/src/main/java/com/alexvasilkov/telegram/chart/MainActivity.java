package com.alexvasilkov.telegram.chart;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

import java.util.List;


public class MainActivity extends Activity {

    private Preferences prefs;
    private ChartFinderView chartFinderView;
    private ViewGroup linesGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = new Preferences(this);
        setNightMode(prefs.isInNightMode());

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        final ChartView chartView = findViewById(R.id.chart_view);
        chartFinderView = findViewById(R.id.chart_finder_view);
        linesGroup = findViewById(R.id.chart_lines);

        chartView.setDirection(-1);
        chartView.setXLabelCreator(this::formatDate);
        chartView.setYLabelCreator(String::valueOf);

        chartFinderView.attachTo(chartView);

        ChartsLoader.loadCharts(
                getApplicationContext(),
                (List<Chart> charts) -> setChart(charts.get(0)),
                Throwable::printStackTrace
        );
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.menu_night_mode);
        item.setIcon(R.drawable.ic_night_mode);
        item.getIcon().setTint(Color.WHITE);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean isInNightMode = !prefs.isInNightMode();
        prefs.setInNightMode(isInNightMode);
        setNightMode(isInNightMode);
        recreate();
        return true;
    }


    private void setChart(Chart chart) {
        chartFinderView.setChart(chart);

        linesGroup.removeAllViews();

        for (int i = 0, size = chart.lines.size(); i < size; i++) {
            final Chart.Line line = chart.lines.get(i);
            final int pos = i;

            CheckBox check = (CheckBox) getLayoutInflater()
                    .inflate(R.layout.chart_line_item, linesGroup, false);

            check.setButtonTintList(ColorStateList.valueOf(line.color));
            check.setText(line.name);
            check.setChecked(true);
            check.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) ->
                    chartFinderView.setLine(pos, isChecked, true));

            linesGroup.addView(check);
        }
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
