/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.entitlement.api.user;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionStatusDryRun.DryRunChangeReason;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

public class DefaultEntitlementUserApi implements EntitlementUserApi {
    private final Clock clock;
    private final EntitlementDao dao;
    private final CatalogService catalogService;
    private final DefaultSubscriptionApiService apiService;
    private final AddonUtils addonUtils;
    private final SubscriptionFactory subscriptionFactory;

    @Inject
    public DefaultEntitlementUserApi(Clock clock, EntitlementDao dao, CatalogService catalogService,
            DefaultSubscriptionApiService apiService, final SubscriptionFactory subscriptionFactory, AddonUtils addonUtils) {
        super();
        this.clock = clock;
        this.apiService = apiService;
        this.dao = dao;
        this.catalogService = catalogService;
        this.addonUtils = addonUtils;
        this.subscriptionFactory = subscriptionFactory;
    }

    
    @Override
    public SubscriptionBundle getBundleFromId(UUID id) throws EntitlementUserApiException {
        SubscriptionBundle result = dao.getSubscriptionBundleFromId(id);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_GET_INVALID_BUNDLE_ID, id.toString());
        }
        return result;
    }

    @Override
    public Subscription getSubscriptionFromId(UUID id) throws EntitlementUserApiException {
        Subscription result = dao.getSubscriptionFromId(subscriptionFactory, id);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_SUBSCRIPTION_ID, id);
        }
        return result;
    }

    @Override
    public SubscriptionBundle getBundleForKey(String bundleKey) throws EntitlementUserApiException {
        SubscriptionBundle result =  dao.getSubscriptionBundleFromKey(bundleKey);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_GET_INVALID_BUNDLE_KEY, bundleKey);
        }
        return result;
    }

    @Override
    public List<SubscriptionBundle> getBundlesForAccount(UUID accountId) {
        return dao.getSubscriptionBundleForAccount(accountId);
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(String bundleKey) {
        return dao.getSubscriptionsForKey(subscriptionFactory, bundleKey);
    }

    @Override
    public List<Subscription> getSubscriptionsForBundle(UUID bundleId) {
        return dao.getSubscriptions(subscriptionFactory, bundleId);
    }

    @Override
    public Subscription getBaseSubscription(UUID bundleId) throws EntitlementUserApiException {
        Subscription result =  dao.getBaseSubscription(subscriptionFactory, bundleId);
        if (result == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_GET_NO_SUCH_BASE_SUBSCRIPTION, bundleId);
        }
        return result;
    }
    

    public SubscriptionBundle createBundleForAccount(UUID accountId, String bundleName, CallContext context)
    throws EntitlementUserApiException {
        SubscriptionBundleData bundle = new SubscriptionBundleData(bundleName, accountId, clock.getUTCNow());
        return dao.createSubscriptionBundle(bundle, context);
    }

    @Override
    public Subscription createSubscription(UUID bundleId, PlanPhaseSpecifier spec, DateTime requestedDate,
                                           CallContext context) throws EntitlementUserApiException {
        try {
            String realPriceList = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            DateTime now = clock.getUTCNow();
            requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : now;
            if (requestedDate.isAfter(now)) {
                throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE, requestedDate.toString());
            }
            DateTime effectiveDate = requestedDate;

            Catalog catalog = catalogService.getFullCatalog();
            Plan plan = catalog.findPlan(spec.getProductName(), spec.getBillingPeriod(), realPriceList, requestedDate);

            PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new EntitlementError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                        spec.getProductName(), spec.getBillingPeriod().toString(), realPriceList));
            }

            SubscriptionBundle bundle = dao.getSubscriptionBundleFromId(bundleId);
            if (bundle == null) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_NO_BUNDLE, bundleId);
            }

            DateTime bundleStartDate = null;
            SubscriptionData baseSubscription = (SubscriptionData) dao.getBaseSubscription(subscriptionFactory, bundleId);
            switch(plan.getProduct().getCategory()) {
            case BASE:
                if (baseSubscription != null) {
                    if (baseSubscription.getState() == SubscriptionState.ACTIVE) {
                        throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BP_EXISTS, bundleId);
                    } else {
                        // If we do create on an existing CANCELLED BP, this is equivalent to call recreate on that Subscription.
                        baseSubscription.recreate(spec, requestedDate, context);
                        return baseSubscription;
                    }
                }
                bundleStartDate = requestedDate;
                break;
            case ADD_ON:
                if (baseSubscription == null) {
                    throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_NO_BP, bundleId);
                }
                if (effectiveDate.isBefore(baseSubscription.getStartDate())) {
                    throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE, requestedDate.toString());
                }
                addonUtils.checkAddonCreationRights(baseSubscription, plan);
                bundleStartDate = baseSubscription.getStartDate();
                break;
            case STANDALONE:
                if (baseSubscription != null) {
                    throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BP_EXISTS, bundleId);
                }
                // Not really but we don't care, there is no alignment for STANDALONE subscriptions
                bundleStartDate = requestedDate;
                break;
            default:
                throw new EntitlementError(String.format("Can't create subscription of type %s",
                        plan.getProduct().getCategory().toString()));
            }

            SubscriptionData subscription = apiService.createPlan(new SubscriptionBuilder()
                 .setId(UUID.randomUUID())
                .setBundleId(bundleId)
                .setCategory(plan.getProduct().getCategory())
                .setBundleStartDate(bundleStartDate)
                .setStartDate(effectiveDate),
            plan, spec.getPhaseType(), realPriceList, requestedDate, effectiveDate, now, context);

            return subscription;
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }


    @Override
    public DateTime getNextBillingDate(UUID accountId) {
        List<SubscriptionBundle> bundles = getBundlesForAccount(accountId);
        DateTime result = null;
        for(SubscriptionBundle bundle : bundles) {
            List<Subscription> subscriptions = getSubscriptionsForBundle(bundle.getId());
            for(Subscription subscription : subscriptions) {
                DateTime chargedThruDate = subscription.getChargedThroughDate();
                if(result == null ||
                        (chargedThruDate != null && chargedThruDate.isBefore(result))) {
                    result = subscription.getChargedThroughDate();
                }
            }
        }
        return result;
    }


    @Override
    public List<SubscriptionStatusDryRun> getDryRunChangePlanStatus(UUID subscriptionId, String baseProductName, DateTime requestedDate)
            throws EntitlementUserApiException {

        Subscription subscription = dao.getSubscriptionFromId(subscriptionFactory, subscriptionId);
        if (subscription == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_SUBSCRIPTION_ID, subscriptionId);
        }
        if (subscription.getCategory() != ProductCategory.BASE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_DRY_RUN_NOT_BP);
        }
        
        List<SubscriptionStatusDryRun> result = new LinkedList<SubscriptionStatusDryRun>();
        
        List<Subscription> bundleSubscriptions = dao.getSubscriptions(subscriptionFactory, subscription.getBundleId());
        for (Subscription cur : bundleSubscriptions) {
            if (cur.getId().equals(subscriptionId)) {
                continue;
            }
            
            DryRunChangeReason reason = null;
            if (addonUtils.isAddonIncludedFromProdName(baseProductName, requestedDate, cur.getCurrentPlan())) {
                reason = DryRunChangeReason.AO_INCLUDED_IN_NEW_PLAN;
            } else if (addonUtils.isAddonAvailableFromProdName(baseProductName, requestedDate, cur.getCurrentPlan())) {
                reason = DryRunChangeReason.AO_AVAILABLE_IN_NEW_PLAN;
            } else {
                reason = DryRunChangeReason.AO_NOT_AVAILABLE_IN_NEW_PLAN;
            }
            SubscriptionStatusDryRun status = new DefaultSubscriptionStatusDryRun(cur.getId(), 
                    cur.getCurrentPlan().getProduct().getName(), cur.getCurrentPhase().getPhaseType(),
                    cur.getCurrentPlan().getBillingPeriod(),
                    cur.getCurrentPriceList().getName(), reason);
            result.add(status);
        }
        return result;
    }
}
