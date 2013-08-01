package com.soundcloud.android.view.play;


import com.soundcloud.android.service.playback.CloudPlaybackService;
import com.soundcloud.android.utils.InputObject;
import com.soundcloud.android.view.NowPlayingIndicator;
import com.soundcloud.android.view.TouchLayout;
import com.wehack.syncedQ.R;
import com.wehack.syncedQ.Track;
import org.jetbrains.annotations.Nullable;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewConfiguration;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;

public class WaveformController extends TouchLayout {
    private static final String TAG = WaveformController.class.getSimpleName();

    private final NowPlayingIndicator mNowPlaying;

    protected ProgressBar mProgressBar;
    protected WaveformHolder mWaveformHolder;
    protected RelativeLayout mWaveformFrame;
    private PlayerTouchBar mPlayerTouchBar;
    protected @Nullable Track mTrack;
    protected int mQueuePosition;

    private ProgressBackgroundMask mBackgroundMask;

    protected boolean mOnScreen;
    private int mDuration;
    private float mSeekPercent;

    protected final Handler mHandler = new Handler();
    private Handler mTouchHandler = new TouchHandler(this);

    private static final int UI_UPDATE_SEEK = 1;
    private static final int UI_SEND_SEEK   = 2;
    private static final int UI_UPDATE_COMMENT_POSITION = 3;
    protected static final int UI_CLEAR_SEEK = 6;

    static final int TOUCH_MODE_NONE = 0;
    static final int TOUCH_MODE_SEEK_DRAG = 1;
    static final int TOUCH_MODE_COMMENT_DRAG = 2;
    static final int TOUCH_MODE_SEEK_CLEAR_DRAG = 4;

    protected int mode = TOUCH_MODE_NONE;

    protected boolean mShowComment;
    private static final long MIN_COMMENT_DISPLAY_TIME = 2000;

    // only allow smooth progress updates on 9 or greater because they have buffering events for proper displaying
    private boolean mIsBuffering, mWaitingForSeekComplete;
    private int mTouchSlop;
    private int mWaveformColor;

    private WaveformListener mListener;



    public interface WaveformListener {
        long sendSeek(float seekPosition);
        long setSeekMarker(int queuePosition, float seekPosition);
    }


    public WaveformController(Context context, AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(false);

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.wave_form_controller, this);

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();

