package com.alexvasilkov.telegram.chart.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;

import com.alexvasilkov.telegram.chart.domain.Chart;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ChartsLoader {

    private static final String TAG = ChartsLoader.class.getSimpleName();
    private static final String CHARTS_FILE = "chart_data.json";

    public static void loadCharts(
            Context appContext,
            Listener<List<Chart>> listener,
            Listener<Throwable> error
    ) {
        // A simple handling for fast background tasks
        new Thread(() -> {
            try {
                final List<Chart> charts = loadChartsInBackground(appContext);
                new Handler(Looper.getMainLooper()).post(() -> listener.onResult(charts));
            } catch (Throwable ex) {
                new Handler(Looper.getMainLooper()).post(() -> error.onResult(ex));
            }
        }).start();
    }

    private static List<Chart> loadChartsInBackground(Context appContext) throws Exception {
        final String json = readAsset(appContext.getAssets(), CHARTS_FILE);
        return ChartParser.parseList(json);
    }

    @SuppressWarnings("SameParameterValue")
    private static String readAsset(AssetManager assets, String fileName) throws IOException {
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
        }
    }


    public interface Listener<T> {
        void onResult(T result);
    }

}
