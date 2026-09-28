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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
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
 *
 * Each test collects every billing period it checks and logs a summary table before asserting, so
 * a failing run shows at a glance which periods were mispriced and which catalog version they were
 * stuck on.
 */
public class TestCatalogWithEffectiveDateForExistingSubscriptionsAlignedToBCD extends TestIntegrationBase {

    private static final String COLLISION_EXPLANATION =
            "Both catalog v2 (2026-07-15, price 50) and v3 (2026-07-18, price 150) fall inside the subscription's "
            + "billing cycle 2026-07-01 -> 2026-08-01, so both are aligned onto 2026-08-01. The two resulting CHANGE "
            + "billing events are then indistinguishable - same subscription, same effective date, and the same "
            + "totalOrdering inherited from the CREATE transition - so DefaultBillingEvent.compareTo returns 0 and "
            + "DefaultBillingEventSet (a TreeSet) silently drops the second add(). Insertion order is catalog "
            + "effective date ascending, so the survivor is v2 (50) and the more recent v3 (150) is lost.";

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
     * EXPECTED : 2026-08-01 -> 2026-09-01 bills 150, from catalog v3 (the most recent change wins).
     * CURRENTLY: it bills 50, from catalog v2, and keeps billing 50 on every later period.
     */
    @Test(groups = "slow", enabled = false, description = "Reproduces #2291")
    public void testTwoCatalogChangesWithinSameBillingCycle() throws Exception {

        clock.setDay(new LocalDate(2026, 7, 1));

        final VersionedCatalog catalog = catalogUserApi.getCatalog("ElectricUtility", callContext);
        Assert.assertEquals(catalog.getVersions().size(), 3, "Expected catalog versions v1/v2/v3 to be loaded");

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey1", "Electricity",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        assertNotNull(bpEntitlement);

        final List<PeriodResult> results = new ArrayList<PeriodResult>();

        // 2026-07-01 -> 2026-08-01 at 100, from catalog v1. The in-flight period is never disturbed,
        // which is the point of the alignment, so this is correct today and must stay correct.
        results.add(recordRecurring(account, 1, new LocalDate(2026, 7, 1), new LocalDate(2026, 8, 1),
                                    new BigDecimal("100.00"), catalog, 0));

        // 2026-08-01 -> 2026-09-01 must pick up the LAST change made during the cycle, i.e. v3 at 150.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-08-01
        assertListenerStatus();
        results.add(recordRecurring(account, 2, new LocalDate(2026, 8, 1), new LocalDate(2026, 9, 1),
                                    new BigDecimal("150.00"), catalog, 2));

        // The candidate list is rebuilt identically on every invoice run, so the loss is permanent
        // rather than limited to the first boundary after the changes.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-09-01
        assertListenerStatus();
        results.add(recordRecurring(account, 3, new LocalDate(2026, 9, 1), new LocalDate(2026, 10, 1),
                                    new BigDecimal("150.00"), catalog, 2));

        reportAndAssert("Two catalog changes within the same billing cycle", results, catalog);
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

        final List<PeriodResult> results = new ArrayList<PeriodResult>();

        // Created on catalog v2, so the first period is priced at 50
        results.add(recordRecurring(account, 1, new LocalDate(2026, 7, 16), new LocalDate(2026, 8, 16),
                                    new BigDecimal("50.00"), catalog, 1));

        // Only v3 is pending for this subscription, so nothing collides and 150 applies normally
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-08-16
        assertListenerStatus();
        results.add(recordRecurring(account, 2, new LocalDate(2026, 8, 16), new LocalDate(2026, 9, 16),
                                    new BigDecimal("150.00"), catalog, 2));

        reportAndAssert("Single catalog change within the billing cycle (control)", results, catalog);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Looks up the RECURRING item covering the given period and records what was billed against what
     * was expected. Nothing is asserted here so that every period is collected before the summary
     * table is logged.
     */
    private PeriodResult recordRecurring(final Account account,
                                         final int invoiceNb,
                                         final LocalDate startDate,
                                         final LocalDate endDate,
                                         final BigDecimal expectedAmount,
                                         final VersionedCatalog catalog,
                                         final int expectedCatalogVersionIdx) throws Exception {

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        Assert.assertTrue(invoices.size() >= invoiceNb,
                          String.format("Expected at least %d invoices for the account but found %d", invoiceNb, invoices.size()));
        final Invoice invoice = invoices.get(invoiceNb - 1);

        InvoiceItem recurring = null;
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (item.getInvoiceItemType() == InvoiceItemType.RECURRING &&
                item.getStartDate().compareTo(startDate) == 0 &&
                item.getEndDate().compareTo(endDate) == 0) {
                recurring = item;
                break;
            }
        }

        final Date expectedVersion = catalog.getVersions().get(expectedCatalogVersionIdx).getEffectiveDate();
        if (recurring == null) {
            return new PeriodResult(invoiceNb, startDate, endDate, expectedAmount, null, expectedVersion, null);
        }
        return new PeriodResult(invoiceNb, startDate, endDate, expectedAmount, recurring.getAmount(),
                                expectedVersion, recurring.getCatalogEffectiveDate() == null ? null : recurring.getCatalogEffectiveDate().toDate());
    }

