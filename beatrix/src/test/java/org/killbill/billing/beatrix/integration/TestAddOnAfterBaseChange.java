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
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.fail;

//
// See https://github.com/killbill/killbill/issues/2363
//
// Catalog: BaseA offers AddonX and AddonY, BaseB offers only AddonY.
//
public class TestAddOnAfterBaseChange extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testAddOnAfterBaseChange");
        return super.getConfigSource(null, allExtraProperties);
    }

    //
    // The BASE goes BaseA -> BaseB -> BaseA. AddonX (not offered by BaseB) and then AddonY are created
    // afterwards, while the BASE is back on BaseA: both must be billed from their own start date.
    //
    @Test(groups = "slow")
    public void testAddOnsCreatedAfterBaseChangeToPlanNotOfferingThem() throws Exception {
        clock.setDay(new LocalDate(2024, 1, 1));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // 2024-01-01: BASE on base-a-monthly
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "BaseA", ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                                                            NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 1, 1), new LocalDate(2024, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("10.00")));

        // 2024-02-01: BASE changes to base-b-monthly (BaseB does not offer AddonX)
        Entitlement refreshedBp = entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext);
        refreshedBp.changePlanWithDate(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("base-b-monthly", null)), new LocalDate(2024, 2, 1), null, callContext);

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 2, 1), new LocalDate(2024, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("8.00")));

        // 2024-03-01: BASE changes back to base-a-monthly
        refreshedBp = entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext);
        refreshedBp.changePlanWithDate(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("base-a-monthly", null)), new LocalDate(2024, 3, 1), null, callContext);

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 3, 1), new LocalDate(2024, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("10.00")));

        // 2024-03-15: AddonX is created (offered by BaseA, the current BASE plan)
        clock.addDays(14);
        assertListenerStatus();
        final Subscription addOnX = createAddOnAndCheckInvoiced(account, bpEntitlement.getBundleId(), "AddonX", 4,
                                                                new LocalDate(2024, 3, 15), new LocalDate(2024, 4, 1), new BigDecimal("1.65"));

        // The earlier BASE change to BaseB must not stop AddonX's billing
        assertEquals(internalCallContext.toLocalDate(addOnX.getBillingStartDate()), new LocalDate(2024, 3, 15),
                     "Wrong billing start date for AddonX\n" + describeAddOn(account, addOnX));
        for (final SubscriptionEvent event : addOnX.getSubscriptionEvents()) {
            assertNotEquals(event.getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING,
                            "Unexpected STOP_BILLING for AddonX\n" + describeAddOn(account, addOnX));
        }

        // 2024-03-22: AddonY is created (offered by both BASE products)
        clock.addDays(7);
        assertListenerStatus();
        createAddOnAndCheckInvoiced(account, bpEntitlement.getBundleId(), "AddonY", 5,
                                    new LocalDate(2024, 3, 22), new LocalDate(2024, 4, 1), new BigDecimal("0.65"));

        // 2024-04-01: next period bills the BASE and both add-ons
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(10);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 6, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 1), new LocalDate(2024, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("10.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 1), new LocalDate(2024, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("3.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 1), new LocalDate(2024, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2.00")));
    }

    // Creates the add-on, expects it to be invoiced right away, and on failure reports what was expected vs. what happened
    private Subscription createAddOnAndCheckInvoiced(final Account account, final UUID bundleId, final String productName, final int expectedInvoiceNumber,
                                                     final LocalDate expectedStart, final LocalDate expectedEnd, final BigDecimal expectedAmount) throws Exception {
        try {
            addAOEntitlementAndCheckForCompletion(bundleId, productName, ProductCategory.ADD_ON, BillingPeriod.MONTHLY,
                                                  NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        } catch (final AssertionError e) {
            final Subscription addOn = findAddOn(bundleId, productName);
            fail("\n" + productName + " created on " + clock.getUTCToday() + " was not invoiced\n" +
                 "  Expected : invoice #" + expectedInvoiceNumber + " with " + productName + " RECURRING " + expectedStart + " -> " + expectedEnd + " " + expectedAmount + "\n" +
                 "  Actual   :\n" +
                 (addOn == null ? "  " + productName + " subscription not found in the bundle\n" : describeAddOn(account, addOn)));
        }
        invoiceChecker.checkInvoice(account.getId(), expectedInvoiceNumber, callContext,
                                    new ExpectedInvoiceItemCheck(expectedStart, expectedEnd, InvoiceItemType.RECURRING, expectedAmount));
        return findAddOn(bundleId, productName);
    }

    private Subscription findAddOn(final UUID bundleId, final String productName) throws Exception {
        for (final Subscription subscription : subscriptionApi.getSubscriptionBundle(bundleId, callContext).getSubscriptions()) {
            if (productName.equals(subscription.getLastActiveProduct().getName())) {
                return subscription;
            }
        }
        return null;
    }

    // Invoices, invoice items and subscription events for one add-on, as printable lines
    private String describeAddOn(final Account account, final Subscription addOn) throws Exception {
        final String productName = addOn.getLastActiveProduct().getName();
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);

        final StringBuilder sb = new StringBuilder();
        sb.append("  Invoices on the account: ").append(invoices.size()).append("\n");
        sb.append("  ").append(productName).append(" invoice items:");
        boolean found = false;
        for (final Invoice invoice : invoices) {
            for (final InvoiceItem item : invoice.getInvoiceItems()) {
                if (addOn.getId().equals(item.getSubscriptionId())) {
                    sb.append("\n    invoice #").append(invoice.getInvoiceNumber()).append("  ").append(item.getInvoiceItemType())
                      .append(" ").append(item.getStartDate()).append(" -> ").append(item.getEndDate())
                      .append(" ").append(item.getAmount().setScale(2, RoundingMode.HALF_UP));
                    found = true;
                }
            }
        }
        sb.append(found ? "\n" : " none\n");
        sb.append("  ").append(productName).append(" billing start date: ").append(internalCallContext.toLocalDate(addOn.getBillingStartDate())).append("\n");
        sb.append("  ").append(productName).append(" subscription events:");
        for (final SubscriptionEvent event : addOn.getSubscriptionEvents()) {
            sb.append("\n    ").append(internalCallContext.toLocalDate(event.getEffectiveDate()))
              .append("  ").append(String.format("%-18s", event.getSubscriptionEventType()))
              .append(" ").append(event.getServiceName());
        }
        return sb.append("\n").toString();
    }
}
