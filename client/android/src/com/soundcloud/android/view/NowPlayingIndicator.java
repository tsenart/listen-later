package com.soundcloud.android.view;

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.soundcloud.android.cache.WaveformCache;
import com.soundcloud.android.model.WaveformData;
import com.soundcloud.android.view.play.WaveformController;
import com.wehack.syncedQ.Track;
import org.jetbrains.annotations.Nullable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ProgressBar;

public class NowPlayingIndicator extends ProgressBar {
    private static final int REFRESH = 1;

    private static final int TOP_ORANGE = 0xFFFF4400;
    private static final int SEPARATOR_ORANGE = 0xFF661400;
    private static final int BOTTOM_ORANGE = 0xFFAA2200;

    private static final int TOP_GREY = 0xFFFFFFFF;
//    private static final int TOP_GREY       = 0xFF666666;

    private static final int SEPARATOR_GREY = 0xFF2D2D2D;

    private static final int BOTTOM_GREY = 0xFFFFFFFF;


    private static final float TOP_WAVEFORM_FRACTION = 0.75f;

    private
    @Nullable
    Bitmap mWaveformMask;

    private long mRefreshDelay;

    private Paint mTopOrange;
    private Paint mSeparatorOrange;
    private Paint mBottomOrange;

    private Paint mTopGrey;
    private Paint mSeparatorGrey;
    private Paint mBottomGrey;

    private Rect mCanvasRect;

    private int mAdjustedWidth;
    private WaveformController.WaveformState mWaveformState;
    private int mWaveformErrorCount;
    private WaveformData mWaveformData;
    private boolean mDrawSeparator;

    @SuppressWarnings("UnusedDeclaration")
    public NowPlayingIndicator(Context context) {
        super(context);
        init(context);
    }

    @SuppressWarnings("UnusedDeclaration")
    public NowPlayingIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    @SuppressWarnings("UnusedDeclaration")
    public NowPlayingIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void setDrawSeparator(boolean drawSeparator){
        mDrawSeparator = drawSeparator;
        invalidate();
    }

    private void init(final Context context) {
        PorterDuffXfermode sourceIn = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);

        setMax(1000);

        mTopOrange = new Paint();
        mTopOrange.setColor(TOP_ORANGE);
        mTopOrange.setXfermode(sourceIn);

        mSeparatorOrange = new Paint();
        mSeparatorOrange.setColor(SEPARATOR_ORANGE);
        mSeparatorOrange.setXfermode(sourceIn);

        mBottomOrange = new Paint();
        mBottomOrange.setColor(BOTTOM_ORANGE);
        mBottomOrange.setXfermode(sourceIn);

        mTopGrey = new Paint();
        mTopGrey.setColor(TOP_GREY);
        mTopGrey.setXfermode(sourceIn);

        mSeparatorGrey = new Paint();
        mSeparatorGrey.setColor(SEPARATOR_GREY);
        mSeparatorGrey.setXfermode(sourceIn);

        mBottomGrey = new Paint();
        mBottomGrey.setColor(BOTTOM_GREY);
        mBottomGrey.setXfermode(sourceIn);

        setIndeterminate(false);
    }

    public void setTrack(final Track _track) {
        if (WaveformCache.get().getData(_track, new WaveformCache.WaveformCallback() {
            @Override
            public void onWaveformDataLoaded(Track track, WaveformData data, boolean fromCache) {
                if (track.equals(_track)) {
                    mWaveformErrorCount = 0;
                    mWaveformState = WaveformController.WaveformState.OK;
                    setWaveform(data);
                }
            }

            @Override
            public void onWaveformError(Track track) {
                if (track.equals(_track)) {
                    mWaveformState = WaveformController.WaveformState.ERROR;
                    mWaveformErrorCount++;
                }
            }

        }) == null) {
            // loading
            // TODO, loading indicator?
        }
    }


    private void setDefaultWaveform() {
        // TODO, set default bitmap
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mAdjustedWidth = getWidth() - 0 * 2;
        mCanvasRect = new Rect(0, 0, mAdjustedWidth, getHeight());
        setWaveformMask();
    }


    public void destroy() {
        mWaveformMask = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mWaveformMask == null) return;

        Canvas tmp = new Canvas(mWaveformMask);

        float density = getResources().getDisplayMetrics().density;

        int topPartHeight = (int) (getHeight() * TOP_WAVEFORM_FRACTION);
        int separatorTop = (int) (topPartHeight - density);
        int separatorBottom = topPartHeight;

        float playedFraction = (float) getProgress() / (float) getMax();
        playedFraction = min(max(playedFraction, 0), getMax());

        // Make sure to at least draw an 1dp line of progress
        int progressWidth = (int) max(mAdjustedWidth * playedFraction, density);

        // Orange
        tmp.drawRect(0, 0, progressWidth, getHeight(), mTopOrange);
        tmp.drawRect(0, topPartHeight, progressWidth, getHeight(), mBottomOrange);
        if (mDrawSeparator){
            tmp.drawRect(0, separatorTop, progressWidth, separatorBottom, mSeparatorOrange);
        }

        // Grey
        tmp.drawRect(progressWidth, 0, mAdjustedWidth, topPartHeight, mTopGrey);
        tmp.drawRect(progressWidth, topPartHeight, mAdjustedWidth, getHeight(), mBottomGrey);
        if (mDrawSeparator){
            tmp.drawRect(progressWidth, separatorTop, mAdjustedWidth, separatorBottom, mSeparatorGrey);
        }

        canvas.drawBitmap(
                mWaveformMask,
                mCanvasRect,
                mCanvasRect,
                null
        );
    }

    private void setWaveformMask() {
        this.mWaveformMask = createWaveformMask(mWaveformData, mAdjustedWidth, getHeight());
    }

    public void setWaveform(WaveformData waveformData) {
        mWaveformData = waveformData;
        setWaveformMask();
        invalidate();
    }

    private static Bitmap createWaveformMask(WaveformData waveformData, int width, int height) {
        if (waveformData == null || width == 0 || height == 0) return null;

        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(mask);

        Paint black = new Paint();
        black.setColor(Color.BLACK);

        canvas.drawRect(0, 0, width, height, black);

        Paint xor = new Paint();
        xor.setColor(Color.BLACK);
        xor.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR));

        // Top half waveform
        int dstHeight = (int) (height * TOP_WAVEFORM_FRACTION);

        WaveformData scaled = waveformData.scale(width);
        for (int i = 0; i < scaled.samples.length; i++) {
            final float scaledHeight1 = (scaled.samples[i] * (float) dstHeight / waveformData.maxAmplitude);
            canvas.drawLine(
                    i, 0,
                    i, dstHeight - scaledHeight1,
                    xor
            );

            final float scaledHeight2 = (scaled.samples[i] * (float) (height - dstHeight) / waveformData.maxAmplitude);
            canvas.drawLine(
                    i, dstHeight + scaledHeight2,
                    i, height,
                    xor
            );
        }

        return mask;
    }


}
