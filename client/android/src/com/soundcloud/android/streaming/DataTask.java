package com.soundcloud.android.streaming;

import com.soundcloud.android.utils.BufferUtils;
import com.soundcloud.api.ApiWrapper;
import com.soundcloud.api.Request;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Locale;

abstract class DataTask extends StreamItemTask {
    static final String LOG_TAG = StreamLoader.LOG_TAG;

    final Range byteRange, chunkRange;
    final ByteBuffer buffer;

    public DataTask(StreamItem item, Range chunkRange, Range byteRange, ApiWrapper api) {
        super(item, api);
        if (byteRange == null) throw new IllegalArgumentException("byterange cannot be null");
        if (chunkRange == null) throw new IllegalArgumentException("chunkRange cannot be null");
        if (item.getContentLength() > 0 &&
            byteRange.start > item.getContentLength()) {

            Log.w(LOG_TAG, String.format("requested range > contentlength (%d > %d)",
                    byteRange.start, item.getContentLength()));
        }
        this.byteRange = byteRange;
        this.chunkRange = chunkRange;
        buffer = ByteBuffer.allocate(byteRange.length);
    }

    protected abstract int getData(URL url, int start, int end, ByteBuffer dst) throws IOException;

    @Override
    public Bundle execute() throws IOException {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            Log.d(LOG_TAG, String.format("fetching chunk %d for item %s with range %s", chunkRange.start, item, byteRange));

        final Bundle b = new Bundle();
        final URL redirect = item.redirectUrl();
        if (redirect == null) {
            return b;
        }
        // need to rewind buffer - request might get retried later.
        buffer.rewind();

        final int status = getData(redirect, byteRange.start, byteRange.end() - 1, buffer);
        b.putInt("status", status);
        switch (status) {
            case HttpStatus.SC_OK:
            case HttpStatus.SC_PARTIAL_CONTENT:
                // already handled in getData()
                buffer.flip();

                b.putBoolean("success", true);
                break;
            // link has expired
            case HttpStatus.SC_FORBIDDEN:
                if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                    Log.d(LOG_TAG, "invalidating redirect url");
                item.invalidateRedirectUrl();
                item.setHttpError(status);
                break;
            // permanent failure
            case HttpStatus.SC_PAYMENT_REQUIRED:
            case HttpStatus.SC_NOT_FOUND:
            case HttpStatus.SC_GONE:
                if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                    Log.d(LOG_TAG, "marking item as unavailable");
                item.markUnavailable(status);
                break;

            default:
                item.setHttpError(status);
                throw new IOException("unexpected status code received: " + status);
        }
        return b;
    }

    @Override
    public String toString() {
        return "DataTask{" +
                "item=" + item +
                ", byteRange=" + byteRange +
                ", chunkRange=" + chunkRange +
                '}';
    }

    public static DataTask create(StreamItem item, Range chunkRange, Range range, ApiWrapper wrapper) {
        // google recommends using HttpURLConnection from Gingerbread on:
        // http://android-developers.blogspot.com/2011/09/androids-http-clients.html
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return new HttpURLConnectionDataTask(item, chunkRange, range, wrapper);
        } else {
            return new HttpClientDataTask(item, chunkRange, range, wrapper);
        }
    }

    static class HttpClientDataTask extends DataTask {
        public HttpClientDataTask(StreamItem item, Range chunkRange, Range byteRange, ApiWrapper api) {
            super(item, chunkRange, byteRange, api);
        }

        @Override
        protected int getData(URL url, int start, int end, ByteBuffer dst) throws IOException {
            HttpGet get = new HttpGet(url.toString());
            get.setHeader("Range", Request.formatRange(start, end));
            HttpResponse resp = api.safeExecute(null, get);

            final int status = resp.getStatusLine().getStatusCode();
            switch (status) {
                case HttpStatus.SC_OK:
                case HttpStatus.SC_PARTIAL_CONTENT:
                    if (!BufferUtils.readBody(resp, buffer)) {
                        throw new IOException("error reading buffer");
                    }
            }
            return status;
        }
    }

    static class HttpURLConnectionDataTask extends DataTask {
        static final int READ_TIMEOUT = 10 * 1000;
        static final int CONNECTION_TIMEOUT = 10 * 1000;

        public HttpURLConnectionDataTask(StreamItem item, Range chunkRange, Range byteRange, ApiWrapper api) {
            super(item, chunkRange, byteRange, api);
        }

        @Override
        protected int getData(URL url, int start, int end, ByteBuffer dst) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Range",
                    String.format(Locale.ENGLISH, "bytes=%d-%d", start, end));

            connection.setReadTimeout(READ_TIMEOUT);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.setRequestProperty("User-Agent", api.getUserAgent());
            connection.setUseCaches(false);
            InputStream is = null;
            try {
                connection.connect();
                final int status = connection.getResponseCode();
                switch (status) {
                    case HttpStatus.SC_OK:
                    case HttpStatus.SC_PARTIAL_CONTENT:
                        if (dst.remaining() < connection.getContentLength()) {
                            throw new IOException(String.format(Locale.ENGLISH, "allocated buffer is too small (%d < %d)",
                                        dst.remaining(), connection.getContentLength()));
                        }
                        is = new BufferedInputStream(connection.getInputStream());
                        final byte[] bytes = new byte[8192];
                        int n;
                        while ((n = is.read(bytes)) != -1) {
                            dst.put(bytes, 0, n);
                        }
                }
                return status;
            } finally {
                if (is != null) is.close();
                connection.disconnect();
            }
        }
    }
}
