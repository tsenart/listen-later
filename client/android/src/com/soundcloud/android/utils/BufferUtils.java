package com.soundcloud.android.utils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class BufferUtils {
    private BufferUtils() {
    }

    public static ByteBuffer readToByteBuffer(File f) throws IOException {
        return readToByteBuffer(f, (int) f.length());
    }

    public static ByteBuffer readToByteBuffer(File f, int toRead) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(toRead);
        FileChannel fc = new FileInputStream(f).getChannel();
        fc.read(b);
        fc.close();
        b.flip();
        return b;
    }

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    public static boolean readBody(HttpResponse resp, ByteBuffer target) throws IOException {
        final HttpEntity entity = resp.getEntity();
        if (entity == null) {
            throw new IllegalArgumentException("HTTP entity may not be null");
        }
        final InputStream is = entity.getContent();
        if (is == null) {
            return false;
        }

        if (entity.getContentLength() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("HTTP entity too large to be buffered in memory");
        } else if (target.remaining() < entity.getContentLength()) {
            throw new IOException(String.format("allocated buffer is too small (%d < %d)",
                    target.remaining(), entity.getContentLength()));
        }

        try {
            final byte[] tmp = new byte[8192];
            int l;
            while ((l = is.read(tmp)) != -1) {
                target.put(tmp, 0, l);
            }
            return true;
        } finally {
            is.close();
        }
    }

    public static ByteBuffer allocateAudioBuffer(int size) {
        ByteBuffer bb = ByteBuffer.allocateDirect(size);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb;
    }
}
