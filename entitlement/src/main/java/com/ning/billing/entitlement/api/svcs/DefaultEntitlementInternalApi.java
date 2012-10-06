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
package com.ning.billing.entitlement.api.svcs;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.Inject;

public class DefaultEntitlementInternalApi implements EntitlementInternalApi {

    private final EntitlementDao dao;
    private final SubscriptionFactory subscriptionFactory;
    @Inject
    public DefaultEntitlementInternalApi(final EntitlementDao dao,
            final SubscriptionFactory subscriptionFactory) {
        this.dao = dao;
        this.subscriptionFactory = subscriptionFactory;
    }

    @Override
    public List<SubscriptionBundle> getBundlesForAccount(UUID accountId,
            InternalTenantContext context) {
        return dao.getSubscriptionBundleForAccount(accountId, context);
    }

    @Override
    public List<Subscription> getSubscriptionsForBundle(UUID bundleId,
            InternalTenantContext context) {
        return dao.getSubscriptions(subscriptionFactory, bundleId, context);
    }

    @Override
    public Subscription getBaseSubscription(UUID bundleId,
            InternalTenantContext context) throws EntitlementUserApiException {
        final Subscription result = dao.getBaseSubscription(subscriptionFactory, bundleId, context);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_GET_NO_SUCH_BASE_SUBSCRIPTION, bundleId);
        }
        return result;
    }

    @Override
    public Subscription getSubscriptionFromId(UUID id,
            InternalTenantContext context) throws EntitlementUserApiException {
        final Subscription result = dao.getSubscriptionFromId(subscriptionFactory, id, context);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_SUBSCRIPTION_ID, id);
        }
        return result;
    }

    @Override
    public SubscriptionBundle getBundleFromId(UUID id,
            InternalTenantContext context) throws EntitlementUserApiException {
        final SubscriptionBundle result = dao.getSubscriptionBundleFromId(id, context);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_GET_INVALID_BUNDLE_ID, id.toString());
        }
        return result;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId,
            InternalTenantContext context)
            throws EntitlementUserApiException {
        return dao.getAccountIdFromSubscriptionId(subscriptionId, context);
    }

    @Override
    public void setChargedThroughDate(UUID subscriptionId,
            LocalDate localChargedThruDate, InternalCallContext context) {
        final SubscriptionData subscription = (SubscriptionData) dao.getSubscriptionFromId(subscriptionFactory, subscriptionId, context);
        final DateTime chargedThroughDate = localChargedThruDate.toDateTime(new LocalTime(subscription.getStartDate(), DateTimeZone.UTC), DateTimeZone.UTC);
        final SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
                .setChargedThroughDate(chargedThroughDate)
                .setPaidThroughDate(subscription.getPaidThroughDate());
        dao.updateChargedThroughDate(new SubscriptionData(builder), context);
    }
}
