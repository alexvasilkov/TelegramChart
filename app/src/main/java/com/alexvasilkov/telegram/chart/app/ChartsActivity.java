package com.alexvasilkov.telegram.chart.app;

import android.os.Bundle;

import com.alexvasilkov.telegram.chart.R;

public class ChartsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.charts_activity);
        setTitle(R.string.charts_title);
        requireActionBar().setDisplayShowTitleEnabled(true);
    }

}
