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

package org.killbill.billing.subscription.api.svcs;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun.DryRunChangeReason;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.subscription.api.SubscriptionApiBase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
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
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultSubscriptionInternalApi extends SubscriptionApiBase implements SubscriptionBaseInternalApi {

    private final Logger log = LoggerFactory.getLogger(DefaultSubscriptionInternalApi.class);

    private final AddonUtils addonUtils;
    private final NonEntityDao nonEntityDao;
    private final CacheControllerDispatcher controllerDispatcher;

    @Inject
    public DefaultSubscriptionInternalApi(final SubscriptionDao dao,
                                          final DefaultSubscriptionBaseApiService apiService,
                                          final Clock clock,
                                          final CatalogService catalogService,
                                          final AddonUtils addonUtils,
                                          final NonEntityDao nonEntityDao,
                                          final CacheControllerDispatcher controllerDispatcher) {
        super(dao, apiService, clock, catalogService);
        this.addonUtils = addonUtils;
        this.nonEntityDao = nonEntityDao;
        this.controllerDispatcher = controllerDispatcher;
    }

    @Override
    public SubscriptionBase createSubscription(final UUID bundleId, final PlanPhaseSpecifier spec, final DateTime requestedDateWithMs, final InternalCallContext context) throws SubscriptionBaseApiException {
        try {
            final String realPriceList = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            final DateTime now = clock.getUTCNow();
            final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;
            if (requestedDate.isAfter(now)) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE, now.toString(), requestedDate.toString());
            }
            final DateTime effectiveDate = requestedDate;

            final Catalog catalog = catalogService.getFullCatalog();
            final Plan plan = catalog.findPlan(spec.getProductName(), spec.getBillingPeriod(), realPriceList, requestedDate);

            final PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new SubscriptionBaseError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                                                              spec.getProductName(), spec.getBillingPeriod().toString(), realPriceList));
            }

            final SubscriptionBaseBundle bundle = dao.getSubscriptionBundleFromId(bundleId, context);
            if (bundle == null) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_NO_BUNDLE, bundleId);
            }

            DateTime bundleStartDate = null;
            final DefaultSubscriptionBase baseSubscription = (DefaultSubscriptionBase) dao.getBaseSubscription(bundleId, context);
            switch (plan.getProduct().getCategory()) {
                case BASE:
                    if (baseSubscription != null) {
                        if (baseSubscription.getState() == EntitlementState.ACTIVE) {
                            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_BP_EXISTS, bundleId);
                        }
                    }
                    bundleStartDate = requestedDate;
                    break;
                case ADD_ON:
                    if (baseSubscription == null) {
                        throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_NO_BP, bundleId);
                    }
                    if (effectiveDate.isBefore(baseSubscription.getStartDate())) {
                        throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE, effectiveDate.toString(), baseSubscription.getStartDate().toString());
                    }
                    addonUtils.checkAddonCreationRights(baseSubscription, plan);
                    bundleStartDate = baseSubscription.getStartDate();
                    break;
                case STANDALONE:
                    if (baseSubscription != null) {
                        throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_BP_EXISTS, bundleId);
                    }
                    // Not really but we don't care, there is no alignment for STANDALONE subscriptions
                    bundleStartDate = requestedDate;
                    break;
                default:
                    throw new SubscriptionBaseError(String.format("Can't create subscription of type %s",
                                                                  plan.getProduct().getCategory().toString()));
            }

            final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT, controllerDispatcher.getCacheController(CacheType.OBJECT_ID));
            return apiService.createPlan(new SubscriptionBuilder()
                                                 .setId(UUID.randomUUID())
                                                 .setBundleId(bundleId)
                                                 .setCategory(plan.getProduct().getCategory())
                                                 .setBundleStartDate(bundleStartDate)
                                                 .setAlignStartDate(effectiveDate),
                                         plan, spec.getPhaseType(), realPriceList, requestedDate, effectiveDate, now, context.toCallContext(tenantId));
        } catch (CatalogApiException e) {
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
        for (SubscriptionBaseBundle cur : existingBundles) {
            final List<SubscriptionBase> subscriptions = dao.getSubscriptions(cur.getId(), context);
            for (SubscriptionBase s : subscriptions) {
                if (s.getCategory() == ProductCategory.ADD_ON) {
                    continue;
                }
                if (s.getEndDate() == null || s.getEndDate().compareTo(clock.getUTCNow()) > 0) {
                    return cur;
                }
            }
        }
        return null;
    }

    @Override
    public List<SubscriptionBase> getSubscriptionsForBundle(UUID bundleId,
                                                            InternalTenantContext context) {
        final List<SubscriptionBase> internalSubscriptions = dao.getSubscriptions(bundleId, context);
        return createSubscriptionsForApiUse(internalSubscriptions);
    }

    @Override
    public Map<UUID, List<SubscriptionBase>> getSubscriptionsForAccount(final InternalTenantContext context) {
        final Map<UUID, List<SubscriptionBase>> internalSubscriptions = dao.getSubscriptionsForAccount(context);
        final Map<UUID, List<SubscriptionBase>> result = new HashMap<UUID, List<SubscriptionBase>>();
        for (final UUID bundleId : internalSubscriptions.keySet()) {
            result.put(bundleId, createSubscriptionsForApiUse(internalSubscriptions.get(bundleId)));
        }
        return result;
    }

    @Override
    public SubscriptionBase getBaseSubscription(UUID bundleId,
                                                InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBase result = dao.getBaseSubscription(bundleId, context);
        if (result == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION, bundleId);
        }
        return createSubscriptionForApiUse(result);
    }

    @Override

    public SubscriptionBase getSubscriptionFromId(UUID id,
                                                  InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBase result = dao.getSubscriptionFromId(id, context);
        if (result == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_SUBSCRIPTION_ID, id);
        }
        return createSubscriptionForApiUse(result);
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
    public void setChargedThroughDate(UUID subscriptionId,
                                      DateTime chargedThruDate, InternalCallContext context) {
        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) dao.getSubscriptionFromId(subscriptionId, context);
        final SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
                .setChargedThroughDate(chargedThruDate);

        dao.updateChargedThroughDate(new DefaultSubscriptionBase(builder), context);
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
    public DateTime getNextBillingDate(final UUID accountId, final InternalTenantContext context) {
        final List<SubscriptionBaseBundle> bundles = getBundlesForAccount(accountId, context);
        DateTime result = null;
        for (final SubscriptionBaseBundle bundle : bundles) {
            final List<SubscriptionBase> subscriptions = getSubscriptionsForBundle(bundle.getId(), context);
            for (final SubscriptionBase subscription : subscriptions) {
                final DateTime chargedThruDate = subscription.getChargedThroughDate();
                if (result == null ||
                    (chargedThruDate != null && chargedThruDate.isBefore(result))) {
                    result = subscription.getChargedThroughDate();
                }
            }
        }
        return result;
    }

    @Override
    public List<EntitlementAOStatusDryRun> getDryRunChangePlanStatus(final UUID subscriptionId, @Nullable final String baseProductName, final DateTime requestedDate, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBase subscription = dao.getSubscriptionFromId(subscriptionId, context);
        if (subscription == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_SUBSCRIPTION_ID, subscriptionId);
        }
        if (subscription.getCategory() != ProductCategory.BASE) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CHANGE_DRY_RUN_NOT_BP);
        }

        final List<EntitlementAOStatusDryRun> result = new LinkedList<EntitlementAOStatusDryRun>();

        final List<SubscriptionBase> bundleSubscriptions = dao.getSubscriptions(subscription.getBundleId(), context);
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
            if (baseProductName != null && addonUtils.isAddonIncludedFromProdName(baseProductName, requestedDate, cur.getCurrentPlan())) {
                reason = DryRunChangeReason.AO_INCLUDED_IN_NEW_PLAN;
            } else if (baseProductName != null && addonUtils.isAddonAvailableFromProdName(baseProductName, requestedDate, cur.getCurrentPlan())) {
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
    }

    @Override
    public void updateExternalKey(final UUID bundleId, final String newExternalKey, final InternalCallContext context) {
        dao.updateBundleExternalKey(bundleId, newExternalKey, context);
    }

    private List<EffectiveSubscriptionInternalEvent> convertEffectiveSubscriptionInternalEventFromSubscriptionTransitions(final SubscriptionBase subscription,
                                                                                                                          final InternalTenantContext context, final List<SubscriptionBaseTransition> transitions) {
        return ImmutableList.<EffectiveSubscriptionInternalEvent>copyOf(Collections2.transform(transitions, new Function<SubscriptionBaseTransition, EffectiveSubscriptionInternalEvent>() {
            @Override
            @Nullable
            public EffectiveSubscriptionInternalEvent apply(@Nullable SubscriptionBaseTransition input) {
                return new DefaultEffectiveSubscriptionEvent((SubscriptionBaseTransitionData) input, ((DefaultSubscriptionBase) subscription).getAlignStartDate(), null, context.getAccountRecordId(), context.getTenantRecordId());
            }
        }));
    }
}
