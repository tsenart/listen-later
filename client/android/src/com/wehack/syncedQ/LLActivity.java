package com.wehack.syncedQ;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.soundcloud.api.Request;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.sql.Timestamp;

public class LLActivity extends FragmentActivity {

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_ON_SERVER_EXPIRATION_TIME =
            "onServerExpirationTimeMs";
    /**
     * Default lifespan (7 days) of a reservation until it is considered expired.
     */
    public static final long REGISTRATION_EXPIRY_TIME_MS = 1000 * 3600 * 24 * 7;

    /**
     * Tag used on log messages.
     */
    static final String TAG = "ListenLater";

    GoogleCloudMessaging gcm;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listen_later_activity);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        gcm = GoogleCloudMessaging.getInstance(this);
        String registrationId = getRegistrationId(getApplicationContext());
        if (!TextUtils.isEmpty(registrationId)) {
            subscribeBackground(registrationId);
        } else {
            registerBackground();
        }
    }

    /**
     * Stores the registration id, app versionCode, and expiration time in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration id
     */
    private void setRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.v(TAG, "Saving regId on app version " + appVersion);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        long expirationTime = System.currentTimeMillis() + REGISTRATION_EXPIRY_TIME_MS;

        Log.v(TAG, "Setting registration expiry time to " + new Timestamp(expirationTime));
        editor.putLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, expirationTime);
        editor.commit();
    }

    /**
     * Gets the current registration id for application on GCM service.
     * <p>
     * If result is empty, the registration has failed.
     *
     * @return registration id, or empty string if the registration is not
     *         complete.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.length() == 0) {
            Log.v(TAG, "Registration not found.");
            return "";
        }
        // check if app was updated; if so, it must clear registration id to
        // avoid a race condition if GCM sends a message
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion || isRegistrationExpired()) {
            Log.v(TAG, "App version changed or registration expired.");
            return "";
        }
        return registrationId;
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration id, app versionCode, and expiration time in the application's
     * shared preferences.
     */
    private void registerBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    if (gcm == null) {

                        Log.d(TAG, "registering for GCM");
                        gcm = GoogleCloudMessaging.getInstance(LLActivity.this);
                    }
                    return gcm.register(LLApplication.SENDER_ID);
                } catch (IOException ex) {
                    Log.w(TAG, ex);
                }
                return null;
            }

            @Override
            protected void onPostExecute(String id) {
                if (id != null) {
                    Log.d(TAG, "successfully registered for GCM");
                    setRegistrationId(LLActivity.this, id);
                    subscribeBackground(id);
                }
            }
        }.execute(null, null, null);
    }

    private void subscribeBackground(final String regId) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {

                HttpClient client = LLApplication.instance.getApiWrapper().getHttpClient();
                HttpResponse response = null;
                try {
                    Log.d(TAG, "subscribing on queue server");
                    response = client.execute(new HttpPost("http://54.246.158.145/subscribe/gcm?device_id="+regId));
                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        Log.d(TAG, "subscribed on server");
                    } else {
                        Log.w(TAG, "error subscribing: "+response.getStatusLine());
                    }
                } catch (IOException e) {
                    Log.w(TAG, e);
                }
                return null;
            }
        }.execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        return getSharedPreferences(LLActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * Checks if the registration has expired.
     *
     * <p>To avoid the scenario where the device sends the registration to the
     * server but the server loses it, the app developer may choose to re-register
     * after REGISTRATION_EXPIRY_TIME_MS.
     *
     * @return true if the registration has expired.
     */
    private boolean isRegistrationExpired() {
        final SharedPreferences prefs = getGCMPreferences(this);
        // checks if the information is not stale
        long expirationTime = prefs.getLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, -1);
        return System.currentTimeMillis() > expirationTime;
    }
}