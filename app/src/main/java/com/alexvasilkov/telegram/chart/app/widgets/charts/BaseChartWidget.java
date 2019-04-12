package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.app.utils.Colors;
import com.alexvasilkov.telegram.chart.app.utils.Formatters;
import com.alexvasilkov.telegram.chart.app.widgets.PopupAdapter;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.ColorUtils;
import com.alexvasilkov.telegram.chart.utils.TimeInterval;
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

public abstract class BaseChartWidget extends FrameLayout {

    final TextView titleView;
    final TextView rangeView;
    final ChartView chartView;
    final ChartFinderView finderView;
    final ViewGroup sourcesView;

    private final PopupAdapter popupAdapter;

    final Formatters formatters = new Formatters(getContext());

    private boolean darken;

    public BaseChartWidget(Context context, AttributeSet attrs, int layoutId) {
        super(context, attrs);
        setAlpha(0f);

        LayoutInflater.from(context).inflate(layoutId, this, true);

        titleView = findViewById(R.id.chart_title);
        rangeView = findViewById(R.id.chart_range);
        chartView = findViewById(R.id.chart_view);
        finderView = findViewById(R.id.chart_finder_view);
        sourcesView = findViewById(R.id.chart_sources);

        popupAdapter = new PopupAdapter(getContext());
        popupAdapter.setDateFormat(formatters::formatDateLong);
        popupAdapter.setValueFormat(formatters::formatNumber);
        chartView.setSelectionPopupAdapter(popupAdapter);
    }

    public void setChart(Chart chart) {
        animate().setDuration(400L).alpha(1f);

        chartView.setXLabelFormatter(formatters::formatDateShort);
        chartView.setYLabelFormatter(value ->
                formatters.formatNumberAbbreviate(value, chartView.getMaxY()));

        chartView.setXRangeListener(range -> {
            final long from = chart.x[(int) range.from];
            final long to = chart.x[(int) range.to];
            rangeView.setText(formatters.formatRangeLong(from, to));
        });

        finderView.attachTo(chartView);
        finderView.setTimeIntervals(TimeInterval.MONTH_BY_DAYS, 2, 12, 4, false);
        finderView.setChart(chart);
        showSources(chart.sources);
    }

    public void setColors(Colors colors) {
        setBackgroundColor(colors.background);

        titleView.setTextColor(colors.text);
        rangeView.setTextColor(colors.text);

        chartView.setBaseColors(colors.chartDarken, colors.background,
                colors.chartGuides, colors.chartSelectionMask);
        chartView.setColors(colors.chartGuides, colors.chartLabels, colors.chartGuides);

        finderView.setBaseColors(colors.chartDarken, colors.background,
                colors.chartGuides, colors.chartSelectionMask);
        finderView.setColors(colors.finderForeground, colors.finderFrame, colors.background);

        popupAdapter.setColors(colors.popup, colors.text);
        chartView.updateSelectionPopupContent();


        darken = colors.chartDarken;
        updateCheckboxColors();
    }

    private void showSources(Source[] sources) {
        sourcesView.removeAllViews();

        if (sources == null) {
            return;
        }

        for (int i = 0, size = sources.length; i < size; i++) {
            final Source source = sources[i];
            final int pos = i;

            final CheckBox check = (CheckBox) LayoutInflater.from(sourcesView.getContext())
                    .inflate(R.layout.chart_source_item, sourcesView, false);

            check.setTag(source.color);
            check.setText(source.name);

            check.setChecked(true);
            updateCheckState(check);

            check.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) -> {
                finderView.setSource(pos, isChecked, true);
                updateCheckState(check);
            });

            check.setOnLongClickListener(view -> checkExclusively(check));

            sourcesView.addView(check);
        }

        updateCheckboxColors();
    }

    private void updateCheckboxColors() {
        final int[][] textStates = new int[][] {
                new int[] { android.R.attr.state_checked }, // Checked
                new int[] {}  // Default
        };

        final int defaultTextColor = Color.WHITE;


        for (int i = 0, size = sourcesView.getChildCount(); i < size; i++) {
            final CheckBox check = (CheckBox) sourcesView.getChildAt(i);
            int color = (int) check.getTag();
            color = darken ? ColorUtils.darken(color) : color;

            final Drawable back = check.getBackground().mutate();
            back.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            check.setBackground(back);

            final ColorStateList textColors =
                    new ColorStateList(textStates, new int[] { defaultTextColor, color });
            check.setTextColor(textColors);
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

    private boolean checkExclusively(CheckBox currentCheck) {
        currentCheck.setChecked(true);
        for (int i = 0, size = sourcesView.getChildCount(); i < size; i++) {
            final CheckBox check = (CheckBox) sourcesView.getChildAt(i);
            if (check != currentCheck) {
                check.setChecked(false);
            }
        }
        return true;
    }

}
