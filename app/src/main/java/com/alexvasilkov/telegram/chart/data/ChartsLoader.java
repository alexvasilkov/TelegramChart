package com.alexvasilkov.telegram.chart.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.domain.Resolution;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChartsLoader {

    private static final String BASE_DIR = "charts";
    private static final String OVERVIEW_FILE = "overview.json";
    private static final String DATE_FORMAT = "yyyy-MM";
    private static final String DAY_FILE_FORMAT = "dd'.json'";

    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");


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
            final Chart chart = loadChart(context, type);
            fixSources(chart, type);
            cache.put(type, chart);
        }

        isCacheReady = true;
    }

    private static Chart loadChart(Context appContext, Type type) throws Exception {
        final String fileName = BASE_DIR + "/" + type.id + "/" + OVERVIEW_FILE;
        final String json = readAsset(appContext.getAssets(), fileName);
        return ChartParser.parse(type.id, type.mainResolution, json);
    }


    public static void loadDetails(
            Context context, Type type, long[] dates, int days, Listener<Chart[]> listener) {
        executor.submit(() -> {
            try {
                final Chart[] charts = new Chart[dates.length];
                for (int i = 0; i < dates.length; i++) {
                    charts[i] = loadChartDetails(context, type, dates[i], days);
                    fixSources(charts[i], type);
                }
                mainHandler.post(() -> listener.onResult(charts));
            } catch (Throwable ex) {
                Log.e("Charts", "Can't read chart details", ex);
            }
        });
    }

    private static Chart loadChartDetails(
            Context appContext, Type type, long date, int days) throws Exception {

        final Calendar calendar = Calendar.getInstance(TIME_ZONE);
        calendar.setTimeInMillis(date);

        // Loading data for X days with requested day in the middle.
        // Note, that we need to load one more day in the end to get missing last value.
        calendar.add(Calendar.DAY_OF_MONTH, -days / 2);
        final long from = calendar.getTimeInMillis();

        calendar.add(Calendar.DAY_OF_MONTH, days);
        final long to = calendar.getTimeInMillis();

        final Chart chart;

        if (type.detailsResolution != null) {
            // Combining details for several days into single chart
            calendar.setTimeInMillis(from);

            final List<Chart> charts = new ArrayList<>(days);
            for (int i = 0; i <= days; i++) {
                try {
                    charts.add(loadChartDetails(appContext, type, calendar.getTimeInMillis()));
                } catch (Exception ignored) {
                    // No details for the day, just skipping it
                }
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }

            chart = mergeCharts(charts);
        } else {
            // No details for this chart, just getting a part of original chart
            initCache(appContext);
            chart = cache.get(type);
        }

        return chart == null ? null : subChart(chart, from, to);
    }

    private static Chart loadChartDetails(Context appContext, Type type, long date)
            throws Exception {
        final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        dateFormat.setTimeZone(TIME_ZONE);

        final SimpleDateFormat dayFileFormat = new SimpleDateFormat(DAY_FILE_FORMAT, Locale.US);
        dayFileFormat.setTimeZone(TIME_ZONE);

        final String fileName = BASE_DIR + "/" + type.id
                + "/" + dateFormat.format(date) + "/" + dayFileFormat.format(date);
        final String json = readAsset(appContext.getAssets(), fileName);
        return ChartParser.parse(type.id, type.detailsResolution, json);
    }

    private static Chart mergeCharts(List<Chart> charts) {
        if (charts.isEmpty()) {
            return null;
        }

        final Chart first = charts.get(0);

        int size = 0;
        for (Chart chart : charts) {
            size += chart.x.length;
        }

        final long[] x = new long[size];
        final int[][] y = new int[first.sources.length][size];

        int pos = 0;
        for (Chart chart : charts) {
            System.arraycopy(chart.x, 0, x, pos, chart.x.length);

            for (int s = 0; s < chart.sources.length; s++) {
                final int[] sourceY = chart.sources[s].y;
                System.arraycopy(sourceY, 0, y[s], pos, sourceY.length);
            }
            pos += chart.x.length;
        }

        final int sourcesCount = first.sources.length;
        final Source[] sources = new Source[sourcesCount];
        for (int s = 0; s < sourcesCount; s++) {
            sources[s] = first.sources[s].setY(y[s]);
        }

        return first.setX(x).setSources(sources);
    }

    private static Chart subChart(Chart chart, long from, long to) {
        final int size = chart.x.length;
        int fromInd = 0;
        int toInd = size - 1;

        for (int i = 0; i < size; i++) {
            if (chart.x[i] >= from) {
                fromInd = i;
                break;
            }
        }
        for (int i = size - 1; i >= 0; i--) {
            if (chart.x[i] <= to) {
                toInd = i;
                break;
            }
        }

        final long[] resultX = new long[toInd - fromInd + 1];
        System.arraycopy(chart.x, fromInd, resultX, 0, resultX.length);

        final int sourcesCount = chart.sources.length;
        final Source[] sources = new Source[sourcesCount];
        for (int s = 0; s < sourcesCount; s++) {
            final Source source = chart.sources[s];
            final int[] resultY = new int[toInd - fromInd + 1];
            System.arraycopy(source.y, fromInd, resultY, 0, resultY.length);

            sources[s] = source.setY(resultY);
        }

        return chart.setX(resultX).setSources(sources);
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
        if (chart != null && type.namesOverride != null) {
            for (int i = 0, size = chart.sources.length; i < size; i++) {
                chart.sources[i] = chart.sources[i].setName(type.namesOverride[i]);
            }
        }
    }


    public interface Listener<T> {
        void onResult(T result);
    }

    public enum Type {
        // Line: Overview: by day. Details: 1 day by hour x 7(8?).
        FOLLOWERS(1, Resolution.DAY, Resolution.HOUR, null),

        // Line: Overview: by day. Details: 1 day by hour x 7(8?).
        INTERACTIONS(2, Resolution.DAY, Resolution.HOUR, null),

        // Overview: Bars by day. Details: Bars 1 day by hour x 7(8?).
        MESSAGES(3, Resolution.DAY, Resolution.HOUR, new String[] {
                "Text", "Photo", "Audio", "Sticker", "Video", "Document", "Location"
        }),

        // Overview: Bars by day. Details: Line 1 day by hour x 3 for 0, -1, -7.
        VIEWS(4, Resolution.DAY, Resolution.FIVE_MIN, null),

        // Overview: Percentage by day. Details: Pie + Area by day x 7.
        APPS(5, Resolution.DAY, null,
                new String[] { "Android", "iPhone", "OSX", "Web", "Desktop", "Other" });

        final int id;
        final Resolution mainResolution;
        final Resolution detailsResolution;
        final String[] namesOverride;

        Type(int id, Resolution mainResolution, Resolution detailsResolution,
                String[] namesOverride) {
            this.id = id;
            this.mainResolution = mainResolution;
            this.detailsResolution = detailsResolution;
            this.namesOverride = namesOverride;
        }
    }

}
