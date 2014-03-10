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

package org.killbill.billing.usage.api.user;

import java.math.BigDecimal;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;

import org.killbill.billing.ObjectType;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.usage.dao.RolledUpUsageDao;
import org.killbill.billing.usage.dao.RolledUpUsageModelDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultUsageUserApi implements UsageUserApi {

    private final RolledUpUsageDao rolledUpUsageDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultUsageUserApi(final RolledUpUsageDao rolledUpUsageDao,
                               final InternalCallContextFactory internalCallContextFactory) {
        this.rolledUpUsageDao = rolledUpUsageDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void recordRolledUpUsage(final UUID subscriptionId, final String unitType, final DateTime startTime, final DateTime endTime,
                                    final BigDecimal amount, final CallContext context) {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(subscriptionId, ObjectType.SUBSCRIPTION, context);
        rolledUpUsageDao.record(subscriptionId, unitType, startTime, endTime, amount, internalCallContext);
    }

    @Override
    public RolledUpUsage getUsageForSubscription(final UUID subscriptionId, final TenantContext context) {
        final RolledUpUsageModelDao usageForSubscription = rolledUpUsageDao.getUsageForSubscription(subscriptionId, internalCallContextFactory.createInternalTenantContext(context));
        return new DefaultRolledUpUsage(usageForSubscription);
    }
}
