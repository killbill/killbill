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
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.entitlement.alignment.EntitlementAlignment;
import com.ning.billing.entitlement.alignment.EntitlementAlignment.TimedPhase;
import com.ning.billing.entitlement.api.user.IApiListener;
import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import com.ning.billing.entitlement.api.user.IUserApi;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.IClock;

public class UserApi implements IUserApi {

    private final Engine engine;
    private final IClock clock;
    private final IEntitlementDao dao;
    private final ICatalog catalog;

    @Inject
    public UserApi(Engine engine, IClock clock, IEntitlementDao dao) {
        super();
        this.engine = engine;
        this.clock = clock;
        this.dao = dao;
        this.catalog = engine.getCatalog();
    }

    @Override
    public void initialize(List<IApiListener> listeners) {
        engine.registerApiObservers(listeners);
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

        Subscription subscription = new Subscription(bundleId, plan.getProduct().getCategory(), bundleStartDate, now);
        ApiEventCreate creationEvent =
            new ApiEventCreate(subscription.getId(), bundleStartDate, now, plan, realPriceList,
                    now, now, subscription.getActiveVersion());

        EntitlementAlignment planPhaseAlignment = new EntitlementAlignment(subscription.getId(), now,
                bundleStartDate, plan, now, subscription.getActiveVersion());
        IPhaseEvent nextPhaseEvent = planPhaseAlignment.getNextPhaseEvent();
        List<IEvent> events = new ArrayList<IEvent>();
        events.add(creationEvent);
        if (nextPhaseEvent != null) {
            events.add(nextPhaseEvent);
        }

        // STEPH Also update startDate for bundle ?
        return dao.createSubscription(subscription, events);
    }


}
