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

    public Chart setType(Type type) {
        return new Chart(this.id, type, this.resolution, this.x, this.sources);
    }

    public Chart setX(long[] x) {
        return new Chart(this.id, this.type, this.resolution, x, this.sources);
    }

    public Chart setSources(Source[] sources) {
        return new Chart(this.id, this.type, this.resolution, this.x, sources);
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

        public Source setName(String name) {
            return new Source(name, this.color, this.y);
        }

        public Source setY(int[] y) {
            return new Source(this.name, this.color, y);
        }
    }


    public enum Type {
        LINES, LINES_INDEPENDENT, BARS, AREA, AREA_SQUARE, PIE
    }

}
