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
import java.util.List;

public class ChartsLoader {

    private static final String CHARTS_FILE = "chart_data.json";

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
                cache = loadChartsInBackground(context);
                new Handler(Looper.getMainLooper()).post(() -> listener.onResult(cache));
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
