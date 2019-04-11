package com.alexvasilkov.telegram.chart.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChartsLoader {

    private static final String BASE_DIR = "charts";
    private static final String OVERVIEW_FILE = "overview.json";
    private static final long LOADING_DELAY = 100L;

    private static final Map<Type, Chart> cache = new HashMap<>();
    private static volatile boolean isCacheReady = false;

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void loadChart(Context context, Type type, Listener<Chart> listener) {
        if (isCacheReady) {
            mainHandler.postDelayed(() -> listener.onResult(cache.get(type)), LOADING_DELAY);
            return;
        }

        executor.submit(() -> {
            try {
                initCache(context);
                // Delaying loading for nicer start up animations
                mainHandler.postDelayed(() -> listener.onResult(cache.get(type)), LOADING_DELAY);
            } catch (Throwable ex) {
                Log.e("Charts", "Can't read charts", ex);
            }
        });
    }

    private static synchronized void initCache(Context context) throws Exception {
        if (isCacheReady) {
            return;
        }

        for (Type type : Type.values()) {
            Chart chart = loadChart(context, type.id);
            fixSources(chart, type);
            cache.put(type, chart);
        }

        isCacheReady = true;
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


    private static void fixSources(Chart chart, Type type) {
        if (type.namesOverride != null) {
            for (int i = 0, size = chart.sources.length; i < size; i++) {
                Source source = chart.sources[i];
                chart.sources[i] = new Source(type.namesOverride[i], source.color, source.y);
            }
        }
    }


    public interface Listener<T> {
        void onResult(T result);
    }

    public enum Type {
        // Line: Overview: by day. Details: 1 day by hour x 7(8?).
        FOLLOWERS(1, null),

        // Line: Overview: by day. Details: 1 day by hour x 7(8?).
        INTERACTIONS(2, null),

        // Overview: Bars by day. Details: Bars 1 day by hour x 7(8?).
        MESSAGES(3, new String[] {
                "Text", "Photo", "Audio", "Sticker", "Video", "Document", "Location"
        }),

        // Overview: Bars by day. Details: Line 1 day by hour x 3 for 0, -1, -7.
        VIEWS(4, null),

        // Overview: Percentage by day. Details: Pie + Bars by day x 7.
        APPS(5, new String[] { "Android", "iPhone", "OSX", "Web", "Desktop", "Other" });

        final int id;
        final String[] namesOverride;

        Type(int id, String[] namesOverride) {
            this.id = id;
            this.namesOverride = namesOverride;
        }
    }

}
