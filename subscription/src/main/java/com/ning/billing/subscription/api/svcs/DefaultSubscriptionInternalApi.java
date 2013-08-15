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

package com.ning.billing.subscription.api.svcs;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.clock.DefaultClock;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.subscription.api.SubscriptionApiBase;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBase;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBaseApiService;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.DefaultSubscriptionStatusDryRun;
import com.ning.billing.subscription.api.user.EntitlementStatusDryRun;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransitionData;
import com.ning.billing.subscription.api.user.SubscriptionBuilder;
import com.ning.billing.subscription.api.user.EntitlementStatusDryRun.DryRunChangeReason;
import com.ning.billing.subscription.engine.addon.AddonUtils;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.exceptions.SubscriptionBaseError;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultSubscriptionInternalApi extends SubscriptionApiBase implements SubscriptionBaseInternalApi {

    private final Logger log = LoggerFactory.getLogger(DefaultSubscriptionInternalApi.class);

    private final AddonUtils addonUtils;

    @Inject
    public DefaultSubscriptionInternalApi(final SubscriptionDao dao,
                                          final DefaultSubscriptionBaseApiService apiService,
                                          final Clock clock,
                                          final CatalogService catalogService,
                                          final AddonUtils addonUtils) {
        super(dao, apiService, clock, catalogService);
        this.addonUtils = addonUtils;
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
                        } else {
                            // If we do create on an existing CANCELLED BP, this is equivalent to call recreate on that SubscriptionBase.
                            final SubscriptionBase recreatedSubscriptionForApiUse = createSubscriptionForApiUse(baseSubscription);
                            recreatedSubscriptionForApiUse.recreate(spec, requestedDate, context.toCallContext());
                            return recreatedSubscriptionForApiUse;
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

            return apiService.createPlan(new SubscriptionBuilder()
                                                 .setId(UUID.randomUUID())
                                                 .setBundleId(bundleId)
                                                 .setCategory(plan.getProduct().getCategory())
                                                 .setBundleStartDate(bundleStartDate)
                                                 .setAlignStartDate(effectiveDate),
                                         plan, spec.getPhaseType(), realPriceList, requestedDate, effectiveDate, now, context.toCallContext());
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public SubscriptionBaseBundle createBundleForAccount(final UUID accountId, final String bundleName, final InternalCallContext context) throws SubscriptionBaseApiException {
        final DefaultSubscriptionBaseBundle bundle = new DefaultSubscriptionBaseBundle(bundleName, accountId, clock.getUTCNow());
        return dao.createSubscriptionBundle(bundle, context);
    }

    @Override
    public SubscriptionBaseBundle getBundleForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBaseBundle result = dao.getSubscriptionBundleFromAccountAndKey(accountId, bundleKey, context);
        if (result == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_KEY, bundleKey);
        }
        return result;
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
    public List<SubscriptionBase> getSubscriptionsForBundle(UUID bundleId,
                                                            InternalTenantContext context) {
        final List<SubscriptionBase> internalSubscriptions = dao.getSubscriptions(bundleId, context);
        return createSubscriptionsForApiUse(internalSubscriptions);
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
                .setChargedThroughDate(chargedThruDate)
                .setPaidThroughDate(subscription.getPaidThroughDate());

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
    public List<EntitlementStatusDryRun> getDryRunChangePlanStatus(final UUID subscriptionId, @Nullable final String baseProductName, final DateTime requestedDate, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBase subscription = dao.getSubscriptionFromId(subscriptionId, context);
        if (subscription == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_SUBSCRIPTION_ID, subscriptionId);
        }
        if (subscription.getCategory() != ProductCategory.BASE) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CHANGE_DRY_RUN_NOT_BP);
        }

        final List<EntitlementStatusDryRun> result = new LinkedList<EntitlementStatusDryRun>();

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
            final EntitlementStatusDryRun status = new DefaultSubscriptionStatusDryRun(cur.getId(),
                                                                                        cur.getCurrentPlan().getProduct().getName(),
                                                                                        cur.getCurrentPhase().getPhaseType(),
                                                                                        cur.getCurrentPlan().getBillingPeriod(),
                                                                                        cur.getCurrentPriceList().getName(), reason);
            result.add(status);
        }
        return result;
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
