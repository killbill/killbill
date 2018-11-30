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

package org.killbill.billing.subscription.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.subscription.api.svcs.DefaultPlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.subscription.api.svcs.DefaultSubscriptionInternalApi;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class SubscriptionApiBase {

    protected final SubscriptionDao dao;

    protected final SubscriptionBaseApiService apiService;
    protected final Clock clock;

    public SubscriptionApiBase(final SubscriptionDao dao, final SubscriptionBaseApiService apiService, final Clock clock) {
        this.dao = dao;
        this.apiService = apiService;
        this.clock = clock;
    }

    protected SubscriptionBaseBundle getActiveBundleForKey(final String bundleKey, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        final List<SubscriptionBaseBundle> existingBundles = dao.getSubscriptionBundlesForKey(bundleKey, context);
        for (final SubscriptionBaseBundle cur : existingBundles) {
            final List<DefaultSubscriptionBase> subscriptions = dao.getSubscriptions(cur.getId(), ImmutableList.<SubscriptionBaseEvent>of(), catalog, context);
            for (final SubscriptionBase s : subscriptions) {
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

    protected SubscriptionBase getBaseSubscription(final UUID bundleId,
                                                   final Catalog catalog,
                                                   final InternalTenantContext context) throws SubscriptionBaseApiException, CatalogApiException {
        final SubscriptionBase result = dao.getBaseSubscription(bundleId, catalog, context);
        if (result == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION, bundleId);
        }
        return createSubscriptionForApiUse(result);
    }

    protected List<DefaultSubscriptionBase> getSubscriptionsForBundle(final UUID bundleId,
                                                                      @Nullable final DryRunArguments dryRunArguments,
                                                                      final Catalog catalog,
                                                                      final AddonUtils addonUtils,
                                                                      final TenantContext tenantContext,
                                                                      final InternalTenantContext context) throws SubscriptionBaseApiException, CatalogApiException {

        final List<SubscriptionBaseEvent> outputDryRunEvents = new ArrayList<SubscriptionBaseEvent>();
        final List<DefaultSubscriptionBase> outputSubscriptions = new ArrayList<DefaultSubscriptionBase>();

        populateDryRunEvents(bundleId, dryRunArguments, outputDryRunEvents, outputSubscriptions, catalog, addonUtils, tenantContext, context);
        final List<DefaultSubscriptionBase> result = dao.getSubscriptions(bundleId, outputDryRunEvents, catalog, context);
        if (result != null && !result.isEmpty()) {
            outputSubscriptions.addAll(result);
        }
        Collections.sort(outputSubscriptions, DefaultSubscriptionInternalApi.SUBSCRIPTIONS_COMPARATOR);

        return createSubscriptionsForApiUse(outputSubscriptions);
    }

    private void populateDryRunEvents(@Nullable final UUID bundleId,
                                      @Nullable final DryRunArguments dryRunArguments,
                                      final Collection<SubscriptionBaseEvent> outputDryRunEvents,
                                      final Collection<DefaultSubscriptionBase> outputSubscriptions,
                                      final Catalog catalog,
                                      final AddonUtils addonUtils,
                                      final TenantContext tenantContext,
                                      final InternalTenantContext context) throws SubscriptionBaseApiException, CatalogApiException {
        if (dryRunArguments == null || dryRunArguments.getAction() == null) {
            return;
        }

        final DateTime utcNow = clock.getUTCNow();
        List<SubscriptionBaseEvent> dryRunEvents = null;
        final EntitlementSpecifier entitlementSpecifier = dryRunArguments.getEntitlementSpecifier();
        final PlanPhaseSpecifier inputSpec = entitlementSpecifier.getPlanPhaseSpecifier();
        final boolean isInputSpecNullOrEmpty = inputSpec == null ||
                                               (inputSpec.getPlanName() == null && inputSpec.getProductName() == null && inputSpec.getBillingPeriod() == null);

        // Create an overridesWithContext with a null context to indicate this is dryRun and no price overridden plan should be created.
        final PlanPhasePriceOverridesWithCallContext overridesWithContext = new DefaultPlanPhasePriceOverridesWithCallContext(entitlementSpecifier.getOverrides(), null);
        final Plan plan = isInputSpecNullOrEmpty ?
                          null :
                          catalog.createOrFindPlan(inputSpec, overridesWithContext, utcNow);

        switch (dryRunArguments.getAction()) {
            case START_BILLING:
                final SubscriptionBase baseSubscription = dao.getBaseSubscription(bundleId, catalog, context);
                final DateTime startEffectiveDate = dryRunArguments.getEffectiveDate() != null ? context.toUTCDateTime(dryRunArguments.getEffectiveDate()) : utcNow;
                final DateTime bundleStartDate = getBundleStartDateWithSanity(bundleId, baseSubscription, plan, startEffectiveDate, addonUtils, context);
                final UUID subscriptionId = UUIDs.randomUUID();
                dryRunEvents = apiService.getEventsOnCreation(subscriptionId, startEffectiveDate, bundleStartDate, plan, inputSpec.getPhaseType(), plan.getPriceListName(),
                                                              startEffectiveDate, entitlementSpecifier.getBillCycleDay(), catalog, context);
                final SubscriptionBuilder builder = new SubscriptionBuilder()
                        .setId(subscriptionId)
                        .setBundleId(bundleId)
                        .setBundleExternalKey(null)
                        .setCategory(plan.getProduct().getCategory())
                        .setBundleStartDate(bundleStartDate)
                        .setAlignStartDate(startEffectiveDate);
                final DefaultSubscriptionBase newSubscription = new DefaultSubscriptionBase(builder, apiService, clock);
                newSubscription.rebuildTransitions(dryRunEvents, catalog);
                outputSubscriptions.add(newSubscription);
                break;

            case CHANGE:
                final DefaultSubscriptionBase subscriptionForChange = (DefaultSubscriptionBase) dao.getSubscriptionFromId(dryRunArguments.getSubscriptionId(), catalog, context);

                DateTime changeEffectiveDate = getDryRunEffectiveDate(dryRunArguments.getEffectiveDate(), subscriptionForChange, context);
                if (changeEffectiveDate == null) {
                    BillingActionPolicy policy = dryRunArguments.getBillingActionPolicy();
                    if (policy == null) {
                        final PlanChangeResult planChangeResult = apiService.getPlanChangeResult(subscriptionForChange, inputSpec, utcNow, tenantContext);
                        policy = planChangeResult.getPolicy();
                    }
                    // We pass null for billingAlignment, accountTimezone, account BCD because this is not available which means that dryRun with START_OF_TERM BillingPolicy will fail
                    changeEffectiveDate = subscriptionForChange.getPlanChangeEffectiveDate(policy, null, -1, context);
                }
                dryRunEvents = apiService.getEventsOnChangePlan(subscriptionForChange, plan, plan.getPriceListName(), changeEffectiveDate, true, entitlementSpecifier.getBillCycleDay(), catalog, context);
                break;

            case STOP_BILLING:
                final DefaultSubscriptionBase subscriptionForCancellation = (DefaultSubscriptionBase) dao.getSubscriptionFromId(dryRunArguments.getSubscriptionId(), catalog, context);

                DateTime cancelEffectiveDate = getDryRunEffectiveDate(dryRunArguments.getEffectiveDate(), subscriptionForCancellation, context);
                if (dryRunArguments.getEffectiveDate() == null) {
                    BillingActionPolicy policy = dryRunArguments.getBillingActionPolicy();
                    if (policy == null) {
                        final Plan currentPlan = subscriptionForCancellation.getCurrentPlan();
                        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(currentPlan.getName(),
                                                                               subscriptionForCancellation.getCurrentPhase().getPhaseType());
                        policy = catalog.planCancelPolicy(spec, clock.getUTCNow(), subscriptionForCancellation.getStartDate());
                    }
                    // We pass null for billingAlignment, accountTimezone, account BCD because this is not available which means that dryRun with START_OF_TERM BillingPolicy will fail
                    cancelEffectiveDate = subscriptionForCancellation.getPlanChangeEffectiveDate(policy, null, -1, context);
                }
                dryRunEvents = apiService.getEventsOnCancelPlan(subscriptionForCancellation, cancelEffectiveDate, true, catalog, context);
                break;

            default:
                throw new IllegalArgumentException("Unexpected dryRunArguments action " + dryRunArguments.getAction());
        }

        if (dryRunEvents != null && !dryRunEvents.isEmpty()) {
            outputDryRunEvents.addAll(dryRunEvents);
        }
    }

    private DateTime getDryRunEffectiveDate(@Nullable final LocalDate inputDate, final SubscriptionBase subscription, final InternalTenantContext context) {
        if (inputDate == null) {
            return null;
        }

        // We first use context account reference time to get a candidate)
        final DateTime tmp = context.toUTCDateTime(inputDate);
        // If we realize that the candidate is on the same LocalDate boundary as the subscription startDate but a bit prior we correct it to avoid weird things down the line
        if (inputDate.compareTo(context.toLocalDate(subscription.getStartDate())) == 0 && tmp.compareTo(subscription.getStartDate()) < 0) {
            return subscription.getStartDate();
        } else {
            return tmp;
        }
    }

    protected DateTime getBundleStartDateWithSanity(final UUID bundleId,
                                                    @Nullable final SubscriptionBase baseSubscription,
                                                    final Plan plan,
                                                    final DateTime effectiveDate,
                                                    final AddonUtils addonUtils,
                                                    final InternalTenantContext context) throws SubscriptionBaseApiException, CatalogApiException {
        switch (plan.getProduct().getCategory()) {
            case BASE:
                if (baseSubscription != null &&
                    (baseSubscription.getState() == EntitlementState.ACTIVE || baseSubscription.getState() == EntitlementState.PENDING)) {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_BP_EXISTS, bundleId);
                }
                return effectiveDate;

            case ADD_ON:
                if (baseSubscription == null) {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_NO_BP, bundleId);
                }
                if (effectiveDate.isBefore(baseSubscription.getStartDate())) {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE, effectiveDate.toString(), baseSubscription.getStartDate().toString());
                }
                addonUtils.checkAddonCreationRights(baseSubscription, plan, effectiveDate, context);
                return baseSubscription.getStartDate();

            case STANDALONE:
                if (baseSubscription != null) {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_BP_EXISTS, bundleId);
                }
                // Not really but we don't care, there is no alignment for STANDALONE subscriptions
                return effectiveDate;

            default:
                throw new SubscriptionBaseError(String.format("Can't create subscription of type %s",
                                                              plan.getProduct().getCategory().toString()));
        }
    }

    protected SubscriptionBaseBundle createBundleForAccount(final UUID accountId,
                                                            final String bundleKey,
                                                            final boolean renameCancelledBundleIfExist,
                                                            final Catalog catalog,
                                                            final CacheController<UUID, UUID> accountIdCacheController,
                                                            final InternalCallContext context) throws SubscriptionBaseApiException {
        final DateTime now = context.getCreatedDate();
        final DefaultSubscriptionBaseBundle bundle = new DefaultSubscriptionBaseBundle(bundleKey, accountId, now, now, now, now);
        if (null != bundleKey && bundleKey.length() > 255) {
            throw new SubscriptionBaseApiException(ErrorCode.EXTERNAL_KEY_LIMIT_EXCEEDED);
        }

        final SubscriptionBaseBundle subscriptionBundle = dao.createSubscriptionBundle(bundle, catalog, renameCancelledBundleIfExist, context);
        accountIdCacheController.putIfAbsent(bundle.getId(), accountId);

        return subscriptionBundle;
    }

    protected List<DefaultSubscriptionBase> createSubscriptionsForApiUse(final Collection<DefaultSubscriptionBase> internalSubscriptions) {
        return new ArrayList<DefaultSubscriptionBase>(Collections2.transform(internalSubscriptions, new Function<SubscriptionBase, DefaultSubscriptionBase>() {
            @Override
            public DefaultSubscriptionBase apply(final SubscriptionBase subscription) {
                return createSubscriptionForApiUse(subscription);
            }
        }));
    }

    protected DefaultSubscriptionBase createSubscriptionForApiUse(final SubscriptionBase internalSubscription) {
        return new DefaultSubscriptionBase((DefaultSubscriptionBase) internalSubscription, apiService, clock);
    }

    protected DefaultSubscriptionBase createSubscriptionForApiUse(final SubscriptionBuilder builder,
                                                                  final List<SubscriptionBaseEvent> events,
                                                                  final Catalog catalog,
                                                                  final InternalTenantContext context) throws CatalogApiException {
        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(builder, apiService, clock);
        if (!events.isEmpty()) {
            subscription.rebuildTransitions(events, catalog);
        }
        return subscription;
    }
}
