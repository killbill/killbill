/*
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
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

/**
 * Same feature as {@link TestCatalogWithEffectiveDateForExistingSubscriptions}, but with
 * org.killbill.subscription.align.effectiveDateForExistingSubscriptions=true. That property is
 * class scoped in the test framework, hence a separate test class.
 *
 * With the alignment enabled, every catalog change whose effectiveDateForExistingSubscriptions
 * falls at or before the subscription's next billing boundary is moved onto that boundary. When
 * more than one lands on the same instant, the resulting billing events are indistinguishable:
 * same subscription, same effective date, and the same totalOrdering, which synthetic catalog
 * change events inherit from the subscription transition they were derived from. They therefore
 * compare equal in DefaultBillingEvent.compareTo, and DefaultBillingEventSet - a TreeSet - keeps
 * only the first one inserted. Insertion order is catalog effective date ascending, so the
 * survivor is the one from the OLDEST catalog version and the more recent price is silently lost.
 *
 * Catalog used here:
 *   v1  2026-07-01  electricity-monthly  100  (no effectiveDateForExistingSubscriptions)
 *   v2  2026-07-15  electricity-monthly   50  effectiveDateForExistingSubscriptions 2026-07-15
 *   v3  2026-07-18  electricity-monthly  150  effectiveDateForExistingSubscriptions 2026-07-18
 */
public class TestCatalogWithEffectiveDateForExistingSubscriptionsAlignedToBCD extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithEffectiveDateForExistingSubscriptionsAlignedToBCD");
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(null, allExtraProperties);
    }

    /**
     * Two catalog changes inside the same billing cycle: only the older one is applied.
     *
     * Timeline:
     *   2026-07-01  subscription created on catalog v1 (100), BCD 1, first cycle runs to 2026-08-01
     *   2026-07-15  catalog v2 makes the price 50,  effectiveDateForExistingSubscriptions 2026-07-15
     *   2026-07-18  catalog v3 makes the price 150, effectiveDateForExistingSubscriptions 2026-07-18
     *
     * Both changes fall inside the 2026-07-01 -> 2026-08-01 cycle, so both align onto 2026-08-01.
     *
     * EXPECTED : 2026-08-01 -> 2026-09-01 bills 150, from catalog v3 (the most recent change wins).
     * CURRENTLY: it bills 50, from catalog v2, and keeps billing 50 on every later period.
     */
    @Test(groups = "slow")
    public void testTwoCatalogChangesWithinSameBillingCycle() throws Exception {

        clock.setDay(new LocalDate(2026, 7, 1));

        final VersionedCatalog catalog = catalogUserApi.getCatalog("ElectricUtility", callContext);
        Assert.assertEquals(catalog.getVersions().size(), 3, "Expected the 3 catalog versions v1/v2/v3 to be loaded");

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey1", "Electricity",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        assertNotNull(bpEntitlement);

        // 2026-07-01 -> 2026-08-01 at 100, from catalog v1. The in-flight period is never disturbed,
        // which is the point of the alignment, so this is correct today and must stay correct.
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2026, 7, 1), new LocalDate(2026, 8, 1),
                                                                                      InvoiceItemType.RECURRING, new BigDecimal("100.00")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0,
                            "First period should be priced from catalog v1 (2026-07-01)");

        // 2026-08-01 -> 2026-09-01 must pick up the LAST change made during the cycle, i.e. v3 at 150.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-08-01
        assertListenerStatus();

        try {
            curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                     new ExpectedInvoiceItemCheck(new LocalDate(2026, 8, 1), new LocalDate(2026, 9, 1),
                                                                                  InvoiceItemType.RECURRING, new BigDecimal("150.00")));
        } catch (final AssertionError e) {
            throw new AssertionError("Catalog change to 150 (v3, effectiveDateForExistingSubscriptions 2026-07-18) was NOT applied.\n"
                                     + "Both v2 (2026-07-15, price 50) and v3 (2026-07-18, price 150) fall inside the subscription's\n"
                                     + "first billing cycle 2026-07-01 -> 2026-08-01, so both are aligned onto 2026-08-01. The two\n"
                                     + "resulting CHANGE billing events are then indistinguishable - same subscription, same effective\n"
                                     + "date, and the same totalOrdering inherited from the CREATE transition - so\n"
                                     + "DefaultBillingEvent.compareTo returns 0 and DefaultBillingEventSet (a TreeSet) silently drops\n"
                                     + "the second add(). Insertion order is catalog effective date ascending, so the survivor is v2\n"
                                     + "and the subscription bills 50 instead of 150.\n"
                                     + "Original failure follows:\n" + e.getMessage(), e);
        }
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(2).getEffectiveDate()), 0,
                            "Period 2026-08-01 -> 2026-09-01 should be priced from catalog v3 (2026-07-18), the most recent change in the cycle");

        // The candidate list is rebuilt identically on every invoice run, so the loss is permanent
        // rather than limited to the first boundary after the changes.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-09-01
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2026, 9, 1), new LocalDate(2026, 10, 1),
                                                                              InvoiceItemType.RECURRING, new BigDecimal("150.00")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(2).getEffectiveDate()), 0,
                            "Later periods should also be priced from catalog v3 (2026-07-18)");
    }

    /**
     * Control: a subscription whose cycle contains only ONE pending catalog change picks it up.
     *
     * This subscription starts 2026-07-16 (BCD 16), so it is created on catalog v2 (50) and only v3
     * (2026-07-18) is pending. Nothing collides and the change applies at the next boundary.
     *
     * This passes today and must keep passing after any fix: it is what shows the defect is specific
     * to two changes colliding on one boundary, and not a general failure of catalog migration.
     */
    @Test(groups = "slow")
    public void testSingleCatalogChangeWithinBillingCycle() throws Exception {

        clock.setDay(new LocalDate(2026, 7, 16));

        final VersionedCatalog catalog = catalogUserApi.getCatalog("ElectricUtility", callContext);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(16));

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey2", "Electricity",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        assertNotNull(bpEntitlement);

        // Created on catalog v2, so the first period is priced at 50
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2026, 7, 16), new LocalDate(2026, 8, 16),
                                                                                      InvoiceItemType.RECURRING, new BigDecimal("50.00")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0,
                            "First period should be priced from catalog v2 (2026-07-15)");

        // Only v3 is pending for this subscription, so nothing collides and 150 applies normally
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-08-16
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2026, 8, 16), new LocalDate(2026, 9, 16),
                                                                              InvoiceItemType.RECURRING, new BigDecimal("150.00")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(2).getEffectiveDate()), 0,
                            "A single pending catalog change must apply at the next boundary, priced from catalog v3");
    }
}
