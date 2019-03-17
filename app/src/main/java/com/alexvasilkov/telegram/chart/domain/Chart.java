package com.alexvasilkov.telegram.chart.domain;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class Chart implements Parcelable {

    public final long[] x;
    public final List<Line> lines;

    public static final Creator<Chart> CREATOR = new Creator<Chart>() {
        @Override
        public Chart createFromParcel(Parcel in) {
            return new Chart(in);
        }

        @Override
        public Chart[] newArray(int size) {
            return new Chart[size];
        }
    };

    public Chart(long[] x, List<Line> lines) {
        this.x = x;
        this.lines = lines;
    }

    private Chart(Parcel in) {
        this.x = in.createLongArray();
        this.lines = in.createTypedArrayList(Line.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLongArray(x);
        dest.writeTypedList(lines);
    }

    @Override
    public int describeContents() {
        return 0;
    }


    public static class Line implements Parcelable {
        public final String name;
        public final int color;
        public final int[] y;

        public static final Creator<Line> CREATOR = new Creator<Line>() {
            @Override
            public Line createFromParcel(Parcel in) {
                return new Line(in);
            }

            @Override
            public Line[] newArray(int size) {
                return new Line[size];
            }
        };

        public Line(String name, int color, int[] y) {
            this.name = name;
            this.color = color;
            this.y = y;
        }

        private Line(Parcel in) {
            this.name = in.readString();
            this.color = in.readInt();
            this.y = in.createIntArray();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeInt(color);
            dest.writeIntArray(y);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

}
