package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.utils.AnimatedState;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

import java.util.Arrays;
import java.util.Locale;

public class PiePainter extends Painter {

    private static final float OPTIMIZATION_FACTOR = 2f;

    private static final float ARC_ANGLE_OFFSET = -90f;
    private static final float SELECTED_ARC_OFFSET = 0.05f;

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

    private final float[] sumsTmp;

    private boolean initialized;

    private final AnimatedState changesAnimation = new AnimatedState();

    PiePainter(Chart chart) {
        super(chart);

        final int sourcesCount = chart.sources.length;

        percents = new float[sourcesCount];
        percentsStart = new float[sourcesCount];
        percentsEnd = new float[sourcesCount];
        percentsTmp = new float[sourcesCount];

        sumsTmp = new float[sourcesCount];

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

            float angel = ARC_ANGLE_OFFSET;

            for (int s = 0; s < chart.sources.length; s++) {
                final float percent = percents[s];

                final float fromAngle = angel;
                angel += percent * 360f;
                final float toAngle = angel;

                if (isBetween(fromAngle, toAngle, selectedAngle)) {
                    return s;
                }
            }
        }

        return -1;
    }

    private static boolean isBetween(float fromAngle, float toAngle, float angle) {
        final float end = (toAngle - fromAngle + 360f) % 360;
        final float test = (angle - fromAngle + 360f) % 360;
        return test < end;
    }

    @Override
    public boolean isAnimating() {
        return !changesAnimation.isFinished();
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
            int selectedSourceInd,
            boolean simplified) {

        initialized = true; // Considering initialized once first drawn

        canvas.save();

        final float maxRadius = 0.5f * Math.min(chartPos.width(), chartPos.height());
        final float radius = maxRadius * (1f - SELECTED_ARC_OFFSET);
        final float centerX = chartPos.centerX();
        final float centerY = chartPos.centerY();

        float radiusOptimized = radius;

        if (simplified) {
            // Scaling down circle and scaling up the canvas instead, reducing drawing area.
            canvas.scale(OPTIMIZATION_FACTOR, OPTIMIZATION_FACTOR, centerX, centerY);
            radiusOptimized /= OPTIMIZATION_FACTOR;
        }

        circleRect.set(
                centerX - radiusOptimized,
                centerY - radiusOptimized,
                centerX + radiusOptimized,
                centerY + radiusOptimized
        );

        applyAnimation();
        float percentsSum = 0f;

        for (int s = 0; s < chart.sources.length; s++) {
            circlePaint.setColor(getSourceColor(s));
            final float percent = percents[s];

            if (percent == 0f) {
                continue;
            }

            canvas.drawArc(
                    circleRect,
                    percentsSum * 360f + ARC_ANGLE_OFFSET - 0.5f,
                    percent * 360f + 0.5f,
                    true,
                    circlePaint
            );

            percentsSum += percent;
        }

        canvas.restore();


        float angel = ARC_ANGLE_OFFSET;

        for (int s = 0; s < chart.sources.length; s++) {
            final float percent = percents[s];

            if (percent == 0f) {
                continue;
            }

            labelsPaint.setTextSize(calculateTextSize(percent));

            final String label = String.format(Locale.US, "%.0f%%", 100f * percent);

            final float sourceAngel = percent * 360f;
            final double labelAngelRad = Math.toRadians(angel + 0.5f * sourceAngel);
            angel += sourceAngel;

            final float labelRadius = calculateLabelRadius(radius, percent);
            measureLabel(label, labelBounds);

            final float labelX = centerX + (float) Math.cos(labelAngelRad) * labelRadius
                    - 0.5f * labelBounds.width();
            final float labelY = centerY + (float) Math.sin(labelAngelRad) * labelRadius
                    + 0.5f * labelBounds.height();

            labelsPaint.setAlpha(toAlpha(sourcesStates[s]));

            canvas.drawText(label, labelX, labelY, labelsPaint);
        }
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

    private void applyAnimation() {
        if (!changesAnimation.isFinished()) {
            changesAnimation.update(AnimatedState.now());

            final float state = changesAnimation.get();

            for (int i = 0; i < percents.length; i++) {
                percents[i] = percentsStart[i] * (1f - state) + percentsEnd[i] * state;
            }
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
