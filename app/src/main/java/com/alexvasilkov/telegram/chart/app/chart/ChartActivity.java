package com.alexvasilkov.telegram.chart.app.chart;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.app.BaseActivity;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

import java.util.List;
import java.util.Locale;


public class ChartActivity extends BaseActivity {

    private static final String PARAM_CHART = "chart";
    private static final String PARAM_TITLE = "title";

    public static Intent createIntent(Context context, Chart chart, String title) {
        Intent intent = new Intent(context, ChartActivity.class);
        intent.putExtra(PARAM_CHART, chart);
        intent.putExtra(PARAM_TITLE, title);
        return intent;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final Chart chart = intent.getParcelableExtra(PARAM_CHART);
        final String title = intent.getStringExtra(PARAM_TITLE);

        setContentView(R.layout.chart_activity);
        setTitle(title);
        showBackButton();

        final ChartView chartView = findViewById(R.id.chart_view);
        final ChartFinderView chartFinderView = findViewById(R.id.chart_finder_view);
        final ViewGroup linesGroup = findViewById(R.id.chart_lines);
        final CheckBox dynamicRange = findViewById(R.id.chart_dynamic_range);

        chartView.setXLabelFormatter(this::formatDate);
        chartView.setYLabelFormatter(
                (long value) -> formatValue((int) value, chartView.getMaxY()));
        chartView.setSelectionPopupAdapter(new PopupAdapter(this));

        chartFinderView.attachTo(chartView);
        chartFinderView.setChart(chart);
        showLines(chart.lines, linesGroup, chartFinderView);

        dynamicRange.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) -> {
            chartFinderView.setUseDynamicRange(isChecked);
            checkAll(linesGroup);
        });
    }

    private void showLines(
            List<Chart.Line> lines, ViewGroup linesGroup, ChartFinderView chartFinderView) {
        linesGroup.removeAllViews();

        if (lines == null) {
            return;
        }

        for (int i = 0, size = lines.size(); i < size; i++) {
            final Chart.Line line = lines.get(i);
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

    private void checkAll(ViewGroup linesGroup) {
        for (int i = 0, size = linesGroup.getChildCount(); i < size; i++) {
            ((CheckBox) linesGroup.getChildAt(i)).setChecked(true);
        }
    }

    private String formatDate(long timestamp) {
        int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_NO_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH;

        return DateUtils.formatDateTime(this, timestamp, flags);
    }

    private String formatValue(int value, int max) {
        if (value == 0) {
            return "0";
        } else if (max >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000f);
        } else if (max >= 10_000) {
            return String.format(Locale.US, "%.0fK", value / 1_000f);
        } else if (max >= 1_000) {
            return String.format(Locale.US, "%.1fK", value / 1_000f);
        } else {
            return String.valueOf(value);
        }
    }

}
