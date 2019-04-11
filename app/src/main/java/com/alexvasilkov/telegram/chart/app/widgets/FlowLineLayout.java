package com.alexvasilkov.telegram.chart.app.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLineLayout extends ViewGroup {

    public FlowLineLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int widthNoPadding = width < 0 ? 0 : width - getPaddingLeft() - getPaddingRight();

        int heightSum = 0;
        int rowHeight = 0;
        int widthSum = 0;

        final int count = getChildCount();

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);

            final int childW = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            final int childH = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            if (widthSum != 0 && widthNoPadding > 0 && widthSum + childW > widthNoPadding) {
                // New line detected
                heightSum += rowHeight;
                widthSum = 0;
                rowHeight = 0;
            }

            lp.top = heightSum;
            lp.left = widthSum;
            widthSum += childW;
            rowHeight = Math.max(rowHeight, childH);
        }

        // If width was not specified we will always have only 1 line
        if (width < 0) {
            width = widthSum + getPaddingLeft() + getPaddingRight();
        }

        setMeasuredDimension(width, heightSum + rowHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int parentLeft = getPaddingLeft();
        final int parentTop = getPaddingTop();

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            final int width = child.getMeasuredWidth();
            final int height = child.getMeasuredHeight();

            LayoutParams params = (LayoutParams) child.getLayoutParams();

            final int childLeft = parentLeft + params.left + params.leftMargin;
            final int childTop = parentTop + params.top + params.topMargin;

            child.layout(childLeft, childTop, childLeft + width, childTop + height);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams params) {
        return new LayoutParams(params);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams params) {
        return params instanceof LayoutParams;
    }


    private static class LayoutParams extends MarginLayoutParams {

        int top;
        int left;

        private LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        private LayoutParams(int width, int height) {
            super(width, height);
        }

        private LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

    }

}
