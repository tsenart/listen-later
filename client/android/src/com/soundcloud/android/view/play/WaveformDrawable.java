package com.soundcloud.android.view.play;

import com.soundcloud.android.model.WaveformData;
import org.jetbrains.annotations.NotNull;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

/**
 * Takes {@link WaveformData} and renders it.
 */
public class WaveformDrawable extends Drawable {
    private @NotNull final WaveformData mData;
    private final Paint mDrawPaint;
    private final boolean mDrawHalf;

    public WaveformDrawable(@NotNull WaveformData data, int drawColor, boolean drawHalf) {
        if (data == null) throw new IllegalArgumentException("Need waveform data");

        mData = data;
        mDrawPaint = new Paint();
        mDrawPaint.setColor(drawColor);
        mDrawHalf = drawHalf;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);
        final int width = getBounds().width();
        final int height = getBounds().height();

        final WaveformData scaled = mData.scale(width);

        if (mDrawHalf){
            for (int i = 0; i < scaled.samples.length; i++) {
                final float scaledHeight = scaled.samples[i] * (float) height / scaled.maxAmplitude;
                canvas.drawLine(
                        i, 0,
                        i, height - scaledHeight
                        , mDrawPaint);
            }
        } else {
            for (int i = 0; i < scaled.samples.length; i++) {
                final float scaledHeight = (scaled.samples[i] * (float) height / scaled.maxAmplitude)/2;
                canvas.drawLine(
                        i, 0,
                        i, height/2 - scaledHeight
                        , mDrawPaint);
                canvas.drawLine(
                        i, height / 2 + scaledHeight,
                        i, height
                        , mDrawPaint);
            }
        }

    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return 95;
    }
}
