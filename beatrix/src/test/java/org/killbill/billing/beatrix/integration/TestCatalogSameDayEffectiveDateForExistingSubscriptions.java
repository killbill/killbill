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
 * Companion to {@link TestCatalogWithEffectiveDateForExistingSubscriptionsAlignedToBCD}, covering the
 * second way a catalog change can be lost when
 * org.killbill.subscription.align.effectiveDateForExistingSubscriptions=true.
 *
 * When a change's effectiveDateForExistingSubscriptions falls on a day whose day-of-month equals the
 * subscription's BCD - the subscription's own start day being the common case - the alignment resolves
 * it to THAT SAME day rather than to the next billing date. The aligned LocalDate is then converted
 * back to a DateTime using the account's reference time of day, which precedes the subscription's
 * CREATE transition, so the candidate is rejected by
 *
 *     if (nextEffectiveDate != null && !nextEffectiveDate.isBefore(cur.getEffectiveTransitionTime()))
 *
 * in DefaultSubscriptionBase.getSubscriptionBillingEvents. Nothing regenerates it afterwards -
 * candidates are only built at CREATE/CHANGE/PHASE transitions - so the price change is lost forever
 * rather than deferred to the next billing date.
 *
 * Catalog used here:
 *   v1  2026-06-01T00:00Z  gas-monthly  100  (no effectiveDateForExistingSubscriptions)
 *   v2  2026-07-01T11:00Z  gas-monthly  200  effectiveDateForExistingSubscriptions 2026-07-01T11:00Z
 */
public class TestCatalogSameDayEffectiveDateForExistingSubscriptions extends TestIntegrationBase {

    private static final String DROP_EXPLANATION =
            "The catalog change is effective 2026-07-01T11:00Z, an hour AFTER the subscription started, so it should "
            + "apply at the subscription's next billing date (2026-08-01). Instead, because the change's day-of-month "
            + "(1) equals the subscription's BCD (1), BillCycleDayCalculator aligns it to 2026-07-01 - the same day the "
            + "subscription started - and TimeAwareContext.toUTCDateTime resolves that LocalDate at the account's "
            + "reference time of day (00:00), i.e. BEFORE the CREATE transition at 10:00. The guard in "
            + "getSubscriptionBillingEvents then discards the candidate, and nothing ever regenerates it, so the "
            + "subscription keeps billing the old price indefinitely.";

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogSameDayEffectiveDateForExistingSubscriptions");
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(null, allExtraProperties);
    }

    /**
     * A catalog change effective on the same day the subscription started is dropped entirely.
     *
     * The account is created at 2026-07-01T00:00Z and the subscription at 2026-07-01T10:00Z, so the
     * account reference time (00:00) is strictly earlier than the CREATE transition (10:00). The
     * catalog change is effective at 11:00 the same day - after the subscription started - and aligns
     * back to 2026-07-01T00:00Z, before CREATE.
     *
     * EXPECTED : 2026-07-01 -> 2026-08-01 bills 100 (the in-flight period is never disturbed), then
     *            2026-08-01 -> 2026-09-01 bills 200 at the next billing date.
     * CURRENTLY: every period bills 100 - the change is never applied at all.
     */
    @Test(groups = "slow", enabled = false, description = "Reproduces #2291")
    public void testCatalogChangeOnSubscriptionStartDay() throws Exception {

        // Account first, so its reference time of day is 00:00
        clock.setTime(new DateTime(2026, 7, 1, 0, 0, 0, DateTimeZone.UTC));

        final VersionedCatalog catalog = catalogUserApi.getCatalog("GasUtility", callContext);
        Assert.assertEquals(catalog.getVersions().size(), 2, "Expected catalog versions v1/v2 to be loaded");

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Subscription a few hours later the same day -> CREATE at 10:00, BCD 1
        clock.setTime(new DateTime(2026, 7, 1, 10, 0, 0, DateTimeZone.UTC));

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey1", "Gas",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        assertNotNull(bpEntitlement);

        final List<PeriodResult> results = new ArrayList<PeriodResult>();

        // 2026-07-01 -> 2026-08-01 at 100, from catalog v1: correct, the in-flight period is not disturbed
        results.add(recordRecurring(account, 1, new LocalDate(2026, 7, 1), new LocalDate(2026, 8, 1),
                                    new BigDecimal("100.00"), catalog, 0));

        // 2026-08-01 -> 2026-09-01 is the subscription's next billing date and must pick up v2 at 200
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-08-01
        assertListenerStatus();
        results.add(recordRecurring(account, 2, new LocalDate(2026, 8, 1), new LocalDate(2026, 9, 1),
                                    new BigDecimal("200.00"), catalog, 1));

        // The candidate is rebuilt and discarded again on every invoice run, so the loss is permanent
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-09-01
        assertListenerStatus();
        results.add(recordRecurring(account, 3, new LocalDate(2026, 9, 1), new LocalDate(2026, 10, 1),
                                    new BigDecimal("200.00"), catalog, 1));

        reportAndAssert("Catalog change effective on the subscription's start day", results, catalog);
    }

    /**
     * Control: the same catalog change applies normally to a subscription started in an earlier month.
     *
     * This subscription starts 2026-06-15 (BCD 15), so the change (2026-07-01, day-of-month 1, which is
     * not greater than the BCD 15) aligns to 2026-07-15 - comfortably after CREATE - and is applied at
     * that billing date. This passes today and must keep passing after any fix: it shows the defect is
     * specific to the aligned date landing on or before the current transition, and not a general
     * failure of catalog migration.
     */
    @Test(groups = "slow")
    public void testCatalogChangeForSubscriptionStartedEarlier() throws Exception {

        clock.setTime(new DateTime(2026, 6, 15, 10, 0, 0, DateTimeZone.UTC));

        final VersionedCatalog catalog = catalogUserApi.getCatalog("GasUtility", callContext);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(15));

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey2", "Gas",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        assertNotNull(bpEntitlement);

        final List<PeriodResult> results = new ArrayList<PeriodResult>();

        // 2026-06-15 -> 2026-07-15 at 100, from catalog v1
        results.add(recordRecurring(account, 1, new LocalDate(2026, 6, 15), new LocalDate(2026, 7, 15),
                                    new BigDecimal("100.00"), catalog, 0));

        // The change aligns to 2026-07-15, this subscription's next billing date, and applies there
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2026-07-15
        assertListenerStatus();
        results.add(recordRecurring(account, 2, new LocalDate(2026, 7, 15), new LocalDate(2026, 8, 15),
                                    new BigDecimal("200.00"), catalog, 1));

        reportAndAssert("Catalog change for a subscription started in an earlier month (control)", results, catalog);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Looks up the RECURRING item covering the given period and records what was billed against what was
     * expected. Nothing is asserted here so that every period is collected before the table is reported.
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
     * Logs a table of every period checked, then fails if any of them is wrong. The table is part of the
     * assertion message so that it shows up in the surefire failure report.
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
            Assert.fail(String.format("%d of %d billing periods were mispriced or priced from the wrong catalog version.%n%s%n%s",
                                      failures, results.size(), table, DROP_EXPLANATION));
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
