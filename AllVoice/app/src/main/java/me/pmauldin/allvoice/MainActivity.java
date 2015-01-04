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
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/* TODO
    * UPLOAD
        * DROPBOX
        * BOX
        * ONEDRIVE
*/

public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String LOG_TAG = "Local";

    // FILE VARIABLES
    private static Boolean local = true;
    private static String fileType = "";
    private static String mFileName = null;
    private static String name = null;
    private static File dir = null;
    private static File dirTemp = null;
    private static String finalPath = null;
    final Context context = this;

    private static String toastText= "Please check at least one option";

    // VARIABLES FOR RECORDING & PLAYING AUDIO
    private static boolean recording = false;
    private static boolean playing = false;
    private static final String DateFormat = "M_d_y-k-m-s";
    private MediaPlayer mPlayer = null;
    private MediaRecorder recorder = null;

    // CLOUD STORAGE VARIABLES
    private static SharedPreferences sharedPrefs;

    // GOOGLE DRIVE
    private static String format = "mp3";
    private static boolean drive = false;
    private static final int RC_SIGN_IN = 0;
    private GoogleApiClient mClient;
    private boolean mIntentInProgress = false;
    private static int attempts = 0;
    private DriveId mFolderDriveId;

    // DROPBOX
    private static boolean dropbox = false;
    private static final String APP_KEY = "c00moo0mcy4htjh";
    private static final String APP_SECRET = "gujtifmjcfvt7bk";
    private static final String ACCOUNT_PREFS_NAME = "prefs";
    private static final String ACCESS_KEY_NAME = "ACCESS_KEY";
    private static final String ACCESS_SECRET_NAME = "ACCESS_SECRET";
    private static String ACCESS_SECRET = "";
    private DropboxAPI<AndroidAuthSession> mDBApi;

    // BOX
    private static boolean box = false;

    // ONEDRIVE
    private static boolean onedrive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recorder = new MediaRecorder();

        final ImageButton recordButton = (ImageButton) findViewById(R.id.record);

        // SETTING AND GETTING PREFERENCES
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        setPrefs();

        // IF NO PREFS SET, GO TO SETTINGS SCREEN
        if(!(local || drive || dropbox || box || onedrive)) {
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, toastText, duration);
            toast.show();

            Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(i);
        }

        // CHECK IF DIR EXISTS, AND CREATE IF NOT
        dir = new File(Environment.getExternalStorageDirectory() + "/AllVoice");
        if(!(dir.exists() && dir.isDirectory())) {

            try {

                dir.mkdirs();
            } catch (Exception e) {
                Log.e(LOG_TAG, "directory failed");
            }
        }

        // CREATE TEMP DIR
        if(!local) {
            dirTemp = new File(Environment.getExternalStorageDirectory() + "/AllVoiceTemp");
            if(!(dirTemp.exists() && dirTemp.isDirectory())) {
                try {
                    dirTemp.mkdirs();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "temp directory failed");
                }
            }
        }

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
                                name = getCurrentTimeFormat(DateFormat) + fileType;
                                if(local) {
                                    mFileName = dir.getAbsolutePath() + "/" + name;
                                } else {
                                    mFileName = dirTemp.getAbsolutePath() + "/" + name;
                                }
                                Log.i(LOG_TAG, mFileName);
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

        if(!local) {
            deleteDirectory(dirTemp);
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setPrefs();

        if(!(local || drive || dropbox || box || onedrive)) {
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, toastText, duration);
            toast.show();

            Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(i);
        }

        if(!local) {
            dirTemp = new File(Environment.getExternalStorageDirectory() + "/AllVoiceTemp");
            if(!(dirTemp.exists() && dirTemp.isDirectory())) {
                try {
                    dirTemp.mkdirs();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "temp directory failed");
                }
            }
        }


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

        // DROPBOX
        if(dropbox) {
            AndroidAuthSession session = mDBApi.getSession();

            if (session.authenticationSuccessful()) {
                try {
                    // Required to complete auth, sets the access token on the session
                    session.finishAuthentication();
                    storeAuth(session);
                } catch (IllegalStateException e) {
                    Log.i("Dropbox", "Error authenticating dropbox", e);
                }
            }
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
        input.setHint(name);
        builder.setView(input);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String fileName = input.getText().toString();
                finalPath = mFileName;
                if(!fileName.equals("")) {
                   File file;
                   if(local) {
                       file = new File(dir.getPath()+"/", name);
                       finalPath = dir.getAbsolutePath()+"/" + fileName + fileType;
                   } else {
                       file = new File(dirTemp.getPath()+"/", name);
                       finalPath = dirTemp.getAbsolutePath()+"/" + fileName + fileType;
                   }
                   name = fileName + fileType;
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
                File file;
                if(local) {
                    file = new File(dir.getPath()+"/", name);
                } else {
                    file = new File(dirTemp.getPath()+"/", name);
                }

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

    public static boolean deleteDirectory(File path) {
        if( path.exists() ) {
            File[] files = path.listFiles();
            if (files == null) {
                return true;
            }
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                }
                else {
                    files[i].delete();
                }
            }
        }
        return( path.delete() );
    }

    // CLOUD STORAGE //
    private void initializeServices() {
        setPrefs();

        // GOOGLE DRIVE
        if(drive) {
            signInToDrive();
            createDriveFolder();
            Log.i("Google", "GOOGLE DRIVE INITIALIZATION SUCCESSFUL " + drive);
        }

        // DROPBOX
        if(dropbox) {
            signInToDropbox();
            Log.i("Dropbox", "DROPBOX INITIALIZATION SUCCESSFUL");
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
        fileType = sharedPrefs.getString("pref_format", ".mp3");
        local = sharedPrefs.getBoolean("pref_local", true);

        switch (fileType) {
            case ".3gp":
                format = "video/3gpp";
                break;
            case ".mp3":
                format = "audio/mpeg";
                break;
            case ".mp4":
                format = "audio/mp4";
                break;
            case ".flac":
                format = "audio/ogg";
                break;
            default:
                format = "audio/mpeg";
                break;
        }

    }

    private void upload() {
        // GOOGLE DRIVE
        if(drive) {
            uploadFileToDrive();
        }

        if(dropbox) {
            uploadFileToDropbox();
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

    // GOOGLE DRIVE INTEGRATION
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
                                    .setMimeType(format).setTitle(name).build();

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

    // END GOOGLE DRIVE INTEGRATION


    // BEGIN DROPBOX INTEGRATION //
    private void signInToDropbox() {
        AndroidAuthSession session = buildSession();
        mDBApi = new DropboxAPI<AndroidAuthSession>(session);
        if(ACCESS_SECRET.equals("")) {
            mDBApi.getSession().startOAuth2Authentication(context);
        }
    }

    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
        return session;
    }

    private void loadAuth(AndroidAuthSession session) {
        String key = sharedPrefs.getString(ACCESS_KEY_NAME, null);
        String secret = sharedPrefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) {
            return;
        }

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
            session.setOAuth2AccessToken(secret);
            ACCESS_SECRET = secret;
        } else {
            // Still support using old OAuth 1 tokens.
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
            ACCESS_SECRET = secret;
        }
    }
    @SuppressWarnings("all")
    private void storeAuth(AndroidAuthSession session) {
        // Store the OAuth 2 access token, if there is one.
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            SharedPreferences.Editor edit = sharedPrefs.edit();
            edit.putString(ACCESS_KEY_NAME, "oauth2:");
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.commit();
            return;
        }
        // Store the OAuth 1 access token, if there is one.  This is only necessary if
        // you're still using OAuth 1.
        AccessTokenPair oauth1AccessToken = session.getAccessTokenPair();
        if (oauth1AccessToken != null) {
            SharedPreferences.Editor edit = sharedPrefs.edit();
            edit.putString(ACCESS_KEY_NAME, oauth1AccessToken.key);
            edit.putString(ACCESS_SECRET_NAME, oauth1AccessToken.secret);
            edit.commit();
            return;
        }
    }

    private void uploadFileToDropbox() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                File file = new File(finalPath);
                FileInputStream inputStream;
                try {
                    inputStream = new FileInputStream(file);
                    try {
                        mDBApi.putFile("/" + name, inputStream, file.length(), null, null);
                    } catch (DropboxException e) {
                        Log.i("Dropbox", "Trying to putFile(): " + e.toString());
                    }
                } catch (FileNotFoundException e) {
                    Log.i("Dropbox", "DROPBOX: FILE NOT FOUND");
                }

            }
        };

        Thread dropboxThread = new Thread(r);
        dropboxThread.start();
    }
    // END DROPBOX INTEGRATION
}
