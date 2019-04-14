package com.alexvasilkov.telegram.chart.domain;

public class Chart {

    public final int id;
    public final Type type;
    public final Resolution resolution;
    public final long[] x;
    public final Source[] sources;

    public Chart(int id, Type type, Resolution resolution, long[] x, Source[] sources) {
        this.id = id;
        this.type = type;
        this.resolution = resolution;
        this.x = x;
        this.sources = sources;
    }

    public static class Source {
        public final String name;
        public final int color;
        public final int[] y;

        public Source(String name, int color, int[] y) {
            this.name = name;
            this.color = color;
            this.y = y;
        }
    }

    public enum Type {
        LINES, LINES_INDEPENDENT, BARS, AREA, AREA_SQUARE
    }

}
