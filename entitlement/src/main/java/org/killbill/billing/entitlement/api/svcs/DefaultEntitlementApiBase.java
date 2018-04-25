/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.entitlement.api.svcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.AccountEntitlements;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.DefaultEntitlementContext;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.EntitlementDateHelper;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution.WithEntitlementPlugin;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.entitlement.plugin.api.EntitlementContext;
import org.killbill.billing.entitlement.plugin.api.OperationType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class DefaultEntitlementApiBase {

    protected static final Logger log = LoggerFactory.getLogger(DefaultEntitlementApiBase.class);

    protected final EntitlementApi entitlementApi;
    protected final AccountInternalApi accountApi;

    protected final SubscriptionBaseInternalApi subscriptionInternalApi;
    protected final Clock clock;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final BlockingChecker checker;
    protected final BlockingStateDao blockingStateDao;
    protected final EntitlementDateHelper dateHelper;
    protected final EventsStreamBuilder eventsStreamBuilder;
    protected final EntitlementUtils entitlementUtils;
    protected final NotificationQueueService notificationQueueService;
    protected final EntitlementPluginExecution pluginExecution;
    protected final SecurityApi securityApi;
    protected final PersistentBus eventBus;

    protected DefaultEntitlementApiBase(final PersistentBus eventBus,
                                        @Nullable final EntitlementApi entitlementApi, final EntitlementPluginExecution pluginExecution,
                                        final InternalCallContextFactory internalCallContextFactory,
                                        final SubscriptionBaseInternalApi subscriptionInternalApi,
                                        final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock,
                                        final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                                        final EventsStreamBuilder eventsStreamBuilder, final EntitlementUtils entitlementUtils, final SecurityApi securityApi) {
        this.eventBus = eventBus;
        this.entitlementApi = entitlementApi != null ? entitlementApi : (EntitlementApi) this;
        this.accountApi = accountApi;
        this.pluginExecution = pluginExecution;
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.notificationQueueService = notificationQueueService;
        this.eventsStreamBuilder = eventsStreamBuilder;
        this.entitlementUtils = entitlementUtils;
        this.securityApi = securityApi;
        this.dateHelper = new EntitlementDateHelper();
    }

    public AccountEntitlements getAllEntitlementsForAccount(final InternalTenantContext tenantContext) throws EntitlementApiException {
        final AccountEventsStreams accountEventsStreams = eventsStreamBuilder.buildForAccount(tenantContext);

        final Map<UUID, Collection<Entitlement>> entitlementsPerBundle = new HashMap<UUID, Collection<Entitlement>>();
        for (final UUID bundleId : accountEventsStreams.getEventsStreams().keySet()) {
            if (entitlementsPerBundle.get(bundleId) == null) {
                entitlementsPerBundle.put(bundleId, new LinkedList<Entitlement>());
            }

            for (final EventsStream eventsStream : accountEventsStreams.getEventsStreams().get(bundleId)) {
                final Entitlement entitlement = new DefaultEntitlement(eventsStream, eventsStreamBuilder, entitlementApi, pluginExecution,
                                                                       blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
                                                                       entitlementUtils, dateHelper, clock, securityApi, tenantContext, internalCallContextFactory);
                entitlementsPerBundle.get(bundleId).add(entitlement);
            }
        }

        return new DefaultAccountEntitlements(accountEventsStreams, entitlementsPerBundle);
    }

    public Entitlement getEntitlementForId(final UUID entitlementId, final InternalTenantContext tenantContext) throws EntitlementApiException {
        final EventsStream eventsStream = eventsStreamBuilder.buildForEntitlement(entitlementId, tenantContext);
        return new DefaultEntitlement(eventsStream, eventsStreamBuilder, entitlementApi, pluginExecution,
                                      blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
                                      entitlementUtils, dateHelper, clock, securityApi, tenantContext, internalCallContextFactory);
    }

    public void pause(final UUID bundleId, @Nullable final LocalDate localEffectiveDate, final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext) throws EntitlementApiException {

        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(
                bundleId,
                null,
                null,
                localEffectiveDate,
                localEffectiveDate,
                false);
        final List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifier);
        final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.PAUSE_BUNDLE,
                                                                               null,
                                                                               null,
                                                                               null,
                                                                               null,
                                                                               properties,
                                                                               internalCallContextFactory.createCallContext(internalCallContext));

        final WithEntitlementPlugin<Void> pauseWithPlugin = new WithEntitlementPlugin<Void>() {
            @Override
            public Void doCall(final EntitlementApi entitlementApi, final EntitlementContext updatedPluginContext) throws EntitlementApiException {
                try {
                    final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, internalCallContext);
                    blockUnblockBundle(bundleId, DefaultEntitlementApi.ENT_STATE_BLOCKED, EntitlementService.ENTITLEMENT_SERVICE_NAME, localEffectiveDate, true, true, true, baseSubscription, internalCallContext);
                } catch (SubscriptionBaseApiException e) {
                    throw new EntitlementApiException(e);
                }
                return null;
            }
        };
        pluginExecution.executeWithPlugin(pauseWithPlugin, pluginContext);
    }

    public void resume(final UUID bundleId, @Nullable final LocalDate localEffectiveDate, final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext) throws EntitlementApiException {

        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(
                bundleId,
                null,
                null,
                localEffectiveDate,
                localEffectiveDate,
                false);
        final List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifier);
        final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.RESUME_BUNDLE,
                                                                               null,
                                                                               null,
                                                                               baseEntitlementWithAddOnsSpecifierList,
                                                                               null,
                                                                               properties,
                                                                               internalCallContextFactory.createCallContext(internalCallContext));
        final WithEntitlementPlugin<Void> resumeWithPlugin = new WithEntitlementPlugin<Void>() {
            @Override
            public Void doCall(final EntitlementApi entitlementApi, final EntitlementContext updatedPluginContext) throws EntitlementApiException {
                try {
                    final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, internalCallContext);
                    blockUnblockBundle(bundleId, DefaultEntitlementApi.ENT_STATE_CLEAR, EntitlementService.ENTITLEMENT_SERVICE_NAME, localEffectiveDate, false, false, false, baseSubscription, internalCallContext);
                } catch (SubscriptionBaseApiException e) {
                    throw new EntitlementApiException(e);
                }
                return null;
            }
        };
        pluginExecution.executeWithPlugin(resumeWithPlugin, pluginContext);
    }

    private UUID blockUnblockBundle(final UUID bundleId, final String stateName, final String serviceName, @Nullable final LocalDate localEffectiveDate, boolean blockBilling, boolean blockEntitlement, boolean blockChange, @Nullable final SubscriptionBase inputBaseSubscription, final InternalCallContext internalCallContext)
            throws EntitlementApiException {
        final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(localEffectiveDate, internalCallContext.getCreatedDate(), internalCallContext);
        final BlockingState state = new DefaultBlockingState(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, stateName, serviceName, blockChange, blockEntitlement, blockBilling, effectiveDate);
        entitlementUtils.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableList.<BlockingState>of(state), bundleId, internalCallContext);
        return state.getId();
    }
}
