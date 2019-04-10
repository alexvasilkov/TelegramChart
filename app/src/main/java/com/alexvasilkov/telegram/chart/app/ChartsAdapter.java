package com.alexvasilkov.telegram.chart.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.TimeInterval;
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

import java.text.NumberFormat;

class ChartsAdapter {

    private final NumberFormat yFormat = NumberFormat.getIntegerInstance();

    View createView(ViewGroup parent, Chart chart, int pos) {
        final Context context = parent.getContext();
        final View layout = LayoutInflater.from(context)
                .inflate(R.layout.chart_item, parent, false);

        final TextView title = layout.findViewById(R.id.chart_title);
        final ChartView chartView = layout.findViewById(R.id.chart_view);
        final ChartFinderView chartFinderView = layout.findViewById(R.id.chart_finder_view);
        final ViewGroup sourcesGroup = layout.findViewById(R.id.chart_sources);

        title.setText(context.getString(R.string.chart_name, pos));

        chartView.setXLabelFormatter((long time) -> formatDate(context, time));
        chartView.setYLabelFormatter(yFormat::format);
        chartView.setSelectionPopupAdapter(new PopupAdapter(context));

        chartFinderView.attachTo(chartView);
        chartFinderView.setTimeIntervals(TimeInterval.MONTH_BY_DAYS, 2, 6);
        chartFinderView.setChart(chart);
        showSources(chart.sources, sourcesGroup, chartFinderView);

        return layout;
    }

    private static void showSources(
            Source[] sources, ViewGroup sourcesGroup, ChartFinderView chartFinderView) {
        sourcesGroup.removeAllViews();

        if (sources == null) {
            return;
        }

        for (int i = 0, size = sources.length; i < size; i++) {
            final Source source = sources[i];
            final int pos = i;

            final CheckBox check = (CheckBox) LayoutInflater.from(sourcesGroup.getContext())
                    .inflate(R.layout.chart_source_item, sourcesGroup, false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                check.setButtonTintList(ColorStateList.valueOf(source.color));
            }
            check.setText(source.name);
            check.setChecked(true);
            check.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) ->
                    chartFinderView.setSource(pos, isChecked, true));

            sourcesGroup.addView(check);
        }
    }

    private static String formatDate(Context context, long timestamp) {
        int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_NO_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH;

        return DateUtils.formatDateTime(context, timestamp, flags);
    }

}
