/*
w * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.entitlement.api.billing;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.inject.Inject;

public class DefaultChargeThruApi implements ChargeThruApi {

    private final EntitlementDao entitlementDao;
    private final SubscriptionFactory subscriptionFactory;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultChargeThruApi(final SubscriptionFactory subscriptionFactory, final EntitlementDao dao, final InternalCallContextFactory internalCallContextFactory) {
        this.subscriptionFactory = subscriptionFactory;
        this.entitlementDao = dao;
        // TODO remove - internal API, should use InternalCallContext directly
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final TenantContext context) {
        return entitlementDao.getAccountIdFromSubscriptionId(subscriptionId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void setChargedThroughDate(final UUID subscriptionId, final LocalDate ctd, final CallContext context) {
        final SubscriptionData subscription = (SubscriptionData) entitlementDao.getSubscriptionFromId(subscriptionFactory, subscriptionId, internalCallContextFactory.createInternalTenantContext(context));
        final DateTime chargedThroughDate = ctd.toDateTime(new LocalTime(subscription.getStartDate(), DateTimeZone.UTC), DateTimeZone.UTC);
        final SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
                .setChargedThroughDate(chargedThroughDate)
                .setPaidThroughDate(subscription.getPaidThroughDate());
        entitlementDao.updateChargedThroughDate(new SubscriptionData(builder), internalCallContextFactory.createInternalCallContext(context));
    }
}
