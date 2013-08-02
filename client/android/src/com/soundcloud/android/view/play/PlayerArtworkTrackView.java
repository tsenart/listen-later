package com.soundcloud.android.view.play;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.LoadedFrom;
import com.nostra13.universalimageloader.core.display.BitmapDisplayer;
import com.soundcloud.android.utils.AnimUtils;
import com.soundcloud.android.utils.images.ImageOptionsFactory;
import com.soundcloud.android.utils.images.ImageSize;
import com.wehack.syncedQ.LLQueue;
import com.wehack.syncedQ.R;
import com.wehack.syncedQ.Track;
import org.jetbrains.annotations.NotNull;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.widget.ImageView;

public class PlayerArtworkTrackView extends PlayerTrackView {


    private ImageView mArtwork;
    public PlayerArtworkTrackView(LLQueue llQueue, Context context, AttributeSet attrs) {
        super(llQueue, context, attrs);
        mArtwork = (ImageView) findViewById(R.id.artwork);
        mArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    @Override
    public void setTrack(@NotNull Track track, int queuePosition, long progress) {
        super.setTrack(track, queuePosition, progress);
        updateArtwork(true);
    }

    public WaveformController getWaveformController() {
        return mWaveformController;
    }

    private void updateArtwork(boolean priority) {
        // this will cause OOMs
        if (mTrack == null || ActivityManager.isUserAMonkey()) return;
        ImageLoader.getInstance().cancelDisplayTask(mArtwork);

        ImageLoader.getInstance().displayImage(
                ImageSize.formatUriForPlayer(getContext(), mTrack.getArtwork()),
                mArtwork,
                createPlayerDisplayImageOptions(priority));
    }

    private DisplayImageOptions createPlayerDisplayImageOptions(boolean priority){
        return ImageOptionsFactory
                .fullCacheBuilder()
                .delayBeforeLoading(priority ? 0 : 200)
                .displayer(mArtworkDisplayer)
                .build();
    }

    private BitmapDisplayer mArtworkDisplayer = new BitmapDisplayer() {
        @Override
        public Bitmap display(Bitmap bitmap, ImageView imageView, LoadedFrom loadedFrom) {
            imageView.setImageBitmap(bitmap);
            if (loadedFrom != LoadedFrom.MEMORY_CACHE) {
                AnimUtils.runFadeInAnimationOn(getContext(), mArtwork);
            }
            return bitmap;
        }
    };
}
