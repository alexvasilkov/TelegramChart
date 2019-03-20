package com.alexvasilkov.telegram.chart.app;

import android.app.ActionBar;
import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.alexvasilkov.telegram.chart.R;

public abstract class BaseActivity extends Activity {

    private Preferences prefs;
    private boolean isInNightMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Applying current night mode
        prefs = new Preferences(this);
        isInNightMode = prefs.isInNightMode();
        setNightMode(isInNightMode);

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Have to restart activity if night mode is changed from another place
        if (isInNightMode != prefs.isInNightMode()) {
            recreate();
        }
    }

    protected void showBackButton() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.night_mode, menu);
        menu.findItem(R.id.menu_night_mode).getIcon().setTint(Color.WHITE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        } else if (item.getItemId() == R.id.menu_night_mode) {
            boolean isInNightMode = !prefs.isInNightMode();
            prefs.setInNightMode(isInNightMode);

            setNightMode(isInNightMode);
            recreate();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setNightMode(boolean isNightMode) {
        final int newNightMode = isNightMode
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;

        final Resources res = getResources();
        final Configuration config = new Configuration(res.getConfiguration());
        config.uiMode = newNightMode | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

}
