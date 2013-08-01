package com.soundcloud.android.view.play;

import com.wehack.syncedQ.R;
import com.wehack.syncedQ.Track;
import org.jetbrains.annotations.Nullable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class ProgressBackgroundMask extends View {

    private @Nullable
    Track mTrack;

    private long mRefreshDelay;

    public double progress = 0;

    public ProgressBackgroundMask(Context context) {
        super(context);
    }

    public ProgressBackgroundMask(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProgressBackgroundMask(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setProgress(int _progress) {
        progress = _progress;
        invalidate();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint p = new Paint();
        p.setColor(0x99e36711);
        Rect r = new Rect();
        getDrawingRect(r);


        final int dimensionPixelOffset = getResources().getDimensionPixelOffset(R.dimen.left_margin);
        r.right = progress == 0 ? 0 :
                (int) (((r.right - 2*dimensionPixelOffset) / (double)1000) * progress) + dimensionPixelOffset;

        canvas.drawRect(r, p);
    }



}
