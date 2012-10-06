/*
 * LensKit, an open source recommender systems toolkit.
 * Copyright 2010-2012 Regents of the University of Minnesota and contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.grouplens.lenskit.vectors;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.grouplens.lenskit.collections.LongSortedArraySet;
import org.grouplens.lenskit.collections.MoreArrays;
import org.grouplens.lenskit.symbols.Symbol;

/**
 * Immutable sparse vectors. These vectors cannot be changed, even by other
 * code, and are therefore safe to store and are thread-safe.
 *
 * @author Michael Ekstrand <ekstrand@cs.umn.edu>
 * @compat Public
 */
@Immutable
public final class ImmutableSparseVector extends SparseVector implements Serializable {
    private static final long serialVersionUID = -4740588973577998934L;

    protected final long[] keys;
    protected double[] values;
    protected final int size;

    private Map<Symbol, ImmutableSparseVector> channelMap;

    /**
     * Create a new, empty immutable sparse vector.
     */
    public ImmutableSparseVector() {
        keys = new long[0];
        values = null;
        size = 0;
    }

    /**
     * Create a new immutable sparse vector from a map of ratings.
     *
     * @param ratings The ratings to make a vector from. Its key set is used as
     *                the vector's key domain.
     */
    public ImmutableSparseVector(Long2DoubleMap ratings) {
        keys = ratings.keySet().toLongArray();
        size = keys.length;
        Arrays.sort(keys);
        assert keys.length == ratings.size();
        assert MoreArrays.isSorted(keys, 0, size);
        values = new double[keys.length];
        final int len = keys.length;
        for (int i = 0; i < len; i++) {
            values[i] = ratings.get(keys[i]);
        }
    }

    /**
     * Construct a new sparse vector from pre-existing arrays. These arrays must
     * be sorted in key order and cannot contain duplicate keys; this condition
     * is not checked.
     *
     * @param ks The key array (will be the key domain).
     * @param vs The value array.
     * @param sz The length to actually use.
     */
    protected ImmutableSparseVector(long[] ks, double[] vs, int sz) {
        keys = ks;
        values = vs;
        size = sz;
        assert MoreArrays.isSorted(ks, 0, sz);
    }

    @Override
    public double get(long key, double dft) {
        final int idx = Arrays.binarySearch(keys, 0, size, key);
        if (idx >= 0) {
            return values[idx];
        } else {
	    return dft;
        }
    }

    @Override
    public boolean containsKey(long key) {
        return Arrays.binarySearch(keys, 0, size, key) >= 0;
    }

    @Override
    public Iterator<VectorEntry> iterator() {
        return new IterImpl();
    }

    @Override
    public Iterator<VectorEntry> fastIterator() {
        return new FastIterImpl();
    }

    @Override
    public Iterator<VectorEntry> fastIterator(VectorEntry.State state) {
        return fastIterator();
    }

    @Override
    public LongSortedSet keySet() {
        return LongSortedArraySet.wrap(keys, size);
    }

    @Override
    public LongSortedSet keyDomain() {
        return keySet();
    }

    @Override
    public DoubleList values() {
        return DoubleLists.unmodifiable(new DoubleArrayList(values, 0, size));
    }

    @Override
    public int size() {
        return size;
    }

    /**
     * {@inheritDoc}
     * <p>This implementation uses an optimized implementation when computing
     * the dot product of two immutable sparse vectors.
     */
    @Override
    public double dot(SparseVector o) {
        if (o instanceof ImmutableSparseVector) {
            // we can speed this up a lot
            final ImmutableSparseVector iv = (ImmutableSparseVector) o;
            double dot = 0;

            final int sz = size;
            final int osz = iv.size;
            int i = 0;
            int j = 0;
            while (i < sz && j < osz) {
                final long k1 = keys[i];
                final long k2 = iv.keys[j];
                if (k1 == k2) {
                    dot += values[i] * iv.values[j];
                    i++;
                    j++;
                } else if (k1 < k2) {
                    i++;
                } else {
                    j++;
                }
            }

            return dot;
        } else {
            return super.dot(o);
        }
    }

    /**
     * {@inheritDoc}
     * <p>Uses an optimized implementation when computing common keys of
     * two immutable sparse vectors.
     */
    @Override
    public int countCommonKeys(SparseVector o) {
        if (o instanceof ImmutableSparseVector) {
            // we can speed this up a lot
            final ImmutableSparseVector iv = (ImmutableSparseVector) o;
            int n = 0;

            final int sz = size;
            final int osz = iv.size;
            int i = 0;
            int j = 0;
            while (i < sz && j < osz) {
                final long k1 = keys[i];
                final long k2 = iv.keys[j];
                if (k1 == k2) {
                    n += 1;
                    i++;
                    j++;
                } else if (k1 < k2) {
                    i++;
                } else {
                    j++;
                }
            }

            return n;
        } else {
            return super.countCommonKeys(o);
        }
    }

    @Override
    public ImmutableSparseVector immutable() {
        return this;
    }

    @Override
    public MutableSparseVector mutableCopy() {
        return new MutableSparseVector(keys, Arrays.copyOf(values, size), size);
    }

    private class IterImpl implements Iterator<VectorEntry> {
        private int pos = 0;

        @Override
        public boolean hasNext() {
            return pos < size;
        }

        @Override
        public VectorEntry next() {
            if (hasNext()) {
                final VectorEntry e = new VectorEntry(keys[pos], values[pos]);
                pos++;
                return e;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private class FastIterImpl implements Iterator<VectorEntry> {
        private int pos;
        private VectorEntry entry = new VectorEntry(0, 0);

        @Override
        public boolean hasNext() {
            return pos < size;
        }

        @Override
        public VectorEntry next() {
            if (hasNext()) {
                entry.set(keys[pos], values[pos]);
                pos++;
                return entry;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
	public boolean hasChannel(Symbol channelSymbol) {
	return channelMap.containsKey(channelSymbol);
    }

    @Override
    public ImmutableSparseVector channel(Symbol channelSymbol) {
	if (hasChannel(channelSymbol)) {
	    return channelMap.get(channelSymbol);
	}
	throw new IllegalArgumentException("No existing channel under name " +
					   channelSymbol.getName());
    }
    
}
