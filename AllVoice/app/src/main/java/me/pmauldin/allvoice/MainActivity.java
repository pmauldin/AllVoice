package me.pmauldin.allvoice;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    * UPLOAD
        * GOOGLE DRIVE DONE
*/

public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String LOG_TAG = "Local";

    // FILE VARIABLES
    private static String fileType = ".3gp";
    private static String mFileName = null;
    private static String path = null;
    private static File dir = null;
    private static String finalPath = null;
    final Context context = this;

    // VARIABLES FOR RECORDING & PLAYING AUDIO
    private static boolean recording = false;
    private static boolean playing = false;
    private static final String DateFormat = "M-d-y-k:m:s";
    private MediaPlayer mPlayer = null;
    private MediaRecorder recorder = null;

    // CLOUD STORAGE VARIABLES
    private static SharedPreferences sharedPrefs;
    // GOOGLE DRIVE
    public static boolean drive = false;
    private static final int RC_SIGN_IN = 0;
    private GoogleApiClient mClient;
    private boolean mIntentInProgress = false;
    private static int attempts = 0;
    private DriveId mFolderDriveId;

    // DROPBOX
    public static boolean dropbox = false;

    // BOX
    public static boolean box = false;

    // ONEDRIVE
    public static boolean onedrive = false;


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

        // SETTING AND GETTING PREFERENCES
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // SIGN IN TO SERVICES
        initializeServices();

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
                                path = getCurrentTimeFormat(DateFormat) + fileType;
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
        if(drive && mClient.isConnected()) {
             mClient.disconnect();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        setPrefs();
        // GOOGLE DRIVE
        if(drive) {
            if (mClient == null) {
                // Create the API client and bind it to an instance variable.
                // We use this instance as the callback for connection and connection
                // failures.
                // Since no account name is passed, the user is prompted to choose.
                mClient = new GoogleApiClient.Builder(this)
                        .addApi(Drive.API)
                        .addScope(Drive.SCOPE_FILE)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();
            }
            // Connect the client. Once connected, the camera is launched.
            mClient.connect();
        }
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
            Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(i);
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
                finalPath = mFileName;
                if(!fileName.equals("")) {
                   File file = new File(dir.getPath()+"/", path);
                   finalPath = dir.getAbsolutePath()+"/" + fileName + fileType;
                   path = fileName + fileType;
                   boolean changed = file.renameTo(new File(finalPath));
                   if(!changed) {
                       Log.i(LOG_TAG, "An error occurred while renaming file");
                   }
                }
                upload();
                dialog.cancel();
            }
        });
        builder.setNegativeButton(R.string.discard, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                File file = new File(dir.getPath()+"/", path);
                boolean deleted = file.delete();
                if(!deleted) {
                    Log.i(LOG_TAG, "An error occurred while discarding the file");
                }
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


    // CLOUD STORAGE //
    private void initializeServices() {
        setPrefs();
        // GOOGLE DRIVE
        if(drive) {
            signInToDrive();
            createDriveFolder();
            Log.i(LOG_TAG, "GOOGLE DRIVE INITIALIZATION SUCCESSFUL " + drive);
        }

        // DROPBOX
        if(dropbox) {
            Log.i(LOG_TAG, "DROPBOX INITIALIZATION SUCCESSFUL");
        }

        // BOX
        if(box) {
            Log.i(LOG_TAG, "BOX INITIALIZATION SUCCESSFUL");
        }

        // ONEDRIVE
        if(onedrive) {
            Log.i(LOG_TAG, "ONEDRIVE INITIALIZATION SUCCESSFUL");
        }
    }

    private void setPrefs() {
        drive = sharedPrefs.getBoolean("pref_drive", false);
        dropbox = sharedPrefs.getBoolean("pref_dropbox", false);
        box = sharedPrefs.getBoolean("pref_box", false);
        onedrive = sharedPrefs.getBoolean("pref_onedrive", false);
    }

    private void upload() {
        // GOOGLE DRIVE
        if(drive) {
            uploadFileToDrive();
        }

        if(dropbox) {

        }

        if(box) {

        }

        if(onedrive) {

        }

    }

    public void signInToDrive() {
        mClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .build();

        mClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (!mIntentInProgress && result.hasResolution()) {
            try {
                Log.d("Google", "Connected Failed Try:" + result.toString());
                mIntentInProgress = true;
                result.startResolutionForResult(this, RC_SIGN_IN);
            } catch (IntentSender.SendIntentException e) {
                Log.d("Google", "Connected Failed Catch: " + e.toString());
                // The intent was canceled before it was sent.  Return to the default
                // state and attempt to connect to get an updated ConnectionResult.
                mIntentInProgress = false;
                mClient.connect();
            }
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // We've resolved any connection errors.  mClient can be used to
        // access Google APIs on behalf of the user.
        Log.d("Google", "Connected Successfully");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        mClient.connect();
    }

    private void uploadFileToDrive() {
        // Start by creating a new contents, and setting a callback.
        mClient.connect();
        if(mClient.isConnected()) {
            Log.i("Google", "Creating new contents.");
            attempts = 0;

            Drive.DriveApi.newDriveContents(mClient)
                    .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {

                        @Override
                        public void onResult(DriveApi.DriveContentsResult result) {
                            // If the operation was not successful, we cannot do anything
                            // and must
                            // fail.
                            if (!result.getStatus().isSuccess()) {
                                Log.i("Google", "Failed to create new contents.");
                                return;
                            }
                            // Otherwise, we can write our data to the new contents.
                            Log.i("Google", "New contents created.");
                            File file = new File(finalPath);
                            // Get an output stream for the contents.
                            OutputStream outputStream = result.getDriveContents().getOutputStream();
                            // Write the bitmap data from it.
                            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                            InputStream in = null;

                            try {
                                in = new FileInputStream(file);
                            } catch (IOException e) {
                                Log.e("Google", e.toString());
                            }

                            int singleByte;
                            try {
                                while ((singleByte = in.read()) != -1) {
                                    byteStream.write(singleByte);
                                }
                            } catch (IOException e) {
                                Log.e("Google", e.toString());
                            }

                            try {
                                outputStream.write(byteStream.toByteArray());
                            } catch (IOException e1) {
                                Log.i("Google", "Unable to write file contents.");
                            }
                            // Create the initial metadata - MIME type and title.
                            MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                    .setMimeType("video/3gpp").setTitle(path).build();

                            DriveFolder folder = Drive.DriveApi.getFolder(mClient, mFolderDriveId);
                            folder.createFile(mClient, metadataChangeSet, result.getDriveContents());
                        }
                    });
        } else {
            if(attempts < 3) {
                mClient.connect();
                attempts++;
                uploadFileToDrive();
            } else {
                Log.e("Google", "Not connected :(");
            }
        }
    }

    public void createDriveFolder() {
        Query q = new Query.Builder().addFilter(Filters.contains(SearchableField.TITLE, "AllVoice")).build();
        Drive.DriveApi.query(mClient, q)
                .setResultCallback(queryCallback);
    }

    ResultCallback<DriveFolder.DriveFolderResult> folderCreatedCallback = new
            ResultCallback<DriveFolder.DriveFolderResult>() {
                @Override
                public void onResult(DriveFolder.DriveFolderResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.i("Google", "Error while trying to create the folder");
                        return;
                    }
                    mFolderDriveId = result.getDriveFolder().getDriveId();
                    Log.i("Google", "Created a folder: " + mFolderDriveId);
                }
            };

    ResultCallback<DriveApi.MetadataBufferResult> queryCallback = new
            ResultCallback<DriveApi.MetadataBufferResult>() {
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if(result.getStatus().isSuccess()) {
                        MetadataBuffer mdb = null;
                        try {
                            mdb = result.getMetadataBuffer();
                            if(mdb == null) {
                                Log.i("Google", "Need to create folder");
                                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                        .setTitle("AllVoice").build();

                                Drive.DriveApi.getRootFolder(mClient).createFolder(mClient, changeSet).setResultCallback(folderCreatedCallback);
                            } else {
                                boolean check = false;
                                for(Metadata md : mdb) {
                                    check = true;
                                    if(md == null) {
                                        Log.i("Google", "Need to create folder");
                                        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                                .setTitle("AllVoice").build();

                                        Drive.DriveApi.getRootFolder(mClient).createFolder(mClient, changeSet).setResultCallback(folderCreatedCallback);
                                        continue;
                                    }
                                    Log.i("Google", "Folder already exists");
                                    mFolderDriveId = md.getDriveId();
                                }
                                if(!check) {
                                    Log.i("Google", "Need to create folder");
                                    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                            .setTitle("AllVoice").build();

                                    Drive.DriveApi.getRootFolder(mClient).createFolder(mClient, changeSet).setResultCallback(folderCreatedCallback);
                                }
                            }
                        } finally {
                            if(mdb != null) mdb.close();
                        }
                    }
                }
            };

}
