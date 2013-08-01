package com.soundcloud.android.streaming;

import com.soundcloud.api.ApiWrapper;
import com.soundcloud.api.CloudAPI;
import com.soundcloud.api.Stream;
import org.apache.http.HttpStatus;

import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

class HeadTask extends StreamItemTask implements HttpStatus {
    static final String LOG_TAG = StreamLoader.LOG_TAG;
    private final boolean skipLogging;

    public HeadTask(StreamItem item, ApiWrapper api, boolean skipPlayLogging) {
        super(item, api);
        this.skipLogging = skipPlayLogging;
    }

    @Override
    public Bundle execute() throws IOException {
        Bundle b = new Bundle();
        try {
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "resolving " + item.streamItemUrl());
            }

            Stream stream = api.resolveStreamUrl(item.streamItemUrl(), skipLogging);
            item.initializeFromStream(stream);
            b.putBoolean("success", true);
            return b;
        } catch (CloudAPI.ResolverException e) {
            Log.w(LOG_TAG, "error resolving " + item, e);
            final int statusCode = e.getStatusCode();

            b.putInt("status", statusCode);

            if (statusCode >= 400 && statusCode < 500) {
                item.markUnavailable(statusCode);
                return b;
            } else {
                item.setHttpError(statusCode);
                throw e;
            }
        }
    }
}