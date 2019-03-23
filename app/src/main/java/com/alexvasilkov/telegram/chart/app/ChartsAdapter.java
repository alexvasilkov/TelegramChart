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
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

import java.text.NumberFormat;
import java.util.List;

class ChartsAdapter {

    private final NumberFormat yFormat = NumberFormat.getIntegerInstance();

    View createView(ViewGroup parent, Chart chart, int pos) {
        final Context context = parent.getContext();
        final View layout = LayoutInflater.from(context)
                .inflate(R.layout.chart_item, parent, false);

        final TextView title = layout.findViewById(R.id.chart_title);
        final ChartView chartView = layout.findViewById(R.id.chart_view);
        final ChartFinderView chartFinderView = layout.findViewById(R.id.chart_finder_view);
        final ViewGroup linesGroup = layout.findViewById(R.id.chart_lines);

        title.setText(context.getString(R.string.chart_name, pos));

        chartView.setXLabelFormatter((long time) -> formatDate(context, time));
        chartView.setYLabelFormatter(yFormat::format);
        chartView.setSelectionPopupAdapter(new PopupAdapter(context));

        chartFinderView.attachTo(chartView);
        chartFinderView.setChart(chart);
        showLines(chart.lines, linesGroup, chartFinderView);

        return layout;
    }

    private static void showLines(
            List<Chart.Line> lines, ViewGroup linesGroup, ChartFinderView chartFinderView) {
        linesGroup.removeAllViews();

        if (lines == null) {
            return;
        }

        for (int i = 0, size = lines.size(); i < size; i++) {
            final Chart.Line line = lines.get(i);
            final int pos = i;

            final CheckBox check = (CheckBox) LayoutInflater.from(linesGroup.getContext())
                    .inflate(R.layout.chart_line_item, linesGroup, false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                check.setButtonTintList(ColorStateList.valueOf(line.color));
            }
            check.setText(line.name);
            check.setChecked(true);
            check.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) ->
                    chartFinderView.setLine(pos, isChecked, true));

            linesGroup.addView(check);
        }
    }

    private static String formatDate(Context context, long timestamp) {
        int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_NO_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH;

        return DateUtils.formatDateTime(context, timestamp, flags);
    }

}
