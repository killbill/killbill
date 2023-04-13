/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TestCatalogWithEffectiveDateForExistingSubscriptionsCustomConfig2 extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithEffectiveDateForExistingSubscriptionsCustomConfig2");
        // Custom subscription config to test the alignment for the catalog effectiveDateForExistingSubscriptions
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testSubscriptionWithBothAccounts() throws Exception { 
    	
        final LocalDate today = new LocalDate(2023, 3, 28);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        final Account account1 = createAccountWithNonOsgiPaymentMethod(getAccountData(28)); //TODO remove bcd?

        final DefaultEntitlement bpEntitlement1 =
                createBaseEntitlementAndCheckForCompletion(account1.getId(), "externalKey1", "Liability",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        assertNotNull(bpEntitlement1);
        Invoice curInvoice1 = invoiceChecker.checkInvoice(account1.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 28), new LocalDate(2023, 4, 28), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice1.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);
        
        clock.setDay(new LocalDate(2023, 4, 6));
        
        final Account account2 = createAccountWithNonOsgiPaymentMethod(getAccountData(6)); //TODO remove bcd?

        final DefaultEntitlement bpEntitlement2 =
                createBaseEntitlementAndCheckForCompletion(account2.getId(), "externalKey2", "Liability",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        assertNotNull(bpEntitlement2);
        Invoice curInvoice2 = invoiceChecker.checkInvoice(account2.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 6), new LocalDate(2023, 5, 6), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice2.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 6));
        assertListenerStatus();
        
        curInvoice1 = invoiceChecker.checkInvoice(account1.getId(), 2, callContext,
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 28), new LocalDate(2023, 5, 28), InvoiceItemType.RECURRING, new BigDecimal("59.95")));
        
        curInvoice2 = invoiceChecker.checkInvoice(account2.getId(), 2, callContext,
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 6), new LocalDate(2023, 6, 6), InvoiceItemType.RECURRING, new BigDecimal("59.95"))); //FAILS here, invoice created for 49.95 as per v1
        
    }
    
    @Test(groups = "slow")
    public void testSubscriptionWithAccountNotWorking() throws Exception {

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);
        clock.setDay(new LocalDate(2023, 4, 6));
        
        final Account account2 = createAccountWithNonOsgiPaymentMethod(getAccountData(6)); //TODO remove bcd?

        final DefaultEntitlement bpEntitlement2 =
                createBaseEntitlementAndCheckForCompletion(account2.getId(), "externalKey2", "Liability",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        assertNotNull(bpEntitlement2);
        Invoice curInvoice2 = invoiceChecker.checkInvoice(account2.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 6), new LocalDate(2023, 5, 6), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice2.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 6));
        assertListenerStatus();
        
        curInvoice2 = invoiceChecker.checkInvoice(account2.getId(), 2, callContext,
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 6), new LocalDate(2023, 6, 6), InvoiceItemType.RECURRING, new BigDecimal("59.95"))); //FAILS here, invoice created for 49.95 as per v1
        
    }  
    
    @Test(groups = "slow")
    public void testSubscriptionWithAccountWorking() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 28);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        final Account account1 = createAccountWithNonOsgiPaymentMethod(getAccountData(28)); //TODO remove bcd?

        final DefaultEntitlement bpEntitlement1 =
                createBaseEntitlementAndCheckForCompletion(account1.getId(), "externalKey1", "Liability",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        assertNotNull(bpEntitlement1);
        Invoice curInvoice1 = invoiceChecker.checkInvoice(account1.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 28), new LocalDate(2023, 4, 28), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice1.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);
        
        clock.setDay(new LocalDate(2023, 4, 6));
        
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 6));
        assertListenerStatus();
        
        curInvoice1 = invoiceChecker.checkInvoice(account1.getId(), 2, callContext,
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 28), new LocalDate(2023, 5, 28), InvoiceItemType.RECURRING, new BigDecimal("59.95")));
        
    }    

}
