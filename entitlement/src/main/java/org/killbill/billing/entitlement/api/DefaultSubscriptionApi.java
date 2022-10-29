/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.OrderingType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.AccountEntitlements;
import org.killbill.billing.entitlement.EntitlementInternalApi;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution.WithEntitlementPlugin;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.plugin.api.EntitlementContext;
import org.killbill.billing.entitlement.plugin.api.OperationType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.ShouldntHappenException;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.entitlement.logging.EntitlementLoggingHelper.logAddBlockingState;
import static org.killbill.billing.entitlement.logging.EntitlementLoggingHelper.logUpdateExternalKey;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultSubscriptionApi implements SubscriptionApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionApi.class);

    private static final Comparator<SubscriptionBundle> SUBSCRIPTION_BUNDLE_COMPARATOR = (o1, o2) -> {
        final int compared = o1.getOriginalCreatedDate().compareTo(o2.getOriginalCreatedDate());
        if (compared != 0) {
            return compared;
        } else {
            final int compared2 = o1.getUpdatedDate().compareTo(o2.getUpdatedDate());
            if (compared2 != 0) {
                return compared2;
            } else {
                // Default stable ordering (in the sense that doing twice the same call will lead to same result)
                return o1.getId().compareTo(o2.getId());
            }
        }
    };

    private final AccountInternalApi accountApi;
    private final EntitlementInternalApi entitlementInternalApi;
    private final SubscriptionBaseInternalApi subscriptionBaseInternalApi;
    private final CatalogInternalApi catalogInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final EntitlementUtils entitlementUtils;
    private final Clock clock;
    private final EntitlementPluginExecution pluginExecution;
    private final BlockingStateDao blockingStateDao;

    @Inject
    public DefaultSubscriptionApi(final AccountInternalApi accountApi,
                                  final EntitlementInternalApi entitlementInternalApi,
                                  final SubscriptionBaseInternalApi subscriptionInternalApi,
                                  final CatalogInternalApi catalogInternalApi,
                                  final InternalCallContextFactory internalCallContextFactory,
                                  final Clock clock,
                                  final EntitlementPluginExecution pluginExecution,
                                  final BlockingStateDao blockingStateDao,
                                  final EntitlementUtils entitlementUtils) {
        this.accountApi = accountApi;
        this.entitlementInternalApi = entitlementInternalApi;
        this.subscriptionBaseInternalApi = subscriptionInternalApi;
        this.catalogInternalApi = catalogInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.pluginExecution = pluginExecution;
        this.blockingStateDao = blockingStateDao;
        this.entitlementUtils = entitlementUtils;
    }

    @Override
    public Subscription getSubscriptionForEntitlementId(final UUID entitlementId, final boolean includeDeletedEvents, final TenantContext tenantContext) throws SubscriptionApiException {

        try {
            final UUID accountId = internalCallContextFactory.getAccountId(entitlementId, ObjectType.SUBSCRIPTION, tenantContext);
            final InternalTenantContext internalTenantContextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);
            final DefaultEntitlement entitlement = (DefaultEntitlement) entitlementInternalApi.getEntitlementForId(entitlementId, includeDeletedEvents, internalTenantContextWithValidAccountRecordId);
            return new DefaultSubscription(entitlement);
        } catch (final EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        }
    }

    @Override
    public Subscription getSubscriptionForExternalKey(final String externalKey, final boolean includeDeletedEvents, final TenantContext tenantContext) throws SubscriptionApiException {

        try {
            final InternalTenantContext contextWithoutAccountRecordId = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(tenantContext);
            final UUID subscriptionId = subscriptionBaseInternalApi.getSubscriptionIdFromSubscriptionExternalKey(externalKey, contextWithoutAccountRecordId);
            return getSubscriptionForEntitlementId(subscriptionId, includeDeletedEvents, tenantContext);
        } catch (final SubscriptionBaseApiException e) {
            throw new SubscriptionApiException(e);
        }
    }

    @Override
    public SubscriptionBundle getSubscriptionBundle(final UUID bundleId, final TenantContext tenantContext) throws SubscriptionApiException {
        final UUID accountId = internalCallContextFactory.getAccountId(bundleId, ObjectType.BUNDLE, tenantContext);
        final InternalTenantContext internalTenantContextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);

        final SubscriptionBaseBundle baseBundle;
        try {
            baseBundle = subscriptionBaseInternalApi.getBundleFromId(bundleId, internalTenantContextWithValidAccountRecordId);
        } catch (final SubscriptionBaseApiException e) {
            throw new SubscriptionApiException(e);
        }

        final List<Entitlement> bundleEntitlements;
        try {
            bundleEntitlements = entitlementInternalApi.getAllEntitlementsForBundle(bundleId, internalTenantContextWithValidAccountRecordId);
        } catch (final EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        }

        // Build subscriptions
        final List<Subscription> bundleSubscriptions = new LinkedList<Subscription>();
        for (final Entitlement entitlement : bundleEntitlements) {
            if (entitlement instanceof DefaultEntitlement) {
                bundleSubscriptions.add(new DefaultSubscription((DefaultEntitlement) entitlement));
            } else {
                throw new ShouldntHappenException("Entitlement should be a DefaultEntitlement instance");
            }
        }

        final String bundleExternalKey = bundleSubscriptions.get(0).getBundleExternalKey();
        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId,
                                                                                          bundleId,
                                                                                          bundleExternalKey,
                                                                                          bundleEntitlements,
                                                                                          internalTenantContextWithValidAccountRecordId);

        return new DefaultSubscriptionBundle(bundleId,
                                             accountId,
                                             bundleExternalKey,
                                             bundleSubscriptions,
                                             timeline,
                                             baseBundle.getOriginalCreatedDate(),
                                             baseBundle.getCreatedDate(),
                                             baseBundle.getUpdatedDate());
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForAccountIdAndExternalKey(final UUID accountId, final String externalKey, final TenantContext context) throws SubscriptionApiException {
        final List<SubscriptionBundle> bundles = getSubscriptionBundlesForAccount(accountId, context);
        return bundles.stream()
                      .filter(bundle -> bundle.getExternalKey().equals(externalKey))
                      .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public SubscriptionBundle getActiveSubscriptionBundleForExternalKey(final String externalKey, final TenantContext context) throws SubscriptionApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context);
        try {
            final UUID activeSubscriptionIdForKey = entitlementUtils.getFirstActiveSubscriptionIdForKeyOrNull(externalKey, internalContext);
            if (activeSubscriptionIdForKey == null) {
                throw new SubscriptionApiException(new SubscriptionBaseApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_KEY, externalKey));
            }

            final InternalTenantContext internalContextWithAccountRecordId =  internalCallContextFactory.createInternalTenantContext(activeSubscriptionIdForKey, ObjectType.SUBSCRIPTION, context);
            final UUID bundleId = subscriptionBaseInternalApi.getBundleIdFromSubscriptionId(activeSubscriptionIdForKey, internalContextWithAccountRecordId);
            return getSubscriptionBundle(bundleId, context);
        } catch (final SubscriptionBaseApiException e) {
            throw new SubscriptionApiException(e);
        }
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForExternalKey(final String externalKey, final TenantContext context) throws SubscriptionApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context);
        final List<SubscriptionBaseBundle> baseBundles = subscriptionBaseInternalApi.getBundlesForKey(externalKey, internalContext);

        final List<SubscriptionBundle> result = new ArrayList<>(baseBundles.size());
        for (final SubscriptionBaseBundle cur : baseBundles) {
            final SubscriptionBundle bundle = getSubscriptionBundle(cur.getId(), context);
            result.add(bundle);
        }
        // Sorting by createdDate will likely place the active bundle last, but this is the same ordering we already use for getSubscriptionBundlesForAccount
        return result.stream().sorted(SUBSCRIPTION_BUNDLE_COMPARATOR).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForAccountId(final UUID accountId, final TenantContext context) throws SubscriptionApiException {
        return getSubscriptionBundlesForAccount(accountId, context);
    }

    @Override
    public Pagination<SubscriptionBundle> getSubscriptionBundles(final Long offset, final Long limit, final TenantContext context) {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context);
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<SubscriptionBaseBundle, SubscriptionApiException>() {
                                                  @Override
                                                  public Pagination<SubscriptionBaseBundle> build() {
                                                      return subscriptionBaseInternalApi.getBundles(offset, limit, internalContext);
                                                  }
                                              },
                                              subscriptionBaseBundle -> {
                                                  try {
                                                      return getSubscriptionBundle(subscriptionBaseBundle.getId(), context);
                                                  } catch (final SubscriptionApiException e) {
                                                      log.warn("Error retrieving bundleId='{}'", subscriptionBaseBundle.getId(), e);
                                                      return null;
                                                  }
                                              }
                                             );
    }

    @Override
    public Pagination<SubscriptionBundle> searchSubscriptionBundles(final String searchKey, final Long offset, final Long limit, final TenantContext context) {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context);
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<SubscriptionBaseBundle, SubscriptionApiException>() {
                                                  @Override
                                                  public Pagination<SubscriptionBaseBundle> build() {
                                                      return subscriptionBaseInternalApi.searchBundles(searchKey, offset, limit, internalContext);
                                                  }
                                              },
                                              subscriptionBaseBundle -> {
                                                  try {
                                                      return getSubscriptionBundle(subscriptionBaseBundle.getId(), context);
                                                  } catch (final SubscriptionApiException e) {
                                                      log.warn("Error retrieving bundleId='{}'", subscriptionBaseBundle.getId(), e);
                                                      return null;
                                                  }
                                              }
                                             );
    }

    @Override
    public void updateExternalKey(final UUID bundleId, final String newExternalKey, final CallContext callContext) throws EntitlementApiException {

        logUpdateExternalKey(log, bundleId, newExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, callContext);

        final ImmutableAccountData account;
        try {
            final UUID accountId = subscriptionBaseInternalApi.getAccountIdFromBundleId(bundleId, internalCallContext);
            account = accountApi.getImmutableAccountDataById(accountId, internalCallContext);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        } catch (final AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        final DateTime effectiveDate = callContext.getCreatedDate();
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(
                bundleId,
                newExternalKey,
                new ArrayList<EntitlementSpecifier>(),
                effectiveDate,
                effectiveDate,
                false);
        final List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifier);
        final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.UPDATE_BUNDLE_EXTERNAL_KEY,
                                                                               account.getId(),
                                                                               null,
                                                                               baseEntitlementWithAddOnsSpecifierList,
                                                                               null,
                                                                               Collections.emptyList(),
                                                                               callContext);

        final WithEntitlementPlugin<Void> updateExternalKeyWithPlugin = new WithEntitlementPlugin<Void>() {

            final InternalCallContext internalCallContextWithValidAccountId = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

            @Override
            public Void doCall(final EntitlementApi entitlementApi, final DefaultEntitlementContext updatedPluginContext) throws EntitlementApiException {
                subscriptionBaseInternalApi.updateExternalKey(bundleId, newExternalKey, internalCallContextWithValidAccountId);
                return null;
            }
        };
        pluginExecution.executeWithPlugin(updateExternalKeyWithPlugin, pluginContext);
    }

    @Override
    public void addBlockingState(final BlockingState inputBlockingState, @Nullable final LocalDate inputEffectiveDate, final Iterable<PluginProperty> properties, final CallContext callContext) throws EntitlementApiException {

        logAddBlockingState(log, inputBlockingState, inputEffectiveDate);

        final InternalCallContext internalCallContextWithValidAccountId = getInternalCallContextUsingBlockingState(inputBlockingState, callContext);
        final DateTime effectiveDate = inputEffectiveDate == null ? callContext.getCreatedDate() : internalCallContextWithValidAccountId.toUTCDateTime(inputEffectiveDate);
        addBlockingState(inputBlockingState, effectiveDate, properties, callContext);

    }

    @Override
    public void addBlockingState(final BlockingState inputBlockingState, @Nullable final DateTime inputEffectiveDate, final Iterable<PluginProperty> properties, final CallContext callContext) throws EntitlementApiException {
        logAddBlockingState(log, inputBlockingState, inputEffectiveDate);

        // This is in no way an exhaustive arg validation, but to to ensure plugin would not hijack private entitlement state or service name
        if (inputBlockingState.getService() == null || inputBlockingState.getService().equals(KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName())) {
            throw new EntitlementApiException(ErrorCode.SUB_BLOCKING_STATE_INVALID_ARG, "Need to specify a valid serviceName");
        }

        if (inputBlockingState.getStateName() == null ||
            inputBlockingState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_CANCELLED) ||
            inputBlockingState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_BLOCKED) ||
            inputBlockingState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_CLEAR)) {
            throw new EntitlementApiException(ErrorCode.SUB_BLOCKING_STATE_INVALID_ARG, "Need to specify a valid stateName");
        }

        final InternalCallContext internalCallContextWithValidAccountId = getInternalCallContextUsingBlockingState(inputBlockingState, callContext);
        final ImmutableAccountData account;
        final UUID accountId;
        final UUID bundleId;
        final String externalKey;
        try {
            switch (inputBlockingState.getType()) {
                case ACCOUNT:
                    account = accountApi.getImmutableAccountDataById(inputBlockingState.getBlockedId(), internalCallContextWithValidAccountId);
                    externalKey = account.getExternalKey();
                    accountId = account.getId();
                    bundleId = null;
                    break;

                case SUBSCRIPTION_BUNDLE:
                    final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.getBundleFromId(inputBlockingState.getBlockedId(), internalCallContextWithValidAccountId);
                    externalKey = bundle.getExternalKey();
                    bundleId = bundle.getId();
                    accountId = bundle.getAccountId();
                    break;

                case SUBSCRIPTION:
                    final Entitlement entitlement = entitlementInternalApi.getEntitlementForId(inputBlockingState.getBlockedId(), false, internalCallContextWithValidAccountId);
                    bundleId = entitlement.getBundleId();
                    accountId = entitlement.getAccountId();
                    externalKey = null;
                    break;

                default:
                    throw new IllegalStateException("Invalid blockingStateType " + inputBlockingState.getType());
            }
        } catch (final AccountApiException e) {
            throw new EntitlementApiException(e);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final DateTime effectiveDate = inputEffectiveDate == null ? callContext.getCreatedDate() : inputEffectiveDate;
        final DefaultBlockingState blockingState = new DefaultBlockingState(inputBlockingState, effectiveDate);

        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(
                bundleId,
                externalKey,
                new ArrayList<EntitlementSpecifier>(),
                effectiveDate,
                effectiveDate,
                false);
        final List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifier);
        final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.ADD_BLOCKING_STATE,
                                                                               accountId,
                                                                               null,
                                                                               baseEntitlementWithAddOnsSpecifierList,
                                                                               null,
                                                                               properties,
                                                                               callContext);

        final WithEntitlementPlugin<Void> addBlockingStateWithPlugin = new WithEntitlementPlugin<Void>() {

            @Override
            public Void doCall(final EntitlementApi entitlementApi, final DefaultEntitlementContext updatedPluginContext) throws EntitlementApiException {
                entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(blockingState, internalCallContextWithValidAccountId);
                return null;
            }
        };
        pluginExecution.executeWithPlugin(addBlockingStateWithPlugin, pluginContext);

    }

    @Override
    public Iterable<BlockingState> getBlockingStates(final UUID accountId, @Nullable final List<BlockingStateType> typeFilter, @Nullable final List<String> svcsFilter, final OrderingType orderingType, final int timeFilter, final TenantContext tenantContext) throws EntitlementApiException {
        try {
            final InternalTenantContext internalTenantContextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);
            final VersionedCatalog catalog = catalogInternalApi.getFullCatalog(true, true, internalTenantContextWithValidAccountRecordId);
            final List<BlockingState> allBlockingStates = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalTenantContextWithValidAccountRecordId);
            final Iterable<BlockingState> filteredBlockingStates = filterBlockingState(allBlockingStates, typeFilter, svcsFilter);

            final LocalDate localDateNowInAccountTimezone = internalTenantContextWithValidAccountRecordId.toLocalDate(clock.getUTCNow());
            final List<BlockingState> result = new ArrayList<>();
            for (final BlockingState cur : filteredBlockingStates) {

                final LocalDate eventDate = internalTenantContextWithValidAccountRecordId.toLocalDate(cur.getEffectiveDate());
                final int comp = eventDate.compareTo(localDateNowInAccountTimezone);
                if ((comp <= 1 && ((timeFilter & SubscriptionApi.PAST_EVENTS) == SubscriptionApi.PAST_EVENTS)) ||
                    (comp == 0 && ((timeFilter & SubscriptionApi.PRESENT_EVENTS) == SubscriptionApi.PRESENT_EVENTS)) ||
                    (comp >= 1 && ((timeFilter & SubscriptionApi.FUTURE_EVENTS) == SubscriptionApi.FUTURE_EVENTS))) {
                    result.add(cur);
                }
            }

            if (orderingType == OrderingType.DESCENDING) {
                Collections.reverse(result);
            }

            return result;

        } catch (final CatalogApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    private Iterable<BlockingState> filterBlockingState(final List<BlockingState> blockingStatesToFilter,
                                                        final List<BlockingStateType> typeFilter,
                                                        final List<String> svcsFilter) {
        List<BlockingState> result = List.copyOf(blockingStatesToFilter);
        if (typeFilter != null && !typeFilter.isEmpty()) {
            result = result.stream()
                           .filter(input -> typeFilter.contains(input.getType()))
                           .collect(Collectors.toUnmodifiableList());
        }
        if (svcsFilter != null && !svcsFilter.isEmpty()) {
            result = result.stream()
                           .filter(input -> svcsFilter.contains(input.getService()))
                           .collect(Collectors.toUnmodifiableList());
        }
        return result;
    }

    @Override
    public List<AuditLogWithHistory> getSubscriptionBundleAuditLogsWithHistoryForId(final UUID uuid, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return subscriptionBaseInternalApi.getSubscriptionBundleAuditLogsWithHistoryForId(uuid, auditLevel, tenantContext);
    }

    @Override
    public List<AuditLogWithHistory> getSubscriptionAuditLogsWithHistoryForId(final UUID uuid, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return subscriptionBaseInternalApi.getSubscriptionAuditLogsWithHistoryForId(uuid, auditLevel, tenantContext);
    }

    @Override
    public List<AuditLogWithHistory> getSubscriptionEventAuditLogsWithHistoryForId(final UUID uuid, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return subscriptionBaseInternalApi.getSubscriptionEventAuditLogsWithHistoryForId(uuid, auditLevel, tenantContext);
    }

    @Override
    public List<AuditLogWithHistory> getBlockingStateAuditLogsWithHistoryForId(final UUID blockingId, final AuditLevel auditLevel, final TenantContext context) {
        return blockingStateDao.getBlockingStateAuditLogsWithHistoryForId(blockingId, auditLevel, internalCallContextFactory.createInternalTenantContext(blockingId, ObjectType.BLOCKING_STATES, context));
    }

    private List<SubscriptionBundle> getSubscriptionBundlesForAccount(final UUID accountId, final TenantContext tenantContext) throws SubscriptionApiException {
        final InternalTenantContext internalTenantContextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);

        // Retrieve entitlements
        final AccountEntitlements accountEntitlements;
        try {
            accountEntitlements = entitlementInternalApi.getAllEntitlementsForAccount(internalTenantContextWithValidAccountRecordId);
        } catch (final EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        }

        // Build subscriptions
        final Map<UUID, List<Subscription>> subscriptionsPerBundle = buildSubscriptionsFromEntitlements(accountEntitlements);

        // Build subscription bundles
        final List<SubscriptionBundle> bundles = new LinkedList<SubscriptionBundle>();
        for (final UUID bundleId : subscriptionsPerBundle.keySet()) {
            final List<Subscription> subscriptionsForBundle = subscriptionsPerBundle.get(bundleId);
            final String bundleExternalKey = subscriptionsForBundle.get(0).getBundleExternalKey();

            final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId,
                                                                                              bundleId,
                                                                                              bundleExternalKey,
                                                                                              accountEntitlements.getEntitlements().get(bundleId),
                                                                                              internalTenantContextWithValidAccountRecordId);

            final SubscriptionBaseBundle baseBundle = accountEntitlements.getBundles().get(bundleId);
            final SubscriptionBundle subscriptionBundle = new DefaultSubscriptionBundle(bundleId,
                                                                                        accountId,
                                                                                        bundleExternalKey,
                                                                                        subscriptionsForBundle,
                                                                                        timeline,
                                                                                        baseBundle.getOriginalCreatedDate(),
                                                                                        baseBundle.getCreatedDate(),
                                                                                        baseBundle.getUpdatedDate());
            bundles.add(subscriptionBundle);
        }

        // Sort the results for predictability
        return bundles.stream().sorted(SUBSCRIPTION_BUNDLE_COMPARATOR).collect(Collectors.toUnmodifiableList());
    }

    private Map<UUID, List<Subscription>> buildSubscriptionsFromEntitlements(final AccountEntitlements accountEntitlements) {
        final Map<UUID, List<Subscription>> subscriptionsPerBundle = new HashMap<UUID, List<Subscription>>();
        for (final UUID bundleId : accountEntitlements.getEntitlements().keySet()) {
            if (subscriptionsPerBundle.get(bundleId) == null) {
                subscriptionsPerBundle.put(bundleId, new LinkedList<Subscription>());
            }

            for (final Entitlement entitlement : accountEntitlements.getEntitlements().get(bundleId)) {
                if (entitlement instanceof DefaultEntitlement) {
                    subscriptionsPerBundle.get(bundleId).add(new DefaultSubscription((DefaultEntitlement) entitlement));
                } else {
                    throw new ShouldntHappenException("Entitlement should be a DefaultEntitlement instance");
                }
            }
        }
        return subscriptionsPerBundle;
    }

    private InternalCallContext getInternalCallContextUsingBlockingState(final BlockingState inputBlockingState, final CallContext callContext) {
        final InternalCallContext internalCallContext;
        switch (inputBlockingState.getType()) {
            case ACCOUNT:
                internalCallContext = internalCallContextFactory.createInternalCallContext(inputBlockingState.getBlockedId(), ObjectType.ACCOUNT, callContext);
                break;

            case SUBSCRIPTION_BUNDLE:
                internalCallContext = internalCallContextFactory.createInternalCallContext(inputBlockingState.getBlockedId(), ObjectType.BUNDLE, callContext);
                break;

            case SUBSCRIPTION:
                internalCallContext = internalCallContextFactory.createInternalCallContext(inputBlockingState.getBlockedId(), ObjectType.SUBSCRIPTION, callContext);
                break;

            default:
                throw new IllegalStateException("Invalid blockingStateType " + inputBlockingState.getType());

        }
        return internalCallContext;
    }

}
