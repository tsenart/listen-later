package com.soundcloud.android.service;

import android.app.Service;
import android.os.Binder;

public abstract class LocalBinder<T extends Service> extends Binder {
    public abstract T getService();
}
