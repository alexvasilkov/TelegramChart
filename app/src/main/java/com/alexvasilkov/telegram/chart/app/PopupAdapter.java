package com.alexvasilkov.telegram.chart.app;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.widget.ChartView;

class PopupAdapter extends ChartView.PopupAdapter<PopupAdapter.ViewHolder> {

    private static final float MIN_VALUE_ALPHA = 0.33f;

    private final Context context;

    PopupAdapter(Context context) {
        this.context = context;
    }

    @Override
    protected ViewHolder createView(int linesCount) {
        return new ViewHolder(LayoutInflater.from(context), linesCount);
    }

    @SuppressWarnings("ConstantConditions")
    @SuppressLint("SetTextI18n")
    @Override
    protected void bindView(
            ViewHolder holder, Chart chart, boolean[] visibilities, int index, boolean animate) {

        holder.title.setText(formatPopupDate(chart.x[index]));

        for (int i = 0, size = chart.lines.size(); i < size; i++) {
            final TextView text = holder.items[i];
            final ObjectAnimator anim = holder.itemsAnim[i];
            final Chart.Line line = chart.lines.get(i);

            text.setTextColor(line.color);
            text.setText(line.y[index] + "\n" + line.name);

            final float targetAlpha = visibilities[i] ? 1f : MIN_VALUE_ALPHA;
            if (animate) {
                // Animating hidden line's value
                anim.setFloatValues(targetAlpha);
                anim.start();
            } else {
                anim.cancel();
                text.setAlpha(targetAlpha);
            }

            // Setting max width of the text view to prevent it from constantly changing its size
            setMinWidth(text, findMax(line.y));
        }
    }

    private int findMax(int[] values) {
        int max = Integer.MIN_VALUE;
        for (int value : values) {
            max = max < value ? value : max;
        }
        return max;
    }

    private void setMinWidth(TextView view, int max) {
        int width = (int) view.getPaint().measureText(String.valueOf(max));
        view.setMinWidth(width);
    }

    private String formatPopupDate(long timestamp) {
        int flags = DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_NO_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH
                | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_ABBREV_WEEKDAY;

        return DateUtils.formatDateTime(context, timestamp, flags);
    }


    static class ViewHolder extends ChartView.PopupViewHolder {

        final TextView title = itemView.findViewById(R.id.chart_popup_title);
        final TextView[] items;
        final ObjectAnimator[] itemsAnim;

        @SuppressLint("InflateParams")
        ViewHolder(LayoutInflater inflater, int size) {
            super(inflater.inflate(R.layout.chart_popup, null));

            final ViewGroup group = itemView.findViewById(R.id.chart_popup_items);
            items = new TextView[size];
            itemsAnim = new ObjectAnimator[size];

            for (int i = 0; i < size; i++) {
                final TextView item =
                        (TextView) inflater.inflate(R.layout.chart_popup_item, group, false);
                items[i] = item;
                itemsAnim[i] = ObjectAnimator.ofFloat(item, View.ALPHA, 1f);
                group.addView(item);
            }
        }

    }

}