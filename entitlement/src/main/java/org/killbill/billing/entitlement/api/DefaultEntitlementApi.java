/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.AccountEventsStreams;
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
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
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
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;

import static org.killbill.billing.entitlement.logging.EntitlementLoggingHelper.logCreateEntitlementsWithAOs;
import static org.killbill.billing.entitlement.logging.EntitlementLoggingHelper.logPauseResumeEntitlement;
import static org.killbill.billing.entitlement.logging.EntitlementLoggingHelper.logTransferEntitlement;

public class DefaultEntitlementApi extends DefaultEntitlementApiBase implements EntitlementApi {

    public static final String ENT_STATE_START = "ENT_STARTED";
    public static final String ENT_STATE_BLOCKED = "ENT_BLOCKED";
    public static final String ENT_STATE_CLEAR = "ENT_CLEAR";
    public static final String ENT_STATE_CANCELLED = "ENT_CANCELLED";

    private static final Pattern TRANSFER_PLUGIN_PROPS_PATTERN = Pattern.compile("^KB_SUB_ID_([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

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
    public DefaultEntitlementApi(final BusOptimizer eventBus, final InternalCallContextFactory internalCallContextFactory,
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
    public UUID createBaseEntitlement(final UUID accountId, final EntitlementSpecifier entitlementSpecifier, final String bundleExternalKey,
                                      @Nullable final LocalDate entitlementEffectiveDate, @Nullable final LocalDate billingEffectiveDate, final boolean isMigrated, final boolean renameCancelledBundleIfExist,
                                      final Iterable<PluginProperty> properties, final CallContext callContext) throws EntitlementApiException {

        InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null,
                                                                                           bundleExternalKey,
                                                                                           List.<EntitlementSpecifier>of(entitlementSpecifier),
                                                                                           dateHelper.fromLocalDateAndReferenceTime(entitlementEffectiveDate, callContext.getCreatedDate(), context),
                                                                                           dateHelper.fromLocalDateAndReferenceTime(billingEffectiveDate, callContext.getCreatedDate(), context),
                                                                                           isMigrated);

        final List<UUID> createdEntitlements = createBaseEntitlementsWithAddOns(OperationType.CREATE_SUBSCRIPTION,
                                                                                accountId,
                                                                                List.of(baseEntitlementWithAddOnsSpecifier),
                                                                                renameCancelledBundleIfExist,
                                                                                properties,
                                                                                callContext);
        return createdEntitlements.get(0);
    }

    @Override
    public List<UUID> createBaseEntitlementsWithAddOns(final UUID accountId, final Iterable<BaseEntitlementWithAddOnsSpecifier> originalBaseEntitlementWithAddOnsSpecifiers, final boolean renameCancelledBundleIfExist, final Iterable<PluginProperty> properties, final CallContext callContext) throws EntitlementApiException {
        return createBaseEntitlementsWithAddOns(OperationType.CREATE_SHOPPING_CART_SUBSCRIPTIONS,
                                                accountId,
                                                updateBaseEntitlementWithAddOnsSpecifiers(originalBaseEntitlementWithAddOnsSpecifiers, callContext),
                                                renameCancelledBundleIfExist,
                                                properties,
                                                callContext);
    }

    @Override
    public UUID addEntitlement(final UUID bundleId, final EntitlementSpecifier entitlementSpecifier, @Nullable final LocalDate entitlementEffectiveDate, @Nullable final LocalDate billingEffectiveDate,
                               final boolean isMigrated, final Iterable<PluginProperty> properties, final CallContext callContext) throws EntitlementApiException {

    	final InternalCallContext context = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, callContext);
    	final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(bundleId,
                                                                                           null,
                                                                                           List.of(entitlementSpecifier),
                                                                                           dateHelper.fromLocalDateAndReferenceTime(entitlementEffectiveDate, callContext.getCreatedDate(), context),
                                                                                           dateHelper.fromLocalDateAndReferenceTime(billingEffectiveDate, callContext.getCreatedDate(), context),
                                                                                           isMigrated);

    	
        final UUID accountId;
        try {
            accountId = subscriptionBaseInternalApi.getAccountIdFromBundleId(bundleId, context);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final List<UUID> createdEntitlements = createBaseEntitlementsWithAddOns(OperationType.CREATE_SUBSCRIPTION,
                                                                                accountId,
                                                                                List.of(baseEntitlementWithAddOnsSpecifier),
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
    public Entitlement getEntitlementForId(final UUID entitlementId, final boolean includeDeletedEvents, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(entitlementId, ObjectType.SUBSCRIPTION, tenantContext);
        return super.getEntitlementForId(entitlementId, includeDeletedEvents, contextWithValidAccountRecordId);
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

        return getAllEntitlementsForAccountId(accountId, tenantContext).stream()
                .filter(input -> bundleId.equals(input.getBundleId()))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<Entitlement> getAllEntitlementsForAccountIdAndBundleExternalKey(final UUID accountId, final String bundleExternalKey, final TenantContext tenantContext) throws EntitlementApiException {
        // getAllEntitlementsForAccount should be fast (uses account_record_id)
        return getAllEntitlementsForAccountId(accountId, tenantContext).stream()
                .filter(input -> bundleExternalKey.equals(input.getBundleExternalKey()))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<Entitlement> getAllEntitlementsForAccountId(final UUID accountId, final TenantContext tenantContext) throws EntitlementApiException {
        final EntitlementApi entitlementApi = this;
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);

        final AccountEventsStreams accountEventsStreams = eventsStreamBuilder.buildForAccount(context);
        return accountEventsStreams.getEventsStreams().values()
                                   .stream()
                                   .flatMap(Collection::stream)
                                   .map(eventsStream -> new DefaultEntitlement(eventsStream, eventsStreamBuilder, entitlementApi, pluginExecution,
                                                            blockingStateDao, subscriptionBaseInternalApi, checker, notificationQueueService,
                                                            entitlementUtils, dateHelper, clock, securityApi,
                                                            context, internalCallContextFactory))
                                   .collect(Collectors.toList());
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
    public UUID transferEntitlements(final UUID sourceAccountId, final UUID destAccountId, final String bundleExternalKey, final LocalDate effectiveDate, final Iterable<PluginProperty> properties, final CallContext context) throws EntitlementApiException {
        return transferEntitlementsOverrideBillingPolicy(sourceAccountId, destAccountId, bundleExternalKey, effectiveDate, BillingActionPolicy.IMMEDIATE, properties, context);
    }

    static Map<UUID, String> toSubExtKeysMap(final Iterable<PluginProperty> properties) {
        final Map<UUID, String> res = new HashMap<>();
        for (final PluginProperty pp : properties) {
            final Matcher m = TRANSFER_PLUGIN_PROPS_PATTERN.matcher(pp.getKey());
            if (m.matches()) {
                final UUID subId = UUID.fromString(m.group(1));
                res.put(subId, (String) pp.getValue());
            }
        }
        return res;
    }

    @Override
    public UUID transferEntitlementsOverrideBillingPolicy(final UUID sourceAccountId, final UUID destAccountId, final String bundleExternalKey, @Nullable final LocalDate effectiveDate, final BillingActionPolicy billingPolicy, final Iterable<PluginProperty> properties, final CallContext context) throws EntitlementApiException {

        logTransferEntitlement(log, sourceAccountId, destAccountId, bundleExternalKey, effectiveDate, billingPolicy);

        final InternalCallContext contextWithSourceAccountRecordId = internalCallContextFactory.createInternalCallContext(sourceAccountId, context);
        final DateTime entitlementEffectiveDateTime = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, context.getCreatedDate(), contextWithSourceAccountRecordId);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(
                null,
                bundleExternalKey,
                new ArrayList<EntitlementSpecifier>(),
                entitlementEffectiveDateTime,
                entitlementEffectiveDateTime,
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
            public UUID doCall(final EntitlementApi entitlementApi, final DefaultEntitlementContext updatedPluginContext) throws EntitlementApiException {
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

                try {

                    final UUID activeSubscriptionIdForKey = entitlementUtils.getFirstActiveSubscriptionIdForKeyOrNull(bundleExternalKey, contextWithSourceAccountRecordId);
                    final UUID bundleId = activeSubscriptionIdForKey != null ?
                                          subscriptionBaseInternalApi.getBundleIdFromSubscriptionId(activeSubscriptionIdForKey, contextWithSourceAccountRecordId) : null;
                    final UUID baseBundleAccountId = bundleId != null ?
                                                     subscriptionBaseInternalApi.getAccountIdFromBundleId(bundleId, contextWithSourceAccountRecordId) : null;

                    if (baseBundleAccountId == null || !baseBundleAccountId.equals(sourceAccountId)) {
                        throw new EntitlementApiException(new SubscriptionBaseApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_KEY, bundleExternalKey));
                    }

                    final DefaultBaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = getFirstBaseEntitlementWithAddOnsSpecifier(updatedPluginContext.getBaseEntitlementWithAddOnsSpecifiers());

                    final DateTime requestedDate = baseEntitlementWithAddOnsSpecifier.getBillingEffectiveDate();
                    final Map<UUID, String> subExtKeysMap = toSubExtKeysMap(properties);
                    final SubscriptionBaseBundle newBundle = subscriptionBaseTransferApi.transferBundle(sourceAccountId, destAccountId, bundleExternalKey, subExtKeysMap, requestedDate, true, cancelImm, context);

                    // Update the context for plugins
                    baseEntitlementWithAddOnsSpecifier.setBundleId(newBundle.getId());
                    baseEntitlementWithAddOnsSpecifier.setBundleExternalKey(newBundle.getExternalKey());

                    final Map<BlockingState, UUID> blockingStates = new HashMap<BlockingState, UUID>();

                    // Block all associated subscriptions - TODO Do we want to block the bundle as well (this will add an extra STOP_ENTITLEMENT event in the bundle timeline stream)?
                    // Note that there is no un-transfer at the moment, so we effectively add a blocking state on disk for all subscriptions
                    for (final SubscriptionBase subscriptionBase : subscriptionBaseInternalApi.getSubscriptionsForBundle(bundleId, null, contextWithSourceAccountRecordId)) {
                        final BlockingState blockingState = new DefaultBlockingState(subscriptionBase.getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), true, true, false, requestedDate);
                        blockingStates.put(blockingState, subscriptionBase.getBundleId());
                    }
                    entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(blockingStates, contextWithSourceAccountRecordId);

                    // Add blocking events for transferred subscriptions..
                    final InternalCallContext contextWithDestAccountRecordId = internalCallContextFactory.createInternalCallContext(destAccountId, context);

                    blockingStates.clear();
                    final DateTime entitlementRequestedDate = baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate();
                    for (final SubscriptionBase subscriptionBase : subscriptionBaseInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, contextWithDestAccountRecordId)) {
                        final BlockingState newBlockingState = new DefaultBlockingState(subscriptionBase.getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, false, entitlementRequestedDate);
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
            public List<UUID> doCall(final EntitlementApi entitlementApi, final DefaultEntitlementContext updatedPluginContext) throws EntitlementApiException {
                final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);

                final VersionedCatalog catalog;
                try {
                    catalog = catalogInternalApi.getFullCatalog(true, true, contextWithValidAccountRecordId);
                } catch (final CatalogApiException e) {
                    throw new EntitlementApiException(e);
                }

                final Map<UUID, Optional<EventsStream>> eventsStreamForBaseSubscriptionPerBundle = new HashMap<>();
                final Map<String, Optional<UUID>> bundleKeyToIdMapping = new HashMap<>();
                final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiersAfterPlugins = updatedPluginContext.getBaseEntitlementWithAddOnsSpecifiers();
                final Collection<SubscriptionBaseWithAddOnsSpecifier> subscriptionBaseWithAddOnsSpecifiers = new LinkedList<>();
                DateTime upTo = null;
                for (final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier : baseEntitlementWithAddOnsSpecifiersAfterPlugins) {
                    // Entitlement
                    final DateTime entitlementRequestedDate = baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate();
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
                                                                                                                                            baseEntitlementWithAddOnsSpecifier.getBundleExternalKey(),
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
                    subscriptionsWithAddOns = subscriptionBaseInternalApi.createBaseSubscriptionsWithAddOns(catalog,
                                                                                                            subscriptionBaseWithAddOnsSpecifiers,
                                                                                                            renameCancelledBundleIfExist,
                                                                                                            contextWithValidAccountRecordId);
                } catch (final SubscriptionBaseApiException e) {
                    throw new EntitlementApiException(e);
                }

                // Update the context for plugins (assume underlying ordering is respected)
                for (int i = 0; i < subscriptionsWithAddOns.size(); i++) {
                    final SubscriptionBaseWithAddOns subscriptionBaseWithAddOns = subscriptionsWithAddOns.get(i);
                    updatedPluginContext.getBaseEntitlementWithAddOnsSpecifiers(i).setBundleId(subscriptionBaseWithAddOns.getBundle().getId());
                    updatedPluginContext.getBaseEntitlementWithAddOnsSpecifiers(i).setBundleExternalKey(subscriptionBaseWithAddOns.getBundle().getExternalKey());
                }

                return createEntitlementEvents(baseEntitlementWithAddOnsSpecifiersAfterPlugins, subscriptionsWithAddOns, updatedPluginContext, contextWithValidAccountRecordId);
            }
        };
        return pluginExecution.executeWithPlugin(createBaseEntitlementsWithAddOns, pluginContext);
    }

    private DefaultBaseEntitlementWithAddOnsSpecifier getFirstBaseEntitlementWithAddOnsSpecifier(final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers) throws SubscriptionBaseApiException {
        if (baseEntitlementWithAddOnsSpecifiers == null) {
            log.warn("getFirstBaseEntitlementWithAddOnsSpecifier: baseEntitlementWithAddOnsSpecifiers is null");
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_INVALID_ENTITLEMENT_SPECIFIER, "no base entitlement specifier");
        }

        final Iterator<BaseEntitlementWithAddOnsSpecifier> iterator = baseEntitlementWithAddOnsSpecifiers.iterator();
        if (!iterator.hasNext()) {
            log.warn("getFirstBaseEntitlementWithAddOnsSpecifier: baseEntitlementWithAddOnsSpecifiers is empty");
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_INVALID_ENTITLEMENT_SPECIFIER, "empty base entitlement specifier");
        }

        return (DefaultBaseEntitlementWithAddOnsSpecifier) iterator.next();
    }

    private UUID populateCaches(final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier,
                                final Map<UUID, Optional<EventsStream>> eventsStreamForBaseSubscriptionPerBundle,
                                final Map<String, Optional<UUID>> bundleKeyToIdMapping,
                                final VersionedCatalog catalog,
                                final TenantContext callContext,
                                final InternalCallContext contextWithValidAccountRecordId) throws EntitlementApiException {
        // In the addEntitlement codepath, bundleId is always set. But, technically, an existing bundle could be specified by bundleExternalKey in
        // the createBaseEntitlementsWithAddOns codepath. In that case, we should also check if that bundle is blocked.
        UUID bundleId = baseEntitlementWithAddOnsSpecifier.getBundleId();
        if (bundleId == null && baseEntitlementWithAddOnsSpecifier.getBundleExternalKey() != null) {
            populateBundleKeyToIdMappingCache(baseEntitlementWithAddOnsSpecifier, bundleKeyToIdMapping, catalog, contextWithValidAccountRecordId);

            final Optional<UUID> bundleIdForKey = bundleKeyToIdMapping.get(baseEntitlementWithAddOnsSpecifier.getBundleExternalKey());
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

    private void populateBundleKeyToIdMappingCache(final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier, final Map<String, Optional<UUID>> bundleKeyToIdMapping, final VersionedCatalog catalog, final InternalCallContext contextWithValidAccountRecordId) throws EntitlementApiException {
        if (bundleKeyToIdMapping.get(baseEntitlementWithAddOnsSpecifier.getBundleExternalKey()) == null) {
            final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.getActiveBundleForKey(catalog, baseEntitlementWithAddOnsSpecifier.getBundleExternalKey(), contextWithValidAccountRecordId);
            if (bundle != null) {
                bundleKeyToIdMapping.put(baseEntitlementWithAddOnsSpecifier.getBundleExternalKey(), Optional.of(bundle.getId()));
            } else {
                bundleKeyToIdMapping.put(baseEntitlementWithAddOnsSpecifier.getBundleExternalKey(), Optional.empty());
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

            final boolean isStandalone = subscriptionsByBundle.stream().anyMatch(input -> ProductCategory.STANDALONE.equals(input.getCategory()));

            if (!isStandalone) {
                final EventsStream eventsStreamForBaseSubscription = eventsStreamBuilder.buildForBaseSubscription(bundleId, callContext);
                eventsStreamForBaseSubscriptionPerBundle.put(bundleId, Optional.of(eventsStreamForBaseSubscription));
            } else {
                eventsStreamForBaseSubscriptionPerBundle.put(bundleId, Optional.empty());
            }
        }
    }

    private void preCheckAddEntitlement(final UUID bundleId, final DateTime entitlementRequestedDate, final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier, final EventsStream eventsStreamForBaseSubscription) throws EntitlementApiException {
        if (eventsStreamForBaseSubscription.isEntitlementCancelled() ||
            (eventsStreamForBaseSubscription.isEntitlementPending() &&
             (baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate() == null ||
              baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate().compareTo(eventsStreamForBaseSubscription.getEntitlementEffectiveStartDateTime()) < 0))) {
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
                                                                             KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(),
                                                                             false,
                                                                             false,
                                                                             false,
                                                                             baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate());
                blockingStateMap.put(blockingState, subscriptionsWithAddOns.get(i).getBundle().getId());

                createdSubscriptionIds.add(subscriptionBase.getId());
            }
        }
        entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(blockingStateMap, contextWithValidAccountRecordId);
        return createdSubscriptionIds;
    }

    private Iterable<BaseEntitlementWithAddOnsSpecifier> updateBaseEntitlementWithAddOnsSpecifiers(final Iterable<BaseEntitlementWithAddOnsSpecifier> originalBaseEntitlementWithAddOnsSpecifiers, final CallContext callContext) {
        final List<BaseEntitlementWithAddOnsSpecifier> updatedBaseEntitlementWithAddOnsSpecifiers = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();

        for (final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier : originalBaseEntitlementWithAddOnsSpecifiers) {
            final DateTime entitlementEffectiveDate = baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate() != null ? baseEntitlementWithAddOnsSpecifier.getEntitlementEffectiveDate() : callContext.getCreatedDate();
            final DateTime billingEffectiveDate = baseEntitlementWithAddOnsSpecifier.getBillingEffectiveDate() != null ? baseEntitlementWithAddOnsSpecifier.getBillingEffectiveDate() : callContext.getCreatedDate();
            final BaseEntitlementWithAddOnsSpecifier updatedBaseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(baseEntitlementWithAddOnsSpecifier.getBundleId(), baseEntitlementWithAddOnsSpecifier.getBundleExternalKey(), baseEntitlementWithAddOnsSpecifier.getEntitlementSpecifier(), entitlementEffectiveDate, billingEffectiveDate, baseEntitlementWithAddOnsSpecifier.isMigrated());
            updatedBaseEntitlementWithAddOnsSpecifiers.add(updatedBaseEntitlementWithAddOnsSpecifier);
        }
        return updatedBaseEntitlementWithAddOnsSpecifiers;
    }

}
