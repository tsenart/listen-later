package com.soundcloud.android.streaming;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class Range implements Iterable<Integer>, Parcelable {
    public final int start;
    public final int length;

    /* private */ Range(int start, int length) {
        if (start < 0) throw new IllegalArgumentException("start must be >=0");
        if (length <= 0) throw new IllegalArgumentException("length must be >0");

        this.start = start;
        this.length = length;
    }

    public static Range from(int start, int length) {
        return new Range(start, length);
    }

    public static Range from(long start, long length) {
        return new Range((int)start, (int)length);
    }

    public Index toIndex() {
        Index index = new Index();
        for (int i = start; i < length+start; i++) {
            index.set(i);
        }
        return index;
    }

    public Range moveStart(int n) {
        return new Range(start +n, length);
    }

    public int end() {
        return start + length;
    }

    public Range intersection(Range range) {
        final int low = Math.max(range.start, start);
        final int high = Math.min(range.end(), end());

        return (low < high) ? new Range(low, high - low) : null;
    }

    public String toString() {
        return "Range{location: " + start +
                ", length:" + length +
                "}";
    }

    public Range chunkRange(int chunkSize) {
       return Range.from(start / chunkSize,
            (int) Math.ceil((double) ((start % chunkSize) + length) / (double) chunkSize));
    }

    public Range byteRange(int chunkSize) {
        return Range.from(start * chunkSize, length * chunkSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range range = (Range) o;
        return length == range.length && start == range.start;
    }

    @Override
    public int hashCode() {
        int result = start;
        result = 31 * result + length;
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Bundle data = new Bundle();
        data.putInt("location", start);
        data.putInt("length", length);
        dest.writeBundle(data);
    }

    public Range(Parcel in) {
        Bundle data = in.readBundle(getClass().getClassLoader());
        start = data.getInt("location");
        length = data.getInt("length");
    }

    public static final Parcelable.Creator<Range> CREATOR = new Parcelable.Creator<Range>() {
        public Range createFromParcel(Parcel in) {
            return new Range(in);
        }

        public Range[] newArray(int size) {
            return new Range[size];
        }
    };

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            int i = Range.this.start;

            @Override public boolean hasNext() {
                return i < end();
            }

            @Override public Integer next() {
                if (!hasNext()) throw new NoSuchElementException();
                return i++;
            }

            @Override public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}