/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.usage.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.util.callcontext.TenantContext;
import org.skife.jdbi.v2.IDBI;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;

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
    public RolledUpUsageModelDao getUsageForSubscription(UUID subscriptionId, DateTime startTime, DateTime endTime, String unitType, InternalTenantContext context) {
        final BigDecimal amount = rolledUpUsageSqlDao.getUsageForSubscription(subscriptionId, startTime.toDate(), endTime.toDate(), unitType, context);
        return new RolledUpUsageModelDao(subscriptionId, unitType, startTime, endTime, amount != null ? amount : BigDecimal.ZERO);
    }
}
