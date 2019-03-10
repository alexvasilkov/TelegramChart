package com.alexvasilkov.telegram.chart

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.alexvasilkov.telegram.chart.data.ChartsLoader
import com.alexvasilkov.telegram.chart.widget.ChartView
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        setContentView(R.layout.activity_main)

        val chartView: ChartView = findViewById(R.id.chart_view)

        val dateFormatter = SimpleDateFormat("MMM d", Locale.US) // TODO: Find a better format

        ChartsLoader.loadCharts(applicationContext) {
            chartView.setChart(it[0]) { dateFormatter.format(it) }
        }
    }

}
