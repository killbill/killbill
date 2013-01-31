/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.usage.dao;

import java.math.BigDecimal;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

public class DefaultRolledUpUsageDao implements RolledUpUsageDao {

    private final RolledUpUsageSqlDao rolledUpUsageSqlDao;

    @Inject
    public DefaultRolledUpUsageDao(final IDBI dbi) {
        this.rolledUpUsageSqlDao = dbi.onDemand(RolledUpUsageSqlDao.class);
    }

    @Override
    public void record(final UUID subscriptionId, final String unitType, final DateTime startTime, final DateTime endTime,
                       final BigDecimal amount, final InternalCallContext context) {
        final RolledUpUsageModelDao rolledUpUsageModelDao = new RolledUpUsageModelDao(subscriptionId, unitType, startTime,
                                                                                      endTime, amount
        );
        rolledUpUsageSqlDao.create(rolledUpUsageModelDao, context);
    }

    @Override
    public RolledUpUsageModelDao getUsageForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        return rolledUpUsageSqlDao.getUsageForSubscription(subscriptionId, context);
    }
}
