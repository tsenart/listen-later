package com.soundcloud.android.view.play;

import com.wehack.syncedQ.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Transformation;
import android.widget.RelativeLayout;

public class WaveformHolder extends RelativeLayout {
    private RelativeLayout mConnectingBar;

    public WaveformHolder(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    public void showWaitingLayout(boolean setAlpha) {
        if (mConnectingBar == null) mConnectingBar = (RelativeLayout) findViewById(R.id.connecting_bar);
        if (setAlpha) setStaticTransformationsEnabled(true);
        mConnectingBar.setVisibility(View.VISIBLE);
    }

    public void hideWaitingLayout() {
        if (mConnectingBar == null) mConnectingBar = (RelativeLayout) findViewById(R.id.connecting_bar);
        setStaticTransformationsEnabled(false);
        mConnectingBar.setVisibility(View.GONE);
    }

    @Override
    protected boolean getChildStaticTransformation(View child, Transformation t) {
        boolean ret = super.getChildStaticTransformation(child, t);
        if (child == mConnectingBar){
            t.setAlpha(0.5f);
            return true;
        }
        return ret;
    }
}
