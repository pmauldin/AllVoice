package me.pmauldin.allvoice;

import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.PopupWindow;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/* TODO
    * CREATE PREFERENCES SCREEN -
        * Checkboxes
            * Google Drive, DropBox, Box, OneDrive(?)
            * Also Save Notes locally?
            * Auto-name or custom?
            * Stop Recording when app is in background?

    * CHECK IF PREFERENCES ALREADY SET; IF NOT, GO TO PREFERENCES SCREEN, ELSE GO TO MAIN
        * If no options are selected for upload, don't allow them to continue

    * POPUP AFTER STOPPING THE RECORDING
        * Allow Discarding of note
        * Playback and pausing, restart
        * Save Button
        * Generate name based on time/date, and make it editable based on setting

    * UPLOAD
*/

public class MainActivity extends ActionBarActivity {
    private static final String LOG_TAG = "AudioRecordTest";

    private static String mFileName = null;

    private static boolean recording = false;
    private static boolean playing = false;

    private static final String DateFormat = "M-d-y-k:m:s";
    private MediaPlayer mPlayer = null;
    private MediaRecorder recorder = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recorder = new MediaRecorder();

//        final Button recordButton = (Button) findViewById(R.id.record);
        final ImageButton recordButton = (ImageButton) findViewById(R.id.blueButton);
        final Button playButton = (Button) findViewById(R.id.play);
        playButton.setVisibility(View.INVISIBLE);

        // CHECK IF DIR EXISTS, AND CREATE IF NOT
        final File dir = new File(Environment.getExternalStorageDirectory() + "/AllVoice");
        if(!(dir.exists() && dir.isDirectory())) {
            try {
                dir.mkdirs();
            } catch (Exception e) {
                Log.e(LOG_TAG, "directory failed");
            }
        }


        recordButton.setOnClickListener(
                new Button.OnClickListener(){
                    public void onClick(View v) {
                        if(recording) {
//                            recordButton.setText("Start Recording");
//                            recordButton.setTypeface(null, Typeface.NORMAL);
//                            recordButton.setTextColor(Color.BLACK);
                            recordButton.setImageResource(R.drawable.bluebutton);
                            // STOP RECORDING //
                            recorder.stop();
                            recorder.reset();
                            playButton.setVisibility(View.VISIBLE);

                        } else {
//                            recordButton.setText("Stop Recording");
//                            recordButton.setTypeface(null, Typeface.BOLD);
//                            recordButton.setTextColor(Color.RED);
                              recordButton.setImageResource(R.drawable.redbutton);
                            // START RECORDING //
                            try {
                                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                                mFileName = dir.getAbsolutePath() + "/" + getCurrentTimeFormat(DateFormat) + ".3gp";
                                recorder.setOutputFile(mFileName);
                                recorder.prepare();
                                recorder.start();
                            } catch (IOException e) {
                                Log.e(LOG_TAG, "prepare() failed");
                            }

                        }
                        recording = !recording;
                    }
                }
        );

        playButton.setOnClickListener(
                new Button.OnClickListener() {
                    public void onClick(View v) {
                        if(!playing) {
                            mPlayer = new MediaPlayer();
                            try {
                                mPlayer.setDataSource(mFileName);
                                mPlayer.prepare();
                                mPlayer.start();
                            } catch (IOException e) {
                                Log.e(LOG_TAG, "prepare() failed");
                            }
                        } else{
                            mPlayer.release();
                            mPlayer = null;
                        }
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

    private String getCurrentTimeFormat(String timeFormat){
        String time = "";
        SimpleDateFormat df = new SimpleDateFormat(timeFormat);
        Calendar c = Calendar.getInstance();
        time = df.format(c.getTime());

        return time;
    }

}
