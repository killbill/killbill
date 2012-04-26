/*
w * Copyright 2010-2011 Ning, Inc.
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

import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.audit.dao.AuditSqlDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.callcontext.CallContextFactory;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.SubscriptionSqlDao;

public class DefaultEntitlementBillingApi implements EntitlementBillingApi {
	private static final Logger log = LoggerFactory.getLogger(DefaultEntitlementBillingApi.class);
    private static final String API_USER_NAME = "Entitlement Billing Api";

    private final CallContextFactory factory;
    private final EntitlementDao entitlementDao;
    private final AccountUserApi accountApi;
    private final CatalogService catalogService;
    private final SubscriptionFactory subscriptionFactory;
    private static final String SUBSCRIPTION_TABLE_NAME = "subscriptions";

    @Inject
    public DefaultEntitlementBillingApi(final CallContextFactory factory, final SubscriptionFactory subscriptionFactory,
            final EntitlementDao dao, final AccountUserApi accountApi, final CatalogService catalogService) {
        super();
        this.factory = factory;
        this.subscriptionFactory = subscriptionFactory;
        this.entitlementDao = dao;
        this.accountApi = accountApi;
        this.catalogService = catalogService;
    }

    @Override
    public SortedSet<BillingEvent> getBillingEventsForAccount(final UUID accountId) {
        Account account = accountApi.getAccountById(accountId);
        Currency currency = account.getCurrency();

        List<SubscriptionBundle> bundles = entitlementDao.getSubscriptionBundleForAccount(accountId);
        SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();
        
        for (final SubscriptionBundle bundle: bundles) {
        	List<Subscription> subscriptions = entitlementDao.getSubscriptions(subscriptionFactory, bundle.getId());

        	DateTime bundleStartDate = bundle.getStartDate(); 
        	for (final Subscription subscription: subscriptions) {
        	    // STEPH hack -- see RI-1169
                bundleStartDate = (subscription.getCategory() == ProductCategory.BASE && bundleStartDate == null) ? subscription.getStartDate() : bundleStartDate;
        	    for (final SubscriptionEventTransition transition : ((SubscriptionData) subscription).getBillingTransitions()) {
        			try {
        				BillingEvent event = new DefaultBillingEvent(transition, subscription, calculateBcd(bundle, subscription, transition, accountId, bundleStartDate), currency);
        				result.add(event);
        			} catch (CatalogApiException e) {
        				log.error("Failing to identify catalog components while creating BillingEvent from transition: " +
        						transition.getId().toString(), e);
        			} catch (Exception e) {
        				log.warn("Failed while getting BillingEvent", e);
        			}
        		}
        	}
        }
        return result;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
        return entitlementDao.getAccountIdFromSubscriptionId(subscriptionId);
    }

    private int calculateBcd(final SubscriptionBundle bundle, final Subscription subscription,
                             final SubscriptionEventTransition transition, final UUID accountId, DateTime bundleStartDate) throws CatalogApiException, AccountApiException {
    	Catalog catalog = catalogService.getFullCatalog();
    	Plan plan =  (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
    	        transition.getNextPlan() : transition.getPreviousPlan();
    	Product product = plan.getProduct();
    	PlanPhase phase = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
    	        transition.getNextPhase() : transition.getPreviousPhase();

    	BillingAlignment alignment = catalog.billingAlignment(
    			new PlanPhaseSpecifier(product.getName(),
    					product.getCategory(),
    					phase.getBillingPeriod(),
    					transition.getNextPriceList(),
    					phase.getPhaseType()),
    					transition.getRequestedTransitionTime());
    	int result = -1;

		Account account = accountApi.getAccountById(accountId);
		switch (alignment) {
    		case ACCOUNT :
    			result = account.getBillCycleDay();

    			if(result == 0) {
                    // in this case, we're making an internal call from the entitlement API to set the BCD for the account
                    CallContext context = factory.createCallContext(API_USER_NAME, CallOrigin.INTERNAL, UserType.SYSTEM);
    				result = calculateBcdFromSubscription(subscription, plan, account, context);
    			}
    		break;
    		case BUNDLE :
    			result = bundleStartDate.toDateTime(account.getTimeZone()).getDayOfMonth();
    		break;
    		case SUBSCRIPTION :
    			result = subscription.getStartDate().toDateTime(account.getTimeZone()).getDayOfMonth();
    		break;
    	}
    	if(result == -1) {
    		throw new CatalogApiException(ErrorCode.CAT_INVALID_BILLING_ALIGNMENT, alignment.toString());
    	}
    	return result;

    }

   	private int calculateBcdFromSubscription(Subscription subscription, Plan plan, Account account,
                                             final CallContext context) throws AccountApiException {
		int result = account.getBillCycleDay();
        if(result != 0) {
            return result;
        }
        result = new DateTime(account.getTimeZone()).getDayOfMonth();

        try {
        	result = billCycleDay(subscription.getStartDate(),account.getTimeZone(), plan);
        } catch (CatalogApiException e) {
            log.error("Unexpected catalog error encountered when updating BCD",e);
        }

        MutableAccountData modifiedData = account.toMutableAccountData();
        modifiedData.setBillCycleDay(result);

        accountApi.updateAccount(account.getExternalKey(), modifiedData, context);
        return result;
    }

    private int billCycleDay(DateTime requestedDate, DateTimeZone timeZone,
    		Plan plan) throws CatalogApiException {

        DateTime date = plan.dateOfFirstRecurringNonZeroCharge(requestedDate);
        return date.toDateTime(timeZone).getDayOfMonth();

    }


    @Override
    public void setChargedThroughDate(final UUID subscriptionId, final DateTime ctd, CallContext context) {
        SubscriptionData subscription = (SubscriptionData) entitlementDao.getSubscriptionFromId(subscriptionFactory, subscriptionId);

        SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
            .setChargedThroughDate(ctd)
            .setPaidThroughDate(subscription.getPaidThroughDate());

        entitlementDao.updateChargedThroughDate(new SubscriptionData(builder), context);
    }

    @Override
    public void setChargedThroughDateFromTransaction(final Transmogrifier transactionalDao, final UUID subscriptionId,
                                                     final DateTime ctd, final CallContext context) {
        SubscriptionSqlDao subscriptionSqlDao = transactionalDao.become(SubscriptionSqlDao.class);
        SubscriptionData subscription = (SubscriptionData) subscriptionSqlDao.getSubscriptionFromId(subscriptionId.toString());

        if (subscription == null) {
            log.warn("Subscription not found when setting CTD.");
        } else {
            DateTime chargedThroughDate = subscription.getChargedThroughDate();
            if (ctd != null && (chargedThroughDate == null || chargedThroughDate.isBefore(ctd))) {
                subscriptionSqlDao.updateChargedThroughDate(subscriptionId.toString(), ctd.toDate(), context);
                AuditSqlDao auditSqlDao = transactionalDao.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction(SUBSCRIPTION_TABLE_NAME, subscriptionId.toString(), ChangeType.UPDATE, context);
            }
        }
    }
}
