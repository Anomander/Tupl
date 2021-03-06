/*
 *  Copyright 2011-2013 Brian S O'Neill
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

import static org.cojen.tupl.LockManager.*;

/**
 * Accumulates a scoped stack of locks, bound to arbitrary keys. Locker
 * instances can only be safely used by one thread at a time. Lockers can be
 * exchanged by threads, as long as a happens-before relationship is
 * established. Without proper exclusion, multiple threads interacting with a
 * Locker instance may cause database corruption.
 *
 * @author Brian S O'Neill
 */
class Locker {
    final LockManager mManager;

    private int mHashCode;

    ParentScope mParentScope;

    // Is null if empty; Lock instance if one; Block if more.
    Object mTailBlock;

    // Locker is currently waiting to acquire this lock. Used for deadlock detection.
    Lock mWaitingFor;

    Locker(LockManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("LockManager is null");
        }
        mManager = manager;
    }

    // Constructor used by Transaction.BOGUS.
    Locker() {
        mManager = null;
    }

    /**
     * Attempts to acquire a shared lock for the given key, denying exclusive
     * locks. If return value is {@link LockResult#alreadyOwned owned}, transaction
     * already owns a strong enough lock, and no extra unlock should be
     * performed.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @param key non-null key to lock; instance is not cloned
     * @param nanosTimeout maximum time to wait for lock; negative timeout is infinite
     * @return {@link LockResult#INTERRUPTED INTERRUPTED}, {@link
     * LockResult#TIMED_OUT_LOCK TIMED_OUT_LOCK}, {@link LockResult#ACQUIRED
     * ACQUIRED}, {@link LockResult#OWNED_SHARED OWNED_SHARED}, {@link
     * LockResult#OWNED_UPGRADABLE OWNED_UPGRADABLE}, or {@link
     * LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     * @throws IllegalStateException if too many shared locks
     * @throws DeadlockException if deadlock was detected after waiting full timeout
     */
    public final LockResult tryLockShared(long indexId, byte[] key, long nanosTimeout)
        throws DeadlockException
    {
        return tryLockShared(indexId, key, hash(indexId, key), nanosTimeout);
    }

    final LockResult tryLockShared(long indexId, byte[] key, int hash, long nanosTimeout)
        throws DeadlockException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_SHARED, this, indexId, key, hash, nanosTimeout);
        if (result == LockResult.TIMED_OUT_LOCK) {
            detectDeadlock(nanosTimeout);
        }
        return result;
    }

    /**
     * Attempts to acquire a shared lock for the given key, denying exclusive
     * locks. If return value is {@link LockResult#alreadyOwned owned}, transaction
     * already owns a strong enough lock, and no extra unlock should be
     * performed.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @param key non-null key to lock; instance is not cloned
     * @param nanosTimeout maximum time to wait for lock; negative timeout is infinite
     * @return {@link LockResult#ACQUIRED ACQUIRED}, {@link
     * LockResult#OWNED_SHARED OWNED_SHARED}, {@link
     * LockResult#OWNED_UPGRADABLE OWNED_UPGRADABLE}, or {@link
     * LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     * @throws IllegalStateException if too many shared locks
     * @throws LockFailureException if interrupted or timed out
     * @throws DeadlockException if deadlock was detected after waiting full timeout
     */
    public final LockResult lockShared(long indexId, byte[] key, long nanosTimeout)
        throws LockFailureException
    {
        return lockShared(indexId, key, hash(indexId, key), nanosTimeout);
    }

    final LockResult lockShared(long indexId, byte[] key, int hash, long nanosTimeout)
        throws LockFailureException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_SHARED, this, indexId, key, hash, nanosTimeout);
        if (result.isHeld()) {
            return result;
        }
        throw failed(result, nanosTimeout);
    }

    /**
     * NT == No Timeout or deadlock exception thrown
     *
     * @return {@link LockResult#TIMED_OUT_LOCK TIMED_OUT_LOCK}, {@link
     * LockResult#ACQUIRED ACQUIRED}, {@link LockResult#OWNED_SHARED
     * OWNED_SHARED}, {@link LockResult#OWNED_UPGRADABLE OWNED_UPGRADABLE}, or
     * {@link LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     */
    final LockResult lockSharedNT(long indexId, byte[] key, int hash, long nanosTimeout)
        throws LockFailureException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_SHARED, this, indexId, key, hash, nanosTimeout);
        return result.isHeld() ? result : nt(result, indexId, key, nanosTimeout);
    }

    /**
     * Attempts to acquire an upgradable lock for the given key, denying
     * exclusive and additional upgradable locks. If return value is {@link
     * LockResult#alreadyOwned owned}, transaction already owns a strong enough
     * lock, and no extra unlock should be performed. If {@link
     * LockResult#ILLEGAL ILLEGAL} is returned, transaction holds a shared
     * lock, which cannot be upgraded.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @param key non-null key to lock; instance is not cloned
     * @param nanosTimeout maximum time to wait for lock; negative timeout is infinite
     * @return {@link LockResult#ILLEGAL ILLEGAL}, {@link
     * LockResult#INTERRUPTED INTERRUPTED}, {@link LockResult#TIMED_OUT_LOCK
     * TIMED_OUT_LOCK}, {@link LockResult#ACQUIRED ACQUIRED}, {@link
     * LockResult#OWNED_UPGRADABLE OWNED_UPGRADABLE}, or {@link
     * LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     * @throws DeadlockException if deadlock was detected after waiting full timeout
     */
    public final LockResult tryLockUpgradable(long indexId, byte[] key, long nanosTimeout)
        throws DeadlockException
    {
        return tryLockUpgradable(indexId, key, hash(indexId, key), nanosTimeout);
    }

    final LockResult tryLockUpgradable(long indexId, byte[] key, int hash, long nanosTimeout)
        throws DeadlockException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_UPGRADABLE, this, indexId, key, hash, nanosTimeout);
        if (result == LockResult.TIMED_OUT_LOCK) {
            detectDeadlock(nanosTimeout);
        }
        return result;
    }

    /**
     * Attempts to acquire an upgradable lock for the given key, denying
     * exclusive and additional upgradable locks. If return value is {@link
     * LockResult#alreadyOwned owned}, transaction already owns a strong enough
     * lock, and no extra unlock should be performed.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @param key non-null key to lock; instance is not cloned
     * @param nanosTimeout maximum time to wait for lock; negative timeout is infinite
     * @return {@link LockResult#ACQUIRED ACQUIRED}, {@link
     * LockResult#OWNED_UPGRADABLE OWNED_UPGRADABLE}, or {@link
     * LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     * @throws LockFailureException if interrupted, timed out, or illegal upgrade
     * @throws DeadlockException if deadlock was detected after waiting full timeout
     */
    public final LockResult lockUpgradable(long indexId, byte[] key, long nanosTimeout)
        throws LockFailureException
    {
        return lockUpgradable(indexId, key, hash(indexId, key), nanosTimeout);
    }

    final LockResult lockUpgradable(long indexId, byte[] key, int hash, long nanosTimeout)
        throws LockFailureException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_UPGRADABLE, this, indexId, key, hash, nanosTimeout);
        if (result.isHeld()) {
            return result;
        }
        throw failed(result, nanosTimeout);
    }

    /**
     * NT == No Timeout or deadlock exception thrown
     *
     * @return {@link LockResult#TIMED_OUT_LOCK TIMED_OUT_LOCK}, {@link
     * LockResult#ACQUIRED ACQUIRED}, {@link LockResult#OWNED_SHARED
     * OWNED_SHARED}, {@link LockResult#OWNED_UPGRADABLE OWNED_UPGRADABLE}, or
     * {@link LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     */
    final LockResult lockUpgradableNT(long indexId, byte[] key, int hash, long nanosTimeout)
        throws LockFailureException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_UPGRADABLE, this, indexId, key, hash, nanosTimeout);
        return result.isHeld() ? result : nt(result, indexId, key, nanosTimeout);
    }

    /**
     * Attempts to acquire an exclusive lock for the given key, denying any
     * additional locks. If return value is {@link LockResult#alreadyOwned
     * owned}, transaction already owns exclusive lock, and no extra unlock
     * should be performed. If {@link LockResult#ILLEGAL ILLEGAL} is returned,
     * transaction holds a shared lock, which cannot be upgraded.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @param key non-null key to lock; instance is not cloned
     * @param nanosTimeout maximum time to wait for lock; negative timeout is infinite
     * @return {@link LockResult#ILLEGAL ILLEGAL}, {@link
     * LockResult#INTERRUPTED INTERRUPTED}, {@link LockResult#TIMED_OUT_LOCK
     * TIMED_OUT_LOCK}, {@link LockResult#ACQUIRED ACQUIRED}, {@link
     * LockResult#UPGRADED UPGRADED}, or {@link LockResult#OWNED_EXCLUSIVE
     * OWNED_EXCLUSIVE}
     * @throws DeadlockException if deadlock was detected after waiting full timeout
     */
    public final LockResult tryLockExclusive(long indexId, byte[] key, long nanosTimeout)
        throws DeadlockException
    {
        return tryLockExclusive(indexId, key, hash(indexId, key), nanosTimeout);
    }

    final LockResult tryLockExclusive(long indexId, byte[] key, int hash, long nanosTimeout)
        throws DeadlockException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_EXCLUSIVE, this, indexId, key, hash, nanosTimeout);
        if (result == LockResult.TIMED_OUT_LOCK) {
            detectDeadlock(nanosTimeout);
        }
        return result;
    }

    /**
     * Attempts to acquire an exclusive lock for the given key, denying any
     * additional locks. If return value is {@link LockResult#alreadyOwned owned},
     * transaction already owns exclusive lock, and no extra unlock should be
     * performed.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @param key non-null key to lock; instance is not cloned
     * @param nanosTimeout maximum time to wait for lock; negative timeout is infinite
     * @return {@link LockResult#ACQUIRED ACQUIRED}, {@link LockResult#UPGRADED
     * UPGRADED}, or {@link LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     * @throws LockFailureException if interrupted, timed out, or illegal upgrade
     * @throws DeadlockException if deadlock was detected after waiting full timeout
     */
    public final LockResult lockExclusive(long indexId, byte[] key, long nanosTimeout)
        throws LockFailureException
    {
        return lockExclusive(indexId, key, hash(indexId, key), nanosTimeout);
    }

    final LockResult lockExclusive(long indexId, byte[] key, int hash, long nanosTimeout)
        throws LockFailureException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_EXCLUSIVE, this, indexId, key, hash, nanosTimeout);
        if (result.isHeld()) {
            return result;
        }
        throw failed(result, nanosTimeout);
    }

    /**
     * @param newLock Lock instance to insert, unless another already exists. The mIndexId,
     * mKey, and mHashCode fields must be set.
     */
    final LockResult lockExclusive(Lock lock, long nanosTimeout) throws LockFailureException {
        LockResult result = mManager.getLockHT(lock.mHashCode)
            .tryLockExclusive(this, lock, nanosTimeout);
        if (result.isHeld()) {
            return result;
        }
        throw failed(result, nanosTimeout);
    }

    /**
     * NT == No Timeout or deadlock exception thrown
     *
     * @return {@link LockResult#TIMED_OUT_LOCK TIMED_OUT_LOCK}, {@link
     * LockResult#ACQUIRED ACQUIRED}, {@link LockResult#OWNED_SHARED
     * OWNED_SHARED}, {@link LockResult#OWNED_UPGRADABLE OWNED_UPGRADABLE}, or
     * {@link LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     */
    final LockResult lockExclusiveNT(long indexId, byte[] key, int hash, long nanosTimeout)
        throws LockFailureException
    {
        LockResult result = mManager.getLockHT(hash)
            .tryLock(TYPE_EXCLUSIVE, this, indexId, key, hash, nanosTimeout);
        return result.isHeld() ? result : nt(result, indexId, key, nanosTimeout);
    }

    /**
     * Checks if an upgrade attempt should be made when the locker only holds a shared lock.
     *
     * @param count current lock count, not zero
     */
    final boolean canAttemptUpgrade(int count) {
        LockManager manager = mManager;
        if (manager == null) {
            return false;
        }
        LockUpgradeRule lockUpgradeRule = manager.mDefaultLockUpgradeRule;
        return lockUpgradeRule == LockUpgradeRule.UNCHECKED
            | (lockUpgradeRule == LockUpgradeRule.LENIENT & count == 1);
    }

    @SuppressWarnings("incomplete-switch")
    LockFailureException failed(LockResult result, long nanosTimeout) throws DeadlockException {
        switch (result) {
        case TIMED_OUT_LOCK:
            detectDeadlock(nanosTimeout);
            break;
        case ILLEGAL:
            return new IllegalUpgradeException();
        case INTERRUPTED:
            return new LockInterruptedException();
        }
        if (result.isTimedOut()) {
            return new LockTimeoutException(nanosTimeout);
        }
        return new LockFailureException();
    }

    @SuppressWarnings("incomplete-switch")
    private LockResult nt(LockResult result, long indexId, byte[] key, long nanosTimeout)
        throws LockFailureException
    {
        switch (result) {
        case ILLEGAL:
            throw new IllegalUpgradeException();
        case INTERRUPTED:
            throw new LockInterruptedException();
        }
        return result;
    }

    private void detectDeadlock(long nanosTimeout) throws DeadlockException {
        if (mWaitingFor != null) {
            try {
                DeadlockDetector detector = new DeadlockDetector(this);
                if (detector.scan()) {
                    throw new DeadlockException(nanosTimeout,
                                                detector.mGuilty,
                                                new DeadlockSet(detector.mLocks));
                }
            } finally {
                mWaitingFor = null;
            }
        }
    }

    /**
     * Checks the lock ownership for the given key.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @return {@link LockResult#UNOWNED UNOWNED}, {@link
     * LockResult#OWNED_SHARED OWNED_SHARED}, {@link
     * LockResult#OWNED_UPGRADABLE OWNED_UPGRADABLE}, or {@link
     * LockResult#OWNED_EXCLUSIVE OWNED_EXCLUSIVE}
     */
    public final LockResult lockCheck(long indexId, byte[] key) {
        return mManager.check(this, indexId, key, hash(indexId, key));
    }

    /**
     * Returns the index id of the last lock acquired, within the current scope.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @return locked index id
     * @throws IllegalStateException if no locks held
     */
    public final long lastLockedIndex() {
        return peek().mIndexId;
    }

    /**
     * Returns the key of the last lock acquired, within the current scope.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i>
     *
     * @return locked key; instance is not cloned
     * @throws IllegalStateException if no locks held
     */
    public final byte[] lastLockedKey() {
        return peek().mKey;
    }

    private Lock peek() {
        Object tailObj = mTailBlock;
        if (tailObj == null) {
            throw new IllegalStateException("No locks held");
        }
        return (tailObj instanceof Lock) ? ((Lock) tailObj) : (((Block) tailObj).last());
    }

    /**
     * Fully releases last lock acquired, within the current scope. If the last
     * lock operation was an upgrade, for a lock not immediately acquired,
     * unlock is not allowed. Instead, an IllegalStateException is thrown.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i> Also, the current
     * implementation does not accurately track scopes. It may permit an unlock operation to
     * cross a scope boundary, which has undefined behavior.
     *
     * @throws IllegalStateException if no locks held, or if unlocking a
     * non-immediate upgrade
     */
    public final void unlock() {
        Object tailObj = mTailBlock;
        if (tailObj == null) {
            throw new IllegalStateException("No locks held");
        }
        if (tailObj instanceof Lock) {
            mTailBlock = null;
            mManager.unlock(this, (Lock) tailObj);
        } else {
            ((Block) tailObj).unlockLast(this);
        }
    }

    /**
     * Releases last lock acquired, within the current scope, retaining a
     * shared lock. If the last lock operation was an upgrade, for a lock not
     * immediately acquired, unlock is not allowed. Instead, an
     * IllegalStateException is thrown.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i> Also, the current
     * implementation does not accurately track scopes. It may permit an unlock operation to
     * cross a scope boundary, which has undefined behavior.
     *
     * @throws IllegalStateException if no locks held, or if too many shared
     * locks, or if unlocking a non-immediate upgrade
     */
    public final void unlockToShared() {
        Object tailObj = mTailBlock;
        if (tailObj == null) {
            throw new IllegalStateException("No locks held");
        }
        if (tailObj instanceof Lock) {
            mManager.unlockToShared(this, (Lock) tailObj);
        } else {
            ((Block) tailObj).unlockLastToShared(this);
        }
    }

    /**
     * Releases last lock acquired or upgraded, within the current scope,
     * retaining an upgradable lock.
     *
     * <p><i>Note: This method is intended for advanced use cases.</i> Also, the current
     * implementation does not accurately track scopes. It may permit an unlock operation to
     * cross a scope boundary, which has undefined behavior.
     *
     * @throws IllegalStateException if no locks held, or if last lock is shared
     */
    public final void unlockToUpgradable() {
        Object tailObj = mTailBlock;
        if (tailObj == null) {
            throw new IllegalStateException("No locks held");
        }
        if (tailObj instanceof Lock) {
            mManager.unlockToUpgradable(this, (Lock) tailObj);
        } else {
            ((Block) tailObj).unlockLastToUpgradable(this);
        }
    }

    /**
     * @return new parent scope
     */
    final ParentScope scopeEnter() {
        ParentScope parent = new ParentScope();
        parent.mParentScope = mParentScope;
        Object tailObj = mTailBlock;
        parent.mTailBlock = tailObj;
        if (tailObj instanceof Block) {
            parent.mTailBlockSize = ((Block) tailObj).mSize;
        }
        mParentScope = parent;
        return parent;
    }

    /**
     * Promote all locks acquired within this scope to the parent scope.
     */
    final void promote() {
        Object tailObj = mTailBlock;
        if (tailObj != null) {
            ParentScope parent = mParentScope;
            parent.mTailBlock = tailObj;
            if (tailObj instanceof Block) {
                parent.mTailBlockSize = ((Block) tailObj).mSize;
            }
        }
    }

    /**
     * Releases all locks held by this Locker, within the current scope. If not
     * in a scope, all held locks are released.
     */
    final void scopeUnlockAll() {
        ParentScope parent = mParentScope;
        Object parentTailObj;
        if (parent == null || (parentTailObj = parent.mTailBlock) == null) {
            // Unlock everything.
            Object tailObj = mTailBlock;
            if (tailObj instanceof Lock) {
                mManager.unlock(this, (Lock) tailObj);
                mTailBlock = null;
            } else {
                Block tail = (Block) tailObj;
                if (tail != null) {
                    do {
                        tail.unlockToSavepoint(this, 0);
                        tail = tail.pop();
                    } while (tail != null);
                    mTailBlock = null;
                }
            }
        } else if (parentTailObj instanceof Lock) {
            Object tailObj = mTailBlock;
            if (tailObj instanceof Block) {
                Block tail = (Block) tailObj;
                while (true) {
                    Block prev = tail.peek();
                    if (prev == null) {
                        tail.unlockToSavepoint(this, 1);
                        break;
                    }
                    tail.unlockToSavepoint(this, 0);
                    tail.discard();
                    tail = prev;
                }
                mTailBlock = tail;
            }
        } else {
            Block tail = (Block) mTailBlock;
            while (tail != parentTailObj) {
                tail.unlockToSavepoint(this, 0);
                tail = tail.pop();
            }
            tail.unlockToSavepoint(this, parent.mTailBlockSize);
            mTailBlock = tail;
        }
    }

    /**
     * Exits the current scope, releasing all held locks.
     *
     * @return old parent scope
     */
    final ParentScope scopeExit() {
        scopeUnlockAll();
        return popScope();
    }

    /**
     * Releases all locks held by this Locker, and exits all scopes.
     */
    final void scopeExitAll() {
        mParentScope = null;
        scopeUnlockAll();
        mTailBlock = null;
    }

    @Override
    public final int hashCode() {
        int hash = mHashCode;
        if (hash == 0) {
            // Scramble the hashcode a bit, just like HashMap does.
            hash = super.hashCode();
            hash ^= (hash >>> 20) ^ (hash >>> 12);
            return mHashCode = hash ^ (hash >>> 7) ^ (hash >>> 4);
        }
        return hash;
    }

    /**
     * @param upgrade only 0 or 1 allowed
     */
    final void push(Lock lock, int upgrade) {
        Object tailObj = mTailBlock;
        if (tailObj == null) {
            mTailBlock = upgrade == 0 ? lock : new Block(lock);
        } else if (tailObj instanceof Lock) {
            // Don't push lock upgrade if it applies to the last acquisition
            // within this scope. This is required for unlockLast.
            if (tailObj != lock || mParentScope != null) {
                mTailBlock = new Block((Lock) tailObj, lock, upgrade);
            }
        } else {
            ((Block) tailObj).pushLock(this, lock, upgrade);
        }
    }

    /**
     * @return old parent scope
     */
    private ParentScope popScope() {
        ParentScope parent = mParentScope;
        if (parent == null) {
            mTailBlock = null;
        } else {
            mTailBlock = parent.mTailBlock;
            mParentScope = parent.mParentScope;
        }
        return parent;
    }

    static final class Block {
        private static final int FIRST_BLOCK_CAPACITY = 8;
        // Limited by number of bits available in mUpgrades.
        private static final int HIGHEST_BLOCK_CAPACITY = 64;

        private Lock[] mLocks;
        private long mUpgrades;
        // Size must always be least 1.
        int mSize;

        private Block mPrev;

        // Always creates first as an upgrade.
        Block(Lock first) {
            (mLocks = new Lock[FIRST_BLOCK_CAPACITY])[0] = first;
            mUpgrades = 1;
            mSize = 1;
        }

        // First is never an upgrade.
        Block(Lock first, Lock second, int upgrade) {
            Lock[] locks = new Lock[FIRST_BLOCK_CAPACITY];
            locks[0] = first;
            locks[1] = second;
            mLocks = locks;
            mUpgrades = upgrade << 1;
            mSize = 2;
        }

        private Block(Block prev, Lock first, int upgrade) {
            mPrev = prev;
            int capacity = prev.mLocks.length;
            if (capacity < FIRST_BLOCK_CAPACITY) {
                capacity = FIRST_BLOCK_CAPACITY;
            } else if (capacity < HIGHEST_BLOCK_CAPACITY) {
                capacity <<= 1;
            }
            (mLocks = new Lock[capacity])[0] = first;
            mUpgrades = upgrade;
            mSize = 1;
        }

        void pushLock(Locker locker, Lock lock, int upgrade) {
            Lock[] locks = mLocks;
            int size = mSize;

            // Don't push lock upgrade if it applies to the last acquisition
            // within this scope. This is required for unlockLast.
            ParentScope parent;
            if (upgrade != 0
                && ((parent = locker.mParentScope) == null || parent.mTailBlockSize != size)
                && locks[size - 1] == lock)
            {
                return;
            }

            if (size < locks.length) {
                locks[size] = lock;
                mUpgrades |= ((long) upgrade) << size;
                mSize = size + 1;
            } else {
                locker.mTailBlock = new Block(this, lock, upgrade);
            }
        }

        Lock last() {
            return mLocks[mSize - 1];
        }

        void unlockLast(Locker locker) {
            int size = mSize - 1;

            long upgrades = mUpgrades;
            long mask = 1L << size;
            if ((upgrades & mask) != 0) {
                throw new IllegalStateException("Cannot unlock non-immediate upgrade");
            }

            Lock[] locks = mLocks;
            locker.mManager.unlock(locker, locks[size]);

            // Only pop lock if unlock succeeded.
            locks[size] = null;
            if (size == 0) {
                locker.mTailBlock = mPrev;
                mPrev = null;
            } else {
                mUpgrades &= upgrades & ~mask;
                mSize = size;
            }
        }

        void unlockLastToShared(Locker locker) {
            int size = mSize - 1;
            if ((mUpgrades & (1L << size)) != 0) {
                throw new IllegalStateException("Cannot unlock non-immediate upgrade");
            }
            locker.mManager.unlockToShared(locker, mLocks[size]);
        }

        void unlockLastToUpgradable(Locker locker) {
            Lock[] locks = mLocks;
            int size = mSize;
            locker.mManager.unlockToUpgradable(locker, locks[--size]);

            long upgrades = mUpgrades;
            long mask = 1L << size;
            if ((upgrades & mask) != 0) {
                // Pop upgrade off stack, but only if unlock succeeded.
                locks[size] = null;
                if (size == 0) {
                    locker.mTailBlock = mPrev;
                    mPrev = null;
                } else {
                    mUpgrades = upgrades & ~mask;
                    mSize = size;
                }
            }
        }

        /**
         * Note: If target size is zero, caller MUST pop and discard the block. Otherwise, the
         * block size will be zero, which is illegal.
         */
        void unlockToSavepoint(Locker locker, int targetSize) {
            int size = mSize;
            if (size > targetSize) {
                Lock[] locks = mLocks;
                LockManager manager = locker.mManager;
                size--;
                long mask = 1L << size;
                long upgrades = mUpgrades;
                while (true) {
                    Lock lock = locks[size];
                    if ((upgrades & mask) != 0) {
                        manager.unlockToUpgradable(locker, lock);
                    } else {
                        manager.unlock(locker, lock);
                    }
                    locks[size] = null;
                    if (size == targetSize) {
                        break;
                    }
                    size--;
                    mask >>>= 1;
                }
                mUpgrades = upgrades & ~(~0L << size);
                mSize = size;
            }
        }

        Block pop() {
            Block prev = mPrev;
            mPrev = null;
            return prev;
        }

        Block peek() {
            return mPrev;
        }

        void discard() {
            mPrev = null;
        }
    }

    static final class ParentScope {
        ParentScope mParentScope;
        Object mTailBlock;
        // Must be zero if tail is not a block.
        int mTailBlockSize;

        // These fields are used by Transaction.
        LockMode mLockMode;
        long mLockTimeoutNanos;
        int mHasState;
        long mSavepoint;
    }
}
