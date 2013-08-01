package com.soundcloud.android.streaming;

import static com.soundcloud.android.utils.IOUtils.mkdirs;

import com.soundcloud.android.utils.IOUtils;
import org.jetbrains.annotations.NotNull;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreamStorage {
    static final String LOG_TAG = StreamStorage.class.getSimpleName();

    public static final int DEFAULT_CHUNK_SIZE = 128 * 1024; // 128k
    public static final int DEFAULT_PCT_OF_FREE_SPACE = 10;  // use 10% of sd card

    private static final int CLEANUP_INTERVAL = 20;

    public static final String INDEX_EXTENSION = "index";
    public static final String CHUNKS_EXTENSION = "chunks";

    public final int chunkSize;

    private Context mContext;
    private File mBaseDir, mCompleteDir, mIncompleteDir;

    private Map<String, StreamItem> mItems = new HashMap<String, StreamItem>();
    private Set<String> mConvertingUrls = new HashSet<String>();

    private final int mCleanupInterval;

    public StreamStorage(Context context, File basedir) {
        this(context, basedir, DEFAULT_CHUNK_SIZE, CLEANUP_INTERVAL);
    }

    public StreamStorage(Context context, File basedir, int chunkSize, int cleanupInterval) {
        mContext = context;
        mBaseDir = basedir;
        mIncompleteDir = new File(mBaseDir, "Incomplete");
        mCompleteDir = new File(mBaseDir, "Complete");
        mCleanupInterval = cleanupInterval;

        mkdirs(mIncompleteDir);
        mkdirs(mCompleteDir);

        this.chunkSize = chunkSize;
    }

    public synchronized boolean storeMetadata(StreamItem item) {
        verifyMetadata(item);

        mItems.put(item.urlHash, item);
        try {
            File indexFile = incompleteIndexFileForUrl(item.streamItemUrl());
            if (indexFile.exists() && !indexFile.delete()) Log.w(LOG_TAG, "could not delete "+indexFile);
            item.toIndexFile(indexFile);
            return true;
        } catch (IOException e) {
            if (IOUtils.isSDCardAvailable()) {
                Log.e(LOG_TAG, "Error storing index data ", e);
            }
            return false;
        }
    }

    public synchronized @NotNull StreamItem getMetadata(String url) {
        String hashed = StreamItem.urlHash(url);
        if (!mItems.containsKey(hashed)) {
            mItems.put(hashed, readMetadata(url));
        }
        return mItems.get(hashed);
    }

    public synchronized boolean removeMetadata(String url) {
        return mItems.remove(StreamItem.urlHash(url)) != null;
    }

    public ByteBuffer fetchStoredDataForUrl(String url, Range range) throws IOException {
        StreamItem item = getMetadata(url);

        Range actualRange = range;
        if (item.getContentLength() > 0) {
            actualRange = range.intersection(Range.from(0, item.getContentLength()));
            if (actualRange == null) {
                throw new IOException("Invalid range, outside content length. Requested range " + range + " from item " + item);
            }
        }
        Range chunkRange = actualRange.chunkRange(chunkSize);
        if (chunkRange.length == 1 && actualRange.length == chunkSize) {
            // optimise for most common case
            return getChunkData(url, chunkRange.start);
        } else {
            ByteBuffer data = ByteBuffer.allocate(chunkRange.length * chunkSize);
            // read all the chunks we need
            for (int index : chunkRange) {
                data.put(getChunkData(url, index));
            }
            // and adjust offsets
            data.position(actualRange.start % chunkSize);
            data.limit(actualRange.start % chunkSize + actualRange.length);
            return data.asReadOnlyBuffer();
        }
    }

    public boolean storeData(final URL url, ByteBuffer data, final int chunkIndex) throws IOException {
        return storeData(url.toString(), data, chunkIndex);
    }

    /**
     * @param data       the data to store
     * @param chunkIndex the chunk index the data belongs to
     * @param url        the url for the data
     * @return if the data was set
     * @throws java.io.IOException IO error
     */
    public boolean storeData(final String url, ByteBuffer data, final int chunkIndex) throws IOException {
        if (data == null) throw new IllegalArgumentException("buffer is null");
        else if (data.limit() == 0) {
            Log.w(LOG_TAG, "Not Storing Data. Content Length is Zero.");
            return false;
        }
        // Do not add to complete files
        else if (completeFileForUrl(url).exists()) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "complete file exists, not adding data");
            return false;
        }
        else if (!IOUtils.isSDCardAvailable()) {
            Log.w(LOG_TAG, "storage not available, not adding data");
            return false;
        }

        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            Log.d(LOG_TAG, String.format("Storing %d bytes at index %d for url %s",
                    data.limit(), chunkIndex, url));

        final StreamItem item = getMetadata(url);
        // return if it's already in store
        if (item.downloadedChunks.contains(chunkIndex)) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, String.format("already got chunk"));
            return false;
        }

        // Prepare incomplete file
        final File incompleteFile = incompleteFileForUrl(url);
        appendToFile(data, incompleteFile);

        // Add Index and save it
        item.downloadedChunks.add(chunkIndex);
        storeMetadata(item);

        if (item.downloadedChunks.size() == item.numberOfChunks(chunkSize)) {
            new CompleteFileTask(item.getContentLength(), item.etag(), chunkSize, item.downloadedChunks) {
                @Override protected void onPreExecute() {
                    mConvertingUrls.add(url);
                }
                @Override protected void onPostExecute(Boolean success) {
                    if (success) {
                        removeIncompleteDataForItem(url);

                    } else {
                        removeAllDataForItem(url);
                    }
                    mConvertingUrls.remove(url);
                }
            }.execute(incompleteFile, completeFileForUrl(url));
        }
        return true;
    }

    public ByteBuffer getChunkData(URL url, int chunkIndex) throws IOException {
        return getChunkData(url.toString(), chunkIndex);
    }

    public ByteBuffer getChunkData(String url, int chunkIndex) throws IOException {
        if (completeFileForUrl(url).exists()) {
            return completeDataForChunk(url, chunkIndex);
        } else {
            return incompleteDataForChunk(url, chunkIndex);
        }
    }

    public Index getMissingChunksForItem(String url, Range chunkRange) {
        //we have everything if the complete file exists
        if (completeFileForUrl(url).exists()) {
            return Index.empty();
        } else {
            StreamItem item = getMetadata(url);
            //We have no idea about track size, so let's assume that all chunks are missing
            if (item.getContentLength() == 0) {
                return chunkRange.toIndex();
            } else {
                long lastChunk = (long) Math.ceil((double) item.getContentLength() / (double) chunkSize) - 1;
                final Index missingIndexes = new Index();
                for (int chunk : chunkRange) {
                    if (!item.downloadedChunks.contains(chunk) && chunk <= lastChunk) {
                        missingIndexes.set(chunk);
                    }
                }
                return missingIndexes;
            }
        }
    }

    /* package */ File completeFileForUrl(String url) {
        return new File(mCompleteDir, StreamItem.urlHash(url));
    }

    /* package */ File incompleteFileForUrl(String url) {
        return new File(mIncompleteDir, StreamItem.urlHash(url)+"."+CHUNKS_EXTENSION);
    }

    /* package */ File incompleteIndexFileForUrl(String url) {
        return new File(mIncompleteDir, StreamItem.urlHash(url)+"."+INDEX_EXTENSION);
    }

    private boolean appendToFile(ByteBuffer data, File incompleteFile) throws IOException {
        mkdirs(incompleteFile.getParentFile());
        final int length = data.remaining();
        FileChannel fc = new FileOutputStream(incompleteFile, true).getChannel();
        fc.write(data);
        // always write chunk size even if it isn't a complete chunk (for offsetting)
        if (length < chunkSize) {
            fc.write(ByteBuffer.allocate(chunkSize - length));
        }
        fc.close();
        return true;
    }

    private @NotNull StreamItem readMetadata(String url) {
        File f = incompleteIndexFileForUrl(url);
        if (f.exists()) {
            try {
                return StreamItem.fromIndexFile(f);
            } catch (IOException e) {
                Log.e(LOG_TAG, "could not read metadata, deleting", e);
                removeAllDataForItem(url);
                return new StreamItem(url);
            }
        } else if (completeFileForUrl(url).exists()) {
            return new StreamItem(url, completeFileForUrl(url));
        } else {
            // we don't have anything yet
            return new StreamItem(url);
        }
    }

    private void removeAllDataForItem(String url) {
        Log.w(LOG_TAG, "removing all data for "+url);
        removeCompleteDataForItem(url);
        removeIncompleteDataForItem(url);
    }

    private boolean removeIncompleteDataForItem(String url) {
        final File incompleteFile = incompleteFileForUrl(url);
        final File indexFile = incompleteIndexFileForUrl(url);
        boolean fileDeleted = true, indexDeleted = true;
        if (incompleteFile.exists()) fileDeleted = incompleteFile.delete();
        if (indexFile.exists()) indexDeleted = indexFile.delete();
        mItems.remove(StreamItem.urlHash(url));
        return fileDeleted && indexDeleted;
    }

    private boolean removeCompleteDataForItem(String url) {
        final File completeFile = completeFileForUrl(url);
        return completeFile.exists() && completeFile.delete();
    }

    /* package */ ByteBuffer incompleteDataForChunk(String url, int chunkIndex) throws IOException {
        StreamItem item = getMetadata(url);
        if (!item.downloadedChunks.contains(chunkIndex)) {
            throw new FileNotFoundException("download chunk not available");
        }
        int readLength = chunkIndex == item.numberOfChunks(chunkSize) ? (int) item.getContentLength() % chunkSize : chunkSize;
        return readBuffer(incompleteFileForUrl(url), item.downloadedChunks.indexOf(chunkIndex) * chunkSize, readLength);
    }

    /* package */ ByteBuffer completeDataForChunk(String url, long chunkIndex) throws IOException {
        final long totalChunks = getMetadata(url).numberOfChunks(chunkSize);
        if (chunkIndex >= totalChunks) {
            throw new IOException("Requested invalid chunk index. Requested index " + chunkIndex + " of size " + totalChunks);
        }
        return readBuffer(completeFileForUrl(url), chunkIndex * chunkSize, chunkSize);
    }

    private ByteBuffer readBuffer(File f, long pos, int length) throws IOException {
        if (!f.exists()) throw new FileNotFoundException("file "+f+" does not exist");
        FileChannel fc = new FileInputStream(f).getChannel();
        fc.position(pos);
        ByteBuffer bb = ByteBuffer.allocate(length);
        try {
            fc.read(bb);
            bb.flip();
            return bb;
        } finally {
            fc.close();
        }
    }

    private List<File> allFiles(Comparator<File> comparator) {
        final List<File> files = new ArrayList<File>();
        File[] chunks = IOUtils.nullSafeListFiles(mIncompleteDir, extension(CHUNKS_EXTENSION));
        if (chunks.length > 0) files.addAll(Arrays.asList(chunks));

        File[] complete = IOUtils.nullSafeListFiles(mCompleteDir, null);
        if (complete.length > 0) files.addAll(Arrays.asList(complete));

        if (comparator != null) {
            Collections.sort(files, comparator);
        }
        return files;
    }


    /* package */ long getUsedSpace() {
        long currentlyUsedSpace = 0;
        File[] complete = IOUtils.nullSafeListFiles(mCompleteDir, null);
        for (File f : complete) {
            currentlyUsedSpace += f.length();
        }

        File[] incomplete = IOUtils.nullSafeListFiles(mIncompleteDir, null);
        for (File f : incomplete) {
            currentlyUsedSpace += f.length();
        }
        return currentlyUsedSpace;
    }

    /* package */ long getSpaceLeft() {
        return IOUtils.getSpaceLeft(mBaseDir);
    }

    /* package */ long getTotalSpace() {
        return IOUtils.getTotalSpace(mBaseDir);
    }

    /* package */ void verifyMetadata(StreamItem item) {
        StreamItem existing = mItems.get(item.urlHash);
        if (existing != null &&
            existing.etag() != null &&
            !existing.etag().equals(item.etag())) {

            Log.w(LOG_TAG, "eTag don't match, removing cached data");
            removeAllDataForItem(item.streamItemUrl());
        }
    }

    static FilenameFilter extension(final String ext) {
        return new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith("."+ext);
            }
        };
    }
}
