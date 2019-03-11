package com.alexvasilkov.telegram.chart.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.WorkerThread;

public class ChartsLoader {

    private static final String TAG = ChartsLoader.class.getSimpleName();
    private static final String CHARTS_FILE = "chart_data.json";

    public static void loadCharts(Context appContext, Listener<List<Chart>> listener) {
        // A simple handling for fast background tasks
        new Thread(() -> {
            final List<Chart> charts = loadChartsInBackground(appContext);
            new Handler(Looper.getMainLooper()).post(() -> listener.onResult(charts));
        }).start();
    }

    @WorkerThread
    private static List<Chart> loadChartsInBackground(Context appContext) {
        String jsonFile = readAsset(appContext.getAssets(), CHARTS_FILE);
        List<ChartJson> jsonCharts =
                new Gson().fromJson(jsonFile, new TypeToken<List<ChartJson>>() {}.getType());

        List<Chart> charts = new ArrayList<>();
        if (jsonCharts != null) {
            for (ChartJson json : jsonCharts) {
                charts.add(json.convert());
            }
        }
        return charts;
    }

    @SuppressWarnings("SameParameterValue")
    private static String readAsset(AssetManager assets, String fileName) {
        try (Reader in = new InputStreamReader(assets.open(fileName), StandardCharsets.UTF_8)) {
            final char[] buffer = new char[4096];
            final StringBuilder out = new StringBuilder();

            for (; ; ) {
                int rsz = in.read(buffer, 0, buffer.length);
                if (rsz < 0) {
                    break;
                }
                out.append(buffer, 0, rsz);
            }

            return out.toString();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return null;
        }
    }


    public interface Listener<T> {
        void onResult(T result);
    }

}
