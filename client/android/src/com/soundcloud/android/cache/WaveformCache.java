package com.soundcloud.android.cache;

import com.soundcloud.android.model.WaveformData;
import com.soundcloud.android.task.fetch.WaveformFetcher;
import com.wehack.syncedQ.Track;

import android.support.v4.util.LruCache;

public final class WaveformCache {

    private static WaveformCache sInstance;

    private android.support.v4.util.LruCache<Track, WaveformData> mCache
            = new LruCache<Track, WaveformData>(128);

    private WaveformCache() {
    }

    public static synchronized WaveformCache get() {
        if (sInstance == null) {
            sInstance = new WaveformCache();
        }
        return sInstance;
    }

    public WaveformData getData(final Track track, final WaveformCallback callback) {
        WaveformData data = mCache.get(track);
        if (data != null) {
            callback.onWaveformDataLoaded(track, data, true);
            return data;
        } else {
            new WaveformFetcher() {
                @Override
                protected void onPostExecute(WaveformData waveformData) {
                    if (waveformData != null) {
                        mCache.put(track, waveformData);
                        callback.onWaveformDataLoaded(track, waveformData, false);
                    } else {
                        callback.onWaveformError(track);
                    }
                }
            }.executeOnThreadPool(track.getWaveformDataURL());
        }
        return null;
    }

    public interface WaveformCallback {
        void onWaveformDataLoaded(Track track, WaveformData data, boolean fromCache);
        void onWaveformError(Track track);
    }
}
