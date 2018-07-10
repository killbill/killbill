/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.entitlement.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution.WithEntitlementPlugin;
import org.killbill.billing.entitlement.api.svcs.DefaultEntitlementApiBase;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.block.BlockingChecker.BlockingAggregator;
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
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOnsSpecifier;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import static org.killbill.billing.entitlement.logging.EntitlementLoggingHelper.logCreateEntitlementsWithAOs;
import static org.killbill.billing.entitlement.logging.EntitlementLoggingHelper.logPauseResumeEntitlement;
import static org.killbill.billing.entitlement.logging.EntitlementLoggingHelper.logTransferEntitlement;

public class DefaultEntitlementApi extends DefaultEntitlementApiBase implements EntitlementApi {


    public static final String ENT_STATE_START = "ENT_STARTED";
    public static final String ENT_STATE_BLOCKED = "ENT_BLOCKED";
    public static final String ENT_STATE_CLEAR = "ENT_CLEAR";
    public static final String ENT_STATE_CANCELLED = "ENT_CANCELLED";

    private final SubscriptionBaseInternalApi subscriptionBaseInternalApi;
    private final SubscriptionBaseTransferApi subscriptionBaseTransferApi;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;
    private final BlockingChecker checker;
    private final BlockingStateDao blockingStateDao;
    private final EntitlementDateHelper dateHelper;
    private final EventsStreamBuilder eventsStreamBuilder;
    private final EntitlementUtils entitlementUtils;
    private final NotificationQueueService notificationQueueService;
    private final EntitlementPluginExecution pluginExecution;
    private final SecurityApi securityApi;
    private final CatalogInternalApi catalogInternalApi;

    @Inject
    public DefaultEntitlementApi(final PersistentBus eventBus, final InternalCallContextFactory internalCallContextFactory,
                                 final SubscriptionBaseTransferApi subscriptionTransferApi, final SubscriptionBaseInternalApi subscriptionInternalApi,
                                 final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock,
                                 final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                                 final EventsStreamBuilder eventsStreamBuilder, final EntitlementUtils entitlementUtils,
                                 final EntitlementPluginExecution pluginExecution,
                                 final CatalogInternalApi catalogInternalApi,
                                 final SecurityApi securityApi) {
        super(eventBus, null, pluginExecution, internalCallContextFactory, subscriptionInternalApi, accountApi, blockingStateDao, clock, checker, notificationQueueService, eventsStreamBuilder, entitlementUtils, securityApi);
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionBaseInternalApi = subscriptionInternalApi;
        this.subscriptionBaseTransferApi = subscriptionTransferApi;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.notificationQueueService = notificationQueueService;
        this.eventsStreamBuilder = eventsStreamBuilder;
        this.entitlementUtils = entitlementUtils;
        this.pluginExecution = pluginExecution;
        this.securityApi = securityApi;
        this.catalogInternalApi = catalogInternalApi;
        this.dateHelper = new EntitlementDateHelper();
    }

