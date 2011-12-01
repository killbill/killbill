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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.ICatalogService;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IPriceListSet;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.alignment.TimedPhase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.clock.Clock;

public class DefaultEntitlementUserApi implements EntitlementUserApi {

    private final Clock clock;
    private final EntitlementDao dao;
    private final PlanAligner planAligner;
    private final ICatalogService catalogService;

    @Inject
    public DefaultEntitlementUserApi(Clock clock, PlanAligner planAligner,
            EntitlementDao dao, ICatalogService catalogService) {
        super();
        this.clock = clock;
        this.dao = dao;
        this.planAligner = planAligner;
        this.catalogService = catalogService;
    }

    @Override
    public SubscriptionBundle getBundleFromId(UUID id) {
        return dao.getSubscriptionBundleFromId(id);
    }

    @Override
    public Subscription getSubscriptionFromId(UUID id) {
        return dao.getSubscriptionFromId(id);
    }

    @Override
    public List<SubscriptionBundle> getBundlesForAccount(UUID accountId) {
        return dao.getSubscriptionBundleForAccount(accountId);
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(String bundleKey) {
        return dao.getSubscriptionsForKey(bundleKey);
    }


    @Override
    public List<Subscription> getSubscriptionsForBundle(UUID bundleId) {
        return dao.getSubscriptions(bundleId);
    }

    @Override
    public SubscriptionBundle createBundleForAccount(IAccount account, String bundleName)
        throws EntitlementUserApiException {
        SubscriptionBundleData bundle = new SubscriptionBundleData(bundleName, account.getId());
        return dao.createSubscriptionBundle(bundle);
    }

    @Override
    public Subscription createSubscription(UUID bundleId, String productName,
            BillingPeriod term, String priceList, DateTime requestedDate) throws EntitlementUserApiException {

        String realPriceList = (priceList == null) ? IPriceListSet.DEFAULT_PRICELIST_NAME : priceList;
        DateTime now = clock.getUTCNow();
        requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : now;
        if (requestedDate != null && requestedDate.isAfter(now)) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE, requestedDate.toString());
        }

        requestedDate = (requestedDate == null) ? now : requestedDate;

        //TODO: Correctly handle exception
        IPlan plan = null;
		try {
			plan = catalogService.getCatalog().findPlan(productName, term, realPriceList);
		} catch (CatalogApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        if (plan == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BAD_CATALOG, productName, term, realPriceList);
        }

        IPlanPhase planPhase = (plan.getInitialPhases() != null) ?
                plan.getInitialPhases()[0] : plan.getFinalPhase();
        if (planPhase == null) {
            throw new EntitlementError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                    productName, term.toString(), realPriceList));
        }

        SubscriptionBundle bundle = dao.getSubscriptionBundleFromId(bundleId);
        if (bundle == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_NO_BUNDLE, bundleId);
        }

        DateTime bundleStartDate = null;
        Subscription baseSubscription = dao.getBaseSubscription(bundleId);

        switch(plan.getProduct().getCategory()) {
        case BASE:
            if (baseSubscription != null) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BP_EXISTS, bundleId);
            }
            bundleStartDate = requestedDate;
            break;
        case ADD_ON:
            if (baseSubscription == null) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_NO_BP, bundleId);
            }
            bundleStartDate = baseSubscription.getStartDate();
            break;
         default:
             throw new EntitlementError(String.format("Can't create subscription of type %s",
                     plan.getProduct().getCategory().toString()));
        }

        DateTime effectiveDate = requestedDate;
        SubscriptionData subscription = new SubscriptionData(new SubscriptionBuilder()
            .setId(UUID.randomUUID())
            .setBundleId(bundleId)
            .setCategory(plan.getProduct().getCategory())
            .setBundleStartDate(bundleStartDate)
            .setStartDate(effectiveDate),
                false);

        TimedPhase currentTimedPhase =  planAligner.getCurrentTimedPhaseOnCreate(subscription, plan, realPriceList, effectiveDate);
        ApiEventCreate creationEvent = new ApiEventCreate(new ApiEventBuilder()
            .setSubscriptionId(subscription.getId())
            .setEventPlan(plan.getName())
            .setEventPlanPhase(currentTimedPhase.getPhase().getName())
            .setEventPriceList(realPriceList)
            .setActiveVersion(subscription.getActiveVersion())
            .setProcessedDate(now)
            .setEffectiveDate(effectiveDate)
            .setRequestedDate(requestedDate));

        TimedPhase nextTimedPhase =  planAligner.getNextTimedPhaseOnCreate(subscription, plan, realPriceList, effectiveDate);
        PhaseEvent nextPhaseEvent = PhaseEventData.getNextPhaseEvent(nextTimedPhase, subscription, now);
        List<EntitlementEvent> events = new ArrayList<EntitlementEvent>();
        events.add(creationEvent);
        if (nextPhaseEvent != null) {
            events.add(nextPhaseEvent);
        }

        // STEPH Also update startDate for bundle ?
        return dao.createSubscription(subscription, events);
    }
}
