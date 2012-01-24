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

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.*;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.SubscriptionSqlDao;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DefaultEntitlementBillingApi implements EntitlementBillingApi {
	private static final Logger log = LoggerFactory.getLogger(DefaultEntitlementBillingApi.class);
	
    private final EntitlementDao dao;
    private final AccountUserApi accountApi;
    private final CatalogService catalogService;

    @Inject
    public DefaultEntitlementBillingApi(final EntitlementDao dao, final AccountUserApi accountApi, final CatalogService catalogService) {
        super();
        this.dao = dao;
        this.accountApi = accountApi;
        this.catalogService = catalogService;
    }

    @Override
    public SortedSet<BillingEvent> getBillingEventsForAccount(
            final UUID accountId) {
        
        List<SubscriptionBundle> bundles = dao.getSubscriptionBundleForAccount(accountId);
        List<Subscription> subscriptions = new ArrayList<Subscription>();
        for (final SubscriptionBundle bundle: bundles) {
            subscriptions.addAll(dao.getSubscriptions(bundle.getId()));
        }

        SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();        
        for (final Subscription subscription: subscriptions) {
        	for (final SubscriptionTransition transition : subscription.getAllTransitions()) {
        		try {
        			result.add(new DefaultBillingEvent(transition, subscription, calculateBCD(transition, accountId)));
        		} catch (CatalogApiException e) {
        			log.error("Failing to identify catalog components while creating BillingEvent from transition: " + 
        					transition.getId().toString(), e);
        		}
        	}
        }
        return result;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
        return dao.getAccountIdFromSubscriptionId(subscriptionId);
    }

    private int calculateBCD(final SubscriptionTransition transition, final UUID accountId) throws CatalogApiException {
    	Catalog catalog = catalogService.getFullCatalog();
    	Plan plan = transition.getNextPlan();
    	Product product = plan.getProduct();
    	PlanPhase phase = transition.getNextPhase();
    	
    	BillingAlignment alignment = catalog.billingAlignment(
    			new PlanPhaseSpecifier(product.getName(), 
    					product.getCategory(), 
    					phase.getBillingPeriod(), 
    					transition.getNextPriceList(), 
    					phase.getPhaseType()), 
    					transition.getRequestedTransitionTime());
    	int result = 0;
    	Account account = accountApi.getAccountById(accountId);
    	switch (alignment) {
    		case ACCOUNT : 
    			result = account.getBillCycleDay();
    		break;
    		case BUNDLE : 
    			SubscriptionBundle bundle = dao.getSubscriptionBundleFromId(transition.getBundleId());
    			//TODO result = bundle.getStartDate().toDateTime(account.getTimeZone()).getDayOfMonth();
    			result = bundle.getStartDate().getDayOfMonth();
    		break;
    		case SUBSCRIPTION :
    			Subscription subscription = dao.getSubscriptionFromId(transition.getSubscriptionId());
    			//TODO result = subscription.getStartDate().toDateTime(account.getTimeZone()).getDayOfMonth();
    			result = subscription.getStartDate().getDayOfMonth();
    		break;
    	}
    	if(result == 0) {
    		throw new CatalogApiException(ErrorCode.CAT_INVALID_BILLING_ALIGNMENT, alignment.toString());
    	}
    	return result;
    		
    }
    
    @Override
    public void setChargedThroughDate(final UUID subscriptionId, final DateTime ctd) {
        SubscriptionData subscription = (SubscriptionData) dao.getSubscriptionFromId(subscriptionId);

        SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
            .setChargedThroughDate(ctd)
            .setPaidThroughDate(subscription.getPaidThroughDate());

        dao.updateSubscription(new SubscriptionData(builder));
    }

    @Override
    public void setChargedThroughDateFromTransaction(final Transmogrifier transactionalDao, final UUID subscriptionId, final DateTime ctd) {
        SubscriptionSqlDao subscriptionSqlDao = transactionalDao.become(SubscriptionSqlDao.class);
        SubscriptionData subscription = (SubscriptionData) subscriptionSqlDao.getSubscriptionFromId(subscriptionId.toString());

        if (subscription == null) {
            log.warn("Subscription not found when setting CTD.");
        } else {
            Date paidThroughDate = (subscription.getPaidThroughDate() == null) ? null : subscription.getPaidThroughDate().toDate();

            subscriptionSqlDao.updateSubscription(subscriptionId.toString(), subscription.getActiveVersion(),
                                                  ctd.toDate(), paidThroughDate);
        }
    }
}
