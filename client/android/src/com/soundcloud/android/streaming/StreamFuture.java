package com.soundcloud.android.streaming;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class StreamFuture implements Future<ByteBuffer> {
    final StreamItem item;
    final Range byteRange;
    private ByteBuffer byteBuffer;

    private volatile boolean ready;
    private volatile boolean canceled;

    public StreamFuture(StreamItem item, Range byteRange) {
        this.item = item;
        this.byteRange = byteRange;
    }

    public synchronized void setByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
        ready = true;
        notifyAll();
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (ready) {
            return false;
        } else {
            canceled = true;
            notifyAll();
            return true;
        }
    }

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public boolean isDone() {
        return ready;
    }

    @Override
    public ByteBuffer get() throws InterruptedException, ExecutionException {
        try {
            return get(-1);
        } catch (TimeoutException e) {
            throw new InterruptedException(e.getMessage());
        }
    }

    @Override
    public ByteBuffer get(long l, TimeUnit timeUnit) throws InterruptedException, TimeoutException, ExecutionException {
        return get(timeUnit.toMillis(l));
    }

    private ByteBuffer get(long millis) throws InterruptedException, TimeoutException, ExecutionException {
        synchronized (this) {
            if (!isCancelled() && !isDone()) {
                if (millis < 0) {
                    wait();
                } else {
                    wait(millis);
                }
            }
            if (canceled) throw new ExecutionException("canceled: "+item, null);
            else if (!ready) throw new TimeoutException();
            else return byteBuffer;
        }
    }
}
