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
import java.util.HashMap;
import java.util.Map;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TestCatalogWithEffectiveDateForExistingSubscriptionsCustomConfigMultiPhasePlan extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithEffectiveDateForExistingSubscriptionsCustomConfigMultiPhasePlan");
        // Custom subscription config to test the alignment for the catalog effectiveDateForExistingSubscriptions
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testSubscriptionWithMultiPhasePlan() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 18);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // We have 2 catalog versions, V2 is effective on 2023-05-18 but there is an effectiveDateForExistingSubscriptions=2023-05-18 for the Liability Plan.
        // The plan has a 14-day DISCOUNT phase followed by an EVERGREEN phase
        // The invoices are generated correctly up until 2023-05-01
        // On moving the clock to 2023-06-01 (v2 effective), invoices are generated for $0
        //


        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));
        final DefaultEntitlement bpEntitlement1 =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey1", "Liability",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        assertListenerStatus();
        assertNotNull(bpEntitlement1);
        //invoice corresponding to discount phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                                      new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 18), new LocalDate(2023, 4, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO)); //TODO - This is of type recurring since the catalog defines a recurring price - maybe this should be changed to fixed?
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-01 (end of discount phase) - invoice generated for evergreen phase
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 4, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 1), new LocalDate(2023, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-01 - invoice generated for evergreen phase
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 1), new LocalDate(2023, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-06-01 (v2 effective) - Test fails here as invoice for $0 is generated
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 1), new LocalDate(2023, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("10.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }
}
