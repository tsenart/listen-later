package com.soundcloud.android.service.playback;

import java.util.EnumSet;
/**
 * States the mediaplayer can be in - we need to track these manually.
 */
    public enum State {
    STOPPED,            // initial state, or stopped
    ERROR,              // onError() was called
    ERROR_RETRYING,     // onError() + retry
    PREPARING,          // initial buffering
    PREPARED,           // initial buffering finished
    PLAYING,            // currently playing
    PAUSED,             // paused by user
    PAUSED_FOR_BUFFERING, // paused by framework
    PAUSED_FOCUS_LOST,    // paused because the focus got lost
    COMPLETED,            // onComplete() was called
    EMPTY_PLAYLIST;       // got told to play but playlist was empty

    // see Valid and invalid states on http://developer.android.com/reference/android/media/MediaPlayer.html
    public static final EnumSet<State> SEEKABLE =
            EnumSet.of(PREPARED, PLAYING, PAUSED, PAUSED_FOR_BUFFERING, PAUSED_FOCUS_LOST, COMPLETED);

    public static final EnumSet<State> STARTABLE =
            EnumSet.of(PREPARED, PLAYING, PAUSED, PAUSED_FOR_BUFFERING, PAUSED_FOCUS_LOST, COMPLETED);

    public static final EnumSet<State> STOPPABLE =
            EnumSet.of(PREPARED, PLAYING, STOPPED, PAUSED, PAUSED_FOR_BUFFERING, PAUSED_FOCUS_LOST, COMPLETED);

    public static final EnumSet<State> PAUSEABLE =
            EnumSet.of(PLAYING, PAUSED_FOR_BUFFERING, PAUSED_FOCUS_LOST, PAUSED);

    public boolean isPausable() {
        return PAUSEABLE.contains(this);
    }

    public boolean isStartable() {
        return STARTABLE.contains(this);
    }

    public boolean isSeekable() {
        return SEEKABLE.contains(this);
    }

    public boolean isStoppable() {
        return STOPPABLE.contains(this);
    }

    public boolean isError() {
        return this == ERROR || this == ERROR_RETRYING;
    }

    // is the service currently playing, or about to play soon?
    public boolean isSupposedToBePlaying() {
        return this == PREPARING || this == PLAYING || this == PAUSED_FOR_BUFFERING || this == EMPTY_PLAYLIST;
    }

    public boolean isInIdleState() {
        return this == PAUSED || this == STOPPED || this == COMPLETED || this == ERROR;
    }
}
