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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.EntitlementApiBase;
import com.ning.billing.entitlement.api.user.DefaultEffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionApiService;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultEntitlementInternalApi extends EntitlementApiBase implements EntitlementInternalApi {

    private final Logger log = LoggerFactory.getLogger(DefaultEntitlementInternalApi.class);


    @Inject
    public DefaultEntitlementInternalApi(final EntitlementDao dao,
                                         final DefaultSubscriptionApiService apiService,
                                         final Clock clock,
                                         final CatalogService catalogService) {
        super(dao, apiService, clock, catalogService);
    }

    @Override
    public List<SubscriptionBundle> getBundlesForAccount(final UUID accountId, final InternalTenantContext context) {
        return dao.getSubscriptionBundleForAccount(accountId, context);
    }

    @Override
    public List<Subscription> getSubscriptionsForBundle(UUID bundleId,
                                                        InternalTenantContext context) {
        final List<Subscription> internalSubscriptions = dao.getSubscriptions(bundleId, context);
        return createSubscriptionsForApiUse(internalSubscriptions);
    }

    @Override
    public Subscription getBaseSubscription(UUID bundleId,
                                            InternalTenantContext context) throws EntitlementUserApiException {
        final Subscription result = dao.getBaseSubscription(bundleId, context);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_GET_NO_SUCH_BASE_SUBSCRIPTION, bundleId);
        }
        return createSubscriptionForApiUse(result);
    }

    @Override

    public Subscription getSubscriptionFromId(UUID id,
                                              InternalTenantContext context) throws EntitlementUserApiException {
        final Subscription result = dao.getSubscriptionFromId(id, context);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_SUBSCRIPTION_ID, id);
        }
        return createSubscriptionForApiUse(result);
    }

    @Override
    public SubscriptionBundle getBundleFromId(final UUID id, final InternalTenantContext context) throws EntitlementUserApiException {
        final SubscriptionBundle result = dao.getSubscriptionBundleFromId(id, context);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_GET_INVALID_BUNDLE_ID, id.toString());
        }
        return result;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) throws EntitlementUserApiException {
        return dao.getAccountIdFromSubscriptionId(subscriptionId, context);
    }

    @Override
    public void setChargedThroughDate(UUID subscriptionId,
                                      DateTime chargedThruDate, InternalCallContext context) {
        final SubscriptionData subscription = (SubscriptionData) dao.getSubscriptionFromId(subscriptionId, context);
        final SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
                .setChargedThroughDate(chargedThruDate)
                .setPaidThroughDate(subscription.getPaidThroughDate());

        dao.updateChargedThroughDate(new SubscriptionData(builder), context);
    }

    @Override
    public List<EffectiveSubscriptionInternalEvent> getAllTransitions(final Subscription subscription, final InternalTenantContext context) {
        final List<SubscriptionTransitionData> transitions = ((SubscriptionData) subscription).getAllTransitions();
        return convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(subscription, context, transitions);
    }

    @Override
    public List<EffectiveSubscriptionInternalEvent> getBillingTransitions(final Subscription subscription, final InternalTenantContext context) {
        final List<SubscriptionTransitionData> transitions = ((SubscriptionData) subscription).getBillingTransitions();
        return convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(subscription, context, transitions);
    }

    private List<EffectiveSubscriptionInternalEvent> convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(final Subscription subscription,
                                                                                                                          final InternalTenantContext context, final List<SubscriptionTransitionData> transitions) {
        return ImmutableList.<EffectiveSubscriptionInternalEvent>copyOf(Collections2.transform(transitions, new Function<SubscriptionTransitionData, EffectiveSubscriptionInternalEvent>() {
            @Override
            @Nullable
            public EffectiveSubscriptionInternalEvent apply(@Nullable SubscriptionTransitionData input) {
                return new DefaultEffectiveSubscriptionEvent(input, ((SubscriptionData) subscription).getAlignStartDate(), null, context.getAccountRecordId(), context.getTenantRecordId());
            }
        }));
    }
}
