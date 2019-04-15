package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.data.ChartsLoader.Type;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.ColorUtils;
import com.alexvasilkov.telegram.chart.widget.BaseChartView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ViewsWidget extends BaseChartWidget {

    private static final Type TYPE = Type.VIEWS;

    public ViewsWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        main.titleText.setText(R.string.chart_title_views);

        details.chartView.setXLabelFormatter(formatters::formatTime);

        animateVisibility(sourcesGroup, false, false);
        details.finderView.setVisibility(GONE);

        getMargins(chartsContentArea).bottomMargin =
                -sourcesGroup.getMinimumHeight() - getMargins(main.finderView).topMargin;

        ChartsLoader.loadChart(context, TYPE, this::setMainChart);
    }

    @Override
    boolean showMainSources() {
        return false;
    }

    @Override
    boolean isDetailsHasSameSources() {
        return false;
    }

    @Override
    void onRequestDetails(long date) {
        final long date1 = main.chart.resolution.add(calendar, date, -1);
        final long date7 = main.chart.resolution.add(calendar, date, -7);

        final long[] dates = new long[] { date, date1, date7 };
        final int detailsDays = 1;
        ChartsLoader.loadDetails(getContext(), TYPE, dates, detailsDays, this::onDetailsLoaded);
    }

    @Override
    void showDetails(boolean show, boolean animate) {
        super.showDetails(show, animate);

        animateVisibility(sourcesGroup, show, animate);
    }

    private void onDetailsLoaded(Chart[] charts) {
        final Chart first = charts[0]; // Should not be null

        final float[] extraColorSat = new float[] { 0f, 0.2f, -0.2f };
        final float[] extraColorVal = new float[] { 0f, -0.1f, -0.1f };

        final List<Source> sources = new ArrayList<>();

        for (int i = 0; i < charts.length; i++) {
            final Chart chart = charts[i];
            if (chart != null && chart.x.length > 1) {
                final Source source = chart.sources[0];
                sources.add(new Source(
                        formatters.formatDateShort(chart.x[0]),
                        ColorUtils.adjust(source.color, extraColorSat[i], extraColorVal[i]),
                        padValuesOnStart(source.y, first.x.length)
                ));
            }
        }

        setDetailsChart(first.setSources(sources.toArray(new Source[0])));
    }

    private static int[] padValuesOnStart(int[] values, int size) {
        final int[] result = new int[size];
        final int length = Math.min(values.length, size);
        System.arraycopy(values, 0, result, size - length, length);
        return result;
    }

    private static MarginLayoutParams getMargins(View view) {
        return (MarginLayoutParams) view.getLayoutParams();
    }


    @Override
    void animateChart(
            BaseChartView fromView, Matrix fromMatrix, Chart fromChart,
            BaseChartView toView, Matrix toMatrix, Chart toChart,
            long date, Calendar calendar, boolean show
    ) {

        if (fromView == main.finderView) {
            float scaleMain = show ? 0.75f : 1f;

            main.finderView.animate()
                    .setDuration(ANIMATION_DURATION)
                    .scaleX(scaleMain)
                    .scaleY(scaleMain);

            sourcesGroup.setPivotX(0f);
            sourcesGroup.setPivotY(sourcesGroup.getHeight());

            float scaleSourcesFrom = show ? 0.75f : 1f;
            float scaleSources = show ? 1f : 0.75f;

            for (int i = 0; i < sourcesGroup.getChildCount(); i++) {
                View child = sourcesGroup.getChildAt(i);

                child.setScaleX(scaleSourcesFrom);
                child.setScaleY(scaleSourcesFrom);

                child.animate()
                        .setDuration(ANIMATION_DURATION)
                        .scaleX(scaleSources)
                        .scaleY(scaleSources);
            }

        } else {
            super.animateChart(
                    fromView, fromMatrix, fromChart,
                    toView, toMatrix, toChart,
                    date, calendar, show
            );
        }
    }
}
