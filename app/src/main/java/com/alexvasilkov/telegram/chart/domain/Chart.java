package com.alexvasilkov.telegram.chart.domain;

import java.util.List;

public class Chart {

    public final int id;
    public final Type type;
    public final long[] x;
    public final List<Source> sources;

    public Chart(int id, Type type, long[] x, List<Source> sources) {
        this.id = id;
        this.type = type;
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
        LINES, TWO_LINES, BARS, AREA
    }

}
