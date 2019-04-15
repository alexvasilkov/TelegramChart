package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.data.ChartsLoader.Type;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.widget.BaseChartView;

import java.util.Calendar;

public class AppsWidget extends BaseChartWidget {

    private static final Type TYPE = Type.APPS;

    public AppsWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        main.titleText.setText(R.string.chart_title_apps);
        main.chartView.setGuideCount(5);

        ChartsLoader.loadChart(context, TYPE, this::setMainChart);
    }

    @Override
    void onRequestDetails(long date) {
        final long[] dates = new long[] { date };
        final int detailsDays = 7;
        ChartsLoader.loadDetails(getContext(), TYPE, dates, detailsDays, this::onDetailsLoaded);
    }

    private void onDetailsLoaded(Chart[] charts) {
        setDetailsChart(charts[0].setType(Chart.Type.PIE));
    }


    @Override
    void animateChart(
            BaseChartView fromView, Matrix fromMatrix, Chart fromChart,
            BaseChartView toView, Matrix toMatrix, Chart toChart,
            long date, Calendar calendar, boolean show
    ) {
        if (fromView == main.chartView) {

            final ValueAnimator animator = ValueAnimator.ofFloat(show ? 0f : 1f, show ? 1f : 0f);
            animator.setDuration(ANIMATION_DURATION);
            animator.addUpdateListener(anim -> {
                final float state = (float) anim.getAnimatedValue();

                final float fromScale = 1f * (1f - state) + 0.5f * state;
                fromView.setScaleX(fromScale);
                fromView.setScaleY(fromScale);

                final float toScale = 1.8f * (1f - state) + 1f * state;
                toView.setScaleX(toScale);
                toView.setScaleY(toScale);
            });
            animator.start();

        } else {
            super.animateChart(
                    fromView, fromMatrix, fromChart,
                    toView, toMatrix, toChart,
                    date, calendar, show
            );
        }
    }

}
