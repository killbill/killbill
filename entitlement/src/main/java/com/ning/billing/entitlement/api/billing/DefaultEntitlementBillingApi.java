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

package com.ning.billing.entitlement.api.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;

public class DefaultEntitlementBillingApi implements EntitlementBillingApi {

    private final EntitlementDao dao;
    private final AccountUserApi accountApi;
    private final CatalogService catalogService;

    @Inject
    public DefaultEntitlementBillingApi(EntitlementDao dao, AccountUserApi accountApi, CatalogService catalogService) {
        super();
        this.dao = dao;
        this.accountApi = accountApi;
        this.catalogService = catalogService;
    }

    @Override
    public SortedSet<BillingEvent> getBillingEventsForAccount(
            UUID accountId) {
        
        List<SubscriptionBundle> bundles = dao.getSubscriptionBundleForAccount(accountId);
        List<Subscription> subscriptions = new ArrayList<Subscription>();
        for (SubscriptionBundle bundle: bundles) {
            subscriptions.addAll(dao.getSubscriptions(bundle.getId()));
        }
        List<SubscriptionTransition> transitions = new ArrayList<SubscriptionTransition>();
        for (Subscription subscription: subscriptions) {
            transitions.addAll(subscription.getAllTransitions());
        }
        
        Account account = accountApi.getAccountById(accountId);
        
        SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();        
        for (SubscriptionTransition transition : transitions) {
            result.add(new DefaultBillingEvent(transition, account.getBillCycleDay()));
        }
        return result;
    }
    
    private int calculateBCD(SubscriptionTransition transition, UUID accountId) throws CatalogApiException {
    	Catalog catalog = catalogService.getCatalog();
    	Plan plan = transition.getNextPlan();
    	Product product = plan.getProduct();
    	PlanPhase phase = transition.getNextPhase();
    	
    	BillingAlignment alignment = catalog.billingAlignment(
    			new PlanPhaseSpecifier(product.getName(), 
    					product.getCategory(), 
    					phase.getBillingPeriod(), 
    					transition.getNextPriceList(), 
    					phase.getPhaseType()));
    	int result = 0;
    	switch (alignment) {
    		case ACCOUNT : result = accountApi.getAccountById(accountId).getBillCycleDay();
    		break;
    		case BUNDLE : result = dao.getSubscriptionBundleFromId(transition.getBundleId()).getStartDate().getDayOfMonth();
    		break;
    		case SUBSCRIPTION : result = dao.getSubscriptionFromId(transition.getSubscriptionId()).getStartDate().getDayOfMonth();
    		break;
    	}
    	return result;
    		
    }
    

    @Override
    public void setChargedThroughDate(UUID subscriptionId, DateTime ctd) {
        SubscriptionData subscription = (SubscriptionData) dao.getSubscriptionFromId(subscriptionId);
        if (subscription == null) {
            new EntitlementBillingApiException(String.format("Unknown subscription %s", subscriptionId));
        }

        SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
            .setChargedThroughDate(ctd)
            .setPaidThroughDate(subscription.getPaidThroughDate());
        dao.updateSubscription(new SubscriptionData(builder));
    }
}
