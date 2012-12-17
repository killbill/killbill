/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.util.globallocker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockGlobalLocker implements GlobalLocker {

    private final Map<String, AtomicBoolean> locks = new ConcurrentHashMap<String, AtomicBoolean>();

    @Override
    public GlobalLock lockWithNumberOfTries(final LockerType service,
                                            final String lockKey,
                                            final int retry) {
        final String lockName = getLockName(service, lockKey);
        int tries_left = retry;
        while (tries_left-- > 0) {
            final GlobalLock lock = lock(lockName);
            if (lock != null) {
                return lock;
            }
        }
        throw new LockFailedException();
    }

    private synchronized GlobalLock lock(final String lockName) throws LockFailedException {
        if (!isFree(lockName)) {
            return null;
        }

        if (locks.get(lockName) == null) {
            locks.put(lockName, new AtomicBoolean(true));
        } else {
            locks.get(lockName).set(true);
        }

        return new GlobalLock() {
            @Override
            public void release() {
                locks.get(lockName).set(false);
            }
        };
    }

    @Override
    public synchronized Boolean isFree(final LockerType service, final String lockKey) {
        final String lockName = getLockName(service, lockKey);
        return isFree(lockName);
    }

    private synchronized Boolean isFree(final String lockName) {
        final AtomicBoolean lock = locks.get(lockName);
        return lock == null || !lock.get();
    }

    private String getLockName(final LockerType service, final String lockKey) {
        return String.format("%s-%s", service.toString(), lockKey);
    }
}
