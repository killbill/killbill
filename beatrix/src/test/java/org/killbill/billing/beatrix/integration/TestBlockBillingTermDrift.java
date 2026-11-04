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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

/**
 * Repeated blockBilling on a multi-month billing period leaves the subscription with overlapping
 * RECURRING terms, after which no invoice can be generated and the account is parked.
 *
 * When billing is unblocked, Kill Bill does not resume the remainder of the in-flight term - it
 * opens a NEW full term anchored to the resume date. The replacement therefore ends LATER than the
 * term it replaced, while the repaired term keeps its original end date on disk (a repair never
 * shortens the item it cancels).
 *
 * After two block/unblock cycles there are three terms whose end dates walk forward:
 *
 *   term 1   2024-06-21 -> 2025-06-21
 *   term 2   2024-07-21 -> 2025-07-21
 *   term 3   2024-09-24 -> 2025-09-21
 *
 * The window between two consecutive end dates - here 2025-06-21 -> 2025-07-21 - is then covered by
 * three items at once: term 2, term 2's repair, and term 3. ItemsInterval.getResulting_ADD_Item
 * permits at most two (one charge plus its repair) and throws:
 *
 *   Preconditions.checkState(items.size() <= 2, "Double billing detected: %s", items);
 *
 * The check runs in buildForExistingItems, i.e. over items ALREADY persisted and before proposed
 * items are merged, so the target date is irrelevant and every subsequent run fails identically.
 *
 * Note the customer is not double billed: each repair cancels its own charge and the net billed
 * periods do not overlap. Only the stored date ranges do.
 *
 * MONTHLY is immune, because the replacement term starts on the day the previous one ends - the two
 * abut instead of overlapping. QUARTERLY, ANNUAL and BIANNUAL all fail on the same timeline.
 */
public class TestBlockBillingTermDrift extends TestIntegrationBase {

    private static final String BLOCK_SERVICE = "test-block-service";

