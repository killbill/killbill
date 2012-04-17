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

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.entitlement.api.user.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.SubscriptionSqlDao;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.audit.dao.AuditSqlDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;

public class DefaultEntitlementBillingApi implements EntitlementBillingApi {
	private static final Logger log = LoggerFactory.getLogger(DefaultEntitlementBillingApi.class);
    private static final String API_USER_NAME = "Entitlement Billing Api";
    private final CallContextFactory factory;
    private final EntitlementDao entitlementDao;
    private final AccountUserApi accountApi;
    private final BillCycleDayCalculator bcdCalculator;
    private final SubscriptionFactory subscriptionFactory;
  
    private static final String SUBSCRIPTION_TABLE_NAME = "subscriptions";

    @Inject
    public DefaultEntitlementBillingApi(final CallContextFactory factory, final SubscriptionFactory subscriptionFactory, final EntitlementDao dao, final AccountUserApi accountApi, final BillCycleDayCalculator bcdCalculator) {
        super();
        this.factory = factory;
        this.subscriptionFactory = subscriptionFactory;
        this.entitlementDao = dao;
        this.accountApi = accountApi;
        this.bcdCalculator = bcdCalculator;
    }

    @Override
    public SortedSet<BillingEvent> getBillingEventsForAccountAndUpdateAccountBCD(
            final UUID accountId) {
        Account account = accountApi.getAccountById(accountId);
        CallContext context = factory.createCallContext(API_USER_NAME, CallOrigin.INTERNAL, UserType.SYSTEM);

        List<SubscriptionBundle> bundles = entitlementDao.getSubscriptionBundleForAccount(accountId);
        SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();
        for (final SubscriptionBundle bundle: bundles) {
        	List<Subscription> subscriptions = entitlementDao.getSubscriptions(subscriptionFactory, bundle.getId());

        	for (final Subscription subscription: subscriptions) {
        		for (final SubscriptionEventTransition transition : ((SubscriptionData) subscription).getBillingTransitions()) {
        			try {
        			    int bcd = bcdCalculator.calculateBcd(bundle, subscription, transition, account);
        			    
        			    if(account.getBillCycleDay() == 0) {
        			        MutableAccountData modifiedData = account.toMutableAccountData();
        			        modifiedData.setBillCycleDay(bcd);
                            accountApi.updateAccount(account.getExternalKey(), modifiedData, context);
        			    }

        				BillingEvent event = new DefaultBillingEvent(account, transition, subscription, bcd, account.getCurrency());
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

    @Override
    public void setChargedThroughDate(final UUID subscriptionId, final DateTime ctd, CallContext context) {
        SubscriptionData subscription = (SubscriptionData) entitlementDao.getSubscriptionFromId(subscriptionFactory, subscriptionId);

        SubscriptionBuilder builder = new SubscriptionBuilder(subscription)
            .setChargedThroughDate(ctd)
            .setPaidThroughDate(subscription.getPaidThroughDate());

        entitlementDao.updateSubscription(new SubscriptionData(builder), context);
    }

    @Override
    public void setChargedThroughDateFromTransaction(final Transmogrifier transactionalDao, final UUID subscriptionId,
                                                     final DateTime ctd, final CallContext context) {
        SubscriptionSqlDao subscriptionSqlDao = transactionalDao.become(SubscriptionSqlDao.class);
        SubscriptionData subscription = (SubscriptionData) subscriptionSqlDao.getSubscriptionFromId(subscriptionId.toString());

        if (subscription == null) {
            log.warn("Subscription not found when setting CTD.");
        } else {
            Date paidThroughDate = (subscription.getPaidThroughDate() == null) ? null : subscription.getPaidThroughDate().toDate();

            DateTime chargedThroughDate = subscription.getChargedThroughDate();
            if (chargedThroughDate == null || chargedThroughDate.isBefore(ctd)) {
                subscriptionSqlDao.updateSubscription(subscriptionId.toString(), subscription.getActiveVersion(),
                                                      ctd.toDate(), paidThroughDate, context);
                AuditSqlDao auditSqlDao = transactionalDao.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction(SUBSCRIPTION_TABLE_NAME, subscriptionId.toString(), ChangeType.UPDATE, context);
            }
        }
    }


}
