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

package com.ning.billing.overdue.calculator;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.overdue.config.api.BillingStateBundle;
import com.ning.billing.overdue.config.api.PaymentResponse;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestBillingStateCalculatorBundle extends TestBillingStateCalculator {

    private List<InvoiceItem> createInvoiceItems(final UUID[] bundleIds) {
        final List<InvoiceItem> result = new ArrayList<InvoiceItem>();
        for (final UUID id : bundleIds) {
            final InvoiceItem ii = Mockito.mock(InvoiceItem.class);
            Mockito.when(ii.getBundleId()).thenReturn(id);
            result.add(ii);
        }
        return result;
    }

    @Test(groups = {"fast"}, enabled = true)
    public void testUnpaidInvoiceForBundle() {
        final UUID thisBundleId = new UUID(0L, 0L);
        final UUID thatBundleId = new UUID(0L, 1L);

        now = new DateTime();
        final List<Invoice> invoices = new ArrayList<Invoice>(5);
        invoices.add(createInvoice(now, BigDecimal.ZERO, createInvoiceItems(new UUID[]{thisBundleId, thatBundleId})));
        // Will not be seen below
        invoices.add(createInvoice(now.plusDays(1), BigDecimal.TEN, createInvoiceItems(new UUID[]{thatBundleId})));
        invoices.add(createInvoice(now.plusDays(2), new BigDecimal("100.00"), createInvoiceItems(new UUID[]{thatBundleId, thisBundleId, thatBundleId})));
        invoices.add(createInvoice(now.plusDays(3), new BigDecimal("1000.00"), createInvoiceItems(new UUID[]{thisBundleId})));
        invoices.add(createInvoice(now.plusDays(4), new BigDecimal("10000.00"), createInvoiceItems(new UUID[]{thatBundleId, thisBundleId})));

        final Clock clock = new ClockMock();
        final InvoiceUserApi invoiceApi = Mockito.mock(InvoiceUserApi.class);
        final EntitlementUserApi entitlementApi = Mockito.mock(EntitlementUserApi.class);
        Mockito.when(invoiceApi.getUnpaidInvoicesByAccountId(Mockito.<UUID>any(), Mockito.<DateTime>any())).thenReturn(Collections2.filter(invoices, new Predicate<Invoice>() {
            @Override
            public boolean apply(@Nullable final Invoice invoice) {
                return invoice != null && BigDecimal.ZERO.compareTo(invoice.getBalance()) < 0;
            }
        }));

        final BillingStateCalculatorBundle calc = new BillingStateCalculatorBundle(entitlementApi, invoiceApi, clock);
        final SortedSet<Invoice> resultinvoices = calc.unpaidInvoicesForBundle(thisBundleId, new UUID(0L, 0L));

        Assert.assertEquals(resultinvoices.size(), 3);
        Assert.assertEquals(new BigDecimal("100.0").compareTo(resultinvoices.first().getBalance()), 0);
        Assert.assertEquals(new BigDecimal("10000.0").compareTo(resultinvoices.last().getBalance()), 0);
    }

    @Test(groups = {"fast"}, enabled = true)
    public void testcalculateBillingStateForBundle() throws Exception {
        final UUID thisBundleId = new UUID(0L, 0L);
        final UUID thatBundleId = new UUID(0L, 1L);

        now = new DateTime();
        final List<Invoice> invoices = new ArrayList<Invoice>(5);
        invoices.add(createInvoice(now.minusDays(5), BigDecimal.ZERO, createInvoiceItems(new UUID[]{thisBundleId, thatBundleId})));
        invoices.add(createInvoice(now.minusDays(4), BigDecimal.TEN, createInvoiceItems(new UUID[]{thatBundleId})));
        invoices.add(createInvoice(now.minusDays(3), new BigDecimal("100.00"), createInvoiceItems(new UUID[]{thatBundleId, thisBundleId, thatBundleId})));
        invoices.add(createInvoice(now.minusDays(2), new BigDecimal("1000.00"), createInvoiceItems(new UUID[]{thisBundleId})));
        invoices.add(createInvoice(now.minusDays(1), new BigDecimal("10000.00"), createInvoiceItems(new UUID[]{thatBundleId, thisBundleId})));

        final Clock clock = new ClockMock();
        final InvoiceUserApi invoiceApi = Mockito.mock(InvoiceUserApi.class);
        Mockito.when(invoiceApi.getUnpaidInvoicesByAccountId(Mockito.<UUID>any(), Mockito.<DateTime>any())).thenReturn(invoices);

        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(thisBundleId);
        Mockito.when(bundle.getAccountId()).thenReturn(UUID.randomUUID());

        final EntitlementUserApi entitlementApi = Mockito.mock(EntitlementUserApi.class);
        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(entitlementApi.getBaseSubscription(Mockito.<UUID>any())).thenReturn(subscription);

        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PriceList pricelist = new MockPriceList();
        Mockito.when(subscription.getCurrentPlan()).thenReturn(plan);
        Mockito.when(subscription.getCurrentPriceList()).thenReturn(pricelist);
        Mockito.when(subscription.getCurrentPhase()).thenReturn(plan.getFinalPhase());

        final BillingStateCalculatorBundle calc = new BillingStateCalculatorBundle(entitlementApi, invoiceApi, clock);

        final BillingStateBundle state = calc.calculateBillingState(bundle);

        Assert.assertEquals(state.getNumberOfUnpaidInvoices(), 4);
        Assert.assertEquals(state.getBalanceOfUnpaidInvoices().intValue(), 11100);
        Assert.assertEquals(state.getDateOfEarliestUnpaidInvoice().compareTo(now.minusDays(5)), 0);
        Assert.assertEquals(state.getResponseForLastFailedPayment(), PaymentResponse.INSUFFICIENT_FUNDS); //TODO needs more when implemented
        Assert.assertEquals(state.getTags().length, 0);//TODO needs more when implemented
        Assert.assertEquals(state.getBasePlanBillingPeriod(), plan.getBillingPeriod());
        Assert.assertEquals(state.getBasePlanPhaseType(), plan.getFinalPhase().getPhaseType());
        Assert.assertEquals(state.getBasePlanPriceList(), pricelist);
        Assert.assertEquals(state.getBasePlanProduct(), plan.getProduct());

    }

    @Test(groups = {"fast"}, enabled = true)
    public void testcalculateBillingStateForBundleNoOverdueInvoices() throws Exception {
        final UUID thisBundleId = new UUID(0L, 0L);

        now = new DateTime();
        final List<Invoice> invoices = new ArrayList<Invoice>(5);

        final Clock clock = new ClockMock();
        final InvoiceUserApi invoiceApi = Mockito.mock(InvoiceUserApi.class);
        Mockito.when(invoiceApi.getUnpaidInvoicesByAccountId(Mockito.<UUID>any(), Mockito.<DateTime>any())).thenReturn(invoices);

        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(thisBundleId);
        Mockito.when(bundle.getAccountId()).thenReturn(UUID.randomUUID());

        final EntitlementUserApi entitlementApi = Mockito.mock(EntitlementUserApi.class);
        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(entitlementApi.getBaseSubscription(Mockito.<UUID>any())).thenReturn(subscription);

        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PriceList pricelist = new MockPriceList();
        Mockito.when(subscription.getCurrentPlan()).thenReturn(plan);
        Mockito.when(subscription.getCurrentPriceList()).thenReturn(pricelist);
        Mockito.when(subscription.getCurrentPhase()).thenReturn(plan.getFinalPhase());

        final BillingStateCalculatorBundle calc = new BillingStateCalculatorBundle(entitlementApi, invoiceApi, clock);

        final BillingStateBundle state = calc.calculateBillingState(bundle);

        Assert.assertEquals(state.getNumberOfUnpaidInvoices(), 0);
        Assert.assertEquals(state.getBalanceOfUnpaidInvoices().intValue(), 0);
        Assert.assertEquals(state.getDateOfEarliestUnpaidInvoice(), null);
        Assert.assertEquals(state.getResponseForLastFailedPayment(), PaymentResponse.INSUFFICIENT_FUNDS); //TODO needs more when implemented
        Assert.assertEquals(state.getTags().length, 0);//TODO needs more when implemented
        Assert.assertEquals(state.getBasePlanBillingPeriod(), plan.getBillingPeriod());
        Assert.assertEquals(state.getBasePlanPhaseType(), plan.getFinalPhase().getPhaseType());
        Assert.assertEquals(state.getBasePlanPriceList(), pricelist);
        Assert.assertEquals(state.getBasePlanProduct(), plan.getProduct());

    }

    public void testCorrectBehaviorForNoOverdueConfig() {
        //TODO with no overdue config the system should be fine - take no action but see no NPEs
    }
}
