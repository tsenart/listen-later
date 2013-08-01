package com.wehack.syncedQ;

import static com.soundcloud.android.service.playback.CloudPlaybackService.getPlaylistManager;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.soundcloud.android.service.playback.CloudPlaybackService;
import com.soundcloud.android.service.playback.PlayQueueManager;
import com.soundcloud.android.view.play.PlayerArtworkTrackView;
import com.soundcloud.android.view.play.PlayerTrackView;
import com.soundcloud.android.view.play.WaveformController;
import com.soundcloud.api.ApiWrapper;
import com.soundcloud.api.Request;
import org.apache.http.HttpResponse;
import org.jetbrains.annotations.Nullable;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LLQueue extends BaseAdapter implements PlayQueueManager, WaveformController.WaveformListener {

    private static LLQueue sInstance;
    protected final Handler mHandler = new Handler();
    private List<PlayQueueItem> mTracks = Collections.emptyList();
    private ListenLaterLoader mLoader;
    private int mCurrentPosition;

    private static final long MINIMUM_PROGRESS_PERIOD = 40;
    private boolean mShowingSmoothProgress;

    private long lastProgressTimestamp;
    private long lastTrackTime;
    private BiMap<Long, PlayerArtworkTrackView> mVisibleViews = HashBiMap.create();

    private @Nullable
    CloudPlaybackService mPlaybackService;
    private long mSeekPos;

    protected LLQueue() {
        loadListenLaterQueue();
    }

    public synchronized static LLQueue get() {
        if (sInstance == null) {
            sInstance = new LLQueue();
        }
        return sInstance;
    }

    public  final void onPlaybackServiceChanged(Intent intent){
        if (intent.getAction().equals(CloudPlaybackService.PLAYQUEUE_CHANGED)){
            notifyDataSetChanged();
            loadListenLaterQueue();
        } else if (intent.getAction().equals(CloudPlaybackService.META_CHANGED)){
            determineProgressInterval();

        } else if (intent.getAction().equals(CloudPlaybackService.PLAYSTATE_CHANGED)){
            if (!intent.getBooleanExtra(CloudPlaybackService.BroadcastExtras.isPlaying, true)){
                stopSmoothProgress();
            }
        } else {
            if (intent.getAction().equals(CloudPlaybackService.PROGRESS)){
                setProgressFromService();
                final PlayQueueItem itemByTrackId = getPlayQueueItemByTrackId(intent.getLongExtra(CloudPlaybackService.BroadcastExtras.id, 0));
                if (itemByTrackId != null){
                    itemByTrackId.progress = CloudPlaybackService.getCurrentProgress();
                }
            } else if (intent.getAction().equals(CloudPlaybackService.BUFFERING)){
                stopSmoothProgress();
            } else if (intent.getAction().equals(CloudPlaybackService.BUFFERING_COMPLETE)){
                setProgressFromService();
                startSmoothProgress();
            }else if (CloudPlaybackService.SEEKING.equals(intent.getAction())) {
                stopSmoothProgress();
            }

            final long longId = intent.getLongExtra(CloudPlaybackService.BroadcastExtras.id, -1L);
            PlayerArtworkTrackView interestedView = mVisibleViews.get(longId);
            if (interestedView != null){
                interestedView.handleStatusIntent(intent);
            }
        }
    }

    private void setProgressFromService() {
        lastProgressTimestamp = System.currentTimeMillis();
        lastTrackTime = CloudPlaybackService.getCurrentProgress();
    }

    private PlayQueueItem getPlayQueueItemByTrackId(long id) {
        for (PlayQueueItem playQueueItem : mTracks){
            if (playQueueItem.track.getId() == id){
                return playQueueItem;
            }
        }
        return null;
    }

    @Override
    public int getCount() {
        return mTracks.size();
    }

    @Override
    public Track getItem(int position) {
        return mTracks.get(position).track;
    }

    @Override
    public long getItemId(int position) {
        return mTracks.get(position).track.getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null){
            convertView = new PlayerArtworkTrackView(this, parent.getContext(), null);
        }
        ((PlayerTrackView) convertView).setTrack(mTracks.get(position).track, position, mTracks.get(position).progress);
        mVisibleViews.forcePut(mTracks.get(position).track.getId(), (PlayerArtworkTrackView) convertView);
        return convertView;
    }

    @Override
    public int getPosition() {
        return mCurrentPosition;
    }

    @Override
    public Track getCurrentTrack() {
        return getTrackAt(mCurrentPosition);
    }

    @Override
    public Track getTrackAt(int position) {
        return mTracks.size() > position ? mTracks.get(position).track : null;
    }

    public boolean prev() {
        if (mCurrentPosition > 0) {
            int newPos = mCurrentPosition - 1;
            Track newTrack = getTrackAt(newPos);
            while (newPos > 0 && (newTrack == null)) {
                newTrack = getTrackAt(--newPos);
            }
            if (newTrack != null) {
                mCurrentPosition = newPos;
                return true;
            }
        }
        return false;
    }

    public boolean next() {
        if (mCurrentPosition < mTracks.size() - 1) {
            int newPos = mCurrentPosition + 1;
            Track newTrack = getTrackAt(newPos);
            while (newPos < mTracks.size() - 1 && (newTrack == null)) {
                newTrack = getTrackAt(++newPos);
            }
            if (newTrack != null) {
                mCurrentPosition = newPos;
                return true;
            }
        }

        return false;
    }

    public Track getNext() {
        if (mCurrentPosition < length() - 1) {
            return getTrackAt(mCurrentPosition + 1);
        } else {
            return null;
        }
    }

    public void play(int position) {
        if (position == mCurrentPosition){
            mPlaybackService.togglePlayback();
        } else {
            mCurrentPosition = position;
            mPlaybackService.openCurrent();
        }
    }

    public void setPlaybackService(@Nullable CloudPlaybackService mPlaybackService) {
        this.mPlaybackService = mPlaybackService;
    }

    @Override
    public long sendSeek(float seekPercent) {
        if (mPlaybackService == null) {
            return -1;
        }
        mSeekPos = -1;
        return mPlaybackService.seek(seekPercent, true);
    }

    public long setSeekMarker(int queuePosition, float seekPercent) {
        final PlayQueueManager playlistManager = getPlaylistManager();
        if (mPlaybackService != null && playlistManager != null) {
            if (playlistManager.getPosition() != queuePosition) {
                mPlaybackService.setQueuePosition(queuePosition);
            } else {
                // returns where would we be if we had seeked
                mSeekPos = mPlaybackService.seek(seekPercent, false);
                return mSeekPos;
            }
        }
        return -1;
    }

    private int length() {
        return mTracks.size();
    }


    @Override
    public boolean setPosition(int pos) {
        return false;
    }
    private long mProgressPeriod = 500;
    private Runnable mSmoothProgress = new Runnable() {
        public void run() {
            PlayerArtworkTrackView interestedView = mVisibleViews.get(CloudPlaybackService.getCurrentTrackId());
            if (interestedView != null){
                interestedView.setProgress(lastTrackTime + System.currentTimeMillis() - lastProgressTimestamp);
            }

            mHandler.postDelayed(this, mProgressPeriod);
        }
    };

    private void determineProgressInterval(){
        mProgressPeriod = Math.max(MINIMUM_PROGRESS_PERIOD,CloudPlaybackService.getCurrentTrack().getDuration()/800);
    }

    private void startSmoothProgress(){
        if (!mShowingSmoothProgress){
            mShowingSmoothProgress = true;
            mHandler.postDelayed(mSmoothProgress, 0);
        }
    }

    private void stopSmoothProgress(){
        if (mShowingSmoothProgress){
            mShowingSmoothProgress = false;
            mHandler.removeCallbacks(mSmoothProgress);
        }
    }








    private void loadListenLaterQueue(){
        mLoader = new ListenLaterLoader(LLApplication.instance.mWrapper);
        mLoader.execute();
    }

    private class ListenLaterLoader extends AsyncTask<Void,Void,List<Track>>{
        private final ApiWrapper mWrapper;
        public ListenLaterLoader(ApiWrapper wrapper) {
            mWrapper = wrapper;
        }


        @Override
        protected List<Track> doInBackground(Void... params) {
            try {
                HttpResponse resp = mWrapper.get(Request.to("/me/favorites.json"));
                Type listType = new TypeToken<List<Track>>() {
                }.getType();
                return new Gson().fromJson(new InputStreamReader(resp.getEntity().getContent()), listType);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<Track> tracks) {
            if (tracks != null){
                mTracks = new ArrayList<PlayQueueItem>(tracks.size());
                for (Track track : tracks){
                    mTracks.add(new PlayQueueItem(track, 0));
                }
                notifyDataSetChanged();
            }

        }
    }

    public static class PlayQueueItem {
        public Track track;
        public long progress;

        public PlayQueueItem(Track _track, long _progress) {
            track = _track;
            progress = _progress;
        }
    }
}
