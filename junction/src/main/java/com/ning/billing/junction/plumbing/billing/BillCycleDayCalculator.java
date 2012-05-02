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

package com.ning.billing.junction.plumbing.billing;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;

public class BillCycleDayCalculator {
	private static final Logger log = LoggerFactory.getLogger(BillCycleDayCalculator.class);
	
	private final CatalogService catalogService;
	private final EntitlementUserApi entitlementApi;

	@Inject
	public BillCycleDayCalculator(final CatalogService catalogService, final EntitlementUserApi entitlementApi) {
		super();
		this.catalogService = catalogService;
		this.entitlementApi = entitlementApi;
	}

	protected int calculateBcd(SubscriptionBundle bundle, Subscription subscription, final SubscriptionEvent transition, final Account account) throws CatalogApiException, AccountApiException {

	    Catalog catalog = catalogService.getFullCatalog();
		
	    Plan prevPlan = (transition.getPreviousPlan() != null) ? catalog.findPlan(transition.getPreviousPlan(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
	    Plan nextPlan = (transition.getNextPlan() != null) ? catalog.findPlan(transition.getNextPlan(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;	    
		
		Plan plan =  (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ? nextPlan : prevPlan;
		Product product = plan.getProduct();

        PlanPhase prevPhase = (transition.getPreviousPhase() != null) ? catalog.findPhase(transition.getPreviousPhase(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
        PlanPhase nextPhase = (transition.getNextPhase() != null) ? catalog.findPhase(transition.getNextPhase(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
        
		PlanPhase phase = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ? nextPhase : prevPhase;

		BillingAlignment alignment = catalog.billingAlignment(
		        new PlanPhaseSpecifier(product.getName(),
		                product.getCategory(),
		                phase.getBillingPeriod(),
		                transition.getNextPriceList(),
		                phase.getPhaseType()),
		                transition.getRequestedTransitionTime());

		int result = -1;
		switch (alignment) {
		case ACCOUNT :
		    result = account.getBillCycleDay();
		    if(result == 0) {
		        result = calculateBcdFromSubscription(subscription, plan, account);
		    }
		    break;
		case BUNDLE :
		    Subscription baseSub = entitlementApi.getBaseSubscription(bundle.getId());
		    result = calculateBcdFromSubscription(baseSub, plan, account);
		    break;
		case SUBSCRIPTION :
		    result = calculateBcdFromSubscription(subscription, plan, account);
		    break;
		}
		if(result == -1) {
		    throw new CatalogApiException(ErrorCode.CAT_INVALID_BILLING_ALIGNMENT, alignment.toString());
		}
		return result;

	}

	private int calculateBcdFromSubscription(Subscription subscription, Plan plan, Account account) throws AccountApiException {
		DateTime date = plan.dateOfFirstRecurringNonZeroCharge(subscription.getStartDate());
		return date.toDateTime(account.getTimeZone()).getDayOfMonth();
	}

}
