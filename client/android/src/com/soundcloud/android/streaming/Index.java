package com.soundcloud.android.streaming;

import java.util.BitSet;
import java.util.Iterator;
import java.util.Set;

class Index extends BitSet implements Iterable<Integer> {
    public static Index create(int... pos) {
        Index idx = new Index();
        for (int i : pos) idx.set(i);
        return idx;
    }

    public int first() {
        return nextSetBit(0);
    }

    public static Index empty() {
        return new Index();
    }

    public static Index fromSet(Set<Integer> set) {
        Index idx = new Index();
        for (int i  : set) idx.set(i, true);
        return idx;
    }

    @Override
    public int size() {
        int size = 0;
        for (Iterator it = iterator(); it.hasNext(); it.next()) size++;
        return size;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            int position = 0;
            @Override
            public boolean hasNext() {
                return nextSetBit(position) != -1;
            }

            @Override
            public Integer next() {
                final int next = nextSetBit(position);
                position = next+1;
                return next;
            }

            @Override
            public void remove() {
                set(position-1, false);
            }
        };
    }
}