    private static final String DRIFT_EXPLANATION =
            "Each END_BILLING_DISABLED opens a new full term anchored to the resume date instead of resuming the "
            + "remainder of the in-flight term, so every replacement ends later than the term it replaced. The "
            + "repaired term keeps its original end date, so the two overlap. Once three terms exist, the window "
            + "between two consecutive end dates carries a charge, its repair and the next charge - three items "
            + "where ItemsInterval.getResulting_ADD_Item allows at most two.";

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testBlockBillingTermDrift");
        return super.getConfigSource(null, allExtraProperties);
    }

    /**
     * Timeline (ANNUAL 300, BCD 21):
     *   2024-06-09  subscribe
     *   2024-07-05  blockBilling ON   -> repairs term 1
     *   2024-07-16  blockBilling OFF  -> term 2, ends 2025-07-21
     *   2024-08-05  blockBilling ON   -> repairs term 2
     *   2024-09-24  blockBilling OFF  -> term 3, ends 2025-09-21
     *   2024-10-21  invoice run
     *
     * EXPECTED : the invoice run succeeds and the account is not parked.
     * TODAY    : it throws ILLEGAL INVOICING STATE and the account is parked, so this test FAILS
     *            against the current code base. It should go green once the behaviour is fixed.
     */
    @Test(groups = "slow", enabled = false, description = "https://github.com/killbill/killbill/issues/2306")
    public void testRepeatedBlockBillingParksAccount() throws Exception {

        clock.setDay(new LocalDate(2024, 6, 9));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(21));

        // createBaseEntitlementAndCheckForCompletion registers these events itself - do not push them here.
        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey1", "Svc",
                                                           ProductCategory.BASE, BillingPeriod.ANNUAL,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE,
                                                           NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        assertNotNull(bpEntitlement);
        final UUID bundleId = bpEntitlement.getBundleId();

        // ---- cycle 1 -------------------------------------------------------------------------

        // Every clock move that crosses a billing boundary fires its own invoice run and must declare
        // its events, otherwise they are still queued when the next call runs.
        //
        // Each repair credits the unused remainder back as a CBA, which covers part of the next
        // invoice. Where the credit covers the whole invoice there is no payment; where it only
        // covers part of it, the balance is paid and PAYMENT / INVOICE_PAYMENT follow.

        // 2024-07-05: crosses 2024-06-21, term 1 (300) is billed and paid.
        addDaysAndCheckForCompletion(26, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // Billing blocked: the unused remainder of term 1 is repaired (-288.49), creating a credit.
        setBlockBilling(bundleId, true, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2024-07-16: nothing to bill while billing is disabled.
        addDaysAndCheckForCompletion(11);

        // Unblocked: a stub (4.10) carries the subscription to the next BCD, drawn from the credit.
        setBlockBilling(bundleId, false, NextEvent.BLOCK, NextEvent.INVOICE);

        // ---- cycle 2 -------------------------------------------------------------------------

        // 2024-08-05: crosses 2024-07-21, where term 2 opens and runs to 2025-07-21 - one month
        // past where term 1 ended. This is the drift. The 288.49 credit less the 4.10 stub leaves
        // 284.39, so 15.61 of the 300 is paid.
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // Blocked again: term 2 is repaired (-287.67).
        setBlockBilling(bundleId, true, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2024-09-24: nothing to bill while blocked.
        addDaysAndCheckForCompletion(50);

        // Unblocked: term 3 opens and runs to 2025-09-21 - later again. The 287.67 credit leaves a
        // balance on the 297.53 charge, so it is paid.
        setBlockBilling(bundleId, false, NextEvent.BLOCK, NextEvent.INVOICE,
                        NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // 2024-10-21: no boundary crossed, nothing to bill.
        addDaysAndCheckForCompletion(27);

        reportLadder(account);

        // EXPECTED: the invoice run succeeds and the account is not parked.
        // TODAY:    it throws ILLEGAL INVOICING STATE / "Double billing detected" and parks the
        //           account, so this test fails until the underlying behaviour is fixed.
        try {
            invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2024, 10, 21),
                                                    Collections.emptyList(), callContext);
        } catch (final InvoiceApiException e) {
            Assert.fail("Invoice generation failed after two blockBilling cycles: " + e.getMessage()
                        + "\n" + DRIFT_EXPLANATION);
        }

        Assert.assertFalse(isParked(account),
                           "Account was parked by the invoice run.\n" + DRIFT_EXPLANATION);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /**
     * isBlockBilling is the flag under test. isBlockChange and isBlockEntitlement are set alongside
     * it only to mirror a typical dunning suspension; neither affects the outcome.
     */
    private void setBlockBilling(final UUID bundleId, final boolean blocked, final NextEvent... events) throws Exception {
        final BlockingState state = new DefaultBlockingState(bundleId,
                                                             BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                             blocked ? "SUSPENDED" : "ACTIVE",
                                                             BLOCK_SERVICE,
                                                             blocked,   // blockChange
                                                             blocked,   // blockEntitlement
                                                             blocked,   // blockBilling
                                                             clock.getUTCNow());
        busHandler.pushExpectedEvents(events);
        subscriptionApi.addBlockingState(state, (LocalDate) null, Collections.emptyList(), callContext);
        assertListenerStatus();
    }

    private List<InvoiceItem> billingItems(final Account account) throws Exception {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        for (final Invoice invoice : invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext)) {
            for (final InvoiceItem item : invoice.getInvoiceItems()) {
                if (item.getInvoiceItemType() == InvoiceItemType.RECURRING ||
                    item.getInvoiceItemType() == InvoiceItemType.REPAIR_ADJ) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /**
     * Logs the full RECURRING / REPAIR_ADJ ladder. Read the end date column top to bottom: the
     * forward walk is the whole defect.
     */
    private void reportLadder(final Account account) throws Exception {

        final StringBuilder sb = new StringBuilder();
        sb.append("\nInvoice item ladder\n");
        sb.append("+-------------+-------------+-------------+------------+\n");
        sb.append("| Type        | Start       | End         |     Amount |\n");
        sb.append("+-------------+-------------+-------------+------------+\n");
        for (final InvoiceItem item : billingItems(account)) {
            sb.append(String.format("| %-11s | %-11s | %-11s | %10s |%n",
                                    item.getInvoiceItemType(),
                                    item.getStartDate(),
                                    item.getEndDate(),
                                    item.getAmount().toPlainString()));
        }
        sb.append("+-------------+-------------+-------------+------------+\n");
        log.info(sb.toString());
    }

    private boolean isParked(final Account account) throws Exception {
        for (final Tag tag : tagUserApi.getTagsForAccount(account.getId(), false, callContext)) {
            if (SystemTags.PARK_TAG_DEFINITION_ID.equals(tag.getTagDefinitionId())) {
                return true;
            }
        }
        return false;
    }

    /** Unused today, kept so the amounts in the ladder can be asserted if the fix changes them. */
    @SuppressWarnings("unused")
    private static BigDecimal amount(final String value) {
        return new BigDecimal(value);
    }
}