    @Override
    public UUID createBaseEntitlement(final UUID accountId, final EntitlementSpecifier entitlementSpecifier, final String externalKey,
                                      @Nullable final LocalDate entitlementEffectiveDate, @Nullable final LocalDate billingEffectiveDate, final boolean isMigrated, final boolean renameCancelledBundleIfExist,
                                      final Iterable<PluginProperty> properties, final CallContext callContext) throws EntitlementApiException {
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null,
                                                                                                                                    externalKey,
                                                                                                                                    ImmutableList.<EntitlementSpecifier>of(entitlementSpecifier),
                                                                                                                                    entitlementEffectiveDate,
                                                                                                                                    billingEffectiveDate,
                                                                                                                                    isMigrated);
        final List<UUID> createdEntitlements = createBaseEntitlementsWithAddOns(OperationType.CREATE_SUBSCRIPTION,
                                                                                accountId,
                                                                                ImmutableList.<BaseEntitlementWithAddOnsSpecifier>of(baseEntitlementWithAddOnsSpecifier),
                                                                                renameCancelledBundleIfExist,
                                                                                properties,
                                                                                callContext);
        return createdEntitlements.get(0);
    }

    @Override
    public List<UUID> createBaseEntitlementsWithAddOns(final UUID accountId, final Iterable<BaseEntitlementWithAddOnsSpecifier> originalBaseEntitlementWithAddOnsSpecifiers, final boolean renameCancelledBundleIfExist, final Iterable<PluginProperty> properties, final CallContext callContext) throws EntitlementApiException {
        return createBaseEntitlementsWithAddOns(OperationType.CREATE_SHOPPING_CART_SUBSCRIPTIONS,
                                                accountId,
                                                originalBaseEntitlementWithAddOnsSpecifiers,
                                                renameCancelledBundleIfExist,
                                                properties,
                                                callContext);
    }

    @Override
    public UUID addEntitlement(final UUID bundleId, final EntitlementSpecifier entitlementSpecifier, @Nullable final LocalDate entitlementEffectiveDate, @Nullable final LocalDate billingEffectiveDate,
                               final boolean isMigrated, final Iterable<PluginProperty> properties, final CallContext callContext) throws EntitlementApiException {
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(bundleId,
                                                                                                                                    null,
                                                                                                                                    ImmutableList.<EntitlementSpecifier>of(entitlementSpecifier),
                                                                                                                                    entitlementEffectiveDate,
                                                                                                                                    billingEffectiveDate,
                                                                                                                                    isMigrated);
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, callContext);
        final UUID accountId;
        try {
            accountId = subscriptionBaseInternalApi.getAccountIdFromBundleId(bundleId, context);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final List<UUID> createdEntitlements = createBaseEntitlementsWithAddOns(OperationType.CREATE_SUBSCRIPTION,
                                                                                accountId,
                                                                                ImmutableList.<BaseEntitlementWithAddOnsSpecifier>of(baseEntitlementWithAddOnsSpecifier),
                                                                                false,
                                                                                properties,
                                                                                callContext);
        return createdEntitlements.get(0);
    }

    @Override
    public List<EntitlementAOStatusDryRun> getDryRunStatusForChange(final UUID bundleId, final String targetProductName, @Nullable final LocalDate effectiveDate, final TenantContext context) throws EntitlementApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(bundleId, ObjectType.BUNDLE, context);
        try {
            final SubscriptionBase baseSubscription = subscriptionBaseInternalApi.getBaseSubscription(bundleId, internalContext);
            final UUID accountId = subscriptionBaseInternalApi.getAccountIdFromBundleId(bundleId, internalContext);
            final InternalTenantContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(accountId, context);
            final DateTime now = clock.getUTCNow();
            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, now, contextWithValidAccountRecordId);
            return subscriptionBaseInternalApi.getDryRunChangePlanStatus(baseSubscription.getId(), targetProductName, requestedDate, contextWithValidAccountRecordId);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement getEntitlementForId(final UUID entitlementId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(entitlementId, ObjectType.SUBSCRIPTION, tenantContext);
        return super.getEntitlementForId(entitlementId, contextWithValidAccountRecordId);
    }

    @Override
    public List<Entitlement> getAllEntitlementsForBundle(final UUID bundleId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(bundleId, ObjectType.BUNDLE, tenantContext);
        final UUID accountId;
        try {
            accountId = subscriptionBaseInternalApi.getAccountIdFromBundleId(bundleId, internalContext);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        return ImmutableList.<Entitlement>copyOf(Iterables.<Entitlement>filter(getAllEntitlementsForAccountId(accountId, tenantContext),
                                                                               new Predicate<Entitlement>() {
                                                                                   @Override
                                                                                   public boolean apply(final Entitlement input) {
                                                                                       return bundleId.equals(input.getBundleId());
                                                                                   }
                                                                               }));
    }

    @Override
    public List<Entitlement> getAllEntitlementsForAccountIdAndExternalKey(final UUID accountId, final String externalKey, final TenantContext tenantContext) throws EntitlementApiException {
        // getAllEntitlementsForAccount should be fast (uses account_record_id)
        return ImmutableList.<Entitlement>copyOf(Iterables.<Entitlement>filter(getAllEntitlementsForAccountId(accountId, tenantContext),
                                                                               new Predicate<Entitlement>() {
                                                                                   @Override
                                                                                   public boolean apply(final Entitlement input) {
                                                                                       return externalKey.equals(input.getExternalKey());
                                                                                   }
                                                                               }));
    }

    @Override
    public List<Entitlement> getAllEntitlementsForAccountId(final UUID accountId, final TenantContext tenantContext) throws EntitlementApiException {
        final EntitlementApi entitlementApi = this;
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);

        final AccountEventsStreams accountEventsStreams = eventsStreamBuilder.buildForAccount(context);
        final List<EventsStream> eventsStreams = ImmutableList.<EventsStream>copyOf(Iterables.<EventsStream>concat(accountEventsStreams.getEventsStreams().values()));
        return Lists.<EventsStream, Entitlement>transform(eventsStreams,
                                                          new Function<EventsStream, Entitlement>() {
                                                              @Override
                                                              public Entitlement apply(final EventsStream eventsStream) {
                                                                  return new DefaultEntitlement(eventsStream, eventsStreamBuilder, entitlementApi, pluginExecution,
                                                                                                blockingStateDao, subscriptionBaseInternalApi, checker, notificationQueueService,
                                                                                                entitlementUtils, dateHelper, clock, securityApi, context, internalCallContextFactory);
                                                              }
                                                          });
    }

    @Override
    public void pause(final UUID bundleId, final LocalDate localEffectiveDate, final Iterable<PluginProperty> properties, final CallContext context) throws EntitlementApiException {

        logPauseResumeEntitlement(log, "Pause", bundleId, localEffectiveDate);

        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);
        super.pause(bundleId, localEffectiveDate, properties, contextWithValidAccountRecordId);
    }

    @Override
    public void resume(final UUID bundleId, final LocalDate localEffectiveDate, final Iterable<PluginProperty> properties, final CallContext context) throws EntitlementApiException {

        logPauseResumeEntitlement(log, "Resume", bundleId, localEffectiveDate);

        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);
        super.resume(bundleId, localEffectiveDate, properties, contextWithValidAccountRecordId);
    }

    @Override
    public UUID transferEntitlements(final UUID sourceAccountId, final UUID destAccountId, final String externalKey, final LocalDate effectiveDate, final Iterable<PluginProperty> properties, final CallContext context) throws EntitlementApiException {
        return transferEntitlementsOverrideBillingPolicy(sourceAccountId, destAccountId, externalKey, effectiveDate, BillingActionPolicy.IMMEDIATE, properties, context);
    }

    @Override
    public UUID transferEntitlementsOverrideBillingPolicy(final UUID sourceAccountId, final UUID destAccountId, final String externalKey, @Nullable final LocalDate effectiveDate, final BillingActionPolicy billingPolicy, final Iterable<PluginProperty> properties, final CallContext context) throws EntitlementApiException {

        logTransferEntitlement(log, sourceAccountId, destAccountId, externalKey, effectiveDate, billingPolicy);

        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(
                null,
                externalKey,
                new ArrayList<EntitlementSpecifier>(),
                effectiveDate,
                effectiveDate,
                false);
        final List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifier);

        final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.TRANSFER_BUNDLE,
                                                                               sourceAccountId,
                                                                               destAccountId,
                                                                               baseEntitlementWithAddOnsSpecifierList,
                                                                               billingPolicy,
                                                                               properties,
                                                                               context);

        final WithEntitlementPlugin<UUID> transferWithPlugin = new WithEntitlementPlugin<UUID>() {
            @Override
            public UUID doCall(final EntitlementApi entitlementApi, final EntitlementContext updatedPluginContext) throws EntitlementApiException {
                final boolean cancelImm;
                switch (billingPolicy) {
                    case IMMEDIATE:
                        cancelImm = true;
                        break;
                    case END_OF_TERM:
                        cancelImm = false;
                        break;
                    default:
                        throw new RuntimeException("Unexpected billing policy " + billingPolicy);
                }

                final InternalCallContext contextWithSourceAccountRecordId = internalCallContextFactory.createInternalCallContext(sourceAccountId, context);
                try {

                    final UUID activeSubscriptionIdForKey = entitlementUtils.getFirstActiveSubscriptionIdForKeyOrNull(externalKey, contextWithSourceAccountRecordId);
                    final UUID bundleId = activeSubscriptionIdForKey != null ?
                                          subscriptionBaseInternalApi.getBundleIdFromSubscriptionId(activeSubscriptionIdForKey, contextWithSourceAccountRecordId) : null;
                    final UUID baseBundleAccountId = bundleId != null ?
                                                     subscriptionBaseInternalApi.getAccountIdFromBundleId(bundleId, contextWithSourceAccountRecordId) : null;

                    if (baseBundleAccountId == null || !baseBundleAccountId.equals(sourceAccountId)) {
                        throw new EntitlementApiException(new SubscriptionBaseApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_KEY, externalKey));
                    }

                    final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = getFirstBaseEntitlementWithAddOnsSpecifier(updatedPluginContext.getBaseEntitlementWithAddOnsSpecifiers());

                    final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(baseEntitlementWithAddOnsSpecifier.getBillingEffectiveDate(), updatedPluginContext.getCreatedDate(), contextWithSourceAccountRecordId);
                    final SubscriptionBaseBundle newBundle = subscriptionBaseTransferApi.transferBundle(sourceAccountId, destAccountId, externalKey, requestedDate, true, cancelImm, context);


                    final Map<BlockingState, UUID> blockingStates = new HashMap<BlockingState, UUID>();

                    // Block all associated subscriptions - TODO Do we want to block the bundle as well (this will add an extra STOP_ENTITLEMENT event in the bundle timeline stream)?
                    // Note that there is no un-transfer at the moment, so we effectively add a blocking state on disk for all subscriptions
                    for (final SubscriptionBase subscriptionBase : subscriptionBaseInternalApi.getSubscriptionsForBundle(bundleId, null, contextWithSourceAccountRecordId)) {
                        final BlockingState blockingState = new DefaultBlockingState(subscriptionBase.getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, requestedDate);
                        blockingStates.put(blockingState, subscriptionBase.getBundleId());
                    }
                    entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(blockingStates, contextWithSourceAccountRecordId);

                    // Add blocking events for transferred subscriptions..
                    final InternalCallContext contextWithDestAccountRecordId = internalCallContextFactory.createInternalCallContext(destAccountId, context);

                    blockingStates.clear();
                    final DateTime entitlementRequestedDate = dateHelper.fromLocalDateAndReferenceTime(baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate(), updatedPluginContext.getCreatedDate(), contextWithDestAccountRecordId);
                    for (final SubscriptionBase subscriptionBase : subscriptionBaseInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, contextWithDestAccountRecordId)) {
                        final BlockingState newBlockingState = new DefaultBlockingState(subscriptionBase.getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, EntitlementService.ENTITLEMENT_SERVICE_NAME, false, false, false, entitlementRequestedDate);
                        blockingStates.put(newBlockingState, subscriptionBase.getBundleId());
                    }
                    entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(blockingStates, contextWithDestAccountRecordId);


                    return newBundle.getId();
                } catch (final SubscriptionBaseTransferApiException e) {
                    throw new EntitlementApiException(e);
                } catch (final SubscriptionBaseApiException e) {
                    throw new EntitlementApiException(e);
                }
            }
        };
        return pluginExecution.executeWithPlugin(transferWithPlugin, pluginContext);
    }

    private List<UUID> createBaseEntitlementsWithAddOns(final OperationType operationType,
                                                        final UUID accountId,
                                                        final Iterable<BaseEntitlementWithAddOnsSpecifier> originalBaseEntitlementWithAddOnsSpecifiers,
                                                        final boolean renameCancelledBundleIfExist,
                                                        final Iterable<PluginProperty> properties,
                                                        final CallContext callContext) throws EntitlementApiException {
        logCreateEntitlementsWithAOs(log, originalBaseEntitlementWithAddOnsSpecifiers);

        final EntitlementContext pluginContext = new DefaultEntitlementContext(operationType,
                                                                               accountId,
                                                                               null,
                                                                               originalBaseEntitlementWithAddOnsSpecifiers,
                                                                               null,
                                                                               properties,
                                                                               callContext);

        final WithEntitlementPlugin<List<UUID>> createBaseEntitlementsWithAddOns = new WithEntitlementPlugin<List<UUID>>() {
            @Override
            public List<UUID> doCall(final EntitlementApi entitlementApi, final EntitlementContext updatedPluginContext) throws EntitlementApiException {
                final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);

                final Catalog catalog;
                try {
                    catalog = catalogInternalApi.getFullCatalog(true, true, contextWithValidAccountRecordId);
                } catch (final CatalogApiException e) {
                    throw new EntitlementApiException(e);
                }

                final Map<UUID, Optional<EventsStream>> eventsStreamForBaseSubscriptionPerBundle = new HashMap<UUID, Optional<EventsStream>>();
                final Map<String, Optional<UUID>> bundleKeyToIdMapping = new HashMap<String, Optional<UUID>>();
                final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiersAfterPlugins = updatedPluginContext.getBaseEntitlementWithAddOnsSpecifiers();
                final Collection<SubscriptionBaseWithAddOnsSpecifier> subscriptionBaseWithAddOnsSpecifiers = new LinkedList<SubscriptionBaseWithAddOnsSpecifier>();
                DateTime upTo = null;
                for (final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier : baseEntitlementWithAddOnsSpecifiersAfterPlugins) {
                    // Entitlement
                    final DateTime entitlementRequestedDate = dateHelper.fromLocalDateAndReferenceTime(baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate(),
                                                                                                       updatedPluginContext.getCreatedDate(),
                                                                                                       contextWithValidAccountRecordId);
                    upTo = upTo == null || upTo.compareTo(entitlementRequestedDate) < 0 ? entitlementRequestedDate : upTo;

                    final UUID bundleId = populateCaches(baseEntitlementWithAddOnsSpecifier,
                                                         eventsStreamForBaseSubscriptionPerBundle,
                                                         bundleKeyToIdMapping,
                                                         catalog,
                                                         callContext,
                                                         contextWithValidAccountRecordId);
                    if (bundleId != null) {
                        final Optional<EventsStream> eventsStreamForBaseSubscription = eventsStreamForBaseSubscriptionPerBundle.get(bundleId);
                        if (eventsStreamForBaseSubscription.isPresent()) {
                            // Verify if the operation is valid for that bundle
                            preCheckAddEntitlement(bundleId, entitlementRequestedDate, baseEntitlementWithAddOnsSpecifier, eventsStreamForBaseSubscription.get());
                        }
                    }

                    final SubscriptionBaseWithAddOnsSpecifier subscriptionBaseWithAddOnsSpecifier = new SubscriptionBaseWithAddOnsSpecifier(baseEntitlementWithAddOnsSpecifier.getBundleId(),
                                                                                                                                            baseEntitlementWithAddOnsSpecifier.getExternalKey(),
                                                                                                                                            baseEntitlementWithAddOnsSpecifier.getEntitlementSpecifier(),
                                                                                                                                            baseEntitlementWithAddOnsSpecifier.getBillingEffectiveDate(),
                                                                                                                                            baseEntitlementWithAddOnsSpecifier.isMigrated());
                    subscriptionBaseWithAddOnsSpecifiers.add(subscriptionBaseWithAddOnsSpecifier);
                }

                // Verify if operation is allowed by looking for is_block_change on Account
                // Note that to fully check for block_change we should also look for BlockingState at the BUNDLE/SUBSCRIPTION level in case some of the input contain a BP that already exists.
                checkForAccountBlockingChange(accountId, upTo, contextWithValidAccountRecordId);

                final List<SubscriptionBaseWithAddOns> subscriptionsWithAddOns;
                try {
                    subscriptionsWithAddOns = subscriptionBaseInternalApi.createBaseSubscriptionsWithAddOns(subscriptionBaseWithAddOnsSpecifiers,
                                                                                                            renameCancelledBundleIfExist,
                                                                                                            contextWithValidAccountRecordId);
                } catch (final SubscriptionBaseApiException e) {
                    throw new EntitlementApiException(e);
                }

                return createEntitlementEvents(baseEntitlementWithAddOnsSpecifiersAfterPlugins, subscriptionsWithAddOns, updatedPluginContext, contextWithValidAccountRecordId);
            }
        };
        return pluginExecution.executeWithPlugin(createBaseEntitlementsWithAddOns, pluginContext);
    }

    private BaseEntitlementWithAddOnsSpecifier getFirstBaseEntitlementWithAddOnsSpecifier(final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers) throws SubscriptionBaseApiException {
        if (baseEntitlementWithAddOnsSpecifiers == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_INVALID_ENTITLEMENT_SPECIFIER);
        }

        final Iterator<BaseEntitlementWithAddOnsSpecifier> iterator = baseEntitlementWithAddOnsSpecifiers.iterator();
        if (!iterator.hasNext()) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_INVALID_ENTITLEMENT_SPECIFIER);
        }

        return iterator.next();
    }

    private UUID populateCaches(final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier,
                                final Map<UUID, Optional<EventsStream>> eventsStreamForBaseSubscriptionPerBundle,
                                final Map<String, Optional<UUID>> bundleKeyToIdMapping,
                                final Catalog catalog,
                                final TenantContext callContext,
                                final InternalCallContext contextWithValidAccountRecordId) throws EntitlementApiException {
        // In the addEntitlement codepath, bundleId is always set. But, technically, an existing bundle could be specified by externalKey in
        // the createBaseEntitlementsWithAddOns codepath. In that case, we should also check if that bundle is blocked.
        UUID bundleId = baseEntitlementWithAddOnsSpecifier.getBundleId();
        if (bundleId == null && baseEntitlementWithAddOnsSpecifier.getExternalKey() != null) {
            populateBundleKeyToIdMappingCache(baseEntitlementWithAddOnsSpecifier, bundleKeyToIdMapping, catalog, contextWithValidAccountRecordId);

            final Optional<UUID> bundleIdForKey = bundleKeyToIdMapping.get(baseEntitlementWithAddOnsSpecifier.getExternalKey());
            if (bundleIdForKey.isPresent()) {
                bundleId = bundleIdForKey.get();
            }
        }

        if (bundleId == null) {
            return null;
        }

        populateEventsStreamForBaseSubscriptionPerBundleCache(bundleId, eventsStreamForBaseSubscriptionPerBundle, callContext, contextWithValidAccountRecordId);

        return bundleId;
    }

    private void populateBundleKeyToIdMappingCache(final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier, final Map<String, Optional<UUID>> bundleKeyToIdMapping, final Catalog catalog, final InternalCallContext contextWithValidAccountRecordId) throws EntitlementApiException {
        if (bundleKeyToIdMapping.get(baseEntitlementWithAddOnsSpecifier.getExternalKey()) == null) {
            final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.getActiveBundleForKey(baseEntitlementWithAddOnsSpecifier.getExternalKey(), catalog, contextWithValidAccountRecordId);
            if (bundle != null) {
                bundleKeyToIdMapping.put(baseEntitlementWithAddOnsSpecifier.getExternalKey(), Optional.<UUID>of(bundle.getId()));
            } else {
                bundleKeyToIdMapping.put(baseEntitlementWithAddOnsSpecifier.getExternalKey(), Optional.<UUID>absent());
            }
        }
    }

    private void populateEventsStreamForBaseSubscriptionPerBundleCache(final UUID bundleId, final Map<UUID, Optional<EventsStream>> eventsStreamForBaseSubscriptionPerBundle, final TenantContext callContext, final InternalCallContext contextWithValidAccountRecordId) throws EntitlementApiException {
        if (eventsStreamForBaseSubscriptionPerBundle.get(bundleId) == null) {
            final List<SubscriptionBase> subscriptionsByBundle;
            try {
                subscriptionsByBundle = subscriptionBaseInternalApi.getSubscriptionsForBundle(bundleId, null, contextWithValidAccountRecordId);

                if (subscriptionsByBundle == null || subscriptionsByBundle.isEmpty()) {
                    throw new EntitlementApiException(ErrorCode.SUB_NO_ACTIVE_SUBSCRIPTIONS, bundleId);
                }
            } catch (final SubscriptionBaseApiException e) {
                throw new EntitlementApiException(e);
            }

            final boolean isStandalone = Iterables.any(subscriptionsByBundle,
                                                       new Predicate<SubscriptionBase>() {
                                                           @Override
                                                           public boolean apply(final SubscriptionBase input) {
                                                               return ProductCategory.STANDALONE.equals(input.getCategory());
                                                           }
                                                       });

            if (!isStandalone) {
                final EventsStream eventsStreamForBaseSubscription = eventsStreamBuilder.buildForBaseSubscription(bundleId, callContext);
                eventsStreamForBaseSubscriptionPerBundle.put(bundleId, Optional.<EventsStream>of(eventsStreamForBaseSubscription));
            } else {
                eventsStreamForBaseSubscriptionPerBundle.put(bundleId, Optional.<EventsStream>absent());
            }
        }
    }

    private void preCheckAddEntitlement(final UUID bundleId, final DateTime entitlementRequestedDate, final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier, final EventsStream eventsStreamForBaseSubscription) throws EntitlementApiException {
        if (eventsStreamForBaseSubscription.isEntitlementCancelled() ||
            (eventsStreamForBaseSubscription.isEntitlementPending() &&
             (baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate() == null ||
              baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate().compareTo(eventsStreamForBaseSubscription.getEntitlementEffectiveStartDate()) < 0))) {
            throw new EntitlementApiException(ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION, bundleId);
        }

        // Check the base entitlement state is not blocked
        if (eventsStreamForBaseSubscription.isBlockChange(entitlementRequestedDate)) {
            throw new EntitlementApiException(new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, BlockingChecker.ACTION_CHANGE, BlockingChecker.TYPE_SUBSCRIPTION, eventsStreamForBaseSubscription.getEntitlementId().toString()));
        } else if (eventsStreamForBaseSubscription.isBlockEntitlement(entitlementRequestedDate)) {
            throw new EntitlementApiException(new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, BlockingChecker.ACTION_ENTITLEMENT, BlockingChecker.TYPE_SUBSCRIPTION, eventsStreamForBaseSubscription.getEntitlementId().toString()));
        }
    }

    private void checkForAccountBlockingChange(final UUID accountId, @Nullable final DateTime upTo, final InternalCallContext context) throws EntitlementApiException {
        try {
            final BlockingAggregator blockingAggregator = checker.getBlockedStatus(accountId, BlockingStateType.ACCOUNT, upTo, context);
            if (blockingAggregator.isBlockChange()) {
                throw new EntitlementApiException(new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, BlockingChecker.ACTION_CHANGE, BlockingChecker.TYPE_ACCOUNT, accountId.toString()));
            }
        } catch (final BlockingApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    private List<UUID> createEntitlementEvents(final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiersAfterPlugins,
                                               final List<SubscriptionBaseWithAddOns> subscriptionsWithAddOns,
                                               final CallContext updatedPluginContext,
                                               final InternalCallContext contextWithValidAccountRecordId) throws EntitlementApiException {
        final List<UUID> createdSubscriptionIds = new LinkedList<UUID>();
        final Map<BlockingState, UUID> blockingStateMap = new HashMap<BlockingState, UUID>();
        int i = 0;
        for (final Iterator<BaseEntitlementWithAddOnsSpecifier> it = baseEntitlementWithAddOnsSpecifiersAfterPlugins.iterator(); it.hasNext(); i++) {
            final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = it.next();
            for (final SubscriptionBase subscriptionBase : subscriptionsWithAddOns.get(i).getSubscriptionBaseList()) {
                final BlockingState blockingState = new DefaultBlockingState(subscriptionBase.getId(),
                                                                             BlockingStateType.SUBSCRIPTION,
                                                                             DefaultEntitlementApi.ENT_STATE_START,
                                                                             EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                             false,
                                                                             false,
                                                                             false,
                                                                             dateHelper.fromLocalDateAndReferenceTime(baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate(), updatedPluginContext.getCreatedDate(), contextWithValidAccountRecordId));
                blockingStateMap.put(blockingState, subscriptionsWithAddOns.get(i).getBundle().getId());

                createdSubscriptionIds.add(subscriptionBase.getId());
            }
        }
        entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(blockingStateMap, contextWithValidAccountRecordId);
        return createdSubscriptionIds;
    }
}
