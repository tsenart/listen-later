package com.soundcloud.android.view;

import com.soundcloud.android.utils.InputObject;
import org.jetbrains.annotations.Nullable;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;

public abstract class TouchLayout extends RelativeLayout implements View.OnTouchListener {
    private static final int INPUT_QUEUE_SIZE = 20;
    private ArrayBlockingQueue<InputObject> mInputObjectPool;
    @Nullable
    private TouchThread mTouchThread;


    public TouchLayout(Context context) {
        super(context);
        init();
    }

    public TouchLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TouchLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mInputObjectPool = new ArrayBlockingQueue<InputObject>(INPUT_QUEUE_SIZE);
        for (int i = 0; i < INPUT_QUEUE_SIZE; i++) {
            mInputObjectPool.add(new InputObject(mInputObjectPool));
        }

        mTouchThread = new TouchThread(this);
        mTouchThread.start();
        setOnTouchListener(this);

    }


    public boolean onTouch(View v, MotionEvent event) {
        if (mTouchThread == null) return false;
        try {
            // Fix scrolling inside workspace view
            if ((event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_DOWN) && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if ((event.getAction() == MotionEvent.ACTION_UP) && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            // history first
            int hist = event.getHistorySize();
            if (hist > 0) {
                // add from oldest to newest
                for (int i = 0; i < hist; i++) {
                    InputObject input = mInputObjectPool.take();
                    input.useEventHistory(v, event, i);
                    mTouchThread.feedInput(input);
                }
            }
            // current last
            InputObject input = mInputObjectPool.take();
            input.useEvent(v, event);
            mTouchThread.feedInput(input);
        } catch (InterruptedException ignored) {
        }
        return true; // indicate event was handled
    }

    private void processInputObject(InputObject input) {

        switch (input.action) {
            case InputObject.ACTION_TOUCH_DOWN:
                processDownInput(input);
                break;
            case InputObject.ACTION_TOUCH_MOVE:
                processMoveInput(input);
                break;
            case InputObject.ACTION_TOUCH_UP:
                processUpInput(input);
                break;
            case InputObject.ACTION_TOUCH_POINTER_DOWN:
                processPointer1DownInput(input);
                break;
            case InputObject.ACTION_TOUCH_POINTER_UP:
                processPointer1UpInput(input);
                break;
        }
    }

    protected abstract void processDownInput(InputObject input);

    protected abstract void processMoveInput(InputObject input);

    protected abstract void processUpInput(InputObject input);

    protected abstract void processPointer1DownInput(InputObject input);

    protected abstract void processPointer1UpInput(InputObject input);

    public void onDestroy() {
        if (mTouchThread != null) {
            mTouchThread.stopped = true;
            mTouchThread.interrupt();
            mTouchThread = null;
        }
    }

    private static class TouchThread extends Thread {
        private ArrayBlockingQueue<InputObject> inputQueue = new ArrayBlockingQueue<InputObject>(INPUT_QUEUE_SIZE);
        private WeakReference<TouchLayout> mLayoutRef;
        private boolean stopped = false;

        private TouchThread(TouchLayout touchLayout) {
            this.mLayoutRef = new WeakReference<TouchLayout>(touchLayout);
        }

        public synchronized void feedInput(InputObject input) {
            try {
                inputQueue.put(input);
            } catch (InterruptedException e) {
                Log.e(getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        @Override
        public void run() {
            while (!stopped) {
                InputObject input = null;
                try {
                    input = inputQueue.take();
                    if (input.eventType == InputObject.EVENT_TYPE_TOUCH) {
                        final TouchLayout touchLayout = mLayoutRef.get();
                        if (touchLayout != null) touchLayout.processInputObject(input);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    if (input != null) input.returnToPool();
                }
            }
        }
    }
}
