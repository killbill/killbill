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

import org.killbill.billing.util.glue.KillbillApiAopModule;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBRouterUntyped {

    private static final Logger logger = LoggerFactory.getLogger(DBRouterUntyped.class);

    protected final IDBI dbi;
    protected final IDBI roDbi;

    public DBRouterUntyped(final IDBI dbi, final IDBI roDbi) {
        this.dbi = dbi;
        this.roDbi = roDbi;
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

    boolean shouldUseRODBI(final boolean requestedRO) {
        if (!requestedRO) {
            KillbillApiAopModule.setDirtyDBFlag();
            logger.debug("Dirty flag set, using RW DBI");
            return false;
        } else {
            if (KillbillApiAopModule.getDirtyDBFlag()) {
                // Redirect to the rw instance, to work-around any replication delay
                logger.debug("RO DBI handle requested, but dirty flag set, using RW DBI");
                return false;
            } else {
                logger.debug("Using RO DBI");
                return true;
            }
        }
    }
}
