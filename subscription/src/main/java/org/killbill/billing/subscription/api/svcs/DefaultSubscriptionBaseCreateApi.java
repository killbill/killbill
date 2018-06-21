/*
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
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.subscription.api.SubscriptionApiBase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOnsSpecifier;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionAndAddOnsSpecifier;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.api.user.SubscriptionSpecifier;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSubscriptionBaseCreateApi extends SubscriptionApiBase {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionBaseCreateApi.class);

    DefaultSubscriptionBaseCreateApi(final SubscriptionDao dao, final SubscriptionBaseApiService apiService, final Clock clock) {
        super(dao, apiService, clock);
    }

    List<SubscriptionBaseWithAddOns> createBaseSubscriptionsWithAddOns(final Iterable<SubscriptionBaseWithAddOnsSpecifier> baseAndAddOnEntitlementsSpecifiers,
                                                                       final boolean renameCancelledBundleIfExist,
                                                                       final Catalog catalog,
                                                                       final AddonUtils addonUtils,
                                                                       final CacheController<UUID, UUID> accountIdCacheController,
                                                                       final CacheController<UUID, UUID> bundleIdCacheController,
                                                                       final CallContext callContext,
                                                                       final InternalCallContext context) throws SubscriptionBaseApiException, CatalogApiException {
        // Prepare the subscription specifiers from the entitlement specifiers
        final Collection<SubscriptionAndAddOnsSpecifier> baseAndAddOnSubscriptionsSpecifiers = new ArrayList<SubscriptionAndAddOnsSpecifier>();
        for (final SubscriptionBaseWithAddOnsSpecifier baseAndAddOnEntitlementsSpecifier : baseAndAddOnEntitlementsSpecifiers) {
            prepareSubscriptionAndAddOnsSpecifier(baseAndAddOnSubscriptionsSpecifiers,
                                                  baseAndAddOnEntitlementsSpecifier,
                                                  renameCancelledBundleIfExist,
                                                  catalog,
                                                  addonUtils,
                                                  accountIdCacheController,
                                                  callContext,
                                                  context);
        }

        // Create the subscriptions
        final List<SubscriptionBaseWithAddOns> subscriptionBaseWithAddOns = apiService.createPlansWithAddOns(callContext.getAccountId(),
                                                                                                             baseAndAddOnSubscriptionsSpecifiers,
                                                                                                             catalog,
                                                                                                             callContext);

        // Populate the caches
        for (final SubscriptionBaseWithAddOns subscriptionBaseWithAO : subscriptionBaseWithAddOns) {
            for (final SubscriptionBase subscriptionBase : subscriptionBaseWithAO.getSubscriptionBaseList()) {
                bundleIdCacheController.putIfAbsent(subscriptionBase.getId(), subscriptionBaseWithAO.getBundle().getId());
            }
        }

        return subscriptionBaseWithAddOns;
    }

    private void prepareSubscriptionAndAddOnsSpecifier(final Collection<SubscriptionAndAddOnsSpecifier> subscriptionAndAddOns,
                                                       final SubscriptionBaseWithAddOnsSpecifier subscriptionBaseWithAddOnsSpecifier,
                                                       final boolean renameCancelledBundleIfExist,
                                                       final Catalog catalog,
                                                       final AddonUtils addonUtils,
                                                       final CacheController<UUID, UUID> accountIdCacheController,
                                                       final CallContext callContext,
                                                       final InternalCallContext context) throws SubscriptionBaseApiException, CatalogApiException {
        SubscriptionBaseBundle bundle = getBundleWithSanity(subscriptionBaseWithAddOnsSpecifier, catalog, callContext, context);

        final DateTime billingRequestedDateRaw = (subscriptionBaseWithAddOnsSpecifier.getBillingEffectiveDate() != null) ?
                                                 context.toUTCDateTime(subscriptionBaseWithAddOnsSpecifier.getBillingEffectiveDate()) : context.getCreatedDate();

        final SubscriptionBase baseSubscription;
        final DateTime billingRequestedDate;
        if (bundle != null) {
            baseSubscription = dao.getBaseSubscription(bundle.getId(), catalog, context);
            billingRequestedDate = computeActualBillingRequestedDate(bundle, billingRequestedDateRaw, baseSubscription, catalog, context);
        } else {
            baseSubscription = null;
            billingRequestedDate = billingRequestedDateRaw;
        }

        final List<EntitlementSpecifier> reorderedSpecifiers = new ArrayList<EntitlementSpecifier>();
        final List<Plan> createdOrRetrievedPlans = new ArrayList<Plan>();
        final boolean hasBaseOrStandalonePlanSpecifier = createPlansIfNeededAndReorderBPOrStandaloneSpecFirstWithSanity(subscriptionBaseWithAddOnsSpecifier,
                                                                                                                        catalog,
                                                                                                                        billingRequestedDate,
                                                                                                                        reorderedSpecifiers,
                                                                                                                        createdOrRetrievedPlans,
                                                                                                                        callContext);

        if (hasBaseOrStandalonePlanSpecifier && baseSubscription != null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_BP_EXISTS, bundle.getExternalKey());
        }

        if (bundle == null && hasBaseOrStandalonePlanSpecifier) {
            bundle = createBundleForAccount(callContext.getAccountId(),
                                            subscriptionBaseWithAddOnsSpecifier.getBundleExternalKey(),
                                            renameCancelledBundleIfExist,
                                            catalog,
                                            accountIdCacheController,
                                            context);
        } else if (bundle == null) {
            log.warn("Invalid specifier: {}", subscriptionBaseWithAddOnsSpecifier);
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_NO_BP, subscriptionBaseWithAddOnsSpecifier.getBundleExternalKey());
        }

        final List<SubscriptionSpecifier> subscriptionSpecifiers = verifyAndBuildSubscriptionSpecifiers(bundle,
                                                                                                        hasBaseOrStandalonePlanSpecifier,
                                                                                                        reorderedSpecifiers,
                                                                                                        createdOrRetrievedPlans,
                                                                                                        subscriptionBaseWithAddOnsSpecifier.isMigrated(),
                                                                                                        billingRequestedDate,
                                                                                                        catalog,
                                                                                                        addonUtils,
                                                                                                        callContext,
                                                                                                        context);
        final SubscriptionAndAddOnsSpecifier subscriptionAndAddOnsSpecifier = new SubscriptionAndAddOnsSpecifier(bundle,
                                                                                                                 billingRequestedDate,
                                                                                                                 subscriptionSpecifiers);
        subscriptionAndAddOns.add(subscriptionAndAddOnsSpecifier);
    }

    private DateTime computeActualBillingRequestedDate(final SubscriptionBaseBundle bundle,
                                                       final DateTime billingRequestedDateRaw,
                                                       @Nullable final SubscriptionBase baseSubscription,
                                                       final Catalog catalog,
                                                       final InternalCallContext context) throws CatalogApiException, SubscriptionBaseApiException {
        DateTime billingRequestedDate = billingRequestedDateRaw;
        if (baseSubscription != null) {
            final DateTime baseSubscriptionStartDate = getBaseSubscription(bundle.getId(), catalog, context).getStartDate();
            billingRequestedDate = billingRequestedDateRaw.isBefore(baseSubscriptionStartDate) ? baseSubscriptionStartDate : billingRequestedDateRaw;
        }
        return billingRequestedDate;
    }

    private SubscriptionBaseBundle getBundleWithSanity(final SubscriptionBaseWithAddOnsSpecifier subscriptionBaseWithAddOnsSpecifier,
                                                       final Catalog catalog,
                                                       final TenantContext callContext,
                                                       final InternalCallContext context) throws SubscriptionBaseApiException, CatalogApiException {
        SubscriptionBaseBundle bundle = null;
        if (subscriptionBaseWithAddOnsSpecifier.getBundleId() != null) {
            bundle = dao.getSubscriptionBundleFromId(subscriptionBaseWithAddOnsSpecifier.getBundleId(), context);
            if (bundle == null ||
                (subscriptionBaseWithAddOnsSpecifier.getBundleExternalKey() != null && !subscriptionBaseWithAddOnsSpecifier.getBundleExternalKey().equals(bundle.getExternalKey()))) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_INVALID_ENTITLEMENT_SPECIFIER);
            }
        } else if (subscriptionBaseWithAddOnsSpecifier.getBundleExternalKey() != null) {
            final SubscriptionBaseBundle tmp = getActiveBundleForKey(subscriptionBaseWithAddOnsSpecifier.getBundleExternalKey(), catalog, context);
            if (tmp != null && !tmp.getAccountId().equals(callContext.getAccountId())) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_ACTIVE_BUNDLE_KEY_EXISTS, subscriptionBaseWithAddOnsSpecifier.getBundleExternalKey());
            } else {
                bundle = tmp;
            }
        }
        return bundle;
    }

    private List<SubscriptionSpecifier> verifyAndBuildSubscriptionSpecifiers(final SubscriptionBaseBundle bundle,
                                                                             final boolean hasBaseOrStandalonePlanSpecifier,
                                                                             final List<EntitlementSpecifier> entitlements,
                                                                             final List<Plan> entitlementsPlans,
                                                                             final boolean isMigrated,
                                                                             final DateTime effectiveDate,
                                                                             final Catalog catalog,
                                                                             final AddonUtils addonUtils,
                                                                             final TenantContext callContext,
                                                                             final InternalCallContext context) throws SubscriptionBaseApiException, CatalogApiException {
        final List<SubscriptionSpecifier> subscriptions = new ArrayList<SubscriptionSpecifier>();
        for (int i = 0; i < entitlements.size(); i++) {
            final EntitlementSpecifier entitlement = entitlements.get(i);
            final PlanPhaseSpecifier spec = entitlement.getPlanPhaseSpecifier();
            if (spec == null) {
                // BP already exists
                continue;
            }

            final Plan plan = entitlementsPlans.get(i);
            final PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new SubscriptionBaseError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                                                              spec.getProductName(), spec.getBillingPeriod().toString(), plan.getPriceListName()));
            }

            // verify the number of subscriptions (of the same kind) allowed per bundle and the existing ones
            if (ProductCategory.ADD_ON.toString().equalsIgnoreCase(plan.getProduct().getCategory().toString())) {
                if (plan.getPlansAllowedInBundle() != -1 && plan.getPlansAllowedInBundle() > 0) {
                    // TODO We should also look to the specifiers being created for validation
                    final List<DefaultSubscriptionBase> subscriptionsForBundle = getSubscriptionsForBundle(bundle.getId(), null, catalog, addonUtils, callContext, context);
                    final int existingAddOnsWithSamePlanName = addonUtils.countExistingAddOnsWithSamePlanName(subscriptionsForBundle, plan.getName());
                    final int currentAddOnsWithSamePlanName = countCurrentAddOnsWithSamePlanName(entitlementsPlans, plan);
                    if ((existingAddOnsWithSamePlanName + currentAddOnsWithSamePlanName) > plan.getPlansAllowedInBundle()) {
                        // a new ADD_ON subscription of the same plan can't be added because it has reached its limit by bundle
                        throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_AO_MAX_PLAN_ALLOWED_BY_BUNDLE, plan.getName());
                    }
                }
            }

            final DateTime bundleStartDate;
            if (hasBaseOrStandalonePlanSpecifier) {
                bundleStartDate = effectiveDate;
            } else {
                final SubscriptionBase baseSubscription = dao.getBaseSubscription(bundle.getId(), catalog, context);
                bundleStartDate = getBundleStartDateWithSanity(bundle.getId(), baseSubscription, plan, effectiveDate, addonUtils, context);
            }

            final SubscriptionSpecifier subscription = new SubscriptionSpecifier();
            subscription.setRealPriceList(plan.getPriceListName());
            subscription.setEffectiveDate(effectiveDate);
            subscription.setProcessedDate(context.getCreatedDate());
            subscription.setPlan(plan);
            subscription.setInitialPhase(spec.getPhaseType());
            subscription.setBuilder(new SubscriptionBuilder()
                                            .setId(UUIDs.randomUUID())
                                            .setBundleId(bundle.getId())
                                            .setBundleExternalKey(bundle.getExternalKey())
                                            .setCategory(plan.getProduct().getCategory())
                                            .setBundleStartDate(bundleStartDate)
                                            .setAlignStartDate(effectiveDate)
                                            .setMigrated(isMigrated)
                                            .setSubscriptionBCD(entitlement.getBillCycleDay()));
            subscriptions.add(subscription);
        }

        return subscriptions;
    }

    private boolean createPlansIfNeededAndReorderBPOrStandaloneSpecFirstWithSanity(final SubscriptionBaseWithAddOnsSpecifier subscriptionBaseWithAddOnsSpecifier,
                                                                                   final Catalog catalog,
                                                                                   final DateTime effectiveDate,
                                                                                   final Collection<EntitlementSpecifier> outputEntitlementSpecifier,
                                                                                   final Collection<Plan> outputEntitlementPlans,
                                                                                   final CallContext callContext) throws SubscriptionBaseApiException, CatalogApiException {
        EntitlementSpecifier basePlanSpecifier = null;
        Plan basePlan = null;
        final Collection<EntitlementSpecifier> addOnSpecifiers = new ArrayList<EntitlementSpecifier>();
        final Collection<EntitlementSpecifier> standaloneSpecifiers = new ArrayList<EntitlementSpecifier>();
        final Collection<Plan> addOnsPlans = new ArrayList<Plan>();
        final Collection<Plan> standalonePlans = new ArrayList<Plan>();

        for (final EntitlementSpecifier cur : subscriptionBaseWithAddOnsSpecifier.getEntitlementSpecifiers()) {
            final PlanPhasePriceOverridesWithCallContext overridesWithContext = new DefaultPlanPhasePriceOverridesWithCallContext(cur.getOverrides(), callContext);
            // Called by createBaseSubscriptionsWithAddOns only -- no need for subscription start date
            final Plan plan = catalog.createOrFindPlan(cur.getPlanPhaseSpecifier(), overridesWithContext, effectiveDate);

            final boolean isBase = isBaseSpecifier(plan);
            final boolean isStandalone = isStandaloneSpecifier(plan);
            if (isStandalone) {
                standaloneSpecifiers.add(cur);
                standalonePlans.add(plan);
            } else if (isBase) {
                if (basePlanSpecifier == null) {
                    basePlanSpecifier = cur;
                    basePlan = plan;
                } else {
                    throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_INVALID_ENTITLEMENT_SPECIFIER);
                }
            } else {
                addOnSpecifiers.add(cur);
                addOnsPlans.add(plan);
            }
        }

        if (basePlanSpecifier != null) {
            outputEntitlementSpecifier.add(basePlanSpecifier);
            outputEntitlementPlans.add(basePlan);
        }
        outputEntitlementSpecifier.addAll(addOnSpecifiers);
        outputEntitlementPlans.addAll(addOnsPlans);

        if (!outputEntitlementSpecifier.isEmpty() && !standaloneSpecifiers.isEmpty()) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_INVALID_ENTITLEMENT_SPECIFIER);
        }

        if (standaloneSpecifiers.isEmpty()) {
            return basePlanSpecifier != null;
        } else {
            outputEntitlementSpecifier.addAll(standaloneSpecifiers);
            outputEntitlementPlans.addAll(standalonePlans);
            return true;
        }
    }

    private boolean isBaseSpecifier(final Plan inputPlan) {
        return inputPlan.getProduct().getCategory() == ProductCategory.BASE;
    }

    private boolean isStandaloneSpecifier(final Plan inputPlan) {
        return inputPlan.getProduct().getCategory() == ProductCategory.STANDALONE;
    }

    private int countCurrentAddOnsWithSamePlanName(final Iterable<Plan> entitlementsPlans, final Plan currentPlan) {
        int countCurrentAddOns = 0;
        for (final Plan plan : entitlementsPlans) {
            if (plan.getName().equalsIgnoreCase(currentPlan.getName())
                && plan.getProduct().getCategory() != null
                && ProductCategory.ADD_ON.equals(plan.getProduct().getCategory())) {
                countCurrentAddOns++;
            }
        }
        return countCurrentAddOns;
    }
}
