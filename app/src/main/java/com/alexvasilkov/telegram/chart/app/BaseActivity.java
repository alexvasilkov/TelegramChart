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

abstract class BaseActivity extends Activity {

    private Preferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Applying current night mode
        prefs = new Preferences(this);
        setNightMode(prefs.isInNightMode());

        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.night_mode, menu);
        menu.findItem(R.id.menu_night_mode).getIcon().setTint(Color.WHITE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_night_mode) {
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
        final int uiMode = res.getConfiguration().uiMode;
        final int newUiMode = newNightMode | (uiMode & ~Configuration.UI_MODE_NIGHT_MASK);

        if (uiMode != newUiMode) {
            final Configuration config = new Configuration(res.getConfiguration());
            config.uiMode = newUiMode;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
    }

    protected ActionBar requireActionBar() {
        ActionBar bar = super.getActionBar();
        if (bar == null) {
            throw new NullPointerException();
        } else {
            return super.getActionBar();
        }
    }
}
