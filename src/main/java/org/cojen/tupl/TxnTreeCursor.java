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
 * TreeCursor which uses an explicit transaction when none is specified, excluding loads.
 *
 * @author Brian S O'Neill
 */
class TxnTreeCursor extends TreeCursor {
    TxnTreeCursor(TxnTree tree, Transaction txn) {
        super(tree, txn);
    }

    TxnTreeCursor(TxnTree tree) {
        super(tree);
    }

    @Override
    public final void store(byte[] value) throws IOException {
        byte[] key = mKey;
        if (key == null) {
            throw new IllegalStateException("Cursor position is undefined");
        }

        try {
            Transaction txn = mTxn;
            final int hash = keyHash();
            if (txn == null) {
                txn = mTree.mDatabase.newAlwaysRedoTransaction();
                try {
                    if (txn.lockMode() != LockMode.UNSAFE) {
                        txn.lockExclusive(mTree.mId, key, hash);
                    }
                    store(txn, leafExclusive(), value, false);
                    txn.commit();
                } catch (Throwable e) {
                    txn.reset();
                    throw e;
                }
            } else {
                if (txn.lockMode() != LockMode.UNSAFE) {
                    txn.lockExclusive(mTree.mId, key, hash);
                }
                store(txn, leafExclusive(), value, false);
            }
        } catch (Throwable e) {
            throw handleException(e, false);
        }
    }
}
