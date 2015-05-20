/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.svcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun.DryRunChangeReason;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.subscription.api.SubscriptionApiBase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseApiService;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionStatusDryRun;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultSubscriptionInternalApi extends SubscriptionApiBase implements SubscriptionBaseInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionInternalApi.class);

    private final AddonUtils addonUtils;
    private final InternalCallContextFactory internalCallContextFactory;

    private final NotificationQueueService notificationQueueService;

    @Inject
    public DefaultSubscriptionInternalApi(final SubscriptionDao dao,
                                          final DefaultSubscriptionBaseApiService apiService,
                                          final NotificationQueueService notificationQueueService,
                                          final Clock clock,
                                          final CatalogService catalogService,
                                          final AddonUtils addonUtils,
                                          final InternalCallContextFactory internalCallContextFactory) {
        super(dao, apiService, clock, catalogService);
        this.addonUtils = addonUtils;
        this.internalCallContextFactory = internalCallContextFactory;
        this.notificationQueueService = notificationQueueService;
    }


    @Override
    public SubscriptionBase createSubscription(final UUID bundleId, final PlanPhaseSpecifier spec, final List<PlanPhasePriceOverride> overrides, final DateTime requestedDateWithMs, final InternalCallContext context) throws SubscriptionBaseApiException {
        try {
            final String realPriceList = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            final DateTime now = clock.getUTCNow();
            final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;
            /*
            if (requestedDate.isAfter(now)) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE, now.toString(), requestedDate.toString());
            }
            */
            final DateTime effectiveDate = requestedDate;

            final CallContext callContext = internalCallContextFactory.createCallContext(context);
            final Catalog catalog = catalogService.getFullCatalog(context);
            final PlanPhasePriceOverridesWithCallContext overridesWithContext = new DefaultPlanPhasePriceOverridesWithCallContext(overrides, callContext);

            final Plan plan = catalog.createOrFindPlan(spec.getProductName(), spec.getBillingPeriod(), realPriceList, overridesWithContext, requestedDate);
            final PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new SubscriptionBaseError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                                                              spec.getProductName(), spec.getBillingPeriod().toString(), realPriceList));
            }

            final SubscriptionBaseBundle bundle = dao.getSubscriptionBundleFromId(bundleId, context);
            if (bundle == null) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_NO_BUNDLE, bundleId);
            }

            final DefaultSubscriptionBase baseSubscription = (DefaultSubscriptionBase) dao.getBaseSubscription(bundleId, context);
            final DateTime bundleStartDate = getBundleStartDateWithSanity(bundleId, baseSubscription, plan, requestedDate, effectiveDate, context);
            return apiService.createPlan(new SubscriptionBuilder()
                                                 .setId(UUID.randomUUID())
                                                 .setBundleId(bundleId)
                                                 .setCategory(plan.getProduct().getCategory())
                                                 .setBundleStartDate(bundleStartDate)
                                                 .setAlignStartDate(effectiveDate),
                                         plan, spec.getPhaseType(), realPriceList, requestedDate, effectiveDate, now, callContext);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public SubscriptionBaseBundle createBundleForAccount(final UUID accountId, final String bundleKey, final InternalCallContext context) throws SubscriptionBaseApiException {

        final List<SubscriptionBaseBundle> existingBundles = dao.getSubscriptionBundlesForKey(bundleKey, context);
        final DateTime now = clock.getUTCNow();
        final DateTime originalCreatedDate = existingBundles.size() > 0 ? existingBundles.get(0).getCreatedDate() : now;
        final DefaultSubscriptionBaseBundle bundle = new DefaultSubscriptionBaseBundle(bundleKey, accountId, now, originalCreatedDate, now, now);
        return dao.createSubscriptionBundle(bundle, context);
    }

    @Override
    public List<SubscriptionBaseBundle> getBundlesForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = dao.getSubscriptionBundlesForAccountAndKey(accountId, bundleKey, context);
        return bundlesForAccountAndKey;
    }

    @Override
    public List<SubscriptionBaseBundle> getBundlesForAccount(final UUID accountId, final InternalTenantContext context) {
        return dao.getSubscriptionBundleForAccount(accountId, context);
    }

    @Override
    public List<SubscriptionBaseBundle> getBundlesForKey(final String bundleKey, final InternalTenantContext context) {
        final List<SubscriptionBaseBundle> result = dao.getSubscriptionBundlesForKey(bundleKey, context);
        return result;
    }

    @Override
    public Pagination<SubscriptionBaseBundle> getBundles(final Long offset, final Long limit, final InternalTenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<SubscriptionBundleModelDao, SubscriptionBaseApiException>() {
                                                  @Override
                                                  public Pagination<SubscriptionBundleModelDao> build() {
                                                      return dao.get(offset, limit, context);
                                                  }
                                              },
                                              new Function<SubscriptionBundleModelDao, SubscriptionBaseBundle>() {
                                                  @Override
                                                  public SubscriptionBaseBundle apply(final SubscriptionBundleModelDao bundleModelDao) {
                                                      return SubscriptionBundleModelDao.toSubscriptionbundle(bundleModelDao);
                                                  }
                                              }
                                             );
    }

    @Override
    public Pagination<SubscriptionBaseBundle> searchBundles(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<SubscriptionBundleModelDao, SubscriptionBaseApiException>() {
                                                  @Override
                                                  public Pagination<SubscriptionBundleModelDao> build() {
                                                      return dao.searchSubscriptionBundles(searchKey, offset, limit, context);
                                                  }
                                              },
                                              new Function<SubscriptionBundleModelDao, SubscriptionBaseBundle>() {
                                                  @Override
                                                  public SubscriptionBaseBundle apply(final SubscriptionBundleModelDao bundleModelDao) {
                                                      return SubscriptionBundleModelDao.toSubscriptionbundle(bundleModelDao);
                                                  }
                                              }
                                             );

    }

    @Override
    public Iterable<UUID> getNonAOSubscriptionIdsForKey(final String bundleKey, final InternalTenantContext context) {
        return dao.getNonAOSubscriptionIdsForKey(bundleKey, context);
    }

    public static SubscriptionBaseBundle getActiveBundleForKeyNotException(final List<SubscriptionBaseBundle> existingBundles, final SubscriptionDao dao, final Clock clock, final InternalTenantContext context) {
        for (final SubscriptionBaseBundle cur : existingBundles) {
            final List<SubscriptionBase> subscriptions;
            try {
                subscriptions = dao.getSubscriptions(cur.getId(), ImmutableList.<SubscriptionBaseEvent>of(), context);
                for (final SubscriptionBase s : subscriptions) {
                    if (s.getCategory() == ProductCategory.ADD_ON) {
                        continue;
                    }
                    if (s.getEndDate() == null || s.getEndDate().compareTo(clock.getUTCNow()) > 0) {
                        return cur;
                    }
                }
            } catch (final CatalogApiException e) {
                log.warn("Failed to get subscriptions, ", e);
                return null;
            }
        }
        return null;
    }

    @Override
    public List<SubscriptionBase> getSubscriptionsForBundle(final UUID bundleId,
                                                            @Nullable final DryRunArguments dryRunArguments,
                                                            final InternalTenantContext context) throws SubscriptionBaseApiException {

        try {
            final List<SubscriptionBaseEvent> outputDryRunEvents = new ArrayList<SubscriptionBaseEvent>();
            final List<SubscriptionBase> outputSubscriptions = new ArrayList<SubscriptionBase>();

            populateDryRunEvents(bundleId, dryRunArguments, outputDryRunEvents, outputSubscriptions, context);
            final List<SubscriptionBase> result;
            result = dao.getSubscriptions(bundleId, outputDryRunEvents, context);
            if (result != null && !result.isEmpty()) {
                outputSubscriptions.addAll(result);
            }
            return createSubscriptionsForApiUse(outputSubscriptions);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public Map<UUID, List<SubscriptionBase>> getSubscriptionsForAccount(final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {
            final Map<UUID, List<SubscriptionBase>> internalSubscriptions = dao.getSubscriptionsForAccount(context);
            final Map<UUID, List<SubscriptionBase>> result = new HashMap<UUID, List<SubscriptionBase>>();
            for (final UUID bundleId : internalSubscriptions.keySet()) {
                result.put(bundleId, createSubscriptionsForApiUse(internalSubscriptions.get(bundleId)));
            }
            return result;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public SubscriptionBase getBaseSubscription(final UUID bundleId, final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {
            final SubscriptionBase result = dao.getBaseSubscription(bundleId, context);
            if (result == null) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION, bundleId);
            }
            return createSubscriptionForApiUse(result);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public SubscriptionBase getSubscriptionFromId(final UUID id, final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {
            final SubscriptionBase result = dao.getSubscriptionFromId(id, context);
            if (result == null) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_SUBSCRIPTION_ID, id);
            }
            return createSubscriptionForApiUse(result);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public SubscriptionBaseBundle getBundleFromId(final UUID id, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBaseBundle result = dao.getSubscriptionBundleFromId(id, context);
        if (result == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_ID, id.toString());
        }
        return result;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) throws SubscriptionBaseApiException {
        return dao.getAccountIdFromSubscriptionId(subscriptionId, context);
    }

    @Override
    public void setChargedThroughDate(final UUID subscriptionId, final DateTime chargedThruDate, final InternalCallContext context) throws SubscriptionBaseApiException {
        try {
            final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) dao.getSubscriptionFromId(subscriptionId, context);
            final SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
                    .setChargedThroughDate(chargedThruDate);

            dao.updateChargedThroughDate(new DefaultSubscriptionBase(builder), context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public List<EffectiveSubscriptionInternalEvent> getAllTransitions(final SubscriptionBase subscription, final InternalTenantContext context) {
        final List<SubscriptionBaseTransition> transitions = ((DefaultSubscriptionBase) subscription).getAllTransitions();
        return convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(subscription, context, transitions);
    }

    @Override
    public List<EffectiveSubscriptionInternalEvent> getBillingTransitions(final SubscriptionBase subscription, final InternalTenantContext context) {
        final List<SubscriptionBaseTransition> transitions = ((DefaultSubscriptionBase) subscription).getBillingTransitions();
        return convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(subscription, context, transitions);
    }

    @Override
    public List<EntitlementAOStatusDryRun> getDryRunChangePlanStatus(final UUID subscriptionId, @Nullable final String baseProductName, final DateTime requestedDate, final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {
            final SubscriptionBase subscription = dao.getSubscriptionFromId(subscriptionId, context);
            if (subscription == null) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_SUBSCRIPTION_ID, subscriptionId);
            }
            if (subscription.getCategory() != ProductCategory.BASE) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_CHANGE_DRY_RUN_NOT_BP);
            }

            final List<EntitlementAOStatusDryRun> result = new LinkedList<EntitlementAOStatusDryRun>();

            final List<SubscriptionBase> bundleSubscriptions = dao.getSubscriptions(subscription.getBundleId(), ImmutableList.<SubscriptionBaseEvent>of(), context);
            for (final SubscriptionBase cur : bundleSubscriptions) {
                if (cur.getId().equals(subscriptionId)) {
                    continue;
                }

                // If ADDON is cancelled, skip
                if (cur.getState() == EntitlementState.CANCELLED) {
                    continue;
                }

                final DryRunChangeReason reason;
                // If baseProductName is null, it's a cancellation dry-run. In this case, return all addons, so they are cancelled
                if (baseProductName != null && addonUtils.isAddonIncludedFromProdName(baseProductName, cur.getCurrentPlan(), requestedDate, context)) {
                    reason = DryRunChangeReason.AO_INCLUDED_IN_NEW_PLAN;
                } else if (baseProductName != null && addonUtils.isAddonAvailableFromProdName(baseProductName, cur.getCurrentPlan(), requestedDate, context)) {
                    reason = DryRunChangeReason.AO_AVAILABLE_IN_NEW_PLAN;
                } else {
                    reason = DryRunChangeReason.AO_NOT_AVAILABLE_IN_NEW_PLAN;
                }
                final EntitlementAOStatusDryRun status = new DefaultSubscriptionStatusDryRun(cur.getId(),
                                                                                             cur.getCurrentPlan().getProduct().getName(),
                                                                                             cur.getCurrentPhase().getPhaseType(),
                                                                                             cur.getCurrentPlan().getRecurringBillingPeriod(),
                                                                                             cur.getCurrentPriceList().getName(), reason);
                result.add(status);
            }
            return result;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }

    }

    @Override
    public void updateExternalKey(final UUID bundleId, final String newExternalKey, final InternalCallContext context) {
        dao.updateBundleExternalKey(bundleId, newExternalKey, context);
    }

    private void populateDryRunEvents(@Nullable final UUID bundleId,
                                      @Nullable final DryRunArguments dryRunArguments,
                                      final List<SubscriptionBaseEvent> outputDryRunEvents,
                                      final List<SubscriptionBase> outputSubscriptions,
                                      final InternalTenantContext context) throws SubscriptionBaseApiException {
        if (dryRunArguments == null || dryRunArguments.getAction() == null) {
            return;
        }

        final DateTime utcNow = clock.getUTCNow();
        List<SubscriptionBaseEvent> dryRunEvents = null;
        try {
            final PlanPhaseSpecifier inputSpec = dryRunArguments.getPlanPhaseSpecifier();
            final String realPriceList = (inputSpec != null && inputSpec.getPriceListName() != null) ? inputSpec.getPriceListName() : PriceListSet.DEFAULT_PRICELIST_NAME;
            final Catalog catalog = catalogService.getFullCatalog(context);

            final PlanPhasePriceOverridesWithCallContext overridesWithContext = null; // TODO not supported to dryRun with custom price
            final Plan plan = (inputSpec != null && inputSpec.getProductName() != null && inputSpec.getBillingPeriod() != null) ?
                              catalog.createOrFindPlan(inputSpec.getProductName(), inputSpec.getBillingPeriod(), realPriceList, overridesWithContext, utcNow) : null;
            final TenantContext tenantContext = internalCallContextFactory.createTenantContext(context);

            if (dryRunArguments != null) {
                switch (dryRunArguments.getAction()) {
                    case START_BILLING:

                        final DefaultSubscriptionBase baseSubscription = (DefaultSubscriptionBase) dao.getBaseSubscription(bundleId, context);
                        final DateTime startEffectiveDate = dryRunArguments.getEffectiveDate() != null ? dryRunArguments.getEffectiveDate() : utcNow;
                        final DateTime bundleStartDate = getBundleStartDateWithSanity(bundleId, baseSubscription, plan, startEffectiveDate, startEffectiveDate, context);
                        final UUID subscriptionId = UUID.randomUUID();
                        dryRunEvents = apiService.getEventsOnCreation(bundleId, subscriptionId, startEffectiveDate, bundleStartDate, 1L, plan, inputSpec.getPhaseType(), realPriceList,
                                                                      utcNow, startEffectiveDate, utcNow, false, context);
                        final SubscriptionBuilder builder = new SubscriptionBuilder()
                                .setId(subscriptionId)
                                .setBundleId(bundleId)
                                .setCategory(plan.getProduct().getCategory())
                                .setBundleStartDate(bundleStartDate)
                                .setAlignStartDate(startEffectiveDate);
                        final DefaultSubscriptionBase newSubscription = new DefaultSubscriptionBase(builder, apiService, clock);
                        newSubscription.rebuildTransitions(dryRunEvents, catalog);
                        outputSubscriptions.add(newSubscription);
                        break;

                    case CHANGE:
                        final DefaultSubscriptionBase subscriptionForChange = (DefaultSubscriptionBase) dao.getSubscriptionFromId(dryRunArguments.getSubscriptionId(), context);
                        DateTime changeEffectiveDate = dryRunArguments.getEffectiveDate();
                        if (changeEffectiveDate == null) {
                            BillingActionPolicy policy = dryRunArguments.getBillingActionPolicy();
                            if (policy == null) {
                                final PlanChangeResult planChangeResult = apiService.getPlanChangeResult(subscriptionForChange,
                                                                                                         dryRunArguments.getPlanPhaseSpecifier().getProductName(),
                                                                                                         dryRunArguments.getPlanPhaseSpecifier().getBillingPeriod(),
                                                                                                         dryRunArguments.getPlanPhaseSpecifier().getPriceListName(), utcNow, tenantContext);
                                policy = planChangeResult.getPolicy();
                            }
                            changeEffectiveDate = subscriptionForChange.getPlanChangeEffectiveDate(policy);
                        }
                        dryRunEvents = apiService.getEventsOnChangePlan(subscriptionForChange, plan, realPriceList, utcNow, changeEffectiveDate, utcNow, true, context);
                        break;

                    case STOP_BILLING:
                        final DefaultSubscriptionBase subscriptionForCancellation = (DefaultSubscriptionBase) dao.getSubscriptionFromId(dryRunArguments.getSubscriptionId(), context);
                        DateTime cancelEffectiveDate = dryRunArguments.getEffectiveDate();
                        if (dryRunArguments.getEffectiveDate() == null) {
                            BillingActionPolicy policy = dryRunArguments.getBillingActionPolicy();
                            if (policy == null) {

                                final Plan currentPlan = subscriptionForCancellation.getCurrentPlan();
                                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                                                                                       currentPlan.getProduct().getCategory(),
                                                                                       subscriptionForCancellation.getCurrentPlan().getRecurringBillingPeriod(),
                                                                                       subscriptionForCancellation.getCurrentPriceList().getName(),
                                                                                       subscriptionForCancellation.getCurrentPhase().getPhaseType());
                                policy = catalogService.getFullCatalog(context).planCancelPolicy(spec, utcNow);
                            }
                            cancelEffectiveDate = subscriptionForCancellation.getPlanChangeEffectiveDate(policy);
                        }
                        dryRunEvents = apiService.getEventsOnCancelPlan(subscriptionForCancellation, utcNow, cancelEffectiveDate, utcNow, true, context);
                        break;

                    default:
                        throw new IllegalArgumentException("Unexpected dryRunArguments action " + dryRunArguments.getAction());
                }
            }
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
        if (dryRunEvents != null && !dryRunEvents.isEmpty()) {
            outputDryRunEvents.addAll(dryRunEvents);
        }
    }

    @Override
    public Iterable<DateTime> getFutureNotificationsForAccount(final InternalCallContext internalCallContext) {
        try {
            final NotificationQueue notificationQueue = notificationQueueService.getNotificationQueue(DefaultSubscriptionBaseService.SUBSCRIPTION_SERVICE_NAME,
                                                                                                      DefaultSubscriptionBaseService.NOTIFICATION_QUEUE_NAME);
            final List<NotificationEventWithMetadata<NotificationEvent>> futureNotifications = notificationQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
            return Iterables.transform(futureNotifications, new Function<NotificationEventWithMetadata<NotificationEvent>, DateTime>() {
                @Nullable
                @Override
                public DateTime apply(final NotificationEventWithMetadata<NotificationEvent> input) {
                    return input.getEffectiveDate();
                }
            });
        } catch(NoSuchNotificationQueue noSuchNotificationQueue) {
            throw new IllegalStateException(noSuchNotificationQueue);
        }
    }


    @Override
    public Map<UUID, DateTime> getNextFutureEventForSubscriptions(final SubscriptionBaseTransitionType eventType, final InternalCallContext internalCallContext) {
        final Iterable<SubscriptionBaseEvent> events = dao.getFutureEventsForAccount(internalCallContext);
        final Iterable<SubscriptionBaseEvent> filteredEvents = Iterables.filter(events, new Predicate<SubscriptionBaseEvent>() {
            @Override
            public boolean apply(final SubscriptionBaseEvent input) {
                return (eventType == SubscriptionBaseTransitionType.PHASE && input.getType() == EventType.PHASE) || input.getType() != EventType.PHASE;
            }
        });
        final Map<UUID, DateTime> result  = filteredEvents.iterator().hasNext() ? new HashMap<UUID, DateTime>() : ImmutableMap.<UUID, DateTime>of();
        for (SubscriptionBaseEvent cur : filteredEvents) {
            final DateTime targetDate = result.get(cur.getSubscriptionId());
            if (targetDate == null || targetDate.compareTo(cur.getEffectiveDate()) > 0) {
                result.put(cur.getSubscriptionId(), cur.getEffectiveDate());
            }
        }
        return result;
    }

    private DateTime getBundleStartDateWithSanity(final UUID bundleId, @Nullable final DefaultSubscriptionBase baseSubscription, final Plan plan,
                                                  final DateTime requestedDate, final DateTime effectiveDate, final InternalTenantContext context) throws SubscriptionBaseApiException, CatalogApiException {
        switch (plan.getProduct().getCategory()) {
            case BASE:
                if (baseSubscription != null &&
                    baseSubscription.getState() == EntitlementState.ACTIVE) {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_BP_EXISTS, bundleId);
                }
                return requestedDate;

            case ADD_ON:
                if (baseSubscription == null) {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_NO_BP, bundleId);
                }
                if (effectiveDate.isBefore(baseSubscription.getStartDate())) {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE, effectiveDate.toString(), baseSubscription.getStartDate().toString());
                }
                addonUtils.checkAddonCreationRights(baseSubscription, plan, requestedDate, context);
                return baseSubscription.getStartDate();

            case STANDALONE:
                if (baseSubscription != null) {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_BP_EXISTS, bundleId);
                }
                // Not really but we don't care, there is no alignment for STANDALONE subscriptions
                return requestedDate;

            default:
                throw new SubscriptionBaseError(String.format("Can't create subscription of type %s",
                                                              plan.getProduct().getCategory().toString()));
        }
    }

    private List<EffectiveSubscriptionInternalEvent> convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(final SubscriptionBase subscription,
                                                                                                                          final InternalTenantContext context, final List<SubscriptionBaseTransition> transitions) {
        return ImmutableList.<EffectiveSubscriptionInternalEvent>copyOf(Collections2.transform(transitions, new Function<SubscriptionBaseTransition, EffectiveSubscriptionInternalEvent>() {
            @Override
            @Nullable
            public EffectiveSubscriptionInternalEvent apply(@Nullable final SubscriptionBaseTransition input) {
                return new DefaultEffectiveSubscriptionEvent((SubscriptionBaseTransitionData) input, ((DefaultSubscriptionBase) subscription).getAlignStartDate(), null, context.getAccountRecordId(), context.getTenantRecordId());
            }
        }));
    }
}
