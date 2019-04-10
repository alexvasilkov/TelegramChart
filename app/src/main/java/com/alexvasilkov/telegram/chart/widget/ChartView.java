package com.alexvasilkov.telegram.chart.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.LabelsHelper;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChartView extends BaseChartView {

    private static final Rect textBounds = new Rect();

    private final int topInset;
    private final int yGuidesCount;

    private final float xLabelPadding;
    private final float yLabelMarginBottom;

    private YGuides yGuides;
    private final List<YGuides> yGuidesOld = new ArrayList<>();

    private List<XLabel> xLabels;
    private float xLabelsMaxWidth;
    final LabelsHelper xLabelsHelper = new LabelsHelper();
    private float xLabelsLevel;

    private final Paint xLabelPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private final Paint xLabelDotPaint = new Paint(ChartStyle.PAINT_FLAGS);

    private final Paint yGuidesPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private final int yGuidesMaxAlpha;
    private final Paint yLabelPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private final Paint yLabelStrokePaint = new Paint(ChartStyle.PAINT_FLAGS);

    private Formatter xLabelFormatter;
    private Formatter yLabelFormatter;

    private final GestureDetector gestureDetector;
    private final Matrix matrixInverse = new Matrix();
    private float selectedPosX = Float.NaN;
    private int selectedChartX = -1;
    private boolean firstScrollEvent;
    private boolean isSelectionTemporary;
    private boolean selectionWasShown;

    private PopupAdapter<?> selectionPopupAdapter;


    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        xLabelPadding = ChartStyle.dpToPx(context, 10f);
        yLabelMarginBottom = ChartStyle.dpToPx(context, 5f);

        float labelsSize = ChartStyle.dpToPx(context, 12f);
        int labelsColor = Color.DKGRAY;
        int labelsStrokeColor = Color.TRANSPARENT;
        float labelsStrokeWidth = ChartStyle.dpToPx(context, 2f);
        int labelsDotColor = Color.DKGRAY;
        float guidesWidth = ChartStyle.dpToPx(context, 1f);
        int guidesColor = Color.LTGRAY;
        int yGuidesCountDefault = 6;

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.ChartView);
        labelsSize = arr.getDimension(R.styleable.ChartView_chart_labelsTextSize, labelsSize);
        labelsColor = arr.getColor(R.styleable.ChartView_chart_labelsColor, labelsColor);
        labelsStrokeColor =
                arr.getColor(R.styleable.ChartView_chart_labelsStrokeColor, labelsStrokeColor);
        labelsDotColor = arr.getColor(R.styleable.ChartView_chart_labelsDotColor, labelsDotColor);
        guidesWidth = arr.getDimension(R.styleable.ChartView_chart_guidesWidth, guidesWidth);
        guidesColor = arr.getColor(R.styleable.ChartView_chart_guidesColor, guidesColor);
        yGuidesCount = arr.getInt(R.styleable.ChartView_chart_guidesNumber, yGuidesCountDefault);
        arr.recycle();

        xLabelPaint.setTextSize(labelsSize);
        xLabelPaint.setColor(labelsColor);

        xLabelDotPaint.setStrokeWidth(guidesWidth * 2f);
        xLabelDotPaint.setColor(labelsDotColor);
        xLabelDotPaint.setStrokeCap(Paint.Cap.ROUND);

        yGuidesPaint.setStrokeWidth(guidesWidth);
        yGuidesPaint.setColor(guidesColor);

        yGuidesMaxAlpha = Color.alpha(guidesColor);

        yLabelPaint.setTextSize(labelsSize);
        yLabelPaint.setColor(labelsColor);

        yLabelStrokePaint.set(yLabelPaint);
        yLabelStrokePaint.setStyle(Paint.Style.STROKE);
        yLabelStrokePaint.setStrokeWidth(labelsStrokeWidth);
        yLabelStrokePaint.setColor(labelsStrokeColor);

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
                return onSingleTapEvent(e.getX());
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dX, float dY) {
                return onScrollEvent(e2.getX(), dX);
            }
        };
        gestureDetector = new GestureDetector(context, listener);
        gestureDetector.setIsLongpressEnabled(false);
    }

    public void setXLabelFormatter(Formatter formatter) {
        xLabelFormatter = formatter;
    }

    public void setYLabelFormatter(Formatter formatter) {
        yLabelFormatter = formatter;
    }

    @Override
    public void setChart(Chart newChart) {
        // Invalidating X labels
        xLabels = null;

        // Invalidating Y guides
        yGuides = null;
        yGuidesOld.clear();

        clearSelectedPosX();
        if (selectionPopupAdapter != null) {
            selectionPopupAdapter.clear();
        }

        super.setChart(newChart);
    }

    @Override
    public void setSource(int pos, boolean visible, boolean animate) {
        super.setSource(pos, visible, animate);

        // We need to update popup since sources visibility is changed
        updateSelectionPopupView(true);
    }

    public void setSelectionPopupAdapter(PopupAdapter<?> adapter) {
        selectionPopupAdapter = adapter;
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
            // We wont animate the very first guides
            final boolean animate = yGuides != null;

            // Animating out old Y guides
            if (yGuides != null) {
                yGuidesOld.add(yGuides);
                yGuides.state.animateTo(0f);
            }

            // Preparing new Y guides
            yGuides = new YGuides(yGuidesCount, hasVisibleSources());
            for (int i = 0; i < yGuidesCount; i++) {
                final int value = (int) (fromY + (toY - fromY) * i / (yGuidesCount - 1));
                yGuides.orig[i] = value;
                yGuides.titles[i] = yLabelFormatter == null
                        ? String.valueOf(value) : yLabelFormatter.format(value);
            }

            if (animate) {
                yGuides.state.setTo(0f); // Setting initial hidden state
                yGuides.state.animateTo(1f); // Animating to visible state
            } else {
                yGuides.state.setTo(1f); // Setting initial visible state
            }
        }
    }

    private void prepareXLabels() {
        if (xLabels != null) {
            return; // Already prepared
        }

        final int size = chart.x.length;
        xLabels = new ArrayList<>(size);

        // Preparing titles
        float maxLabelWidth = 0f;
        final float[] widths = new float[size];
        final String[] titles = new String[size];

        for (int i = 0; i < size; i++) {
            titles[i] = xLabelFormatter == null
                    ? String.valueOf(chart.x[i]) : xLabelFormatter.format(chart.x[i]);
            widths[i] = measureXLabel(titles[i]);
            maxLabelWidth = Math.max(maxLabelWidth, widths[i]);
        }
        xLabelsMaxWidth = maxLabelWidth;

        // Computing maximum number of intervals that can possibly fit into single screen
        int totalWidth = getChartPosition().width();
        float maxIntervals = (totalWidth - maxLabelWidth) / (maxLabelWidth + xLabelPadding);
        maxIntervals = Math.max(maxIntervals, 2f); // Assuming screen must fit at least 3 labels
        xLabelsHelper.init(maxIntervals);

        final float[] levels = xLabelsHelper.computeLabelsLevels(chart.x[0],
                chart.x[chart.x.length - 1]);

        for (int i = 0; i < size; i++) {
            // Inverting levels position according to direction
            xLabels.add(new XLabel(titles[i], levels[i], widths[i]));
        }
    }

    private float measureXLabel(String title) {
        xLabelPaint.getTextBounds(title, 0, title.length(), textBounds);
        return textBounds.width();
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
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
        selectionWasShown = selectedChartX != -1;
        return true;
    }

    private void onCancelEvent() {
        // Clearing selection if it was only a temporary "show press" effect
        if (isSelectionTemporary && !selectionWasShown) {
            clearSelectedPosX();
        }
    }

    private void onUpOrCancelEvent() {
        useSimplifiedDrawing(false);
    }

    private void onShowPressEvent(float posX) {
        isSelectionTemporary = true;
        setSelectedPosX(posX);
    }

    private boolean onSingleTapEvent(float posX) {
        if (selectionWasShown) {
            clearSelectedPosX();
        } else {
            setSelectedPosX(posX);
        }
        return true;
    }

    private boolean onScrollEvent(float posX, float distanceX) {
        isSelectionTemporary = false;

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
        selectedPosX = posX;

        // Selecting nearest X point in chart coordinates
        matrix.invert(matrixInverse);
        float chartX = ChartMath.mapX(matrixInverse, posX);
        selectedChartX = Math.round(chartRange.fit(chartX));
        setSelectedPointX(selectedChartX);

        updateSelectionPopupView(false);
    }

    private void clearSelectedPosX() {
        selectedPosX = Float.NaN;
        selectedChartX = -1;
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

    private void updateSelectionPopupView(boolean animate) {
        if (selectionPopupAdapter != null && selectedChartX != -1) {
            final Rect pos = getChartPosition();
            selectionPopupAdapter.bind(chart, getSourcesVisibility(), selectedChartX,
                    pos.width(), pos.height(), animate);
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
            drawYLabels(canvas, guides, pos.left);
        }
        drawYLabels(canvas, yGuides, pos.left);

        drawXLabels(canvas, pos.left, pos.right);

        // Drawing selection popup
        if (selectionPopupAdapter != null && selectedChartX != -1) {
            canvas.save();

            // Moving popup view to correct position
            final float posX = ChartMath.mapX(matrix, selectedChartX);
            float shift = (posX - pos.left) / pos.width();
            shift = shift < 0f ? 0f : (shift > 1f ? 1f : shift);

            final int width = selectionPopupAdapter.getWidth();
            canvas.translate(posX - width * shift, 0);

            selectionPopupAdapter.draw(canvas);

            canvas.restore();
        }
    }

    private void drawYGuides(Canvas canvas, YGuides guides, float left, float right) {
        yGuidesPaint.setAlpha((int) (yGuidesMaxAlpha * guides.state.get()));

        for (int i = 0, size = guides.size(); i < size; i++) {
            final float posY = guides.transformed[i];
            canvas.drawLine(left, posY, right, posY, yGuidesPaint);
        }
    }

    private void drawYLabels(Canvas canvas, YGuides guides, float left) {
        if (!guides.showTitles) {
            return;
        }
        yLabelStrokePaint.setAlpha(toAlpha(guides.state.get()));
        yLabelPaint.setAlpha(toAlpha(guides.state.get()));

        for (int i = 0, size = guides.size(); i < size; i++) {
            final float posY = guides.transformed[i] - yLabelMarginBottom;
            canvas.drawText(guides.titles[i], left, posY, yLabelStrokePaint);
            canvas.drawText(guides.titles[i], left, posY, yLabelPaint);
        }
    }

    private void drawXLabels(Canvas canvas, float left, float right) {
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

            xLabelDotPaint.setAlpha((int) (yGuidesMaxAlpha * alpha));
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

        xLabelPaint.setAlpha(toAlpha(alpha));
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

    static int toAlpha(float alpha) {
        return Math.round(255 * alpha);
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
        final String[] titles;
        final float[] orig;
        final float[] transformed;
        final boolean showTitles;
        final AnimatedState state = new AnimatedState();

        YGuides(int size, boolean showTitles) {
            orig = new float[size];
            transformed = new float[size];
            titles = new String[size];
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


    public interface Formatter {
        String format(long value);
    }


    public static abstract class PopupAdapter<T extends PopupViewHolder> {
        private T holder;

        protected abstract T createView(int sourcesCount);

        protected abstract void bindView(
                T holder, Chart chart, boolean[] visibilities, int index, boolean animate);

        @SuppressWarnings("ConstantConditions")
        private void bind(Chart chart, boolean[] visibilities, int index,
                int maxWidth, int maxHeight, boolean animate) {
            if (holder == null) {
                holder = createView(chart.sources.size());
            }
            bindView(holder, chart, visibilities, index, animate);

            // Measure and lay out the view
            final View view = holder.itemView;

            int widthSpecs = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
            int heightSpecs = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            view.measure(widthSpecs, heightSpecs);
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }

        private void draw(Canvas canvas) {
            holder.itemView.draw(canvas);
        }

        private void clear() {
            holder = null;
        }

        private int getWidth() {
            return holder.itemView.getWidth();
        }
    }

    public static abstract class PopupViewHolder {
        protected final View itemView;

        public PopupViewHolder(View itemView) {
            this.itemView = itemView;
        }
    }

}
