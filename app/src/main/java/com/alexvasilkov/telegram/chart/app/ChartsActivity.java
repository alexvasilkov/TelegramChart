package com.alexvasilkov.telegram.chart.app;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import com.alexvasilkov.telegram.chart.R;
import com.alexvasilkov.telegram.chart.app.utils.Colors;
import com.alexvasilkov.telegram.chart.app.widgets.charts.BaseChartWidget;

public class ChartsActivity extends Activity {

    private Preferences prefs;
    private Colors colors;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Applying current night mode
        super.onCreate(savedInstanceState);

        setContentView(R.layout.charts_activity);

        if (getActionBar() != null) {
            getActionBar().setDisplayShowTitleEnabled(true);
            getActionBar().setTitle(R.string.charts_title);
        }

        prefs = new Preferences(this);
        setNightMode(prefs.isInNightMode());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.night_mode, menu);
        menu.findItem(R.id.menu_night_mode).getIcon()
                .setColorFilter(colors.text, PorterDuff.Mode.SRC_IN);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_night_mode) {
            boolean isInNightMode = !prefs.isInNightMode();
            prefs.setInNightMode(isInNightMode);
            setNightMode(isInNightMode);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setNightMode(boolean isNight) {
        colors = new Colors(this, isNight ? Colors.Type.NIGHT : Colors.Type.DAY);
        applyColors();
    }

    private void applyColors() {
        invalidateOptionsMenu();

        Colors.setStatusBarColor(getWindow(), colors.statusBar);
        Colors.setNavBarColor(getWindow(), colors.navBar);
        Colors.setActionBarColors(getActionBar(), colors.actionBar, colors.text);
        Colors.setWindowBackground(getWindow(), colors.window);


        ViewGroup list = findViewById(R.id.charts_list);
        for (int i = 0, size = list.getChildCount(); i < size; i++) {
            ((BaseChartWidget) list.getChildAt(i)).setColors(colors);
        }
    }

}
