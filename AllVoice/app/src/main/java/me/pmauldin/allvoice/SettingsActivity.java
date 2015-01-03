package me.pmauldin.allvoice;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getActionBar().setTitle("Settings");
        } catch (NullPointerException e) {
            Log.i("Local", e.toString());
        }

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
}