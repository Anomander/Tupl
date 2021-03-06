/*
 *  Copyright 2014 Brian S O'Neill
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
 * Tree which uses an explicit transaction when none is specified, excluding loads.
 *
 * @author Brian S O'Neill
 */
class TxnTree extends Tree {
    TxnTree(Database db, long id, byte[] idBytes, byte[] name, Node root) {
        super(db, id, idBytes, name, root);
    }

    @Override
    public Cursor newCursor(Transaction txn) {
        return new TxnTreeCursor(this, txn);
    }

    @Override
    public void store(Transaction txn, byte[] key, byte[] value) throws IOException {
        if (txn != null) {
            super.store(txn, key, value);
        } else {
            txn = mDatabase.newAlwaysRedoTransaction();
            try {
                super.store(txn, key, value);
                txn.commit();
            } catch (Throwable e) {
                txn.reset();
                throw e;
            }
        }
    }

    @Override
    public byte[] exchange(Transaction txn, byte[] key, byte[] value) throws IOException {
        if (txn != null) {
            return super.exchange(txn, key, value);
        } else {
            txn = mDatabase.newAlwaysRedoTransaction();
            try {
                byte[] oldValue = super.exchange(txn, key, value);
                txn.commit();
                return oldValue;
            } catch (Throwable e) {
                txn.reset();
                throw e;
            }
        }
    }

    @Override
    public boolean insert(Transaction txn, byte[] key, byte[] value) throws IOException {
        if (txn != null) {
            return super.insert(txn, key, value);
        } else {
            txn = mDatabase.newAlwaysRedoTransaction();
            try {
                boolean result = super.insert(txn, key, value);
                txn.commit();
                return result;
            } catch (Throwable e) {
                txn.reset();
                throw e;
            }
        }
    }

    @Override
    public boolean replace(Transaction txn, byte[] key, byte[] value) throws IOException {
        if (txn != null) {
            return super.replace(txn, key, value);
        } else {
            txn = mDatabase.newAlwaysRedoTransaction();
            try {
                boolean result = super.replace(txn, key, value);
                txn.commit();
                return result;
            } catch (Throwable e) {
                txn.reset();
                throw e;
            }
        }
    }

    @Override
    public boolean update(Transaction txn, byte[] key, byte[] oldValue, byte[] newValue)
        throws IOException
    {
        if (txn != null) {
            return super.update(txn, key, oldValue, newValue);
        } else {
            txn = mDatabase.newAlwaysRedoTransaction();
            try {
                boolean result = super.update(txn, key, oldValue, newValue);
                txn.commit();
                return result;
            } catch (Throwable e) {
                txn.reset();
                throw e;
            }
        }
    }

    @Override
    public Stream newStream() {
        TreeCursor cursor = new TxnTreeCursor(this);
        cursor.autoload(false);
        return new TreeValueStream(cursor);
    }
}
