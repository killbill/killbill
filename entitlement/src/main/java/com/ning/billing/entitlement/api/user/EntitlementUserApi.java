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

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.entitlement.alignment.IPlanAligner;
import com.ning.billing.entitlement.alignment.IPlanAligner.TimedPhase;
import com.ning.billing.entitlement.api.user.IApiListener;
import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import com.ning.billing.entitlement.api.user.IEntitlementUserApi;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.entitlement.glue.InjectorMagic;
import com.ning.billing.util.clock.IClock;

public class EntitlementUserApi implements IEntitlementUserApi {

    private final Engine engine;
    private final IClock clock;
    private final IEntitlementDao dao;
    private final IPlanAligner planAligner;

    private boolean initialized;

    private ICatalog catalog;

    public EntitlementUserApi(Engine engine, IClock clock, IPlanAligner planAligner, IEntitlementDao dao) {
        super();
        this.engine = engine;
        this.clock = clock;
        this.dao = dao;
        this.planAligner = planAligner;
        this.initialized = false;

    }

    @Override
    public synchronized void initialize(List<IApiListener> listeners) {
        if (!initialized) {
            this.catalog = InjectorMagic.getCatlog();
            engine.registerApiObservers(listeners);
            initialized = true;
        }
    }

    @Override
    public ISubscriptionBundle getBundleFromId(UUID id) {
        return dao.getSubscriptionBundleFromId(id);
    }

    @Override
    public ISubscription getSubscriptionFromId(UUID id) {
        return dao.getSubscriptionFromId(id);
    }

    @Override
    public List<ISubscriptionBundle> getBundlesForAccount(UUID accountId) {
        return dao.getSubscriptionBundleForAccount(accountId);
    }

    @Override
    public List<ISubscription> getSubscriptionsForBundle(UUID bundleId) {
        return dao.getSubscriptions(bundleId);
    }

    @Override
    public ISubscriptionBundle createBundleForAccount(IAccount account, String bundleName)
        throws EntitlementUserApiException {
        SubscriptionBundle bundle = new SubscriptionBundle(bundleName, account.getId());
        return dao.createSubscriptionBundle(bundle);
    }

    @Override
    public ISubscription createSubscription(UUID bundleId, String productName,
            BillingPeriod term, String priceList) throws EntitlementUserApiException {

        // STEPH Should really get 'standard' from catalog
        String realPriceList = (priceList == null) ? "standard" : priceList;

        DateTime now = clock.getUTCNow();

        IPlan plan = catalog.getPlan(productName, term, realPriceList);
        if (plan == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BAD_CATALOG, productName, term, realPriceList);
        }

        IPlanPhase planPhase = (plan.getInitialPhases() != null) ?
                plan.getInitialPhases()[0] : plan.getFinalPhase();
        if (planPhase == null) {
            throw new EntitlementError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                    productName, term.toString(), realPriceList));
        }

        ISubscriptionBundle bundle = dao.getSubscriptionBundleFromId(bundleId);
        if (bundle == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_NO_BUNDLE, bundleId);
        }

        DateTime bundleStartDate = null;
        ISubscription baseSubscription = dao.getBaseSubscription(bundleId);

        switch(plan.getProduct().getCategory()) {
        case BASE:
            if (baseSubscription != null) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BP_EXISTS, bundleId);
            }
            bundleStartDate = now;
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

        DateTime effectiveDate = now;

        Subscription subscription = new Subscription(bundleId, plan.getProduct().getCategory(), bundleStartDate, effectiveDate);

        TimedPhase currentTimedPhase =  planAligner.getCurrentTimedPhaseOnCreate(subscription, plan, realPriceList, effectiveDate);
        ApiEventCreate creationEvent =
            new ApiEventCreate(subscription.getId(), bundleStartDate, now, plan.getName(), currentTimedPhase.getPhase().getName(), realPriceList,
                    now, effectiveDate, subscription.getActiveVersion());

        TimedPhase nextTimedPhase =  planAligner.getNextTimedPhaseOnCreate(subscription, plan, realPriceList, effectiveDate);
        IPhaseEvent nextPhaseEvent = PhaseEvent.getNextPhaseEvent(nextTimedPhase, subscription, now);
        List<IEvent> events = new ArrayList<IEvent>();
        events.add(creationEvent);
        if (nextPhaseEvent != null) {
            events.add(nextPhaseEvent);
        }

        // STEPH Also update startDate for bundle ?
        return dao.createSubscription(subscription, events);
    }


}
