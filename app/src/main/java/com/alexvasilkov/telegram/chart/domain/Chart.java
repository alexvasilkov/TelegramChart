package com.alexvasilkov.telegram.chart.domain;

import java.util.List;

public class Chart {

    public final long[] x;
    public final List<Line> lines;

    public Chart(long[] x, List<Line> lines) {
        this.x = x;
        this.lines = lines;
    }

    public static class Line {
        public final String name;
        public final int color;
        public final int[] y;

        public Line(String name, int color, int[] y) {
            this.name = name;
            this.color = color;
            this.y = y;
        }
    }

}
