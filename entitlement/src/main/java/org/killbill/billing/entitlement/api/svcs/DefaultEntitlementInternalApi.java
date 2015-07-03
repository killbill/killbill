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

package org.killbill.billing.entitlement.api.svcs;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.AccountEntitlements;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.EntitlementInternalApi;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.EntitlementDateHelper;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultEntitlementInternalApi implements EntitlementInternalApi {

    private final EntitlementApi entitlementApi;
    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;
    private final BlockingChecker checker;
    private final BlockingStateDao blockingStateDao;
    private final EntitlementDateHelper dateHelper;
    private final EventsStreamBuilder eventsStreamBuilder;
    private final EntitlementUtils entitlementUtils;
    private final NotificationQueueService notificationQueueService;
    private final EntitlementPluginExecution pluginExecution;

    @Inject
    public DefaultEntitlementInternalApi(final EntitlementApi entitlementApi, final EntitlementPluginExecution pluginExecution,
                                         final InternalCallContextFactory internalCallContextFactory,
                                         final SubscriptionBaseInternalApi subscriptionInternalApi,
                                         final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock,
                                         final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                                         final EventsStreamBuilder eventsStreamBuilder, final EntitlementUtils entitlementUtils) {
        this.entitlementApi = entitlementApi;
        this.pluginExecution= pluginExecution;
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.notificationQueueService = notificationQueueService;
        this.eventsStreamBuilder = eventsStreamBuilder;
        this.entitlementUtils = entitlementUtils;
        this.dateHelper = new EntitlementDateHelper(accountApi, clock);
    }

    @Override
    public AccountEntitlements getAllEntitlementsForAccountId(final UUID accountId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);

        final AccountEventsStreams accountEventsStreams = eventsStreamBuilder.buildForAccount(context);

        final Map<UUID, Collection<Entitlement>> entitlementsPerBundle = new HashMap<UUID, Collection<Entitlement>>();
        for (final UUID bundleId : accountEventsStreams.getEventsStreams().keySet()) {
            if (entitlementsPerBundle.get(bundleId) == null) {
                entitlementsPerBundle.put(bundleId, new LinkedList<Entitlement>());
            }

            for (final EventsStream eventsStream : accountEventsStreams.getEventsStreams().get(bundleId)) {
                final Entitlement entitlement = new DefaultEntitlement(eventsStream, eventsStreamBuilder, entitlementApi, pluginExecution,
                                                                       blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
                                                                       entitlementUtils, dateHelper, clock, internalCallContextFactory);
                entitlementsPerBundle.get(bundleId).add(entitlement);
            }
        }

        return new DefaultAccountEntitlements(accountEventsStreams, entitlementsPerBundle);
    }
}
