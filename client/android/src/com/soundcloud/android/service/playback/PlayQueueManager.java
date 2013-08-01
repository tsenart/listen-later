package com.soundcloud.android.service.playback;

import com.wehack.syncedQ.Track;

public interface PlayQueueManager {
    boolean isEmpty();

    int getPosition();

    Track getCurrentTrack();

    Track getNext();

    boolean prev();

    boolean next();

    boolean setPosition(int pos);

    public Track getTrackAt(int position);
}
