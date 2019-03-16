package com.alexvasilkov.telegram.chart.utils;

import android.graphics.Matrix;

public class ChartMath {

    // This is a pre-computed zero-bits lookup table (maps a bit value mod 37 to its position)
    private static final int[] trailingZeroLookup = {
            32, 0, 1, 26, 2, 23, 27, 0, 3, 16, 24, 30, 28, 11, 0, 13, 4,
            7, 17, 0, 25, 22, 31, 15, 29, 10, 12, 6, 0, 21, 14, 9, 5,
            20, 8, 19, 18
    };

    private static final float[] tmpFloatPoint = new float[2];

    /**
     * Counts trailing zeros at O(1).
     *
     * See https://graphics.stanford.edu/~seander/bithacks.html#ZerosOnRightModLookup
     */
    public static int countTrailingZeroBits(int x) {
        return trailingZeroLookup[(-x & x) % 37];
    }


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

    public static float getScaleX(Matrix matrix) {
        return mapX(matrix, 1f) - mapX(matrix, 0f);
    }

}
