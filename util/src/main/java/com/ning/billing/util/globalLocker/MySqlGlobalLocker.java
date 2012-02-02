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

import com.google.inject.Inject;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;

public class MySqlGlobalLocker implements GlobalLocker {
    private final IDBI dbi;
    private long timeout;

    @Inject
    public MySqlGlobalLocker(IDBI dbi)
    {
        this.dbi = dbi;
        this.timeout = 1000L;
    }

    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }

    @Override
    public GlobalLock lockWithNumberOfTries(final String lockName, final int i)
    {
        int tries_left = i;
        while (tries_left-- > 0) {
            GlobalLock lock = lock(lockName);
            if (lock != null) {
                return lock;
            }
        }
        throw new LockFailedException();
    }

    private GlobalLock lock(final String lockName) throws LockFailedException
    {
        final Handle h = dbi.open();
        final MySqlGlobalLockerDao dao = h.attach(MySqlGlobalLockerDao.class);

        final boolean obtained = dao.lock(lockName, timeout);
        if (obtained) {
            return new GlobalLock() {
                public void release()
                {
                    try {
                        dao.releaseLock(lockName);
                    }
                    finally {
                        h.close();
                    }
                }
            };
        }
        else {
            return null;
        }
    }
}
