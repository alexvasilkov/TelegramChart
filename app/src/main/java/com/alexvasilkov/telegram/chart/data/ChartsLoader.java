package com.alexvasilkov.telegram.chart.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;

import com.alexvasilkov.telegram.chart.domain.Chart;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class ChartsLoader {

    private static final int[] IDS = { 1, 2, 3, 4, 5 };

    private static final String BASE_DIR = "charts";
    private static final String OVERVIEW_FILE = "overview.json";

    private static List<Chart> cache;

    public static void loadCharts(
            Context context, Listener<List<Chart>> listener, Listener<Throwable> error
    ) {
        if (cache != null) {
            listener.onResult(cache);
            return;
        }

        // A simple handling for fast background tasks.
        // Assuming it will run quick enough to avoid memory leaks.
        new Thread(() -> {
            try {
                cache = loadCharts(context);
                new Handler(Looper.getMainLooper()).post(() -> listener.onResult(cache));
            } catch (Throwable ex) {
                new Handler(Looper.getMainLooper()).post(() -> error.onResult(ex));
            }
        }).start();
    }

    private static List<Chart> loadCharts(Context appContext) throws Exception {
        final List<Chart> charts = new ArrayList<>();
        for (int id : IDS) {
            charts.add(loadChart(appContext, id));
        }
        return charts;
    }

    private static Chart loadChart(Context appContext, int id) throws Exception {
        final String fileName = BASE_DIR + "/" + id + "/" + OVERVIEW_FILE;
        final String json = readAsset(appContext.getAssets(), fileName);
        return ChartParser.parse(id, json);
    }

    @SuppressWarnings("SameParameterValue")
    private static String readAsset(AssetManager assets, String fileName) throws IOException {
        try (Reader in = new InputStreamReader(assets.open(fileName), Charset.forName("UTF-8"))) {
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
        }
    }


    public interface Listener<T> {
        void onResult(T result);
    }

}
