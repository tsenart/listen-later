package com.wehack.syncedQ;

import static com.soundcloud.android.service.playback.CloudPlaybackService.getPlaylistManager;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.soundcloud.android.model.ClientUri;
import com.soundcloud.android.service.playback.CloudPlaybackService;
import com.soundcloud.android.service.playback.PlayQueueManager;
import com.soundcloud.android.view.play.PlayerArtworkTrackView;
import com.soundcloud.android.view.play.PlayerTrackView;
import com.soundcloud.android.view.play.WaveformController;
import com.soundcloud.api.ApiWrapper;
import com.soundcloud.api.Endpoints;
import com.soundcloud.api.Request;
import de.timroes.swipetodismiss.SwipeDismissList;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jetbrains.annotations.Nullable;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    private static Map<Long, Track> mTrackCache = new HashMap<Long, Track>();

    private @Nullable
    CloudPlaybackService mPlaybackService;
    private long mSeekPos;

    protected LLQueue() {
        loadListenLaterQueue();
    }

    public synchronized static boolean hasInstance() {
       return sInstance != null;
    }

    public synchronized static LLQueue get() {
        if (sInstance == null) {
            sInstance = new LLQueue();
        }
        return sInstance;
    }

    public SwipeDismissList.OnDismissCallback mSwipeCallback = new SwipeDismissList.OnDismissCallback() {
        // Gets called whenever the user deletes an item.
        public SwipeDismissList.Undoable onDismiss(AbsListView listView, final int position) {
            // Get your item from the adapter (mAdapter being an adapter for MyItem objects)
            final PlayQueueItem playQueueItem = mTracks.remove(position);
            // Use this place to e.g. delete the item from database
            if (mCurrentPosition >= position) mCurrentPosition--;
            notifyDataSetChanged();


            if (playQueueItem != null) {
                Log.d("LLQueue", "dismissed "+playQueueItem);

                new AsyncTask<Void,Void,Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        HttpClient client = LLApplication.instance.getApiWrapper().getHttpClient();
                        try {
                            HttpResponse resp = client.execute(new HttpDelete("http://54.246.158.145/list/"+playQueueItem.urn));


                            if (resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                                Log.w("LLQueue", "error deleting item "+playQueueItem +","+resp.getStatusLine());
                            }

                        } catch (IOException e) {
                            Log.w("LLQueue", "error deleting item "+playQueueItem, e);
                        }
                        return null;
                    }
                }.execute();
            }

            return null;

            // Return an Undoable implementing every method
