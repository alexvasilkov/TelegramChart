package com.alexvasilkov.telegram.chart.data

import android.graphics.Color
import com.alexvasilkov.telegram.chart.domain.Chart

class ChartJson {

    var columns: Array<Array<Any>>? = null
    var types: Map<String, String>? = null
    var names: Map<String, String>? = null
    var colors: Map<String, String>? = null

    fun convert(): Chart {
        val columns = checkNotNull(columns)
        val types = checkNotNull(types)
        val names = checkNotNull(names)
        val colors = checkNotNull(colors)

        val xName = types.filterValues { it == "x" }.keys.firstOrNull()
        checkNotNull(xName)

        val yNames = types.filterValues { it == "line" }.keys.toList()
        check(yNames.isNotEmpty())

        val xValuesAny = columns.find { it[0] == xName }?.drop(1)
        checkNotNull(xValuesAny)
        val xValues = LongArray(xValuesAny.size) { pos -> (xValuesAny[pos] as Double).toLong() }

        val lines = yNames.map { yName ->
            val valuesAny = columns.find { it[0] == yName }?.drop(1)
            checkNotNull(valuesAny)
            val values = IntArray(valuesAny.size) { pos -> (valuesAny[pos] as Double).toInt() }

            val name = names[yName]
            val colorStr = colors[yName]

            Chart.Line(
                name = checkNotNull(name),
                color = Color.parseColor(checkNotNull(colorStr)),
                y = values
            )
        }

        return Chart(
            x = xValues,
            lines = lines
        )
    }

}
