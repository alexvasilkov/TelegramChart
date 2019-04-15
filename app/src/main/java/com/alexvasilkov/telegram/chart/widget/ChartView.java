package com.alexvasilkov.telegram.chart.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.FormatterDate;
import com.alexvasilkov.telegram.chart.domain.FormatterValue;
import com.alexvasilkov.telegram.chart.domain.GroupBy;
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.LabelsHelper;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ChartView extends BaseChartView {

    private static final Rect textBounds = new Rect();

    private final int topInset;
    private int yGuidesCount;

    private final float xLabelPadding;
    private final float yLabelMarginBottom;
    private final float yLabelMarginSide;

    private YGuides yGuides;
    private final List<YGuides> yGuidesOld = new ArrayList<>();

    private List<XLabel> xLabels;
    private float xLabelsMaxWidth;
    final LabelsHelper xLabelsHelper = new LabelsHelper();
    private float xLabelsLevel;

    private int labelMaxAlpha;
    private final Paint xLabelPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private final Paint xLabelDotPaint = new Paint(ChartStyle.PAINT_FLAGS);

    private int yGuidesMaxAlpha;
    private final Paint yGuidesPaint = new Paint(ChartStyle.PAINT_FLAGS);

    private final Paint yLabelPaint = new Paint(ChartStyle.PAINT_FLAGS);

    private FormatterDate xLabelFormatter;
    private FormatterValue yLabelFormatter;

    private final GestureDetector gestureDetector;
    private boolean firstScrollEvent;

    private final Matrix matrixInverse = new Matrix();
    private float selectedPosX = Float.NaN;
    private int selectedChartX = -1;
    private boolean isTemporarySelectionX;
    private boolean isSelectionXWasShown;
    private PopupAdapterX<?> popupAdapterX;

    private int selectedSourceInd = -1;
    private PopupAdapterSource<?> popupAdapterSource;


    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        xLabelPadding = ChartStyle.dpToPx(context, 10f);
        yLabelMarginBottom = ChartStyle.dpToPx(context, 5f);
        yLabelMarginSide = ChartStyle.dpToPx(context, 2f);

        float labelsSize = ChartStyle.dpToPx(context, 12f);
        int labelsColor = Color.DKGRAY;
        int labelsDotColor = Color.DKGRAY;
        float guidesWidth = ChartStyle.dpToPx(context, 1f);
        int guidesColor = Color.LTGRAY;
        int yGuidesCountDefault = 6;

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.ChartView);
        labelsSize = arr.getDimension(R.styleable.ChartView_chart_labelsTextSize, labelsSize);
        labelsColor = arr.getColor(R.styleable.ChartView_chart_labelsColor, labelsColor);
        labelsDotColor = arr.getColor(R.styleable.ChartView_chart_labelsDotColor, labelsDotColor);
        guidesWidth = arr.getDimension(R.styleable.ChartView_chart_guidesWidth, guidesWidth);
        guidesColor = arr.getColor(R.styleable.ChartView_chart_guidesColor, guidesColor);
        yGuidesCount = arr.getInt(R.styleable.ChartView_chart_guidesNumber, yGuidesCountDefault);
        arr.recycle();

        xLabelPaint.setTextSize(labelsSize);
        yLabelPaint.setTextSize(labelsSize);

        yGuidesPaint.setStrokeWidth(guidesWidth);

        xLabelDotPaint.setStrokeWidth(guidesWidth * 2f);
        xLabelDotPaint.setStrokeCap(Paint.Cap.ROUND);

        setColors(guidesColor, labelsColor, labelsDotColor);

        topInset = (int) (labelsSize + yLabelMarginBottom);
        int bottomInset = (int) (1.33f * labelsSize);
        setInsets(0, topInset, 0, bottomInset);


        final OnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return onDownEvent();
            }

            @Override
            public void onShowPress(MotionEvent e) {
                onShowPressEvent(e.getX());
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return onSingleTapEvent(e.getX(), e.getY());
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dX, float dY) {
                return onScrollEvent(e2.getX(), dX);
            }
        };
        gestureDetector = new GestureDetector(context, listener);
        gestureDetector.setIsLongpressEnabled(false);

        setClipToPadding(false);
    }

    public void setColors(int guides, int labels, int dot) {
        yGuidesMaxAlpha = Color.alpha(guides);
        yGuidesPaint.setColor(guides);

        labelMaxAlpha = Color.alpha(labels);
        xLabelPaint.setColor(labels);
        yLabelPaint.setColor(labels);

        xLabelDotPaint.setColor(dot);

        invalidate();
    }

    public void setGuideCount(int guidesCount) {
        yGuidesCount = guidesCount;
    }

    public void setXLabelFormatter(FormatterDate formatter) {
        xLabelFormatter = formatter;
    }

    public void setYLabelFormatter(FormatterValue formatter) {
        yLabelFormatter = formatter;
    }

    public void groupBy(GroupBy groupBy) {
        xLabelsHelper.setGroupBy(groupBy);
    }

    @Override
    public void setChart(Chart newChart) {
        // Invalidating X labels
        xLabels = null;

        // Invalidating Y guides
        yGuides = null;
        yGuidesOld.clear();

        clearSelectedPosX();
        if (popupAdapterX != null) {
            popupAdapterX.clear();
            popupAdapterX.init(this, newChart);
        }

        selectedSourceInd = -1;
        if (popupAdapterSource != null) {
            popupAdapterSource.clear();
            popupAdapterSource.init(this, newChart);
        }

        super.setChart(newChart);
    }

    @Override
    public void setSourceVisibility(boolean[] visibility, boolean animate) {
        super.setSourceVisibility(visibility, animate);

        // We need to update popup since sources visibility is changed
        updatePopupX();
    }

    public void setPopupAdapterX(PopupAdapterX<?> adapter) {
        popupAdapterX = adapter;
    }

    public void setPopupAdapterSource(PopupAdapterSource<?> adapter) {
        popupAdapterSource = adapter;
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        if (popupAdapterX != null) {
            popupAdapterX.scaleX(1f / scaleX);
        }
        if (popupAdapterSource != null) {
            popupAdapterSource.scaleX(1f / scaleX);
        }
    }

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        if (popupAdapterX != null) {
            popupAdapterX.scaleY(1f / scaleY);
        }
        if (popupAdapterSource != null) {
            popupAdapterSource.scaleY(1f / scaleY);
        }
    }

    @Override
    protected void doOnReady() {
        prepareXLabels(); // Should be called before super

        super.doOnReady();
    }

    @Override
    protected void onRangeSet(
            float fromX, float toX, float fromY, float toY, boolean animateX, boolean animateY) {

        // Adjusting Y range to better fit chart height + extra inset on top.
        // Final range will be divisible by the number of intervals, to have integer guide values.
        final int yIntervals = yGuidesCount - 1;
        final int chartHeight = getChartPosition().height();
        final float minToY = painter.useExactRange()
                ? toY : toY - (toY - fromY) * topInset / (chartHeight + topInset);

        // Rounding guides to nearest 10^x value
        final float stepSize = (minToY - fromY) / yIntervals;
        final int factor10 = (int) Math.floor(Math.log10(stepSize) - 0.5);
        final int roundFactor = (int) Math.pow(10, Math.max(0f, factor10)) * yIntervals;

        fromY = (int) Math.floor(fromY / roundFactor) * roundFactor;
        toY = (int) Math.ceil(minToY / roundFactor) * roundFactor;

        // Checking if Y range changes are big enough to switch Y guides
        final float range = toY - fromY; // Y change range, never 0
        final float fromChanges = (fromY - yRangeEnd.from) / range;
        final float toChanges = (toY - yRangeEnd.to) / range;
        final float fromThreshold = 0; // Can't tolerate 'from' value changes because of X labels
        final float toThreshold = getPaddingTop() / (float) getHeight();
        final boolean isSmallChange =
                Math.abs(fromChanges) <= fromThreshold && Math.abs(toChanges) <= toThreshold;

        // Setting up new Y guides only if Y range is significantly changed
        if (isSmallChange) {
            fromY = yRangeEnd.from;
            toY = yRangeEnd.to;
        }

        super.onRangeSet(fromX, toX, fromY, toY, animateX, animateY);

        // Preparing Y guides once range is set
        if (!isSmallChange) {
            prepareYGuides(fromY, toY);
        }

        updatePopupSource();
    }

    private void prepareXLabels() {
        if (xLabels != null) {
            return; // Already prepared
        }

        if (chart.type == Chart.Type.PIE) {
            // No X labels needed
            xLabels = Collections.emptyList();
            return;
        }

        final int size = chart.x.length;
        xLabels = new ArrayList<>(size);

        // Preparing titles
        int maxLabelWidth = 0;
        final int[] widths = new int[size];
        final String[] titles = new String[size];

        for (int i = 0; i < size; i++) {
            titles[i] = xLabelFormatter == null
                    ? String.valueOf(chart.x[i]) : xLabelFormatter.format(chart.x[i]);
            widths[i] = measureXLabel(titles[i]);
            maxLabelWidth = Math.max(maxLabelWidth, widths[i]);
        }
        xLabelsMaxWidth = maxLabelWidth;

        xLabelsHelper.init(getChartPosition().width(), maxLabelWidth, xLabelPadding);

        final float[] levels = xLabelsHelper.computeLabelsLevels(chart);

        for (int i = 0; i < size; i++) {
            // Inverting levels position according to direction
            xLabels.add(new XLabel(titles[i], levels[i], widths[i]));
        }
    }

    private int measureXLabel(String title) {
        xLabelPaint.getTextBounds(title, 0, title.length(), textBounds);
        return textBounds.width();
    }

    private void prepareYGuides(float fromY, float toY) {
        // We wont animate the very first guides
        final boolean animate = yGuides != null;

        if (chart.type == Chart.Type.PIE) {
            // No Y guides needed
            yGuides = new YGuides(0, 0, false);
            return;
        }

        // Animating out old Y guides
        if (yGuides != null) {
            yGuidesOld.add(yGuides);
            yGuides.state.animateTo(0f);
        }

        // Preparing new Y guides
        final boolean hasIndependentSources = painter.hasIndependentSources();
        final int independentSources = hasIndependentSources ? chart.sources.length : 1;

        yGuides = new YGuides(yGuidesCount, independentSources, hasVisibleSources());

        final int maxValue = (int) yRangeEnd.to;

        for (int i = 0; i < yGuidesCount; i++) {
            final int value = (int) (fromY + (toY - fromY) * i / (yGuidesCount - 1));
            yGuides.orig[i] = value;

            for (int s = 0; s < independentSources; s++) {
                final float scale = painter.getSourcesScales()[s];
                final int scaledValue = Math.round(value / scale);
                final int scaledMaxValue = Math.round(maxValue / scale);

                yGuides.titles[s][i] = yLabelFormatter == null
                        ? String.valueOf(scaledValue)
                        : yLabelFormatter.format(scaledValue, scaledMaxValue);
            }
        }

        if (animate) {
            yGuides.state.setTo(0f); // Setting initial hidden state
            yGuides.state.animateTo(1f); // Animating to visible state
        } else {
            yGuides.state.setTo(1f); // Setting initial visible state
        }
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean childTouched = super.dispatchTouchEvent(ev);
        if (childTouched) {
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            onCancelEvent();
            onUpOrCancelEvent();
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
            onUpOrCancelEvent();
        }

        return gestureDetector.onTouchEvent(ev);
    }

    private boolean onDownEvent() {
        firstScrollEvent = true;
        isSelectionXWasShown = selectedChartX != -1;
        return true;
    }

    private void onCancelEvent() {
        // Clearing selection if it was only a temporary "show press" effect
        if (isTemporarySelectionX && !isSelectionXWasShown) {
            clearSelectedPosX();
        }
    }

    private void onUpOrCancelEvent() {
        useSimplifiedDrawing(false);
    }

    private void onShowPressEvent(float posX) {
        isTemporarySelectionX = true;
        setSelectedPosX(posX);
    }

    private boolean onSingleTapEvent(float posX, float posY) {
        if (painter != null) {
            int selectedSource = painter.pickSource(getChartPosition(), posX, posY);
            if (selectedSource == selectedSourceInd) {
                selectedSource = -1;
            }

            if (selectedSource != selectedSourceInd) {
                selectedSourceInd = selectedSource;
                painter.setSelectedSource(selectedSource);
                requestAnimation();
                updatePopupSource();
                return true;
            }
        }

        if (isSelectionXWasShown) {
            clearSelectedPosX();
        } else {
            setSelectedPosX(posX);
        }
        return true;
    }

    private boolean onScrollEvent(float posX, float distanceX) {
        isTemporarySelectionX = false;

        if (firstScrollEvent) {
            firstScrollEvent = false;
            getParent().requestDisallowInterceptTouchEvent(true);

            setSelectedPosX(posX);
        } else {
            setSelectedPosX(selectedPosX - distanceX);
        }

        // Optimizing chart drawing while being dragged
        useSimplifiedDrawing(true);

        return true;
    }

    private void setSelectedPosX(float posX) {
        if (painter == null || !painter.allowXSelection()) {
            return;
        }

        selectedPosX = posX;

        // Selecting nearest X point in chart coordinates
        matrix.invert(matrixInverse);
        float chartX = ChartMath.mapX(matrixInverse, posX);
        selectedChartX = Math.round(chartRange.fit(chartX));
        setSelectedPointX(selectedChartX);

        updatePopupX();
    }

    private void clearSelectedPosX() {
        selectedPosX = Float.NaN;
        selectedChartX = -1;
        updatePopupX();
        setSelectedPointX(-1);
    }


    @Override
    protected boolean onAnimationStep() {
        boolean result = super.onAnimationStep();

        // Checking X labels animations states
        for (XLabel label : xLabels) {
            result |= !label.state.isFinished();
        }

        // Checking Y guides animations states
        result |= !yGuides.state.isFinished();

        for (Iterator<YGuides> iterator = yGuidesOld.iterator(); iterator.hasNext(); ) {
            boolean finished = iterator.next().state.isFinished();
            if (finished) {
                iterator.remove();
            }
            result |= !finished;
        }

        return result;
    }

    @Override
    protected void onUpdateChartState(long now) {
        super.onUpdateChartState(now);

        // Calculating current X labels level
        xLabelsLevel = xLabelsHelper.computeLevel(xRange.size());
        setXLabelsVisibility(now);

        yGuides.transform(matrix);
        for (YGuides guides : yGuidesOld) {
            guides.transform(matrix);
        }
        setYGuidesVisibility(now);

        updatePosOfPopupX();
        updatePosOfPopupSource();
    }

    @Override
    protected float getExtraLeftSize() {
        return super.getExtraLeftSize() + xLabelsMaxWidth;
    }

    @Override
    protected float getExtraRightSize() {
        return super.getExtraRightSize() + xLabelsMaxWidth;
    }

    private void setXLabelsVisibility(long now) {
        final float fromX = xRangeExt.from;
        final float toX = xRangeExt.to;

        for (int i = 0, size = xLabels.size(); i < size; i++) {
            final XLabel label = xLabels.get(i);

            // Resetting out-of-range labels
            if (i < fromX || i > toX) {
                label.state.reset();
                continue;
            }

            final boolean show = label.level >= xLabelsLevel;

            if (label.state.isSet()) {
                label.state.update(now);
                label.state.animateTo(show ? 1f : 0f, now);
            } else {
                label.state.setTo(show ? 1f : 0f);
            }
        }
    }

    private void setYGuidesVisibility(long now) {
        yGuides.state.update(now);

        for (YGuides guides : yGuidesOld) {
            guides.state.update(now);
        }
    }

    public void updatePopupX() {
        if (popupAdapterX == null) {
            return;
        }
        if (selectedChartX != -1) {
            updatePosOfPopupX();
            popupAdapterX.show(chart, getSourcesVisibility(), selectedChartX);
        } else {
            popupAdapterX.hide();
        }
    }

    private void updatePosOfPopupX() {
        if (popupAdapterX != null && selectedChartX != -1) {
            // Moving popup view to correct position
            final float posX = ChartMath.mapX(matrix, selectedChartX);
            final Rect chartPos = getChartPosition();
            final int chartMid = (chartPos.left + chartPos.right) / 2;
            float shift = posX > chartMid ? 1.07f : -0.07f;

            popupAdapterX.setPosition(posX - getPaddingLeft(), shift);
        }
    }

    public void updatePopupSource() {
        if (popupAdapterSource == null) {
            return;
        }
        if (selectedSourceInd != -1) {
            updatePosOfPopupSource();
            popupAdapterSource.show(
                    chart, selectedSourceInd, Math.round(xRange.from), Math.round(xRange.to));
        } else {
            popupAdapterSource.hide();
        }
    }

    private void updatePosOfPopupSource() {
        if (popupAdapterSource != null && selectedSourceInd != -1) {
            // Moving popup view to correct position
            final int gravity = painter.getSourcePopupGravity(selectedSourceInd);
            popupAdapterSource.setPosition(gravity);
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isReady()) {
            return;
        }

        final Rect pos = getChartPosition();

        // Drawing old and current Y guides
        for (YGuides guides : yGuidesOld) {
            drawYGuides(canvas, guides, pos.left, pos.right);
        }
        drawYGuides(canvas, yGuides, pos.left, pos.right);

        // Drawing old and current Y labels, drawing X labels
        for (YGuides guides : yGuidesOld) {
            drawYLabels(canvas, guides, pos.left, pos.right);
        }
        drawYLabels(canvas, yGuides, pos.left, pos.right);

        drawXLabels(canvas, pos.left, pos.right);
    }

    private void drawYGuides(Canvas canvas, YGuides guides, float left, float right) {
        yGuidesPaint.setAlpha(Math.round(yGuidesMaxAlpha * guides.state.get()));

        for (int i = 0, size = guides.size(); i < size; i++) {
            final float posY = guides.transformed[i];
            canvas.drawLine(left, posY, right, posY, yGuidesPaint);
        }
    }

    private void drawYLabels(Canvas canvas, YGuides guides, int left, int right) {
        if (!guides.showTitles) {
            return;
        }

        final boolean showTwoLabels = guides.titles.length > 1;
        final float alpha = guides.state.get();

        if (showTwoLabels) {
            // Drawing separate labels on the left and right sides
            drawYLabels(
                    canvas, guides.titles[0], chart.sources[0].color,
                    Math.round(255 * alpha * sourcesStatesValues[0]), Paint.Align.LEFT,
                    left + yLabelMarginSide, guides.transformed
            );
            drawYLabels(
                    canvas, guides.titles[1], chart.sources[1].color,
                    Math.round(255 * alpha * sourcesStatesValues[1]), Paint.Align.RIGHT,
                    right - yLabelMarginSide, guides.transformed
            );
        } else {
            // Drawing regular labels on the left side
            drawYLabels(
                    canvas, guides.titles[0],
                    yLabelPaint.getColor(), Math.round(labelMaxAlpha * alpha), Paint.Align.LEFT,
                    left + yLabelMarginSide, guides.transformed
            );
        }
    }

    private void drawYLabels(
            Canvas canvas, String[] titles,
            int color, int alpha, Paint.Align align,
            float posX, float[] posY) {

        if (alpha == 0) {
            return;
        }

        yLabelPaint.setTextAlign(align);
        yLabelPaint.setColor(color);
        yLabelPaint.setAlpha(alpha);

        for (int i = 0, size = titles.length; i < size; i++) {
            final float posYShifted = posY[i] - yLabelMarginBottom;
            canvas.drawText(titles[i], posX, posYShifted, yLabelPaint);
        }
    }


    private void drawXLabels(Canvas canvas, float left, float right) {
        if (xLabels.isEmpty()) {
            return; // No X labels needed
        }

        final int fromExtX = (int) Math.ceil(xRangeExt.from);
        final int toExtX = (int) Math.floor(xRangeExt.to);

        for (int i = fromExtX; i <= toExtX; i++) {
            final XLabel label = xLabels.get(i);

            // Ignoring unset and invisible labels
            if (!label.state.isSet() || label.state.get() == 0f) {
                continue;
            }

            // Ignoring labels from deeper levels, to avoid labels stacking
            if (xLabelsLevel > 2f * label.level) {
                continue;
            }

            drawXDot(canvas, i, left, right, label.state.get());

            drawXLabel(canvas, label, i, left, right);
        }
    }

    private void drawXDot(Canvas canvas, int pos, float left, float right, float alpha) {
        final float dotPosX = ChartMath.mapX(matrix, pos);

        // Drawing a dot if it is inside internal range
        if (left - 0.5f < dotPosX && dotPosX < right + 0.5f) {
            final float dotPosY = yGuides.transformed[0];

            xLabelDotPaint.setAlpha(Math.round(yGuidesMaxAlpha * alpha));
            canvas.drawPoint(dotPosX, dotPosY, xLabelDotPaint);
        }
    }

    private void drawXLabel(Canvas canvas, XLabel label, int pos, float left, float right) {
        // Getting indexes of nearby labels of same or higher level
        final int prev = findNeighbourLabel(xLabels, pos, -1);
        final int next = findNeighbourLabel(xLabels, pos, 1);

        // Calculating uniform label position (since distances between labels may not be equal)
        final float posUneven = prev == -1 || next == -1 ? pos : 0.5f * (prev + next);

        // Although posUneven allows nice labels distribution we still have to switch
        // to a real position at some point to make a room for the labels to appear
        final float posAdjusted;
        if (xLabelsLevel <= 0.5f * label.level) {
            posAdjusted = pos;
        } else if (xLabelsLevel >= 0.66f * label.level) {
            posAdjusted = posUneven;
        } else {
            // Interpolating between pos and posUneven
            posAdjusted =
                    pos + (xLabelsLevel / label.level - 0.5f) * (posUneven - pos) / (0.66f - 0.5f);
        }

        final float labelPosX = ChartMath.mapX(matrix, posAdjusted);
        final float labelPosY = getHeight() - getPaddingBottom();

        // Shifting label's X pos according to its position on screen to fit internal width
        final float labelShift = Math.max(0f, Math.min((labelPosX - left) / (right - left), 1f));
        final float labelPosXShifted = labelPosX - label.width * labelShift;

        // Calculating alpha so that labels outside of chart area are gradually disappearing
        float edgeState = 1f;
        if (labelPosX < left) {
            edgeState = (labelPosX + label.width) / (left + label.width);
        } else if (labelPosX > right) {
            edgeState = (getWidth() + label.width - labelPosX) / (getWidth() + label.width - right);
        }

        // Calculating alpha so that disappearing labels are not stacking
        final float stackState = label.level >= xLabelsLevel ? 1f : 2f - xLabelsLevel / label.level;

        final float alpha = label.state.get()
                * Math.max(0f, edgeState)
                * Math.max(0f, Math.min(stackState * stackState, 1f));

        xLabelPaint.setAlpha(Math.round(labelMaxAlpha * alpha));
        canvas.drawText(label.title, labelPosXShifted, labelPosY, xLabelPaint);
    }

    private static int findNeighbourLabel(List<XLabel> labels, int from, int direction) {
        final float currLevel = labels.get(from).level;
        for (int i = from + direction, size = labels.size(); 0 <= i && i < size; i += direction) {
            final float level = labels.get(i).level;
            if (level >= currLevel) {
                return i;
            }
        }
        return -1;
    }


    private static class XLabel {
        final String title;
        final float level;
        final float width;
        final AnimatedState state = new AnimatedState();

        XLabel(String title, float level, float width) {
            this.title = title;
            this.level = level;
            this.width = width;
        }
    }

    private static class YGuides {
        final String[][] titles;
        final float[] orig;
        final float[] transformed;
        final boolean showTitles;
        final AnimatedState state = new AnimatedState();

        YGuides(int size, int sources, boolean showTitles) {
            orig = new float[size];
            transformed = new float[size];
            titles = new String[sources][size];
            this.showTitles = showTitles;
        }

        void transform(Matrix matrix) {
            for (int i = 0, size = orig.length; i < size; i++) {
                transformed[i] = ChartMath.mapY(matrix, orig[i]);
            }
        }

        int size() {
            return orig.length;
        }
    }


    public static abstract class PopupAdapterX<T extends PopupViewHolder>
            extends PopupAdapterBase<T> {

        private float shiftLeft;

        protected abstract void bindView(
                T holder, Chart chart, boolean[] visibilities, int index, boolean animate);

        void show(Chart chart, boolean[] visibilities, int index) {
            bindView(holder, chart, visibilities, index, shown);
            show();
        }

        void setPosition(float left, float leftShift) {
            ((MarginLayoutParams) holder.itemView.getLayoutParams()).leftMargin = Math.round(left);
            holder.itemView.requestLayout();

            final float newShiftLeft = -holder.itemView.getWidth() * leftShift;
            if (shiftLeft != newShiftLeft) {
                shiftLeft = newShiftLeft;
                if (shown) {
                    holder.itemView.animate().translationX(shiftLeft);
                } else {
                    holder.itemView.animate().cancel();
                    holder.itemView.setTranslationX(shiftLeft);
                }
            }
        }

        @Override
        void clear() {
            super.clear();
            shiftLeft = 0f;
        }
    }

    public static abstract class PopupAdapterSource<T extends PopupViewHolder>
            extends PopupAdapterBase<T> {

        protected abstract void bindView(
                T holder, Chart chart, int source, int from, int to, boolean animate);

        void show(Chart chart, int source, int from, int to) {
            bindView(holder, chart, source, from, to, shown);
            show();
        }

        void setPosition(int gravity) {
            ((LayoutParams) holder.itemView.getLayoutParams()).gravity = gravity;
            parent.requestLayout();

            if (Build.VERSION.SDK_INT >= 19) {
                AutoTransition transition = new AutoTransition();
                transition.setDuration(ANIMATION_DURATION);
                TransitionManager.beginDelayedTransition(parent, transition);
            }
        }
    }

    private static abstract class PopupAdapterBase<T extends PopupViewHolder> {
        static final long ANIMATION_DURATION = 150L;

        ViewGroup parent;

        T holder;
        boolean shown;

        protected abstract T createView(ViewGroup parent, Chart chart);

        void init(ViewGroup parent, Chart chart) {
            this.parent = parent;

            holder = createView(parent, chart);
            parent.addView(holder.itemView);

            holder.itemView.setAlpha(0f);
            holder.itemView.setVisibility(INVISIBLE);
        }

        void show() {
            if (!shown) {
                holder.itemView.setVisibility(VISIBLE);
                holder.itemView.animate()
                        .setListener(null)
                        .setDuration(ANIMATION_DURATION)
                        .alpha(1f);
                shown = true;
            }
        }

        void hide() {
            if (shown) {
                holder.itemView.animate()
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                holder.itemView.setVisibility(INVISIBLE);
                            }
                        })
                        .setDuration(ANIMATION_DURATION)
                        .alpha(0f);
                shown = false;
            }
        }


        void scaleX(float scaleX) {
            if (holder != null) {
                holder.itemView.setScaleX(scaleX);
            }
        }

        void scaleY(float scaleY) {
            if (holder != null) {
                holder.itemView.setScaleY(scaleY);
            }
        }

        void clear() {
            if (holder != null) {
                parent.removeView(holder.itemView);
            }

            parent = null;
            holder = null;
            shown = false;
        }
    }

    public static abstract class PopupViewHolder {
        public final View itemView;

        public PopupViewHolder(View itemView) {
            this.itemView = itemView;
        }
    }

}
