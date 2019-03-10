package com.alexvasilkov.telegram.chart.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alexvasilkov.telegram.chart.domain.Chart
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.concurrent.thread

object ChartsLoader {

    private val TAG = ChartsLoader::class.java.simpleName
    private const val CHARTS_FILE = "chart_data.json"

    fun loadCharts(appContext: Context, action: (List<Chart>) -> Unit) {
        thread {
            val json = readAsset(appContext, CHARTS_FILE)

            val chartsJson: List<ChartJson> =
                Gson().fromJson(json, object : TypeToken<List<ChartJson>>() {}.type)

            val charts = chartsJson.map { it.convert() }

            Handler(Looper.getMainLooper()).post { action.invoke(charts) }
        }
    }

    private fun readAsset(context: Context, fileName: String): String? {
        return try {
            val inputStream = context.assets.open(fileName)
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            null
        }
    }

}
