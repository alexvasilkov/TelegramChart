package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.app.widgets.PopupAdapter;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.TimeInterval;
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

public abstract class BaseChartWidget extends FrameLayout {

    final TextView titleView;
    final ChartView chartView;
    final ChartFinderView finderView;
    final ViewGroup sourcesView;


    public BaseChartWidget(Context context, AttributeSet attrs, int layoutId) {
        super(context, attrs);
        setAlpha(0f);

        LayoutInflater.from(context).inflate(layoutId, this, true);

        titleView = findViewById(R.id.chart_title);
        chartView = findViewById(R.id.chart_view);
        finderView = findViewById(R.id.chart_finder_view);
        sourcesView = findViewById(R.id.chart_sources);
    }

    public void setChart(Chart chart) {
        animate().setDuration(400L).alpha(1f);

        chartView.setXLabelFormatter(this::formatDate);
        chartView.setYLabelFormatter(this::formatValue);
        chartView.setSelectionPopupAdapter(new PopupAdapter(getContext()));

        finderView.attachTo(chartView);
        finderView.setTimeIntervals(TimeInterval.MONTH_BY_DAYS, 2, 12);
        finderView.setChart(chart);
        showSources(chart.sources);
    }

    private void showSources(Source[] sources) {
        sourcesView.removeAllViews();

        if (sources == null) {
            return;
        }

        final int[][] textStates = new int[][] {
                new int[] { android.R.attr.state_checked }, // Checked
                new int[] {}  // Default
        };

        final int defaultTextColor = Color.WHITE;

        for (int i = 0, size = sources.length; i < size; i++) {
            final Source source = sources[i];
            final int pos = i;

            final CheckBox check = (CheckBox) LayoutInflater.from(sourcesView.getContext())
                    .inflate(R.layout.chart_source_item, sourcesView, false);

            final Drawable back = check.getBackground().mutate();
            back.setColorFilter(source.color, PorterDuff.Mode.SRC_IN);
            check.setBackground(back);

            final ColorStateList textColors =
                    new ColorStateList(textStates, new int[] { defaultTextColor, source.color });
            check.setTextColor(textColors);

            check.setText(source.name);

            check.setChecked(true);
            updateCheckState(check);

            check.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) -> {
                finderView.setSource(pos, isChecked, true);
                updateCheckState(check);
            });

            sourcesView.addView(check);
        }
    }

    private void updateCheckState(CheckBox check) {
        final boolean isChecked = check.isChecked();

        check.setCompoundDrawablesWithIntrinsicBounds(
                isChecked ? R.drawable.ic_check : 0, 0, 0, 0
        );

        final int left = getResources().getDimensionPixelSize(
                isChecked ? R.dimen.source_checked_left : R.dimen.source_unchecked_left);
        final int right = getResources().getDimensionPixelSize(
                isChecked ? R.dimen.source_checked_right : R.dimen.source_unchecked_right);
        check.setPadding(left, 0, right, 0);
    }

    private String formatDate(long timestamp) {
        final int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_NO_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH;

        return DateUtils.formatDateTime(getContext(), timestamp, flags);
    }

    private String formatValue(long value) {
        return String.valueOf(value); // TODO: better format
    }

}
