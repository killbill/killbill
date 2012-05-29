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

package com.ning.billing.beatrix.integration;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

import java.util.Collection;

import junit.framework.Assert;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;

@Guice(modules = {BeatrixModule.class})
public class TestIntegrationWithAutoInvoiceOffTag extends TestIntegrationBase {

    @Inject
    private InvoiceUserApi invoiceApi;

    //TODO MDW write this test TestIntegrationWithAutoInvoiceOffTag
    private Account account;
    private SubscriptionBundle bundle;
    private String productName;
    private BillingPeriod term;
    private String planSetName;

    @BeforeMethod(groups = {"slow"})
    public void setupOverdue() throws Exception {
        
        account = accountUserApi.createAccount(getAccountData(25), null, null, context);
        assertNotNull(account);

        bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
    }

    @Test(groups={"slow"}, enabled = true)
    public void testAutoInvoiceOffAccount() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        
        // set next invoice to fail and create network 
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);
        clock.addDays(10); // DAY 10 still in trial
        assertTrue(busHandler.isCompleted(DELAY));

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId());
        
        assertEquals(invoices.size(), 0);

    }
}
