/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.entity.dao;

import org.killbill.commons.profiling.Profiling.WithProfilingCallback;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import static org.killbill.billing.util.entity.dao.DBRouterUntyped.THREAD_STATE.RO_ALLOWED;
import static org.killbill.billing.util.entity.dao.DBRouterUntyped.THREAD_STATE.RW_ONLY;

public class DBRouterUntyped {

    private static final Logger logger = LoggerFactory.getLogger(DBRouterUntyped.class);

    private static final ThreadLocal<THREAD_STATE> CURRENT_THREAD_STATE = new ThreadLocal<THREAD_STATE>() {
        @Override
        public THREAD_STATE initialValue() {
            return RW_ONLY;
        }
    };

    protected final IDBI dbi;
    protected final IDBI roDbi;

    public DBRouterUntyped(final IDBI dbi, final IDBI roDbi) {
        this.dbi = dbi;
        this.roDbi = roDbi;
    }

    public static Object withRODBIAllowed(final boolean allowRODBI,
                                          final WithProfilingCallback<Object, Throwable> callback) throws Throwable {
        final THREAD_STATE currentState = getCurrentState();
        CURRENT_THREAD_STATE.set(allowRODBI ? RO_ALLOWED : RW_ONLY);

        try {
            return callback.execute();
        } finally {
            CURRENT_THREAD_STATE.set(currentState);
        }
    }

    @VisibleForTesting
    public static THREAD_STATE getCurrentState() {
        return CURRENT_THREAD_STATE.get();
    }

    boolean shouldUseRODBI(final boolean requestedRO) {
        if (requestedRO) {
            if (isRODBIAllowed()) {
                logger.debug("Using RO DBI");
                return true;
            } else {
                // Redirect to the rw instance, to work-around any replication delay
                logger.debug("RO DBI requested, but thread state is {}, using RW DBI", getCurrentState());
                return false;
            }
        } else {
            // Disable RO DBI for future calls in this thread
            disallowRODBI();
            logger.debug("Using RW DBI");
            return false;
        }
    }

    private boolean isRODBIAllowed() {
        return getCurrentState() == RO_ALLOWED;
    }

    private void disallowRODBI() {
        CURRENT_THREAD_STATE.set(RW_ONLY);
    }

    public Handle getHandle(final boolean requestedRO) {
        if (shouldUseRODBI(requestedRO)) {
            return roDbi.open();
        } else {
            return dbi.open();
        }
    }

    public <T> T onDemand(final boolean requestedRO, final Class<T> sqlObjectType) {
        if (shouldUseRODBI(requestedRO)) {
            return roDbi.onDemand(sqlObjectType);
        } else {
            return dbi.onDemand(sqlObjectType);
        }
    }

    public <T> T inTransaction(final boolean requestedRO, final TransactionCallback<T> callback) {
        if (shouldUseRODBI(requestedRO)) {
            return roDbi.inTransaction(callback);
        } else {
            return dbi.inTransaction(callback);
        }
    }

    public enum THREAD_STATE {
        // Advisory that RO DBI can be used
        RO_ALLOWED,
        // Dirty flag, calls must go to RW DBI
        RW_ONLY
    }
}
