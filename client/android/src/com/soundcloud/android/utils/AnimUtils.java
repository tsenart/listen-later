
package com.soundcloud.android.utils;

import com.wehack.syncedQ.R;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

public final class AnimUtils {

    private AnimUtils() {}

    public static class SimpleAnimationListener implements Animation.AnimationListener {

        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    }

    public static void attachVisibilityListener(final View target, final int visibility) {
        target.getAnimation().setAnimationListener(new SimpleAnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                if (target.getAnimation().equals(animation)) {
                    target.setVisibility(visibility);
                    target.setEnabled(true);
                }
            }
        });
    }


    public static Animation runFadeInAnimationOn(Context ctx, View target) {
        Animation animation = AnimationUtils.loadAnimation(ctx, android.R.anim.fade_in);
        target.startAnimation(animation);
        return animation;
    }

    public static Animation runFadeOutAnimationOn(Context ctx, View target) {
        Animation animation = AnimationUtils.loadAnimation(ctx, android.R.anim.fade_out);
        target.startAnimation(animation);
        return animation;
    }

    public static void hideView(Context context, final View view, boolean animated) {
        view.clearAnimation();

        if (view.getVisibility() == View.GONE) return;

        if (!animated) {
            view.setVisibility(View.GONE);
        } else {
            hideView(context, view,new SimpleAnimationListener() {
               @Override
                public void onAnimationEnd(Animation animation) {
                    if (animation == view.getAnimation()) {
                        view.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    public static void hideView(Context context, final View view, Animation.AnimationListener listener) {
        view.clearAnimation();
        if (view.getVisibility() == View.GONE) return;
        Animation animation = AnimationUtils.loadAnimation(context, R.anim.fade_out);
        animation.setAnimationListener(listener);
        view.startAnimation(animation);
    }

    public static void showView(Context context, final View view, boolean animated) {
        view.clearAnimation();
        if (view.getVisibility() != View.VISIBLE){
            view.setVisibility(View.VISIBLE);
            if (animated) {
                view.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_in));
            }
        }
    }
}
