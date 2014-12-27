package me.pmauldin.allvoice;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;


/* TODO
    * CREATE PREFERENCES SCREEN -
        * Checkboxes
            * Google Drive, DropBox, Box, OneDrive(?)
            * Also Save Notes locally?
            * Auto-name or custom?

    * CHECK IF PREFERENCES ALREADY SET; IF NOT, GO TO PREFERENCES SCREEN, ELSE GO TO MAIN
        * If no options are selected for upload, don't allow them to continue

    * MAKE FOLDER TO SAVE FILES LOCALLY (REGARDLESS OF SETTING)
        * Check at onCreate

    * ALLOW RECORDING ***************

    * POPUP AFTER STOPPING THE RECORDING
        * Allow Discarding of note
        * Playback and pausing, restart
        * Save Button
        * Generate name based on time/date, and make it editable based on setting

    * MAKE SURE TO STOP MIC AND DISCARD ON EXIT

    * UPLOAD
*/

public class MainActivity extends ActionBarActivity {

    private static boolean recording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button recordButton = (Button) findViewById(R.id.record);

        recordButton.setOnClickListener(
                new Button.OnClickListener(){
                    public void onClick(View v) {
                        if(recording) {
                            // STOP RECORDING //


                            recordButton.setText("Start Recording");
                        } else {
                            // START RECORDING //


                            recordButton.setText("Stop Recording");
                        }
                        recording = !recording;
                    }
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
