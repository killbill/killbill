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

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class MySqlGlobalLocker implements GlobalLocker {

    private static final Logger logger = LoggerFactory.getLogger(MySqlGlobalLocker.class);

    private static final long DEFAULT_TIMEOUT = 5L; // 5 seconds

    private final IDBI dbi;
    private long timeout;

    @Inject
    public MySqlGlobalLocker(final IDBI dbi) {
        this.dbi = dbi;
        this.timeout = DEFAULT_TIMEOUT;
    }

    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }

    @Override
    public GlobalLock lockWithNumberOfTries(final LockerType service, final String lockKey, final int retry) {

        final String lockName = getLockName(service, lockKey);
        int tries_left = retry;
        while (tries_left-- > 0) {
            final GlobalLock lock = lock(lockName);
            if (lock != null) {
                return lock;
            }
        }
        logger.error(String.format("Failed to acquire lock %s for service %s after %d retry", lockKey, service, retry));
        throw new LockFailedException();
    }

    private GlobalLock lock(final String lockName) throws LockFailedException {

        final Handle h = dbi.open();
        final MySqlGlobalLockerDao dao = h.attach(MySqlGlobalLockerDao.class);

        final boolean obtained = dao.lock(lockName, timeout);
        if (obtained) {
            return new GlobalLock() {
                @Override
                public void release() {
                    try {
                        dao.releaseLock(lockName);
                    } finally {
                        if (h != null) {
                            h.close();
                        }
                    }
                }
            };
        } else {
            return null;
        }
    }

    @Override
    public Boolean isFree(final LockerType service, final String lockKey) {

        final String lockName = getLockName(service, lockKey);
        final Handle h = dbi.open();
        try {
            final MySqlGlobalLockerDao dao = h.attach(MySqlGlobalLockerDao.class);
            return dao.isFree(lockName);
        } finally {
            if (h != null) {
                h.close();
            }
        }
    }

    private String getLockName(final LockerType service, final String lockKey) {
        final StringBuilder tmp = new StringBuilder()
                .append(service.toString())
                .append("-")
                .append(lockKey);
        return tmp.toString();
    }
}
