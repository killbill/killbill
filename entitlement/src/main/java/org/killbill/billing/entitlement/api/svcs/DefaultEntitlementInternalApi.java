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

import javax.inject.Inject;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.entitlement.EntitlementInternalApi;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;

public class DefaultEntitlementInternalApi extends DefaultEntitlementApiBase implements EntitlementInternalApi {

    @Inject
    public DefaultEntitlementInternalApi(final PersistentBus eventBus,
                                         final EntitlementApi entitlementApi, final EntitlementPluginExecution pluginExecution,
                                         final InternalCallContextFactory internalCallContextFactory,
                                         final SubscriptionBaseInternalApi subscriptionInternalApi,
                                         final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock,
                                         final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                                         final EventsStreamBuilder eventsStreamBuilder, final EntitlementUtils entitlementUtils, final SecurityApi securityApi) {
        super(eventBus, entitlementApi, pluginExecution, internalCallContextFactory, subscriptionInternalApi, accountApi, blockingStateDao, clock, checker, notificationQueueService, eventsStreamBuilder, entitlementUtils, securityApi);
    }

}
