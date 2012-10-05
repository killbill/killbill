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
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.entitlement.ChargeThruInternalApi;

import com.google.inject.Inject;

public class DefaultChargeThruApi implements ChargeThruInternalApi {

    private final EntitlementDao entitlementDao;
    private final SubscriptionFactory subscriptionFactory;

    @Inject
    public DefaultChargeThruApi(final SubscriptionFactory subscriptionFactory, final EntitlementDao dao) {
        this.subscriptionFactory = subscriptionFactory;
        this.entitlementDao = dao;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) {
        return entitlementDao.getAccountIdFromSubscriptionId(subscriptionId, context);
    }

    @Override
    public void setChargedThroughDate(final UUID subscriptionId, final LocalDate ctd, final InternalCallContext context) {
        final SubscriptionData subscription = (SubscriptionData) entitlementDao.getSubscriptionFromId(subscriptionFactory, subscriptionId, context);
        final DateTime chargedThroughDate = ctd.toDateTime(new LocalTime(subscription.getStartDate(), DateTimeZone.UTC), DateTimeZone.UTC);
        final SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
                .setChargedThroughDate(chargedThroughDate)
                .setPaidThroughDate(subscription.getPaidThroughDate());
        entitlementDao.updateChargedThroughDate(new SubscriptionData(builder), context);
    }
}
