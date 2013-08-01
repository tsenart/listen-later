package com.soundcloud.android.view.play;


import com.soundcloud.android.service.playback.CloudPlaybackService;
import com.wehack.syncedQ.LLQueue;
import com.wehack.syncedQ.R;
import com.wehack.syncedQ.Track;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class PlayerTrackView extends LinearLayout implements WaveformController.WaveformListener {


    private WaveformController mWaveformController;
    protected  @Nullable Track mTrack;
    private long mDuration;
    protected boolean mOnScreen;
    @NotNull
    protected WaveformController.WaveformListener mListener;
    public PlayerTrackView(LLQueue llQueue, Context context, AttributeSet attrs) {
        super(context, attrs);

        View.inflate(context, R.layout.player_track_haq, this);

        mListener = llQueue;

        ((ProgressBar) findViewById(R.id.progress_bar)).setMax(1000);
        mWaveformController = (WaveformController) findViewById(R.id.waveform_controller);
        mWaveformController.setListener(mListener);
        mWaveformController.setProgressBackgroundMask((ProgressBackgroundMask) findViewById(R.id.progress_overlay));
    }

    @Override
    public long sendSeek(float seekPosition) {
        return mListener.sendSeek(seekPosition);
    }

    @Override
    public long setSeekMarker(int queuePosition, float seekPosition) {
        return mListener.setSeekMarker(queuePosition, seekPosition);
    }

    @Deprecated
    public void setTrack(@NotNull Track track, int queuePosition, long progress) {
        mTrack = track;


        ((TextView) findViewById(R.id.user)).setText(mTrack.getUsername());
        ((TextView) findViewById(R.id.track)).setText(mTrack.getTitle());

        mWaveformController.updateTrack(mTrack, queuePosition, progress);
        if (mDuration != mTrack.getDuration()) {
            mDuration = mTrack.getDuration();
        }
    }


    public void onDestroy() {
        clear();
        mWaveformController.onDestroy();
    }

    public void handleStatusIntent(Intent intent) {
        if (mTrack == null) return;

        String action = intent.getAction();
        if (CloudPlaybackService.PLAYSTATE_CHANGED.equals(action)) {
            if (!intent.getBooleanExtra(CloudPlaybackService.BroadcastExtras.isSupposedToBePlaying, false)) {
                // TODO!!!
                mWaveformController.setPlaybackStatus(false, intent.getLongExtra(CloudPlaybackService.BroadcastExtras.position, 0));
            }

        } else if (CloudPlaybackService.BUFFERING.equals(action)) {
            setBufferingState(true);
        } else if (CloudPlaybackService.BUFFERING_COMPLETE.equals(action)) {
            setBufferingState(false);
            mWaveformController.setPlaybackStatus(intent.getBooleanExtra(CloudPlaybackService.BroadcastExtras.isPlaying, false),
                    intent.getLongExtra(CloudPlaybackService.BroadcastExtras.position, 0));
        } else if (CloudPlaybackService.SEEKING.equals(action)) {
            mWaveformController.onSeek(intent.getLongExtra(CloudPlaybackService.BroadcastExtras.position, -1));
        } else if (CloudPlaybackService.SEEK_COMPLETE.equals(action)) {
            mWaveformController.onSeekComplete();
        }else if (CloudPlaybackService.PROGRESS.equals(action)) {
            mWaveformController.setProgress(intent.getLongExtra(CloudPlaybackService.BroadcastExtras.position, 0));
        }
    }

    public void setProgress(long pos) {
        if (pos >= 0 && mDuration > 0) {
            mWaveformController.setProgress(pos);
        } else {
            mWaveformController.setProgress(0);
        }
    }

    public void setBufferingState(boolean isBuffering) {
        mWaveformController.setBufferingState(isBuffering);
    }

    public void clear() {
        mOnScreen = false;
        mWaveformController.reset(true);
        mWaveformController.setOnScreen(false);
    }
}
