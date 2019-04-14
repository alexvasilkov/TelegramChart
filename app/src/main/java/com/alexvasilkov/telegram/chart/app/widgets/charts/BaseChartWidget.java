package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
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
import com.alexvasilkov.telegram.chart.domain.GroupBy;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.ColorUtils;
import com.alexvasilkov.telegram.chart.widget.BaseChartView;
import com.alexvasilkov.telegram.chart.widget.ChartFinderView;
import com.alexvasilkov.telegram.chart.widget.ChartView;

import java.util.Calendar;
import java.util.TimeZone;

public abstract class BaseChartWidget extends FrameLayout {

    private static final long ANIMATION_DURATION = 300L;
    private final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    final Holder main;
    final Holder details;
    final ViewGroup sourcesGroup;

    final Formatters formatters = new Formatters(getContext());

    private boolean darken;

    private Boolean detailsShown = null;
    private long detailsDate;

    public BaseChartWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.chart_widget, this, true);

        setAlpha(0f);

        main = new Holder(
                findViewById(R.id.chart_layout),
                findViewById(R.id.chart_title),
                findViewById(R.id.chart_range),
                findViewById(R.id.chart_view),
                findViewById(R.id.chart_finder_view),
                new PopupAdapter(getContext()),
                formatters
        );

        details = new Holder(
                findViewById(R.id.chart_details_layout),
                findViewById(R.id.chart_details_title),
                findViewById(R.id.chart_details_range),
                findViewById(R.id.chart_details_view),
                findViewById(R.id.chart_details_finder_view),
                new PopupAdapter(getContext()),
                formatters
        );

        sourcesGroup = findViewById(R.id.chart_sources);

        main.chartView.setXLabelFormatter(formatters::formatDateShort);
        main.chartView.groupBy(GroupBy.MONTH);
        main.finderView.groupBy(GroupBy.MONTH, 2, 12, 4, Gravity.END, false);
        main.popupAdapter.setDateFormat(formatters::formatDateLong);
        main.popupAdapter.setClickListener(
                (chart, index) -> onRequestDetails(detailsDate = chart.x[index]));

        details.titleText.setOnClickListener(view -> showDetails(false, true));
        details.chartView.setXLabelFormatter(time -> {
            if (GroupBy.DAY.isStart(calendar, time)) {
                return formatters.formatDateShort(time);
            } else {
                return formatters.formatTime(time);
            }
        });
        details.chartView.groupBy(GroupBy.DAY);
        details.finderView.groupBy(GroupBy.DAY, 1, Integer.MAX_VALUE, 1, Gravity.CENTER, true);
        details.popupAdapter.setDateFormat(formatters::formatTime);
    }

    boolean isDetailsHasSameSources() {
        return true;
    }

    void setMainChart(Chart chart) {
        animate().setDuration(400L).alpha(1f);
        main.chart = chart;
        main.finderView.setChart(chart);

        showDetails(false, false);
    }

    void setDetailsChart(Chart chart) {
        details.chart = chart;
        details.finderView.setChart(chart);

        if (isDetailsHasSameSources()) {
            details.finderView.setSourceVisibility(main.finderView.getSourcesVisibility(), false);
        }

        showDetails(true, true);
    }

    void onRequestDetails(long date) {} // TODO: Make abstract once all widgets are implemented

    void showDetails(boolean show, boolean animate) {
        if (detailsShown != null && detailsShown == show) {
            return;
        }

        if (!isDetailsHasSameSources()) {
            Holder holder = show ? details : main;
            showSources(holder.chart.sources, holder.chartView.getSourcesVisibility());
        } else if (detailsShown == null) {
            showSources(main.chart.sources, main.chartView.getSourcesVisibility());
        }

        detailsShown = show;

        animateVisibility(main.layout, !show, animate);
        animateVisibility(details.layout, show, animate);

        final float minScale = 0.5f;

        animateScale(main.titleText, Gravity.TOP | Gravity.START, show ? minScale : 1f, animate);
        animateScale(main.rangeText, Gravity.TOP | Gravity.END, show ? minScale : 1f, animate);

        animateScale(
                details.titleText, Gravity.BOTTOM | Gravity.START, show ? 1f : minScale, animate);
        animateScale(
                details.rangeText, Gravity.BOTTOM | Gravity.END, show ? 1f : minScale, animate);

        if (animate) {
            animateChart(
                    main.chartView, main.matrixChart, main.chart,
                    details.chartView, details.matrixChart, details.chart,
                    detailsDate, calendar, show
            );
            animateChart(
                    main.finderView, main.matrixFinder, main.chart,
                    details.finderView, details.matrixFinder, details.chart,
                    detailsDate, calendar, show
            );
        }
    }

    private void animateVisibility(View view, boolean show, boolean animate) {
        if (animate) {
            if (show) {
                view.setVisibility(VISIBLE);
            }

            view.animate()
                    .setDuration(ANIMATION_DURATION)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!show) {
                                view.setVisibility(INVISIBLE);
                            }
                        }
                    })
                    .alpha(show ? 1f : 0f);
        } else {
            view.setVisibility(show ? VISIBLE : INVISIBLE);
            view.setAlpha(show ? 1f : 0f);
        }
    }

    private void animateScale(View view, int gravity, float scale, boolean animate) {
        boolean right = (gravity & Gravity.END) == Gravity.END;
        boolean bottom = (gravity & Gravity.BOTTOM) == Gravity.BOTTOM;

        view.setPivotX(right ? view.getWidth() : 0f);
        view.setPivotY(bottom ? view.getHeight() : 0f);

        if (animate) {
            view.animate()
                    .setDuration(ANIMATION_DURATION)
                    .scaleX(scale).scaleY(scale);
        } else {
            view.setScaleX(scale);
            view.setScaleY(scale);
        }
    }

    private static void animateChart(
            BaseChartView fromView, Matrix fromMatrix, Chart fromChart,
            BaseChartView toView, Matrix toMatrix, Chart toChart,
            long date, Calendar calendar, boolean show) {

        fromMatrix.reset();
        fromView.setChartMatrixExtra(fromMatrix);

        toMatrix.reset();
        toView.setChartMatrixExtra(toMatrix);

        final float fromWidth = ChartMath.mapX(fromView.getChartMatrix(), 0f)
                - ChartMath.mapX(fromView.getChartMatrix(), 1f);

        final long toDateEnd = fromChart.resolution.add(calendar, date, 1);
        final float steps = toChart.resolution.distance(date, toDateEnd);

        final float toWidth = ChartMath.mapX(toView.getChartMatrix(), 0f)
                - ChartMath.mapX(toView.getChartMatrix(), steps);

        final float scale = Math.min(toWidth / fromWidth, 4f);

        final float fromPos = fromChart.resolution.distance(fromChart.x[0], date);
        final float fromPivot = ChartMath.mapX(fromView.getChartMatrix(), fromPos);

        final float toPos = toChart.resolution.distance(toChart.x[0], date) + 0.5f * steps;
        final float toPivot = ChartMath.mapX(toView.getChartMatrix(), toPos);

        final ValueAnimator animator = ValueAnimator.ofFloat(show ? 0f : 1f, show ? 1f : 0f);
        animator.setDuration(ANIMATION_DURATION);
        animator.addUpdateListener(anim -> {
            final float state = (float) anim.getAnimatedValue();

            fromMatrix.reset();
            final float fromTranslateX = state * (toPivot - fromPivot);
            fromMatrix.postTranslate(fromTranslateX, 0f);
            final float fromScaleX = 1f - state + state * scale;
            fromMatrix.postScale(fromScaleX, 1f, fromPivot + fromTranslateX, 0f);
            fromView.setChartMatrixExtra(fromMatrix);

            toMatrix.reset();
            final float toTranslateX = (1f - state) * (fromPivot - toPivot);
            toMatrix.postTranslate((1f - state) * (fromPivot - toPivot), 0f);
            final float toScaleX = state + (1f - state) / scale;
            toMatrix.postScale(toScaleX, 1f, toPivot + toTranslateX, 0f);
            toView.setChartMatrixExtra(toMatrix);
        });
        animator.start();
    }


    public void setColors(Colors colors) {
        setBackgroundColor(colors.background);

        setColors(main, colors);
        setColors(details, colors);

        details.titleText.setTextColor(colors.textHighlight);

        final Drawable zoomIcon = details.titleText.getCompoundDrawables()[0].mutate();
        zoomIcon.setColorFilter(colors.textHighlight, PorterDuff.Mode.SRC_IN);
        details.titleText.setCompoundDrawablesWithIntrinsicBounds(zoomIcon, null, null, null);

        darken = colors.isDark;
        updateCheckboxColors();
    }

    private void setColors(Holder holder, Colors colors) {
        holder.titleText.setTextColor(colors.text);
        holder.rangeText.setTextColor(colors.text);

        holder.chartView.setBaseColors(colors.isDark, colors.background,
                colors.chartGuides, colors.chartSelectionMask);
        holder.chartView.setColors(colors.chartGuides, colors.chartLabels, colors.chartGuides);

        holder.finderView.setBaseColors(colors.isDark, colors.background,
                colors.chartGuides, colors.chartSelectionMask);
        holder.finderView.setColors(colors.finderForeground, colors.finderFrame, colors.background);

        holder.popupAdapter.setColors(colors.popup, colors.text);
        holder.chartView.updateSelectionPopupContent();
    }

    private void showSources(Source[] sources, boolean[] states) {
        sourcesGroup.removeAllViews();

        if (sources == null) {
            return;
        }

        for (int i = 0, size = sources.length; i < size; i++) {
            final Source source = sources[i];
            final int pos = i;

            final CheckBox check = (CheckBox) LayoutInflater.from(sourcesGroup.getContext())
                    .inflate(R.layout.chart_source_item, sourcesGroup, false);

            check.setTag(source.color);
            check.setText(source.name);

            check.setChecked(states[i]);
            updateCheckState(check);

            check.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) -> {
                if (isDetailsHasSameSources() || !detailsShown) {
                    main.finderView.setSourceVisibility(pos, isChecked, true);
                }
                if (detailsShown) {
                    details.finderView.setSourceVisibility(pos, isChecked, true);
                }
                updateCheckState(check);
            });

            check.setOnLongClickListener(view -> checkExclusively(check));

            sourcesGroup.addView(check);
        }

        updateCheckboxColors();
    }

    private void updateCheckboxColors() {
        final int[][] textStates = new int[][] {
                new int[] { android.R.attr.state_checked }, // Checked
                new int[] {}  // Default
        };

        final int defaultTextColor = Color.WHITE;


        for (int i = 0, size = sourcesGroup.getChildCount(); i < size; i++) {
            final CheckBox check = (CheckBox) sourcesGroup.getChildAt(i);
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
        for (int i = 0, size = sourcesGroup.getChildCount(); i < size; i++) {
            final CheckBox check = (CheckBox) sourcesGroup.getChildAt(i);
            if (check != currentCheck) {
                check.setChecked(false);
            }
        }
        return true;
    }


    static class Holder {
        final View layout;
        final TextView titleText;
        final TextView rangeText;
        final ChartView chartView;
        final ChartFinderView finderView;

        final PopupAdapter popupAdapter;

        final Matrix matrixChart = new Matrix();
        final Matrix matrixFinder = new Matrix();

        Chart chart;

        Holder(
                View layout,
                TextView titleText, TextView rangeText,
                ChartView chartView, ChartFinderView finderView,
                PopupAdapter popupAdapter,
                Formatters formatters
        ) {
            this.layout = layout;
            this.titleText = titleText;
            this.rangeText = rangeText;
            this.chartView = chartView;
            this.finderView = finderView;
            this.popupAdapter = popupAdapter;

            chartView.setSelectionPopupAdapter(popupAdapter);
            finderView.attachTo(chartView);

            chartView.setYLabelFormatter(value ->
                    formatters.formatNumberAbbreviate(value, chartView.getMaxY()));

            chartView.setXRangeListener((chart, range) -> {
                final long from = chart.x[Math.round(range.from)];
                final long to = chart.x[Math.round(range.to) - 1];
                rangeText.setText(formatters.formatRangeLong(from, to));
            });

            popupAdapter.setValueFormat(formatters::formatNumber);
        }
    }

}
