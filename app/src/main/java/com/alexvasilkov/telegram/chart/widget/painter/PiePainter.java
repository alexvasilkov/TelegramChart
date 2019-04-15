package com.alexvasilkov.telegram.chart.widget.painter;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.Arrays;
import java.util.Locale;

public class PiePainter extends Painter {

    private static final float OPTIMIZATION_FACTOR = 2f;

    private static final float ARC_ANGLE_OFFSET = -90f;
    private static final float SELECTED_ARC_OFFSET = 0.075f;

    private final Paint circlePaint = new Paint(ChartStyle.PAINT_FLAGS);
    private final Paint labelsPaint = new Paint(ChartStyle.PAINT_FLAGS);

    private final RectF circleRect = new RectF();

    private final Rect labelBounds = new Rect();
    private float labelMinSize;
    private float labelMaxSize;

    private final float[] percents;
    private final float[] percentsStart;
    private final float[] percentsEnd;
    private final float[] percentsTmp;

    private final float[] startAngles;
    private final float[] sweepAngles;

    private final float[] sumsTmp;

    private boolean initialized;

    private final AnimatedState changesAnimation = new AnimatedState();
    private final AnimatedState[] selectionAnimations;

    PiePainter(Chart chart) {
        super(chart);

        final int sourcesCount = chart.sources.length;

        percents = new float[sourcesCount];
        percentsStart = new float[sourcesCount];
        percentsEnd = new float[sourcesCount];
        percentsTmp = new float[sourcesCount];

        startAngles = new float[sourcesCount];
        sweepAngles = new float[sourcesCount];

        sumsTmp = new float[sourcesCount];

        selectionAnimations = new AnimatedState[sourcesCount];
        for (int s = 0; s < sourcesCount; s++) {
            selectionAnimations[s] = new AnimatedState();
            selectionAnimations[s].setTo(0f);
        }

        circlePaint.setStyle(Paint.Style.FILL);

        labelsPaint.setColor(Color.WHITE);
        labelsPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    @Override
    public void applyStyle(ChartStyle style) {
        super.applyStyle(style);

        labelMinSize = style.pieMinTextSize;
        labelMaxSize = style.pieMaxTextSize;
    }

    @Override
    public void calculateYRange(Range yRange, int from, int to, boolean[] sourcesStates) {
        yRange.set(0f, 100f);

        // Computing new percentages
        computePercents(percentsTmp, from, to, sourcesStates);

        // Starting animation if we detect changes
        if (!Arrays.equals(percentsTmp, percentsEnd)) {
            // Init start and end values
            System.arraycopy(percents, 0, percentsStart, 0, percents.length);
            System.arraycopy(percentsTmp, 0, percentsEnd, 0, percentsTmp.length);

            if (initialized) {
                changesAnimation.setTo(0f);
                changesAnimation.animateTo(1f);
            } else {
                changesAnimation.setTo(1f);
                System.arraycopy(percentsTmp, 0, percents, 0, percentsTmp.length);
            }
        }
    }

    @Override
    public boolean useExactRange() {
        return true;
    }

    @Override
    public boolean allowXSelection() {
        return false;
    }

    @Override
    public int pickSource(Rect chartPos, float posX, float posY) {
        final float radius = 0.5f * Math.min(chartPos.width(), chartPos.height());
        final float centerX = chartPos.centerX();
        final float centerY = chartPos.centerY();

        final float distance = (float) Math.hypot(posX - centerX, posY - centerY);
        if (distance <= radius) {
            final float selectedAngle =
                    (float) Math.toDegrees(Math.atan2(posY - centerY, posX - centerX));

            for (int s = 0; s < chart.sources.length; s++) {
                if (isBetween(startAngles[s], startAngles[s] + sweepAngles[s], selectedAngle)) {
                    return s;
                }
            }
        }

        return -1;
    }

    private static boolean isBetween(float fromAngle, float toAngle, float angle) {
        final float end = toAngle < fromAngle ? toAngle - fromAngle + 360f : toAngle - fromAngle;
        final float test = angle < fromAngle ? angle - fromAngle + 360f : angle - fromAngle;
        return test < end;
    }

    @Override
    public void setSelectedSource(int selected) {
        final long now = AnimatedState.now();
        for (int s = 0; s < selectionAnimations.length; s++) {
            selectionAnimations[s].animateTo(s == selected ? 1f : 0f, now);
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public int getSourcePopupGravity(int sourceInd) {
        final float angle = startAngles[sourceInd] + 0.5f * sweepAngles[sourceInd];
        int gravity = 0;
        gravity |= isBetween(0f, 180f, angle) ? Gravity.TOP : Gravity.BOTTOM;
        gravity |= isBetween(-90f, 91f, angle) ? Gravity.LEFT : Gravity.RIGHT;
        return gravity;
    }

    @Override
    public boolean isAnimating() {
        boolean result = !changesAnimation.isFinished();

        for (AnimatedState anim : selectionAnimations) {
            result |= !anim.isFinished();
        }

        return result;
    }

    @Override
    public void draw(
            Canvas canvas,
            Rect chartPos,
            Matrix matrix,
            int from,
            int to,
            float[] sourcesStates,
            int selectedPos,
            boolean simplified) {

        initialized = true; // Considering initialized once first drawn

        final long now = AnimatedState.now();
        applyAnimation(now);
        for (AnimatedState anim : selectionAnimations) {
            anim.update(now);
        }

        final float maxRadius = 0.5f * Math.min(chartPos.width(), chartPos.height());
        final float radius = maxRadius * (1f - SELECTED_ARC_OFFSET);
        final float offset = maxRadius - radius;
        final float centerX = chartPos.centerX();
        final float centerY = chartPos.centerY();


        drawPie(canvas, radius, offset, centerX, centerY, simplified);


        for (int s = 0; s < chart.sources.length; s++) {
            final float percent = percents[s];

            if (percent == 0f) {
                continue;
            }

            labelsPaint.setTextSize(calculateTextSize(percent));

            final String label = String.format(Locale.US, "%.0f%%", 100f * percent);

            final double labelAngelRad = Math.toRadians(startAngles[s] + 0.5f * sweepAngles[s]);

            final float labelRadius = calculateLabelRadius(radius, percent)
                    + selectionAnimations[s].get() * offset;

            measureLabel(label, labelBounds);

            final float labelX = centerX + (float) Math.cos(labelAngelRad) * labelRadius
                    - 0.5f * labelBounds.width();
            final float labelY = centerY + (float) Math.sin(labelAngelRad) * labelRadius
                    + 0.5f * labelBounds.height();

            labelsPaint.setAlpha(toAlpha(sourcesStates[s]));

            canvas.drawText(label, labelX, labelY, labelsPaint);
        }
    }

    private void drawPie(
            Canvas canvas,
            float radius, float offset,
            float centerX, float centerY,
            boolean simplified
    ) {
        canvas.save();

        float radiusOptimized = radius;
        float offsetOptimized = offset;

        if (simplified) {
            // Scaling down circle and scaling up the canvas instead, reducing drawing area.
            canvas.scale(OPTIMIZATION_FACTOR, OPTIMIZATION_FACTOR, centerX, centerY);
            radiusOptimized /= OPTIMIZATION_FACTOR;
            offsetOptimized /= OPTIMIZATION_FACTOR;
        }

        circleRect.set(
                centerX - radiusOptimized,
                centerY - radiusOptimized,
                centerX + radiusOptimized,
                centerY + radiusOptimized
        );

        for (int s = 0; s < chart.sources.length; s++) {
            if (sweepAngles[s] == 0f) {
                continue;
            }

            circlePaint.setColor(getSourceColor(s));

            final double midAngelRad = Math.toRadians(startAngles[s] + 0.5f * sweepAngles[s]);
            final float state = selectionAnimations[s].get();

            final float offsetX = (float) Math.cos(midAngelRad) * offsetOptimized * state;
            final float offsetY = (float) Math.sin(midAngelRad) * offsetOptimized * state;

            final float fromOffset = -0.4f; // Extra offset to avoid drawing artifacts
            final float offsetSweep = fromOffset + (sweepAngles[s] < 360f ? 2.4f * state : 0f);

            circleRect.offset(offsetX, offsetY);

            canvas.drawArc(
                    circleRect,
                    startAngles[s] + 0.5f * offsetSweep,
                    Math.max(sweepAngles[s] - offsetSweep, 0f),
                    true,
                    circlePaint
            );

            circleRect.offset(-offsetX, -offsetY);
        }

        canvas.restore();
    }

    private float calculateTextSize(float percent) {
        final float labelState = Math.min(4f * percent, 1f);
        return labelMinSize * (1f - labelState) + labelMaxSize * labelState;
    }

    private float calculateLabelRadius(float radius, float percent) {
        measureLabel("0", labelBounds);
        final float extra = 0.5f * (float) Math.hypot(labelBounds.width(), labelBounds.height());

        return radius - labelMinSize - extra
                - Math.max(0f, 3f * percent - 0.3f) * labelsPaint.getTextSize();
    }

    private void applyAnimation(long now) {
        changesAnimation.update(now);

        final float state = changesAnimation.get();

        float percentsSum = 0f;

        for (int i = 0; i < percents.length; i++) {
            percents[i] = percentsStart[i] * (1f - state) + percentsEnd[i] * state;

            startAngles[i] = percentsSum * 360f + ARC_ANGLE_OFFSET;
            sweepAngles[i] = percents[i] * 360f;
            percentsSum += percents[i];
        }
    }

    private void computePercents(float[] result, int from, int to, boolean[] sourcesVisibility) {
        final int sourcesCount = chart.sources.length;

        Arrays.fill(sumsTmp, 0f);
        float totalSum = 0f;

        for (int s = 0; s < sourcesCount; s++) {
            for (int i = from; i < to; i++) {
                if (sourcesVisibility[s]) {
                    sumsTmp[s] += chart.sources[s].y[i];
                }
            }
            totalSum += sumsTmp[s];
        }

        for (int s = 0; s < sourcesCount; s++) {
            result[s] = totalSum == 0f ? 0f : sumsTmp[s] / totalSum;
        }
    }

    private void measureLabel(String title, Rect rect) {
        labelsPaint.getTextBounds(title, 0, title.length(), rect);
    }

}