//            return new SwipeDismissList.Undoable() {
//
//                // Method is called when user undoes this deletion
//                public void undo() {
//                    mTracks.add(position, playQueueItem);
//                    if (mCurrentPosition >= position) mCurrentPosition++;
//                    notifyDataSetChanged();
//                }
//
//                // Return an undo message for that item
//                public String getTitle() {
//                    return playQueueItem.track.getTitle() + " deleted";
//                }
//
//                // Called when user cannot undo the action anymore
//                public void discard() {
//
//
//                }
//            };
        }
    };

    public void handlePush(Intent intent) {
        Log.i("asdf", "HANDLE PUSH " + intent);
    }

    private void remoteDeletePlayqueueItem(PlayQueueItem item, int position){

    }

    public  final void onPlaybackServiceChanged(Intent intent){
        if (intent.getAction().equals(CloudPlaybackService.PLAYQUEUE_CHANGED)){
            notifyDataSetChanged();
            loadListenLaterQueue();
        } else if (intent.getAction().equals(CloudPlaybackService.META_CHANGED)){
            determineProgressInterval();

            final long longId = intent.getLongExtra(CloudPlaybackService.BroadcastExtras.id, -1L);
            PlayerArtworkTrackView interestedView = mVisibleViews.get(longId);
            if (interestedView != null){
                final View viewById = interestedView.findViewById(R.id.waveform_controller);
            }

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
            if (playQueueItem.getId() == id){
                return playQueueItem;
            }
        }
        return null;
    }

    @Override
    public int getCount() {
        return mTracks == null ? 0 : mTracks.size();
    }

    @Override
    public Track getItem(int position) {
        return mTrackCache.get(mTracks.get(position).getId());
    }

    @Override
    public long getItemId(int position) {
        return mTracks.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null){
            convertView = new PlayerArtworkTrackView(this, parent.getContext(), null);
        }
        Track track = getItem(position);

        if (track != null) {
            ((PlayerTrackView) convertView).setTrack(track, position, mTracks.get(position).progress);
            mVisibleViews.forcePut(mTracks.get(position).getId(), (PlayerArtworkTrackView) convertView);
        }
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
        return mTracks.size() > position ? getItem(position) : null;
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

    public void play(final int position) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpPut httppost = new HttpPut("http://54.246.158.145/list/soundcloud:tracks:" + getTrackAt(position).getId());

                try {
                    // Execute HTTP Post Request
                    HttpResponse response = new DefaultHttpClient().execute(httppost);
                    Log.i("asdf","Response was " + response.getStatusLine());

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

//        if (position == mCurrentPosition){
//            mPlaybackService.togglePlayback();
//        } else {
//            mCurrentPosition = position;
//            final PlayQueueItem playQueueItem = mTracks.get(position);
//            if (playQueueItem.progress > 0){
//                mPlaybackService.setResumeInfo(playQueueItem.track.getId(), playQueueItem.progress);
//            }
//            mPlaybackService.openCurrent();
//        }
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



    public void loadListenLaterQueue(){
        mLoader = new ListenLaterLoader(LLApplication.instance.mWrapper);
        mLoader.execute();
    }

    public void removeUrn(String urn) {
        if (mTracks == null) return;

        for (Iterator<PlayQueueItem> iterator = mTracks.iterator(); iterator.hasNext(); ) {
            PlayQueueItem item = iterator.next();
            if (item.urn.equalsIgnoreCase(urn)) {
                iterator.remove();
                notifyDataSetChanged();
            }
        }
    }

    public void addUrn(final String urn) {

        ClientUri uri = ClientUri.fromUri(urn);
        if (uri == null) return;
        long id = uri.numericId;
        new LookupTracks(LLApplication.instance.mWrapper) {
            @Override
            protected void onPostExecute(List<Track> tracks) {
                if (tracks != null) {
                    for (Track t : tracks) {
                        mTrackCache.put(t.getId(), t);
                    }

                    PlayQueueItem item = new PlayQueueItem();
                    item.urn = urn;

                    mTracks.add(item);
                    notifyDataSetChanged();
                }
            }
        }.execute(Arrays.asList(id));


    }

    private class ListenLaterLoader extends AsyncTask<Void,Void,List<PlayQueueItem>>{
        private final ApiWrapper mWrapper;
        public ListenLaterLoader(ApiWrapper wrapper) {
            mWrapper = wrapper;
        }


        @Override
        protected List<PlayQueueItem> doInBackground(Void... params) {

            Log.d("asdf", "loading queue");

            try {

                HttpGet httpGet = new HttpGet("http://54.246.158.145/list");
                // Execute HTTP Post Request
                HttpResponse resp = new DefaultHttpClient().execute(httpGet);

                Log.i("asdf","Status 1 " + resp.getStatusLine().getStatusCode());

                Type listType = new TypeToken<List<PlayQueueItem>>() {
                }.getType();
                List<PlayQueueItem> playQueueItems =  new Gson().fromJson(new InputStreamReader(resp.getEntity().getContent()), listType);

                List<Long> lookups = new ArrayList<Long>(playQueueItems.size());
                for (PlayQueueItem playQueueItem : playQueueItems){
                    if (!mTrackCache.containsKey(playQueueItem.getId())){
                        lookups.add(playQueueItem.getId());
                    }
                }

                if (!lookups.isEmpty()){
                    for (Track t : lookupTracks(mWrapper, lookups)){
                        mTrackCache.put(t.getId(), t);
                    }
                }

                Log.i("asdf","QUeue 1 " + playQueueItems);

                for (Iterator<PlayQueueItem> iterator = playQueueItems.iterator(); iterator.hasNext(); ) {
                    PlayQueueItem next = iterator.next();
                    if (!mTrackCache.containsKey(next.getId())){
                        iterator.remove();
                    }
                }

                Log.i("asdf","QUeue 2 " + playQueueItems);

                return playQueueItems;

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<PlayQueueItem> tracks) {
            mTracks = tracks;
            notifyDataSetChanged();
        }
    }

    private class LookupTracks extends AsyncTask<List<Long>, Void, List<Track>> {

        private final ApiWrapper mWrapper;
        public LookupTracks(ApiWrapper wrapper) {
            mWrapper = wrapper;
        }

        @Override
        protected List<Track> doInBackground(List<Long>... lists) {
            try {
                return lookupTracks(mWrapper, lists[0]);
            } catch (IOException e) {
                Log.w("", e);
                return null;
            }
        }
    }


    private List<Track> lookupTracks(ApiWrapper wrapper, List<Long> ids) throws IOException {
        Type listType = new TypeToken<List<Track>>() {
        }.getType();
        Request request = Request.to(Endpoints.TRACKS).add("ids", TextUtils.join(",", ids));
        //.add(Wrapper.LINKED_PARTITIONING, "1")

        HttpResponse lookupResponse = wrapper.get(request);
        Log.i("asdf","Status 2 " + lookupResponse.getStatusLine().getStatusCode());
        return new Gson().fromJson(new InputStreamReader(lookupResponse.getEntity().getContent()), listType);
    }


    public static class PlayQueueItem {
        public long progress;
        public String urn;
        private long mId;

        public long getId(){
            if (mId == 0){
                mId = ClientUri.fromUri(urn).numericId;
            }
            return mId;
        }


        @Override
        public String toString() {
            return "PlayQueueItem{" +
                    "progress=" + progress +
                    ", urn='" + urn + '\'' +
                    ", mId=" + mId +
                    '}';
        }
    }
}
