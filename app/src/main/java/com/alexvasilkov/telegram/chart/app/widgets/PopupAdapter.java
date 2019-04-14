package com.alexvasilkov.telegram.chart.app.widgets;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TextView;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.ColorUtils;
import com.alexvasilkov.telegram.chart.widget.ChartView;
import com.alexvasilkov.telegram.chart.widget.ChartView.Formatter;

import java.util.Locale;

public class PopupAdapter extends ChartView.PopupAdapter<PopupAdapter.ViewHolder> {

    private static final float MIN_VALUE_ALPHA = 0.33f;

    private final Context context;

    private boolean colorsSet;
    private int popupColor;
    private int textColor;

    private OnPopupClickListener clickListener;
    private Formatter dateFormat;
    private Formatter valueFormat;

    private Chart chart;
    private int chartIndex;

    public PopupAdapter(Context context) {
        this.context = context;
    }

    public void setColors(int popup, int text) {
        this.colorsSet = true;
        this.popupColor = popup;
        this.textColor = text;
    }

    public void setClickListener(OnPopupClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setDateFormat(Formatter dateFormat) {
        this.dateFormat = dateFormat;
    }

    public void setValueFormat(Formatter valueFormat) {
        this.valueFormat = valueFormat;
    }


    @Override
    protected ViewHolder createView(ViewGroup parent, Chart chart) {
        final int itemsCount = chart.sources.length + (showTotal(chart) ? 1 : 0);

        ViewHolder holder = new ViewHolder(parent, LayoutInflater.from(context), itemsCount);
        if (clickListener != null) {
            holder.itemView.setOnClickListener(view -> onPopupClicked());
            holder.arrow.setVisibility(View.VISIBLE);
        }

        holder.itemsTable.setColumnStretchable(1, true);
        holder.itemsTable.setColumnCollapsed(0, !showPercent(chart));

        return holder;
    }

    private void onPopupClicked() {
        if (clickListener != null && chart != null) {
            clickListener.onPopupClick(chart, chartIndex);
        }
    }

    @Override
    protected void bindView(
            ViewHolder holder, Chart chart, boolean[] visibilities, int index, boolean animate) {

        this.chart = chart;
        this.chartIndex = index;
        final long date = chart.x[index];

        if (colorsSet) {
            holder.title.setTextColor(textColor);

            final Drawable back = holder.itemView.getBackground().mutate();
            back.setColorFilter(popupColor, PorterDuff.Mode.MULTIPLY);
            holder.itemView.setBackground(back);
        }

        holder.title.setText(dateFormat == null ? String.valueOf(date) : dateFormat.format(date));

        int totalValue = 0;
        for (int i = 0, size = chart.sources.length; i < size; i++) {
            totalValue += visibilities[i] ? chart.sources[i].y[index] : 0;
        }

        for (int i = 0, size = chart.sources.length; i < size; i++) {
            final ObjectAnimator anim = holder.itemsAnim[i];
            final Source source = chart.sources[i];
            final int value = source.y[index];

            if (colorsSet) {
                holder.items[i].setTextColor(textColor);
            }

            holder.items[i].name.setText(source.name);
            holder.items[i].value.setText(valueFormat.format(value));
            holder.items[i].value.setTextColor(ColorUtils.darken(source.color));

            if (showPercent(chart)) {
                String percentValue = visibilities[i] && totalValue > 0
                        ? String.format(Locale.US, "%.0f%%", 100f * value / totalValue)
                        : "-";
                holder.items[i].percent.setText(percentValue);
            }

            final float targetAlpha = visibilities[i] ? 1f : MIN_VALUE_ALPHA;
            if (animate) {
                // Animating hidden sources's value
                anim.setFloatValues(targetAlpha);
                anim.start();
            } else {
                anim.cancel();
                holder.items[i].layout.setAlpha(targetAlpha);
            }
        }

        if (showTotal(chart)) {
            final ViewHolder.Item total = holder.items[chart.sources.length];

            if (colorsSet) {
                total.setTextColor(textColor);
            }

            total.name.setText(R.string.all);
            total.value.setText(valueFormat.format(totalValue));
        }
    }


    private static boolean showTotal(Chart chart) {
        return chart.type == Chart.Type.BARS && chart.sources.length > 1;
    }

    private static boolean showPercent(Chart chart) {
        return chart.type == Chart.Type.AREA;
    }

    public interface OnPopupClickListener {
        void onPopupClick(Chart chart, int index);
    }

    static class ViewHolder extends ChartView.PopupViewHolder {

        final TextView title = itemView.findViewById(R.id.chart_popup_title);
        final View arrow = itemView.findViewById(R.id.chart_popup_arrow);
        final TableLayout itemsTable = itemView.findViewById(R.id.chart_popup_sources);

        final Item[] items;
        final ObjectAnimator[] itemsAnim;

        @SuppressLint("InflateParams")
        ViewHolder(ViewGroup parent, LayoutInflater inflater, int size) {
            super(inflater.inflate(R.layout.chart_popup, parent, false));

            items = new Item[size];
            itemsAnim = new ObjectAnimator[size];

            for (int i = 0; i < size; i++) {
                final View layout = inflater.inflate(R.layout.chart_popup_item, itemsTable, false);
                items[i] = new Item(layout);
                itemsAnim[i] = ObjectAnimator.ofFloat(layout, View.ALPHA, 1f);
                itemsTable.addView(layout);
            }
        }

        static class Item {
            final View layout;
            final TextView percent;
            final TextView name;
            final TextView value;

            Item(View layout) {
                this.layout = layout;
                this.percent = layout.findViewById(R.id.chart_popup_source_percent);
                this.name = layout.findViewById(R.id.chart_popup_source_name);
                this.value = layout.findViewById(R.id.chart_popup_source_value);
            }

            void setTextColor(int color) {
                percent.setTextColor(color);
                name.setTextColor(color);
                value.setTextColor(color);
            }
        }

    }

}
