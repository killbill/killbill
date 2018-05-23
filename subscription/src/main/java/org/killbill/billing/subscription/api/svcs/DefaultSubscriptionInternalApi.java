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

package org.killbill.billing.subscription.api.svcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun.DryRunChangeReason;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOnsSpecifier;
import org.killbill.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionStatusDryRun;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.bcd.BCDEvent;
import org.killbill.billing.subscription.events.bcd.BCDEventData;
import org.killbill.billing.util.bcd.BillCycleDayCalculator;
import org.killbill.billing.util.cache.AccountIdFromBundleIdCacheLoader;
import org.killbill.billing.util.cache.BundleIdFromSubscriptionIdCacheLoader;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultSubscriptionInternalApi extends DefaultSubscriptionBaseCreateApi implements SubscriptionBaseInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionInternalApi.class);

    private final AddonUtils addonUtils;
    private final InternalCallContextFactory internalCallContextFactory;
    private final CatalogInternalApi catalogInternalApi;
    private final CacheController<UUID, UUID> accountIdCacheController;
    private final CacheController<UUID, UUID> bundleIdCacheController;

    public static final Comparator<SubscriptionBase> SUBSCRIPTIONS_COMPARATOR = new Comparator<SubscriptionBase>() {

        @Override
        public int compare(final SubscriptionBase o1, final SubscriptionBase o2) {
            if (o1.getCategory() == ProductCategory.BASE) {
                return -1;
            } else if (o2.getCategory() == ProductCategory.BASE) {
                return 1;
            } else {
                return ((DefaultSubscriptionBase) o1).getAlignStartDate().compareTo(((DefaultSubscriptionBase) o2).getAlignStartDate());
            }
        }
    };

    @Inject
    public DefaultSubscriptionInternalApi(final SubscriptionDao dao,
                                          final SubscriptionBaseApiService apiService,
                                          final Clock clock,
                                          final CatalogInternalApi catalogInternalApi,
                                          final AddonUtils addonUtils,
                                          final CacheControllerDispatcher cacheControllerDispatcher,
                                          final InternalCallContextFactory internalCallContextFactory) {
        super(dao, apiService, clock);
        this.addonUtils = addonUtils;
        this.internalCallContextFactory = internalCallContextFactory;
        this.catalogInternalApi = catalogInternalApi;
        this.accountIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_ID_FROM_BUNDLE_ID);
        this.bundleIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.BUNDLE_ID_FROM_SUBSCRIPTION_ID);
    }

    @Override
    public List<SubscriptionBaseWithAddOns> createBaseSubscriptionsWithAddOns(final Iterable<SubscriptionBaseWithAddOnsSpecifier> subscriptionWithAddOnsSpecifiers, final boolean renameCancelledBundleIfExist, final InternalCallContext context) throws SubscriptionBaseApiException {
        try {
            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);
            final CallContext callContext = internalCallContextFactory.createCallContext(context);

            return super.createBaseSubscriptionsWithAddOns(subscriptionWithAddOnsSpecifiers,
                                                           renameCancelledBundleIfExist,
                                                           catalog,
                                                           addonUtils,
                                                           accountIdCacheController,
                                                           bundleIdCacheController,
                                                           callContext,
                                                           context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public void cancelBaseSubscriptions(final Iterable<SubscriptionBase> subscriptions, final BillingActionPolicy policy, int accountBillCycleDayLocal, final InternalCallContext context) throws SubscriptionBaseApiException {

        try {
            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);
            apiService.cancelWithPolicyNoValidationAndCatalog(Iterables.<SubscriptionBase, DefaultSubscriptionBase>transform(subscriptions,
                                                                                                                             new Function<SubscriptionBase, DefaultSubscriptionBase>() {
                                                                                                                       @Override
                                                                                                                       public DefaultSubscriptionBase apply(final SubscriptionBase subscriptionBase) {
                                                                                                                           try {
                                                                                                                               return getDefaultSubscriptionBase(subscriptionBase, catalog, context);
                                                                                                                           } catch (final CatalogApiException e) {
                                                                                                                               throw new RuntimeException(e);
                                                                                                                           }
                                                                                                                       }
                                                                                                                   }),
                                                              policy,
                                                              accountBillCycleDayLocal,
                                                              catalog,
                                                              context);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }

    }

    @VisibleForTesting
    @Override
    public SubscriptionBaseBundle createBundleForAccount(final UUID accountId, final String bundleKey, final boolean renameCancelledBundleIfExist, final InternalCallContext context) throws SubscriptionBaseApiException {
        final DateTime now = context.getCreatedDate();
        final DefaultSubscriptionBaseBundle bundle = new DefaultSubscriptionBaseBundle(bundleKey, accountId, now, now, now, now);
        if (null != bundleKey && bundleKey.length() > 255) {
            throw new SubscriptionBaseApiException(ErrorCode.EXTERNAL_KEY_LIMIT_EXCEEDED);
        }
        try {
            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);
            return super.createBundleForAccount(accountId, bundleKey, renameCancelledBundleIfExist, catalog, accountIdCacheController, context);
        } catch (final CatalogApiException e) {
            throw new  SubscriptionBaseApiException(e);
        }
    }

    @Override
    public List<SubscriptionBaseBundle> getBundlesForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBaseBundle subscriptionBundlesForAccountAndKey = dao.getSubscriptionBundlesForAccountAndKey(accountId, bundleKey, context);
        return subscriptionBundlesForAccountAndKey != null ? ImmutableList.of(subscriptionBundlesForAccountAndKey) : ImmutableList.<SubscriptionBaseBundle>of();
    }

    @Override
    public List<SubscriptionBaseBundle> getBundlesForAccount(final UUID accountId, final InternalTenantContext context) {
        return dao.getSubscriptionBundleForAccount(accountId, context);
    }

    @Override
    public List<SubscriptionBaseBundle> getBundlesForKey(final String bundleKey, final InternalTenantContext context) {
        return dao.getSubscriptionBundlesForKey(bundleKey, context);
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
                                                      return SubscriptionBundleModelDao.toSubscriptionBundle(bundleModelDao);
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
                                                      return SubscriptionBundleModelDao.toSubscriptionBundle(bundleModelDao);
                                                  }
                                              }
                                             );

    }

    @Override
    public Iterable<UUID> getNonAOSubscriptionIdsForKey(final String bundleKey, final InternalTenantContext context) {
        return dao.getNonAOSubscriptionIdsForKey(bundleKey, context);
    }

    @Override
    public SubscriptionBaseBundle getActiveBundleForKey(final String bundleKey, final Catalog catalog, final InternalTenantContext context) {
        try {
            return super.getActiveBundleForKey(bundleKey, catalog, context);
        } catch (final CatalogApiException e) {
            log.warn("Failed to get subscriptions", e);
            return null;
        }
    }

    @Override
    public List<SubscriptionBase> getSubscriptionsForBundle(final UUID bundleId,
                                                            @Nullable final DryRunArguments dryRunArguments,
                                                            final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {
            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);
            final TenantContext tenantContext = internalCallContextFactory.createTenantContext(context);
            final List<DefaultSubscriptionBase> subscriptionsForBundle = super.getSubscriptionsForBundle(bundleId, dryRunArguments, catalog, addonUtils, tenantContext, context);
            return new ArrayList<SubscriptionBase>(subscriptionsForBundle);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public Map<UUID, List<SubscriptionBase>> getSubscriptionsForAccount(final Catalog catalog, final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {
            final Map<UUID, List<DefaultSubscriptionBase>> internalSubscriptions = dao.getSubscriptionsForAccount(catalog, context);
            final Map<UUID, List<SubscriptionBase>> result = new HashMap<UUID, List<SubscriptionBase>>();
            for (final UUID bundleId : internalSubscriptions.keySet()) {
                final List<DefaultSubscriptionBase> subscriptionsForApiUse = createSubscriptionsForApiUse(internalSubscriptions.get(bundleId));
                result.put(bundleId, new ArrayList<SubscriptionBase>(subscriptionsForApiUse));
            }
            return result;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public SubscriptionBase getBaseSubscription(final UUID bundleId, final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {
            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);
            return super.getBaseSubscription(bundleId, catalog, context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public SubscriptionBase getSubscriptionFromId(final UUID id, final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {

            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);

            final SubscriptionBase result = dao.getSubscriptionFromId(id, catalog, context);
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
    public void setChargedThroughDate(final UUID subscriptionId, final DateTime chargedThruDate, final InternalCallContext context) throws SubscriptionBaseApiException {
        try {

            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);

            final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) dao.getSubscriptionFromId(subscriptionId, catalog, context);
            final SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
                    .setChargedThroughDate(chargedThruDate);

            dao.updateChargedThroughDate(new DefaultSubscriptionBase(builder), context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public List<EffectiveSubscriptionInternalEvent> getAllTransitions(final SubscriptionBase subscription, final InternalTenantContext context) {
        final List<SubscriptionBaseTransition> transitions = subscription.getAllTransitions();
        return convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(subscription, context, transitions);
    }

    @Override
    public List<EffectiveSubscriptionInternalEvent> getBillingTransitions(final SubscriptionBase subscription, final InternalTenantContext context) {
        final List<SubscriptionBaseTransition> transitions = ((DefaultSubscriptionBase) subscription).getBillingTransitions();
        return convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(subscription, context, transitions);
    }

    @Override
    public DateTime getDryRunChangePlanEffectiveDate(final SubscriptionBase subscription,
                                                     final PlanPhaseSpecifier spec,
                                                     final DateTime requestedDateWithMs,
                                                     final BillingActionPolicy requestedPolicy,
                                                     final List<PlanPhasePriceOverride> overrides,
                                                     final InternalCallContext context) throws SubscriptionBaseApiException, CatalogApiException {
        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(context);
        final CallContext callContext = internalCallContextFactory.createCallContext(context);

        // verify the number of subscriptions (of the same kind) allowed per bundle
        final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);
        final DateTime effectiveDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : null;
        final DateTime effectiveCatalogDate = effectiveDate != null ? effectiveDate : context.getCreatedDate();
        final PlanPhasePriceOverridesWithCallContext overridesWithContext = new DefaultPlanPhasePriceOverridesWithCallContext(overrides, callContext);
        final Plan plan = catalog.createOrFindPlan(spec, overridesWithContext, effectiveCatalogDate, subscription.getStartDate());
        if (ProductCategory.ADD_ON.toString().equalsIgnoreCase(plan.getProduct().getCategory().toString())) {
            if (plan.getPlansAllowedInBundle() != -1
                && plan.getPlansAllowedInBundle() > 0
                && addonUtils.countExistingAddOnsWithSamePlanName(getSubscriptionsForBundle(subscription.getBundleId(), null, catalog, addonUtils, callContext, context), plan.getName())
                   >= plan.getPlansAllowedInBundle()) {
                // the plan can be changed to the new value, because it has reached its limit by bundle
                throw new SubscriptionBaseApiException(ErrorCode.SUB_CHANGE_AO_MAX_PLAN_ALLOWED_BY_BUNDLE, plan.getName());
            }
        }
        return apiService.dryRunChangePlan((DefaultSubscriptionBase) subscription, spec, effectiveDate, requestedPolicy, tenantContext);
    }

    @Override
    public List<EntitlementAOStatusDryRun> getDryRunChangePlanStatus(final UUID subscriptionId, @Nullable final String baseProductName, final DateTime requestedDate, final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {

            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, context);

            final SubscriptionBase subscription = dao.getSubscriptionFromId(subscriptionId, catalog, context);
            if (subscription == null) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_SUBSCRIPTION_ID, subscriptionId);
            }
            if (subscription.getCategory() != ProductCategory.BASE) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_CHANGE_DRY_RUN_NOT_BP);
            }

            final List<EntitlementAOStatusDryRun> result = new LinkedList<EntitlementAOStatusDryRun>();

            final List<DefaultSubscriptionBase> bundleSubscriptions = dao.getSubscriptions(subscription.getBundleId(), ImmutableList.<SubscriptionBaseEvent>of(), catalog, context);
            for (final SubscriptionBase cur : bundleSubscriptions) {
                if (cur.getId().equals(subscriptionId)) {
                    continue;
                }

                // If ADDON is cancelled, skip
                if (cur.getState() == EntitlementState.CANCELLED) {
                    continue;
                }

                final Product baseProduct = baseProductName != null ? catalog.findProduct(baseProductName, requestedDate) : null;

                final DryRunChangeReason reason;
                // If baseProductName is null, it's a cancellation dry-run. In this case, return all addons, so they are cancelled
                if (baseProduct != null && addonUtils.isAddonIncluded(baseProduct, cur.getCurrentPlan())) {
                    reason = DryRunChangeReason.AO_INCLUDED_IN_NEW_PLAN;
                } else if (baseProduct != null && addonUtils.isAddonAvailable(baseProduct, cur.getCurrentPlan())) {
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

    @Override
    public void updateBCD(final UUID subscriptionId, final int bcd, @Nullable final LocalDate effectiveFromDate, final InternalCallContext internalCallContext) throws SubscriptionBaseApiException {

        final Catalog catalog;
        try {
            catalog = catalogInternalApi.getFullCatalog(true, true, internalCallContext);
            final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) getSubscriptionFromId(subscriptionId, internalCallContext);
            final DateTime effectiveDate = getEffectiveDateForNewBCD(bcd, effectiveFromDate, internalCallContext);
            final BCDEvent bcdEvent = BCDEventData.createBCDEvent(subscription, effectiveDate, bcd);
            dao.createBCDChangeEvent(subscription, bcdEvent, catalog, internalCallContext);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public int getDefaultBillCycleDayLocal(final Map<UUID, Integer> bcdCache, final SubscriptionBase subscription, final SubscriptionBase baseSubscription, final PlanPhaseSpecifier planPhaseSpecifier, final int accountBillCycleDayLocal, final Catalog catalog, final InternalTenantContext context) throws SubscriptionBaseApiException {
        try {
            final BillingAlignment alignment = catalog.billingAlignment(planPhaseSpecifier, clock.getUTCNow(), subscription.getStartDate());
            return BillCycleDayCalculator.calculateBcdForAlignment(bcdCache, subscription, baseSubscription, alignment, context, accountBillCycleDayLocal);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public UUID getAccountIdFromBundleId(final UUID bundleId, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final CacheLoaderArgument arg = createAccountIdFromBundleIdCacheLoaderArgument(context);
        return accountIdCacheController.get(bundleId, arg);
    }

    @Override
    public UUID getBundleIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final CacheLoaderArgument arg = createBundleIdFromSubscriptionIdCacheLoaderArgument(context);
        return bundleIdCacheController.get(subscriptionId, arg);
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final UUID bundleId = getBundleIdFromSubscriptionId(subscriptionId, context);
        if (bundleId == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_GET_NO_BUNDLE_FOR_SUBSCRIPTION, subscriptionId);
        }
        final UUID accountId = getAccountIdFromBundleId(bundleId, context);
        if (accountId == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_ID, bundleId);
        }
        return accountId;
    }

    private CacheLoaderArgument createAccountIdFromBundleIdCacheLoaderArgument(final InternalTenantContext internalTenantContext) {
        final AccountIdFromBundleIdCacheLoader.LoaderCallback loaderCallback = new AccountIdFromBundleIdCacheLoader.LoaderCallback() {
            public UUID loadAccountId(final UUID bundleId, final InternalTenantContext internalTenantContext) {
                final SubscriptionBaseBundle bundle;
                try {
                    bundle = getBundleFromId(bundleId, internalTenantContext);
                } catch (final SubscriptionBaseApiException e) {
                    log.warn("Unable to retrieve bundle for id='{}'", bundleId);
                    return null;
                }
                return bundle.getAccountId();
            }
        };

        final Object[] args = {loaderCallback};
        return new CacheLoaderArgument(null, args, internalTenantContext);
    }

    private CacheLoaderArgument createBundleIdFromSubscriptionIdCacheLoaderArgument(final InternalTenantContext internalTenantContext) {
        final BundleIdFromSubscriptionIdCacheLoader.LoaderCallback loaderCallback = new BundleIdFromSubscriptionIdCacheLoader.LoaderCallback() {
            public UUID loadBundleId(final UUID subscriptionId, final InternalTenantContext internalTenantContext) {
                return dao.getBundleIdFromSubscriptionId(subscriptionId, internalTenantContext);
            }
        };

        final Object[] args = {loaderCallback};
        return new CacheLoaderArgument(null, args, internalTenantContext);
    }

    @VisibleForTesting
    DateTime getEffectiveDateForNewBCD(final int bcd, @Nullable final LocalDate effectiveFromDate, final InternalCallContext internalCallContext) {
        if (internalCallContext.getAccountRecordId() == null) {
            throw new IllegalStateException("Need to have a valid context with accountRecordId");
        }

        // Today as seen by this account
        final LocalDate startDate = effectiveFromDate != null ? effectiveFromDate : internalCallContext.toLocalDate(internalCallContext.getCreatedDate());

        // We want to compute a LocalDate in account TZ which maps to the provided 'bcd' and then compute an effectiveDate for when that BCD_CHANGE event needs to be triggered
        //
        // There is a bit of complexity to make sure the date we chose exists (e.g: a BCD of 31 in a february month would not make sense).
        final int currentDay = startDate.getDayOfMonth();
        final int lastDayOfMonth = startDate.dayOfMonth().getMaximumValue();

        final LocalDate requestedDate;
        if (bcd < currentDay) {
            final LocalDate startDatePlusOneMonth = startDate.plusMonths(1);
            final int lastDayOfNextMonth = startDatePlusOneMonth.dayOfMonth().getMaximumValue();
            final int originalBCDORLastDayOfMonth = bcd <= lastDayOfNextMonth ? bcd : lastDayOfNextMonth;
            requestedDate = new LocalDate(startDatePlusOneMonth.getYear(), startDatePlusOneMonth.getMonthOfYear(), originalBCDORLastDayOfMonth);
        } else if (bcd == currentDay && effectiveFromDate == null) {
            // will default to immediate event
            requestedDate = null;
        } else if (bcd <= lastDayOfMonth) {
            requestedDate = new LocalDate(startDate.getYear(), startDate.getMonthOfYear(), bcd);
        } else /* bcd > lastDayOfMonth && bcd > currentDay */ {
            requestedDate = new LocalDate(startDate.getYear(), startDate.getMonthOfYear(), lastDayOfMonth);
        }
        return requestedDate == null ? internalCallContext.getCreatedDate() : internalCallContext.toUTCDateTime(requestedDate);
    }

    private List<EffectiveSubscriptionInternalEvent> convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(final SubscriptionBase subscription,
                                                                                                                          final InternalTenantContext context, final Collection<SubscriptionBaseTransition> transitions) {
        return ImmutableList.<EffectiveSubscriptionInternalEvent>copyOf(Collections2.transform(transitions, new Function<SubscriptionBaseTransition, EffectiveSubscriptionInternalEvent>() {
            @Override
            @Nullable
            public EffectiveSubscriptionInternalEvent apply(@Nullable final SubscriptionBaseTransition input) {
                return new DefaultEffectiveSubscriptionEvent((SubscriptionBaseTransitionData) input, ((DefaultSubscriptionBase) subscription).getAlignStartDate(), null, context.getAccountRecordId(), context.getTenantRecordId());
            }
        }));
    }

    // For forward-compatibility
    private DefaultSubscriptionBase getDefaultSubscriptionBase(final Entity subscriptionBase, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        if (subscriptionBase instanceof DefaultSubscriptionBase) {
            return (DefaultSubscriptionBase) subscriptionBase;
        } else {
            // Safe cast, see above
            return (DefaultSubscriptionBase) dao.getSubscriptionFromId(subscriptionBase.getId(), catalog, context);
        }
    }
}
