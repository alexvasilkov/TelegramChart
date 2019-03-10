package com.alexvasilkov.telegram.chart.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.alexvasilkov.telegram.chart.domain.Chart
import com.alexvasilkov.telegram.chart.utils.ChartMath
import kotlin.math.log2
import kotlin.math.pow

class ChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val yGuidesCount = 6

    private val xLabelMaxWidth = 50f.dpToPx()

    private val pathPaintTemplate = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dpToPx()
    }

    private val yGuidesPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dpToPx()
        color = Color.parseColor("#F1F1F1")
    }


    private lateinit var chart: Chart
    private lateinit var xLabels: List<XLabel>
    private lateinit var pathsOrig: List<Path>
    private lateinit var pathsTransformed: List<Path>
    private lateinit var pathsPaints: List<Paint>

    private var xRange = Range(from = 0, to = 0)
    private var yRange = Range(from = 0, to = 0)
    private lateinit var yGuidesOrig: YGuides
    private lateinit var yGuidesTransformed: YGuides

    private var xLabelsLevel: Int = 1
    private var xLabelsMinCount: Int = 0

    private val chartMatrix = Matrix()


    private val tmpFloatPoint = FloatArray(size = 2)


    private class XLabel(
        val title: String,
        val maxLevel: Int
    )

    private class YGuides(
        val values: MutableList<Float>
    )

    private class Range(
        var from: Int,
        var to: Int
    ) {
        val size: Int
            get() = to - from
    }


    init {
        setWillNotDraw(false)
    }


    fun setChart(chart: Chart, labelCreator: (Long) -> String) {
        this.chart = chart

        xLabels = chart.x.reversed().mapIndexed { ind, x ->
            XLabel(
                title = labelCreator.invoke(x),
                maxLevel = getLabelMaxLevel(ind)
            )
        }

        pathsOrig = chart.lines.map { line ->
            // Building path for each chart line
            val path = Path()
            line.y.forEachIndexed { ind, y ->
                if (ind == 0) path.moveTo(0f, line.y[0].toFloat())
                else path.lineTo(ind.toFloat(), line.y[ind].toFloat())
            }
            path
        }

        pathsTransformed = pathsOrig.map { Path(it) }

        pathsPaints = chart.lines.map { line ->
            Paint(pathPaintTemplate).apply { color = line.color }
        }

        // Show entire chart by default
        showRange(from = 0, to = chart.x.size)
    }

    /**
     * Specifies x-range to be shown. Chart should already be set before calling this method.
     */
    fun showRange(from: Int, to: Int) {
        xRange.from = from
        xRange.to = to

        yRange.from = 0
        yRange.to = chart.lines.map { line -> line.y.max() ?: 0 }.max() ?: 0

        // Ensure we have enough values to show guides
        if (yRange.size < yGuidesCount) yRange.to = yRange.from + yGuidesCount

        yGuidesOrig =
            YGuides((0 until yGuidesCount)
                .map { i -> yRange.from + yRange.size * i / (yGuidesCount - 1) }
                .map { it.toFloat() }
                .toMutableList())

        yGuidesTransformed = YGuides(yGuidesOrig.values.toMutableList())

        prepareChartIfReady()
    }


    /**
     * Computes max level at which label on this position is still shown.
     * Where levels are powers of 2.
     */
    private fun getLabelMaxLevel(ind: Int): Int {
        if (ind == 0) return Int.MAX_VALUE // First label is always shown

        // Label's max possible level is a biggest power of 2 which divides label position.
        // E.g. max level for 12 is 4, max level for 8 is 8.
        // This can be computed as a number of trailing zeros in label index binary representation.
        return ChartMath.countTrailingZeroBits(ind)
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // Calculating minimum number of labels which should fit view's width.
        // E.g. if width = 320 and max width = 50 then min labels count = 3.
        xLabelsMinCount = ((measuredWidth / xLabelMaxWidth).toInt() + 1) / 2
        // Min labels number cannot be less than 2
        xLabelsMinCount = Math.max(2, xLabelsMinCount)

        prepareChartIfReady()
    }


    private fun prepareChartIfReady() {
        if (xLabelsMinCount == 0) return // Not measured yet

        val xSize = xRange.size
        if (xSize <= 1) return // No valid x range is set

        val ySize = yRange.size
        if (ySize <= 1) return // No valid y range is set

        // Number of labels we can shown at the same time without hiding them is
        // [xLabelsMinCount, 2 * xLabelsMinCount - 1).
        // If there are more x values needs to be shown then we have to hide some of the labels.
        // Overall we will only show every 2^x (== xLabelsLevel) labels, so we need to find out
        // what level should be used for given number of x values.
        xLabelsLevel = when {
            // Maximum number of labels we can show without hiding them is (2 * xLabelsMinCount - 1)
            xSize < 2 * xLabelsMinCount -> 1
            // Current level can be calculated as a maximum power of 2 that is less or equal to
            // the number of items in maximum possible interval.
            else -> {
                val intervalSize = (xSize - 2f) / (xLabelsMinCount - 1f)
                2f.pow(log2(intervalSize).toInt()).toInt()
            }
        }

        val chartScaleX = measuredWidth.toFloat() / (xSize - 1)
        val chartScaleY = measuredHeight.toFloat() / (ySize - 1)
        val left = -xRange.from * chartScaleX

        chartMatrix.setScale(chartScaleX, chartScaleY)
        chartMatrix.postScale(1f, -1f) // Flip along X axis
        chartMatrix.postTranslate(left, measuredHeight.toFloat()) // Translate to place

        // Transforming paths for drawing
        pathsTransformed.forEachIndexed { ind, path ->
            pathsOrig[ind].transform(chartMatrix, path)
        }

        yGuidesOrig.values.forEachIndexed { ind, y ->
            tmpFloatPoint[0] = 0f
            tmpFloatPoint[1] = y
            chartMatrix.mapPoints(tmpFloatPoint)
            yGuidesTransformed.values[ind] = tmpFloatPoint[1]
        }
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas!!)

        if (::chart.isInitialized) {

            yGuidesTransformed.values.forEach { y ->
                canvas.drawLine(0f, y, width.toFloat(), y, yGuidesPaint)
            }

            pathsTransformed.forEachIndexed { ind, path ->
                canvas.drawPath(path, pathsPaints[ind])
            }


        }
    }


    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

}
