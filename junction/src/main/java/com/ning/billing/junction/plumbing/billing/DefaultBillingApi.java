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
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.ChargeThruApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;

public class DefaultBillingApi implements BillingApi {
    private static final String API_USER_NAME = "Billing Api";
    private static final Logger log = LoggerFactory.getLogger(DefaultBillingApi.class);
    private final ChargeThruApi chargeThruApi;
    private final CallContextFactory factory;
    private final AccountUserApi accountApi;
    private final BillCycleDayCalculator bcdCalculator;
    private final EntitlementUserApi entitlementUserApi;
    private final CatalogService catalogService;
    
    @Inject
    public DefaultBillingApi(ChargeThruApi chargeThruApi, CallContextFactory factory, AccountUserApi accountApi, 
            BillCycleDayCalculator bcdCalculator, EntitlementUserApi entitlementUserApi, final CatalogService catalogService) {
        this.chargeThruApi = chargeThruApi;
        this.accountApi = accountApi;
        this.bcdCalculator = bcdCalculator;
        this.factory = factory;
        this.entitlementUserApi = entitlementUserApi;
        this.catalogService = catalogService;
    }

    @Override
    public SortedSet<BillingEvent> getBillingEventsForAccountAndUpdateAccountBCD(final UUID accountId) {
        Account account = accountApi.getAccountById(accountId);
        CallContext context = factory.createCallContext(API_USER_NAME, CallOrigin.INTERNAL, UserType.SYSTEM);

        List<SubscriptionBundle> bundles = entitlementUserApi.getBundlesForAccount(accountId);
        SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();
        for (final SubscriptionBundle bundle: bundles) {
            List<Subscription> subscriptions = entitlementUserApi.getSubscriptionsForBundle(bundle.getId());

            for (final Subscription subscription: subscriptions) {
                for (final SubscriptionEvent transition : subscription.getBillingTransitions()) {
                    try {
                        int bcd = bcdCalculator.calculateBcd(bundle, subscription, transition, account);
                        
                        if(account.getBillCycleDay() == 0) {
                            MutableAccountData modifiedData = account.toMutableAccountData();
                            modifiedData.setBillCycleDay(bcd);
                            accountApi.updateAccount(account.getExternalKey(), modifiedData, context);
                        }

                        BillingEvent event = new DefaultBillingEvent(account, transition, subscription, bcd, account.getCurrency(), catalogService.getFullCatalog());
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
    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId) {
        return chargeThruApi.getAccountIdFromSubscriptionId(subscriptionId);
    }

    @Override
    public void setChargedThroughDate(UUID subscriptionId, DateTime ctd, CallContext context) {
        chargeThruApi.setChargedThroughDate(subscriptionId, ctd, context);
    }

    @Override
    public void setChargedThroughDateFromTransaction(Transmogrifier transactionalDao, UUID subscriptionId,
            DateTime ctd, CallContext context) {
        chargeThruApi.setChargedThroughDateFromTransaction(transactionalDao, subscriptionId, ctd, context);
    }

}
