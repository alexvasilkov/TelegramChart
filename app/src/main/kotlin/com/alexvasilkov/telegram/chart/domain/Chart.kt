package com.alexvasilkov.telegram.chart.domain

class Chart(
    val x: LongArray,
    val lines: List<Line>
) {

    class Line(
        val name: String,
        val color: Int,
        val y: IntArray
    )

}
