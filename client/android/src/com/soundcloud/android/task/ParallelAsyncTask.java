package com.soundcloud.android.task;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;

public abstract class ParallelAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {


    @TargetApi(11)
    public final AsyncTask<Params, Progress, Result> executeOnThreadPool(Params... params) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return execute(params);
        } else {
            return executeOnExecutor(THREAD_POOL_EXECUTOR, params);
        }
    }
}
