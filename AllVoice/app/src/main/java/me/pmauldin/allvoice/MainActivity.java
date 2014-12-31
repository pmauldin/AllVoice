package me.pmauldin.allvoice;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.MetadataChangeSet;

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
    private static final String LOG_TAG = "Custom";

    private static String mFileName = null;
    private static String path = null;
    private static File dir = null;
    final Context context = this;

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

        final ImageButton recordButton = (ImageButton) findViewById(R.id.record);

        // CHECK IF DIR EXISTS, AND CREATE IF NOT
        dir = new File(Environment.getExternalStorageDirectory() + "/AllVoice");
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
                            recordButton.setImageResource(R.drawable.record);
                            // STOP RECORDING //
                            recorder.stop();
                            recorder.reset();
                            createDialog();

                        } else {
                              recordButton.setImageResource(R.drawable.stop);
                            // START RECORDING //
                            try {
                                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                                path = getCurrentTimeFormat(DateFormat) + ".3gp";
                                mFileName = dir.getAbsolutePath() + "/" + path;
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

    }

    @Override
    protected void onPause() {
        if(recording) {
            recorder.stop();
        } else if(playing) {
            mPlayer.stop();
        }
        super.onPause();
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
        SimpleDateFormat df = new SimpleDateFormat(timeFormat);
        Calendar c = Calendar.getInstance();

        return df.format(c.getTime());
    }

    public void createDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.message);
        builder.setCancelable(false);

        final EditText input = new EditText(this);
        input.setHint(path);
        builder.setView(input);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String fileName = input.getText().toString();
                if(!fileName.equals("")) {
                   File file = new File(dir.getPath()+"/", path);
                   file.renameTo(new File(dir.getAbsoluteFile()+"/" + fileName + ".3gp"));
                }
                dialog.cancel();
            }
        });
        builder.setNegativeButton(R.string.discard, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                File file = new File(dir.getPath()+"/", path);
                boolean deleted = file.delete();
                dialog.cancel();
            }
        });
        builder.setNeutralButton(R.string.neutral, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {

            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play();
            }
        });
    }

    public void play() {
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
