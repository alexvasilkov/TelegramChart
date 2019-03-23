package com.alexvasilkov.telegram.chart.utils;

import android.graphics.Matrix;

public class ChartMath {

    private static final float[] tmpFloatPoint = new float[2];

    public static float mapX(Matrix matrix, float x) {
        tmpFloatPoint[0] = x;
        tmpFloatPoint[1] = 0f;
        matrix.mapPoints(tmpFloatPoint);
        return tmpFloatPoint[0];
    }

    public static float mapY(Matrix matrix, float y) {
        tmpFloatPoint[0] = 0f;
        tmpFloatPoint[1] = y;
        matrix.mapPoints(tmpFloatPoint);
        return tmpFloatPoint[1];
    }

}
