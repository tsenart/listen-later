package com.soundcloud.android.streaming;

import static com.soundcloud.android.streaming.StreamStorage.LOG_TAG;

import com.soundcloud.android.utils.IOUtils;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

class CompleteFileTask extends AsyncTask<File, Integer, Boolean> {
    static final long MAX_MD5_CHECK_SIZE = 5 * 1024*1024; // don't md5 check files over 5MB

    private long mContentLength;
    private String mEtag;
    private List<Integer> mIndexes;

    private int mChunkSize;

    public CompleteFileTask(long length, String etag, int chunkSize, List<Integer> indexes) {
        mIndexes = indexes;
        mChunkSize = chunkSize;
        mContentLength = length;
        mEtag = etag;
    }

    @Override
    protected Boolean doInBackground(File... params) {
        File chunkFile = params[0];
        File completeFile = params[1];

        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            Log.d(LOG_TAG, "About to write complete file to " + completeFile);

        if (completeFile.exists()) {
            Log.e(LOG_TAG, "Complete file already exists at path " + completeFile.getAbsolutePath());
            return false;
        }
        // make sure complete dir exists
        else if (!completeFile.getParentFile().exists() && !IOUtils.mkdirs(completeFile.getParentFile())) {
            Log.w(LOG_TAG, "could not create complete file dir");
            return false;
        }
        // optimization - if chunks have been written in order, just move and truncate file
        else if (isOrdered(mIndexes)) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "chunk file is already in order, moving");
            return move(chunkFile, completeFile) && checkEtag(completeFile, mEtag);
        } else {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "reassembling chunkfile");
            return reassembleFile(chunkFile, completeFile) && checkEtag(completeFile, mEtag);
        }
    }

    private boolean checkEtag(File file, String etag) {
        if (etag == null || file.length() > MAX_MD5_CHECK_SIZE) return true;

        final String calculatedEtag = '"'+ IOUtils.md5(file)+'"';
        if (!calculatedEtag.equals(etag)) {
            Log.w(LOG_TAG, "etag " +etag+ " for complete file "+ file + " does not match "+calculatedEtag);
            return false;
        } else return true;
    }

    private Boolean move(File chunkFile, File completeFile) {
        if (chunkFile.renameTo(completeFile)) {
            if (completeFile.length() != mContentLength) {
                try {
                    new RandomAccessFile(completeFile, "rw").setLength(mContentLength);
                    return true;
                } catch (IOException e) {
                    Log.w(LOG_TAG, e);
                }
            }
        } else {
            Log.w(LOG_TAG, "error moving file");
        }
        return false;
    }

    private boolean isOrdered(Iterable<Integer> indexes) {
        int last = 0;
        for (int i : indexes) {
            if (last > i) return false;
            last = i;
        }
        return true;
    }

    private Boolean reassembleFile(File chunkFile, File completeFile) {
        FileOutputStream fos = null;
        RandomAccessFile raf = null;
        try {
            fos = new FileOutputStream(completeFile);
            raf = new RandomAccessFile(chunkFile, "r");

            byte[] buffer = new byte[mChunkSize];
            for (int chunkNumber = 0; chunkNumber < mIndexes.size(); chunkNumber++) {
                int offset = mChunkSize * mIndexes.indexOf(chunkNumber);
                raf.seek(offset);
                raf.readFully(buffer, 0, mChunkSize);

                if (chunkNumber == mIndexes.size() - 1) {
                    fos.write(buffer, 0, (int) (mContentLength % mChunkSize));
                } else {
                    fos.write(buffer);
                }
            }
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "complete file " + completeFile + " written");
        } catch (IOException e) {
            Log.e(LOG_TAG, "IO error during complete file creation", e);
            if (completeFile.delete()) Log.d(LOG_TAG, "Deleted " + completeFile);
            return false;
        } finally {
            if (raf != null) try {
                raf.close();
            } catch (IOException ignored) {
            }
            if (fos != null) try {
                fos.close();
            } catch (IOException ignored) {
            }
        }
        return true;
    }
}
