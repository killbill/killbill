/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.invoice.optimizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.MockInternationalPrice;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizerExp.AccountInvoicesExp;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.plumbing.billing.DefaultBillingEventSet;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.killbill.billing.invoice.TestInvoiceHelper.ONE;
import static org.killbill.billing.invoice.TestInvoiceHelper.ZERO;

public class TestInvoiceOptimizerExp extends InvoiceTestSuiteNoDB {

    final String productName = "Foo";
    final String planName = "foo-monthly";
    final String phaseName = "foo-monthly-recurring";

    private Account account;
    private SubscriptionBase subscription;

    @BeforeClass(groups = "fast")
    public void setup() throws AccountApiException, SubscriptionBaseApiException {
        account = invoiceUtil.createAccount(callContext);
        subscription = invoiceUtil.createSubscription();
    }


    @Test(groups = "fast")
    public void testInArrearP0M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        LocalDate targetDate =  new LocalDate(2021, 6, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-6-1
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);

        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 5, 1), new LocalDate(2021, 6, 1)));


        // P0M
        final LocalDate cutoffDate = targetDate;
        // Existing: invoice from 2021-2-1 -> 2021-5-1
        // Existing (filtered) empty
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        existing.add(invoice);

        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);

        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ARREAR, SubscriptionBaseTransitionType.CREATE));


        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 1);
        // New proposed item
        Assert.assertEquals(proposedItems.get(0).getStartDate(), new LocalDate(2021, 5, 1));
        Assert.assertEquals(proposedItems.get(0).getEndDate(), new LocalDate(2021, 6, 1));
    }

    @Test(groups = "fast")
    public void testInArrearLateP0M() {
        // Exactly same test as testInArrearP0M as we both end up with the same empty filtered existing invoices
    }

    @Test(groups = "fast")
    public void testInArrearWithCancellationP0M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        final LocalDate cancelDate = new LocalDate(2021, 4, 30);
        LocalDate targetDate =  new LocalDate(2021, 6, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-4-30
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, new BigDecimal("9.88"), new LocalDate(2021, 4, 1), cancelDate));

        // P0M
        final LocalDate cutoffDate = targetDate;
        // Existing: invoice from 2021-2-1 -> 2021-4-30
        // Existing (filtered) empty
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        existing.add(invoice);

        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);

        // Note that we don't really need the billing events except to fetch Plan info in AccountInvoicesExp#filterProposedItems
        // so we don't need to explicitly add the CANCEL billing event
        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ARREAR, SubscriptionBaseTransitionType.CREATE));


        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 0);
    }

    @Test(groups = "fast")
    public void testInArrearP1M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        LocalDate targetDate =  new LocalDate(2021, 6, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-6-1
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);

        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 5, 1), new LocalDate(2021, 6, 1)));


        // P1M
        final LocalDate cutoffDate = targetDate.minusMonths(1);
        // Existing: invoice from 2021-2-1 -> 2021-5-1
        // Existing (filtered) 2021-4-1 -> 2021-5-1
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        final InvoiceItem newItem = createItem(invoice.getId(), BigDecimal.TEN, BigDecimal.TEN, cutoffDate.minusMonths(1), cutoffDate);
        invoice.addInvoiceItem(newItem);
        existing.add(invoice);

        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);

        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ARREAR, SubscriptionBaseTransitionType.CREATE));


        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 2);
        // Latest existing (P1M)
        Assert.assertEquals(proposedItems.get(0).getStartDate(), new LocalDate(2021, 4, 1));
        Assert.assertEquals(proposedItems.get(0).getEndDate(), new LocalDate(2021, 5, 1));
        // New proposed item
        Assert.assertEquals(proposedItems.get(1).getStartDate(), new LocalDate(2021, 5, 1));
        Assert.assertEquals(proposedItems.get(1).getEndDate(), new LocalDate(2021, 6, 1));
    }


    @Test(groups = "fast")
    public void testInArrearLateP1M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        LocalDate targetDate =  new LocalDate(2021, 6, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-6-1
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);

        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 5, 1), new LocalDate(2021, 6, 1)));


        // P1M
        // We assume we did not bill last N period so there is no filtered
        final LocalDate cutoffDate = targetDate.minusMonths(1);
        // Existing: empty
        // Existing (filtered) empty
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        existing.add(invoice);

        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);

        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ARREAR, SubscriptionBaseTransitionType.CREATE));


        //
        // Output is similar to previous test 'testInArrearP1M', i.e we have a proposed
        // for last period and new one (but because there is no existing for last period)
        // it would remain when coming back from the tree
        //
        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 2);
        // Latest existing (P1M)
        Assert.assertEquals(proposedItems.get(0).getStartDate(), new LocalDate(2021, 4, 1));
        Assert.assertEquals(proposedItems.get(0).getEndDate(), new LocalDate(2021, 5, 1));
        // New proposed item
        Assert.assertEquals(proposedItems.get(1).getStartDate(), new LocalDate(2021, 5, 1));
        Assert.assertEquals(proposedItems.get(1).getEndDate(), new LocalDate(2021, 6, 1));
    }


    @Test(groups = "fast")
    public void testInArrearWithCancellationP1M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        final LocalDate cancelDate = new LocalDate(2021, 4, 30);
        LocalDate targetDate =  new LocalDate(2021, 6, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-4-30
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, new BigDecimal("9.88"), new LocalDate(2021, 4, 1), new LocalDate(2021, 4, 30))); // cancelDate

        // P1M
        final LocalDate cutoffDate = targetDate.minusMonths(1);
        // Existing: invoice from 2021-2-1 -> 2021-4-30
        // Existing (filtered) 2021-4-1 -> 2021-4-30
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        final InvoiceItem newItem = createItem(invoice.getId(), BigDecimal.TEN, new BigDecimal("9.88"),new LocalDate(2021, 4, 1), new LocalDate(2021, 4, 30));
        invoice.addInvoiceItem(newItem);
        existing.add(invoice);

        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);

        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ARREAR, SubscriptionBaseTransitionType.CREATE));


        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 1);
        // Latest existing (P1M)
        Assert.assertEquals(proposedItems.get(0).getStartDate(), new LocalDate(2021, 4, 1));
        Assert.assertEquals(proposedItems.get(0).getEndDate(), cancelDate);
    }



    @Test(groups = "fast")
    public void testInAdvanceP0M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        LocalDate targetDate =  new LocalDate(2021, 5, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-6-1
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);

        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 5, 1), new LocalDate(2021, 6, 1)));


        // P0M
        final LocalDate cutoffDate = targetDate;
        // Existing: invoice from 2021-2-1 -> 2021-5-1
        // Existing (filtered) empty
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        existing.add(invoice);

        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ADVANCE, SubscriptionBaseTransitionType.CREATE));

        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);
        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 1);
        // New proposed item
        Assert.assertEquals(proposedItems.get(0).getStartDate(), new LocalDate(2021, 5, 1));
        Assert.assertEquals(proposedItems.get(0).getEndDate(), new LocalDate(2021, 6, 1));
    }

    @Test(groups = "fast")
    public void testInAdvanceLateP0M() {
        // Exactly same test as testInAdvanceP0M as we both end up with the same empty filtered existing invoices
    }

    @Test(groups = "fast")
    public void testInAdvanceWithCancellationP0M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        final LocalDate cancelDate = new LocalDate(2021, 4, 30);
        final LocalDate targetDate =  new LocalDate(2021, 5, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-4-30
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);

        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 4, 1), cancelDate));


        // P0M
        final LocalDate cutoffDate = targetDate;
        // Existing: invoice from 2021-2-1 -> 2021-4-30
        // Existing (filtered) empty
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        existing.add(invoice);


        // Note that we don't really need the billing events except to fetch Plan info in AccountInvoicesExp#filterProposedItems
        // so we don't need to explicitly add the CANCEL billing event
        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ADVANCE, SubscriptionBaseTransitionType.CREATE));


        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);
        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 0);
    }



    @Test(groups = "fast")
    public void testInAdvanceP1M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        LocalDate targetDate =  new LocalDate(2021, 5, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-6-1
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);

        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 5, 1), new LocalDate(2021, 6, 1)));

        // P1M
        final LocalDate cutoffDate = targetDate.minusMonths(1);
        // Existing: invoice from 2021-2-1 -> 2021-5-1
        // Existing (filtered)  2021-4-1 ->  2021-5-1
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        final InvoiceItem newItem = createItem(invoice.getId(), BigDecimal.TEN, BigDecimal.TEN,new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1));
        invoice.addInvoiceItem(newItem);
        existing.add(invoice);


        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ADVANCE, SubscriptionBaseTransitionType.CREATE));

        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);
        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 2);
        // Latest existing (P1M) - this would be filtered out by the tree later on
        Assert.assertEquals(proposedItems.get(0).getStartDate(), new LocalDate(2021, 4, 1));
        Assert.assertEquals(proposedItems.get(0).getEndDate(), new LocalDate(2021, 5, 1));
        // New proposed item
        Assert.assertEquals(proposedItems.get(1).getStartDate(), new LocalDate(2021, 5, 1));
        Assert.assertEquals(proposedItems.get(1).getEndDate(), new LocalDate(2021, 6, 1));
    }


    @Test(groups = "fast")
    public void testInAdvanceLateP1M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        LocalDate targetDate =  new LocalDate(2021, 5, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-6-1
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);

        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 5, 1), new LocalDate(2021, 6, 1)));

        // P1M
        final LocalDate cutoffDate = targetDate.minusMonths(1);
        // Existing: empty
        // Existing (filtered)  empty
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        final InvoiceItem newItem = createItem(invoice.getId(), BigDecimal.TEN, BigDecimal.TEN,new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1));
        invoice.addInvoiceItem(newItem);
        existing.add(invoice);


        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ADVANCE, SubscriptionBaseTransitionType.CREATE));

        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);
        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 2);
        // Latest existing (P1M) - this would be regenerated, we would catch up for one period
        Assert.assertEquals(proposedItems.get(0).getStartDate(), new LocalDate(2021, 4, 1));
        Assert.assertEquals(proposedItems.get(0).getEndDate(), new LocalDate(2021, 5, 1));
        // New proposed item
        Assert.assertEquals(proposedItems.get(1).getStartDate(), new LocalDate(2021, 5, 1));
        Assert.assertEquals(proposedItems.get(1).getEndDate(), new LocalDate(2021, 6, 1));
    }


    @Test(groups = "fast")
    public void testInAdvanceWithCancellationP1M() {

        final LocalDate startDate = new LocalDate(2021, 2, 1);
        final LocalDate cancelDate = new LocalDate(2021, 4, 30);
        final LocalDate targetDate =  new LocalDate(2021, 5, 1);

        // Proposed: invoice from 2021-2-1 -> 2021-4-30
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final Invoice proposed = createInvoice(targetDate);

        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, startDate, new LocalDate(2021, 3, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, BigDecimal.TEN, new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1)));
        proposedItems.add(createItem(proposed.getId(), BigDecimal.TEN, new BigDecimal("9.88"), new LocalDate(2021, 4, 1), cancelDate));


        // P0M
        final LocalDate cutoffDate = targetDate.minusMonths(1);
        // Existing: invoice from 2021-2-1 -> 2021-4-30
        // Existing (filtered) 2021-4-1 -> 2021-4-30
        final List<Invoice> existing = new ArrayList<Invoice>();
        final Invoice invoice = createInvoice(cutoffDate);
        final InvoiceItem newItem = createItem(invoice.getId(), BigDecimal.TEN, new BigDecimal("9.88"),new LocalDate(2021, 4, 1), cancelDate);
        invoice.addInvoiceItem(newItem);
        existing.add(invoice);



        // Note that we don't really need the billing events except to fetch Plan info in AccountInvoicesExp#filterProposedItems
        // so we don't need to explicitly add the CANCEL billing event
        final DefaultBillingEventSet billingEvents = new DefaultBillingEventSet(false, false, false);
        billingEvents.add(createBillingEvent(startDate, BillingMode.IN_ADVANCE, SubscriptionBaseTransitionType.CREATE));


        final AccountInvoicesExp test = new AccountInvoicesExp(cutoffDate, null, existing);
        test.filterProposedItems(proposedItems, billingEvents, internalCallContext);
        Assert.assertEquals(proposedItems.size(), 1);
        // Latest existing (P1M)
        Assert.assertEquals(proposedItems.get(0).getStartDate(), new LocalDate(2021, 4, 1));
        Assert.assertEquals(proposedItems.get(0).getEndDate(), cancelDate);

    }


    private InvoiceItem createItem(final UUID invoiceId, final BigDecimal amount, final BigDecimal rate, final LocalDate startDate, final LocalDate endDate) {
        final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, account.getId(), subscription.getBundleId(), subscription.getId(), productName, planName, phaseName, null, startDate, endDate,
                                                                   amount, rate, Currency.USD);

        return item;
    }

    private Invoice createInvoice(final LocalDate targetDate, final InvoiceItem...items) {
        Invoice invoice = new DefaultInvoice(account.getId(), targetDate, targetDate, account.getCurrency());
        for (InvoiceItem ii : items) {
            invoice.addInvoiceItem(ii);
        }
        return invoice;
    }


    private BillingEvent createBillingEvent(final LocalDate eventDate, final BillingMode billingMode, final SubscriptionBaseTransitionType transitionType) {
        final Plan plan = new MockPlan(planName).setRecurringBillingMode(billingMode);

        final MockInternationalPrice zeroPrice = new MockInternationalPrice(new DefaultPrice(ZERO, Currency.USD));
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(new DefaultPrice(ONE, Currency.USD));

        final PlanPhase phase = new MockPlanPhase(recurringPrice, zeroPrice, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(account, subscription, eventDate.toDateTimeAtStartOfDay(),
                                                                       plan, phase,
                                                                       ZERO, BigDecimal.TEN, Currency.USD, BillingPeriod.MONTHLY, 1,
                                                                       BillingMode.IN_ARREAR, "Test Event 1", 1L,
                                                                       transitionType);
        return event1;
    }
}