        mWaveformFrame = (RelativeLayout) findViewById(R.id.waveform_frame);
        mWaveformHolder = (WaveformHolder) findViewById(R.id.waveform_holder);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);

        mWaveformColor = context.getResources().getColor(android.R.color.holo_red_dark);

        mNowPlaying = (NowPlayingIndicator) findViewById(R.id.waveform_now_playing);
        mPlayerTouchBar = (PlayerTouchBar) findViewById(R.id.track_touch_bar);

        mPlayerTouchBar.setLandscape(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        // override default touch layout functionality, these views are all we want to respond
        mPlayerTouchBar.setOnTouchListener(this);
        setOnTouchListener(null);
    }

    @Override
    protected boolean ignoreTouchEvents() {
        return mTrack.getId() != CloudPlaybackService.getCurrentTrackId();
    }

    public void setProgressBackgroundMask(ProgressBackgroundMask progressBackgroundMask){
        mBackgroundMask = progressBackgroundMask;
    }

    public void setOnScreen(boolean onScreen){
        mOnScreen = onScreen;
    }

    public void setListener(WaveformListener listener){
        mListener = listener;
    }

    protected boolean isLandscape(){
        return false;
    }

    public void setBufferingState(boolean isBuffering) {
        mIsBuffering = isBuffering;
        if (mIsBuffering){
            showWaiting();
        } else if ( !mWaitingForSeekComplete){
            hideWaiting();
        }
    }



    public void setPlaybackStatus(boolean isPlaying, long pos){
        setProgress(pos);
        if (!isPlaying) {
            mWaitingForSeekComplete = false;
        }
    }

    public void reset(boolean hide){
        mWaitingForSeekComplete = mIsBuffering = false;
        setProgressInternal(0);
        setSecondaryProgress(0);

        if (hide){
            showWaiting();
        }
    }

    public void onSeek(long seekTime){
        setProgressInternal(seekTime);
        setProgress(seekTime);
        //stopSmoothProgress();
        mWaitingForSeekComplete = true;
        mHandler.postDelayed(mShowWaiting,500);
    }

    public void onSeekComplete(){
        //stopSmoothProgress();
        mWaitingForSeekComplete = false;
        hideWaiting();
    }

    private void showWaiting() {
        mWaveformHolder.showWaitingLayout(true);
        mHandler.removeCallbacks(mShowWaiting);
        invalidate();
    }

    private void hideWaiting() {
        mHandler.removeCallbacks(mShowWaiting);
        mWaveformHolder.hideWaitingLayout();
        invalidate();

    }

    public void setProgress(long pos) {
        if (pos < 0) return;
        if (mode != TOUCH_MODE_SEEK_DRAG){
            setProgressInternal(pos);
        }
    }

    protected void setProgressInternal(long pos) {
        Log.i("asdf", "Set Progress Internal " + pos + " " + mDuration);
        if (mDuration <= 0)
            return;

        final int progress = (int) (pos * 1000 / mDuration);
        mProgressBar.setProgress(progress);
        mNowPlaying.setProgress(progress);
        mNowPlaying.invalidate();

        if (mBackgroundMask != null) mBackgroundMask.setProgress(progress);
    }


    public void setSecondaryProgress(int percent) {
        mProgressBar.setSecondaryProgress(percent);
        mNowPlaying.setSecondaryProgress(percent);
    }

    public void updateTrack(@Nullable Track track, int queuePosition, long progress) {
        mQueuePosition = queuePosition;
        if (track == null || (mTrack != null
                && mTrack.getId() == track.getId()
                && mDuration == mTrack.getDuration())) {
            return;
        }

        final boolean changed = mTrack != track;
        mTrack = track;
        mDuration = mTrack.getDuration();

        if (changed) {
            mNowPlaying.setTrack(track);
            setPlaybackStatus(CloudPlaybackService.isTrackPlaying(track.getId()), progress);
        }
    }


   final Runnable mShowWaiting = new Runnable() {
        public void run() {
            showWaiting();
        }
    };

    @Override
    protected void processDownInput(InputObject input) {
        if (mode == TOUCH_MODE_COMMENT_DRAG) {
            mSeekPercent = ((float) input.x) / mWaveformHolder.getWidth();
            queueUnique(UI_UPDATE_COMMENT_POSITION);

        } else if (input.view == mPlayerTouchBar) {
            mode = TOUCH_MODE_SEEK_DRAG;
            mSeekPercent = ((float) input.x) / mWaveformHolder.getWidth();
            queueUnique(UI_UPDATE_SEEK);
        }
    }

    @Override
    protected void processMoveInput(InputObject input) {
        switch (mode) {
            case TOUCH_MODE_COMMENT_DRAG:
                if (isOnTouchBar(input.y)) {
                    mSeekPercent = ((float) input.x) / mWaveformHolder.getWidth();
                    queueUnique(UI_UPDATE_COMMENT_POSITION);
                }
                break;
            case TOUCH_MODE_SEEK_DRAG:
                if (isOnTouchBar(input.y)) {
                    mSeekPercent = ((float) input.x) / mWaveformHolder.getWidth();
                    queueUnique(UI_UPDATE_SEEK);
                } else {
                    queueUnique(UI_CLEAR_SEEK);
                    mode = TOUCH_MODE_SEEK_CLEAR_DRAG;
                }
                break;

            case TOUCH_MODE_SEEK_CLEAR_DRAG:
                if (isOnTouchBar(input.y)) {
                    mSeekPercent = ((float) input.x) / mWaveformHolder.getWidth();
                    queueUnique(UI_UPDATE_SEEK);
                    mode = TOUCH_MODE_SEEK_DRAG;
                }
                break;
        }
    }

    @Override
    protected void processUpInput(InputObject input) {
        switch (mode) {
            case TOUCH_MODE_SEEK_DRAG:
            case TOUCH_MODE_SEEK_CLEAR_DRAG:
                if (isOnTouchBar(input.y)) {
                    queueUnique(UI_SEND_SEEK);
                } else {
                    queueUnique(UI_CLEAR_SEEK);
                }
                break;
        }
        mode = TOUCH_MODE_NONE;
    }

    @Override
    protected void processPointer1DownInput(InputObject input) {
    }

    @Override
    protected void processPointer1UpInput(InputObject input) {
    }

    private boolean isOnTouchBar(int y){
        return (y > mPlayerTouchBar.getTop() - mTouchSlop && y < mPlayerTouchBar.getBottom() + mTouchSlop);
    }

    protected void queueUnique(int what) {
        if (!mTouchHandler.hasMessages(what)) mTouchHandler.sendEmptyMessage(what);
    }

    protected long stampFromPosition(int x) {
        return (long) (Math.min(Math.max(.001, (((float) x) / getWidth())), 1) * mTrack.getDuration());
    }

    public enum WaveformState {
        OK, LOADING, ERROR
    }


    private void processTouchMessage(int what){
        final float seekPercent = mSeekPercent;
        final PlayerTouchBar touchBar = mPlayerTouchBar;

        switch (what) {
            case UI_UPDATE_SEEK:
                if (mListener != null) {
                    long seekTime = mListener.setSeekMarker(mQueuePosition, seekPercent);
                    if (seekTime == -1) {
                        // the seek did not work, abort
                        mode = TOUCH_MODE_NONE;
                    } else {
                        mPlayerTouchBar.setSeekPosition((int) (seekPercent * getWidth()), mPlayerTouchBar.getHeight(), false);
                        mBackgroundMask.setProgress((int) (1000 * seekPercent));
                    }
                    mWaveformHolder.invalidate();
                }
                break;

            case UI_SEND_SEEK:
                if (mListener != null) {
                    mListener.sendSeek(seekPercent);
                }
                mPlayerTouchBar.clearSeek();
                break;

            case UI_CLEAR_SEEK:
                touchBar.clearSeek();
                break;
        }
    }


    private static final class TouchHandler extends Handler {
        private WeakReference<WaveformController> mRef;

        private TouchHandler(WaveformController controller) {
            this.mRef = new WeakReference<WaveformController>(controller);
        }

        @Override
        public void handleMessage(Message msg) {
            final WaveformController controller = mRef.get();
            if (controller != null) {
                controller.processTouchMessage(msg.what);
            }
        }
    }
}
