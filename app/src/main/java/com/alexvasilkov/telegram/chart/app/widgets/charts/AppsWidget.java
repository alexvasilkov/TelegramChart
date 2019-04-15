package com.alexvasilkov.telegram.chart.app.widgets.charts;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.app.utils.Colors;
import com.alexvasilkov.telegram.chart.data.ChartsLoader;
import com.alexvasilkov.telegram.chart.data.ChartsLoader.Type;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.domain.FormatterValue;
import com.alexvasilkov.telegram.chart.utils.ColorUtils;
import com.alexvasilkov.telegram.chart.widget.BaseChartView;
import com.alexvasilkov.telegram.chart.widget.ChartView.PopupAdapterSource;
import com.alexvasilkov.telegram.chart.widget.ChartView.PopupViewHolder;

import java.util.Calendar;

public class AppsWidget extends BaseChartWidget {

    private static final Type TYPE = Type.APPS;

    private final SourcePopupAdapter detailsPopupAdapter;

    public AppsWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        main.titleText.setText(R.string.chart_title_apps);
        main.chartView.setGuideCount(5);

        detailsPopupAdapter = new SourcePopupAdapter(formatters::formatNumber);
        details.chartView.setPopupAdapterSource(detailsPopupAdapter);

        ChartsLoader.loadChart(context, TYPE, this::setMainChart);
    }

    @Override
    void onRequestDetails(long date) {
        final long[] dates = new long[] { date };
        final int detailsDays = 6;
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

    @Override
    public void setColors(Colors colors) {
        super.setColors(colors);

        detailsPopupAdapter.setColors(colors.popup, colors.text);
        details.chartView.updatePopupSource();
    }

    static class SourcePopupAdapter extends PopupAdapterSource<SourcePopupAdapter.ViewHolder> {

        private final FormatterValue formatter;

        private boolean colorsSet;
        private int popupColor;
        private int textColor;

        SourcePopupAdapter(FormatterValue formatter) {
            this.formatter = formatter;
        }

        void setColors(int popup, int text) {
            this.colorsSet = true;
            this.popupColor = popup;
            this.textColor = text;
        }

        @Override
        protected ViewHolder createView(ViewGroup parent, Chart chart) {
            return new ViewHolder(parent, LayoutInflater.from(parent.getContext()));
        }

        @Override
        protected void bindView(
                ViewHolder holder, Chart chart, int sourceInd, int from, int to, boolean animate) {

            if (colorsSet) {
                holder.name.setTextColor(textColor);

                final Drawable back = holder.itemView.getBackground().mutate();
                back.setColorFilter(popupColor, PorterDuff.Mode.MULTIPLY);
                holder.itemView.setBackground(back);
            }

            final Source source = chart.sources[sourceInd];
            int total = 0;
            for (int i = from; i < to; i++) {
                total += source.y[i];
            }

            holder.name.setText(source.name);

            holder.value.setText(formatter.format(total, 0));
            holder.value.setTextColor(ColorUtils.darken(source.color));
        }

        static class ViewHolder extends PopupViewHolder {

            final TextView name;
            final TextView value;

            @SuppressLint("InflateParams")
            ViewHolder(ViewGroup parent, LayoutInflater inflater) {
                super(inflater.inflate(R.layout.chart_source_popup, parent, false));

                this.name = itemView.findViewById(R.id.chart_popup_source_name);
                this.value = itemView.findViewById(R.id.chart_popup_source_value);
            }
        }

    }

}
