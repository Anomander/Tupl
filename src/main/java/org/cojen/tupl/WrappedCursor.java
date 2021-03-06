/*
 *  Copyright 2013 Brian S O'Neill
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.tupl;

import java.io.IOException;

/**
 * 
 *
 * @author Brian S O'Neill
 */
abstract class WrappedCursor<C extends Cursor> implements Cursor {
    final C mSource;

    WrappedCursor(C source) {
        mSource = source;
    }

    @Override
    public Ordering getOrdering() {
        return mSource.getOrdering();
    }

    @Override
    public Transaction link(Transaction txn) {
        return mSource.link(txn);
    }

    @Override
    public byte[] key() {
        return mSource.key();
    }

    @Override
    public byte[] value() {
        return mSource.value();
    }

    @Override
    public boolean autoload(boolean mode) {
        return mSource.autoload(mode);
    }

    @Override
    public int compareKeyTo(byte[] rkey) {
        return mSource.compareKeyTo(rkey);
    }

    @Override
    public int compareKeyTo(byte[] rkey, int offset, int length) {
        return mSource.compareKeyTo(rkey, offset, length);
    }

    @Override
    public LockResult first() throws IOException {
        return mSource.first();
    }

    @Override
    public LockResult last() throws IOException {
        return mSource.last();
    }

    @Override
    public LockResult skip(long amount) throws IOException {
        return mSource.skip(amount);
    }

    @Override
    public LockResult next() throws IOException {
        return mSource.next();
    }

    @Override
    public LockResult nextLe(byte[] limitKey) throws IOException {
        return mSource.nextLe(limitKey);
    }

    @Override
    public LockResult nextLt(byte[] limitKey) throws IOException {
        return mSource.nextLt(limitKey);
    }

    @Override
    public LockResult previous() throws IOException {
        return mSource.previous();
    }

    @Override
    public LockResult previousGe(byte[] limitKey) throws IOException {
        return mSource.previousGe(limitKey);
    }

    @Override
    public LockResult previousGt(byte[] limitKey) throws IOException {
        return mSource.previousGt(limitKey);
    }

    @Override
    public LockResult find(byte[] key) throws IOException {
        return mSource.find(key);
    }

    @Override
    public LockResult findGe(byte[] key) throws IOException {
        return mSource.findGe(key);
    }

    @Override
    public LockResult findGt(byte[] key) throws IOException {
        return mSource.findGt(key);
    }

    @Override
    public LockResult findLe(byte[] key) throws IOException {
        return mSource.findLe(key);
    }

    @Override
    public LockResult findLt(byte[] key) throws IOException {
        return mSource.findLt(key);
    }

    @Override
    public LockResult findNearby(byte[] key) throws IOException {
        return mSource.findNearby(key);
    }

    @Override
    public LockResult random(byte[] lowKey, byte[] highKey) throws IOException {
        return mSource.random(lowKey, highKey);
    }

    @Override
    public LockResult load() throws IOException {
        return mSource.load();
    }

    @Override
    public void store(byte[] value) throws IOException {
        mSource.store(value);
    }

    @Override
    public void reset() {
        mSource.reset();
    }
}