    /**
     * Logs a table of every period checked, then fails if any of them is wrong.
     */
    private void reportAndAssert(final String title, final List<PeriodResult> results, final VersionedCatalog catalog) {

        final StringBuilder sb = new StringBuilder();
        sb.append('\n').append(title).append('\n');
        sb.append("+---------+---------------------------+----------+----------+--------+----------------------+----------------------+\n");
        sb.append("| Invoice | Period                    | Expected |   Actual | Result | Catalog used         | Catalog expected     |\n");
        sb.append("+---------+---------------------------+----------+----------+--------+----------------------+----------------------+\n");

        int failures = 0;
        for (final PeriodResult r : results) {
            if (!r.passed()) {
                failures++;
            }
            sb.append(String.format("| %7d | %s -> %s | %8s | %8s | %-6s | %-20s | %-20s |%n",
                                    r.invoiceNb,
                                    r.startDate,
                                    r.endDate,
                                    r.expectedAmount.toPlainString(),
                                    r.actualAmount == null ? "MISSING" : r.actualAmount.toPlainString(),
                                    r.passed() ? "PASS" : "FAIL",
                                    versionLabel(catalog, r.actualCatalogVersion),
                                    versionLabel(catalog, r.expectedCatalogVersion)));
        }
        sb.append("+---------+---------------------------+----------+----------+--------+----------------------+----------------------+\n");

        final String table = sb.toString();
        log.info(table);

        if (failures > 0) {
            // The table goes into the assertion message itself so that it shows up in the surefire
            // failure report, rather than being buried in the test log.
            Assert.fail(String.format("%d of %d billing periods were mispriced or priced from the wrong catalog version.%n%s%n%s",
                                      failures, results.size(), table, COLLISION_EXPLANATION));
        }
    }

    private String versionLabel(final VersionedCatalog catalog, final Date date) {
        if (date == null) {
            return "n/a";
        }
        for (int i = 0; i < catalog.getVersions().size(); i++) {
            if (catalog.getVersions().get(i).getEffectiveDate().compareTo(date) == 0) {
                return String.format("%s (v%d)", new DateTime(date, DateTimeZone.UTC).toLocalDate(), i + 1);
            }
        }
        return new DateTime(date, DateTimeZone.UTC).toLocalDate().toString();
    }

    private static final class PeriodResult {

        private final int invoiceNb;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final BigDecimal expectedAmount;
        private final BigDecimal actualAmount;
        private final Date expectedCatalogVersion;
        private final Date actualCatalogVersion;

        private PeriodResult(final int invoiceNb,
                             final LocalDate startDate,
                             final LocalDate endDate,
                             final BigDecimal expectedAmount,
                             final BigDecimal actualAmount,
                             final Date expectedCatalogVersion,
                             final Date actualCatalogVersion) {
            this.invoiceNb = invoiceNb;
            this.startDate = startDate;
            this.endDate = endDate;
            this.expectedAmount = expectedAmount;
            this.actualAmount = actualAmount;
            this.expectedCatalogVersion = expectedCatalogVersion;
            this.actualCatalogVersion = actualCatalogVersion;
        }

        private boolean passed() {
            return actualAmount != null &&
                   actualAmount.compareTo(expectedAmount) == 0 &&
                   actualCatalogVersion != null &&
                   actualCatalogVersion.compareTo(expectedCatalogVersion) == 0;
        }
    }
}
