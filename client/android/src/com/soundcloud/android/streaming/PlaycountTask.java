package com.soundcloud.android.streaming;

import com.soundcloud.api.ApiWrapper;
import com.soundcloud.api.Endpoints;
import com.soundcloud.api.Request;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

public class PlaycountTask extends StreamItemTask {
    static final String LOG_TAG = StreamLoader.class.getSimpleName();
    private final boolean mUsePlaycountApi;

    public PlaycountTask(StreamItem item, ApiWrapper api, boolean usePlaycountApi) {
        super(item, api);
        mUsePlaycountApi = usePlaycountApi;
    }

    @Override
    public Bundle execute() throws IOException {
        final Bundle b = new Bundle();
        if (item.isAvailable()) {
            if (Log.isLoggable(StreamLoader.LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "Logging playcount for item " + item);
            }
            return mUsePlaycountApi ? logWithTrusted(b) : logWithRangeRequest(b);
        } else {
            if (Log.isLoggable(StreamLoader.LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "Not logging playcount for item " + item + ": not available");
            return b;
        }
    }

    private Bundle logWithRangeRequest(Bundle b) throws IOException {
        // request 1st byte to get counted as play
        HttpResponse resp = api.get(Request.to(item.getUrl().getPath()).range(0, 1));

        final int status = resp.getStatusLine().getStatusCode();
        switch (status) {
            case HttpStatus.SC_MOVED_TEMPORARILY:
                if (Log.isLoggable(StreamLoader.LOG_TAG, Log.DEBUG))
                    Log.d(LOG_TAG, "logged playcount for " + item);
                b.putBoolean("success", true);
                return b;
            default:
                throw new IOException("unexpected status code received:" + resp.getStatusLine());
        }
    }

    private Bundle logWithTrusted(Bundle b) throws IOException {
        HttpResponse resp = api.post(Request.to(Endpoints.TRACK_PLAYS, item.trackId));
        switch (resp.getStatusLine().getStatusCode()) {
            case HttpStatus.SC_UNAUTHORIZED: /* bad token or scope */
            case HttpStatus.SC_FORBIDDEN: /* track not streamable */

                Log.w(LOG_TAG, "could not log playcount:" + resp.getStatusLine());
                break;
            case HttpStatus.SC_ACCEPTED: /* ok */

                if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                    Log.d(LOG_TAG, "logged playcount for " + item);
                b.putBoolean("success", true);
                break;
            default:
                // retry
                throw new IOException("unexpected status code received:" + resp.getStatusLine());
        }
        return b;
    }
}
