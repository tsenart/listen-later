package com.soundcloud.android.task.fetch;

import com.soundcloud.android.model.WaveformData;
import com.soundcloud.android.task.ParallelAsyncTask;
import com.soundcloud.android.utils.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class WaveformFetcher extends ParallelAsyncTask<URL, Void, WaveformData> {
    private static final String TAG = WaveformFetcher.class.getSimpleName();

    @Override
    protected WaveformData doInBackground(URL... url) {
        if (url == null || url.length == 0) throw new IllegalArgumentException("Need url");
        try {
            HttpURLConnection connection = (HttpURLConnection) url[0].openConnection();
            connection.setUseCaches(true);
            int code = connection.getResponseCode();
            switch (code) {
                case HttpURLConnection.HTTP_OK:
                    return parseWaveformData(IOUtils.readInputStream(connection.getInputStream()));
                default:
                     Log.w(TAG, "invalid status code received: " +code);
            }
        } catch (IOException e) {
            Log.w(TAG, e);
        } catch (JSONException e) {
            Log.w(TAG, "invalid waveform JSON", e);
        }
        return null;
    }

    private static WaveformData parseWaveformData(String data) throws JSONException, IOException {
        JSONObject obj = new JSONObject(data);
        int width = obj.getInt("width");
        int height = obj.getInt("height");

        if (width <= 0) {
            throw new IOException("invalid width: "+width);
        }

        int[] samples = new int[width];
        JSONArray sampleArray = obj.getJSONArray("samples");
        if (sampleArray == null || sampleArray.length() == 0) {
            throw new IOException("no samples provided");
        }
        if (sampleArray.length() != width) {
            throw new IOException("incomplete sample data");
        }
        for (int i=0; i<width; i++) {
            samples[i] = sampleArray.getInt(i);
        }
        return new WaveformData(height, samples);
    }
}
