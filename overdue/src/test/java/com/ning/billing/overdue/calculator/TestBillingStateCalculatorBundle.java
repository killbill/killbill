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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

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
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.overdue.config.api.BillingStateBundle;
import com.ning.billing.overdue.config.api.PaymentResponse;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestBillingStateCalculatorBundle extends TestBillingStateCalculator {
    
    
    private List<InvoiceItem> createInvoiceItems(UUID[] bundleIds) {
        List<InvoiceItem> result = new ArrayList<InvoiceItem> ();
        for (UUID id : bundleIds) {
           InvoiceItem ii = BrainDeadProxyFactory.createBrainDeadProxyFor(InvoiceItem.class);
           ((ZombieControl)ii).addResult("getBundleId", id);
           result.add(ii);
        }
        return result;
    }
    
    @Test(groups = {"fast"}, enabled=true)
    public void testUnpaidInvoiceForBundle() {
       UUID thisBundleId = new UUID(0L,0L);
       UUID thatBundleId = new UUID(0L,1L);
       
       now = new DateTime();
       List<Invoice> invoices = new ArrayList<Invoice>(5);
       invoices.add(createInvoice(now, BigDecimal.ZERO, createInvoiceItems(new UUID[]{thisBundleId,thatBundleId})));
       invoices.add(createInvoice(now, BigDecimal.TEN, createInvoiceItems(new UUID[]{thatBundleId})));
       invoices.add(createInvoice(now, new BigDecimal("100.00"), createInvoiceItems(new UUID[]{thatBundleId,thisBundleId,thatBundleId})));
       invoices.add(createInvoice(now, new BigDecimal("1000.00"), createInvoiceItems(new UUID[]{thisBundleId})));
       invoices.add(createInvoice(now, new BigDecimal("10000.00"), createInvoiceItems(new UUID[]{thatBundleId, thisBundleId})));
       
       
       Clock clock = new ClockMock();
       InvoiceUserApi invoiceApi = BrainDeadProxyFactory.createBrainDeadProxyFor(InvoiceUserApi.class);
       EntitlementUserApi entitlementApi = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementUserApi.class);
       ((ZombieControl)invoiceApi).addResult("getUnpaidInvoicesByAccountId", invoices);
       
       
       BillingStateCalculatorBundle calc = new BillingStateCalculatorBundle(entitlementApi, invoiceApi, clock);
       SortedSet<Invoice> resultinvoices = calc.unpaidInvoicesForBundle(thisBundleId, new UUID(0L,0L));
       
       Assert.assertEquals(resultinvoices.size(), 4);
       Assert.assertEquals(BigDecimal.ZERO.compareTo(resultinvoices.first().getBalance()), 0);
       Assert.assertEquals(new BigDecimal("10000.0").compareTo(resultinvoices.last().getBalance()), 0);
       
    }
    
    @Test(groups = {"fast"}, enabled=true)
    public void testcalculateBillingStateForBundle() throws Exception {
        
       UUID thisBundleId = new UUID(0L,0L);
       UUID thatBundleId = new UUID(0L,1L);
       
       now = new DateTime();
       List<Invoice> invoices = new ArrayList<Invoice>(5);
       invoices.add(createInvoice(now.minusDays(5), BigDecimal.ZERO, createInvoiceItems(new UUID[]{thisBundleId,thatBundleId})));
       invoices.add(createInvoice(now.minusDays(4), BigDecimal.TEN, createInvoiceItems(new UUID[]{thatBundleId})));
       invoices.add(createInvoice(now.minusDays(3), new BigDecimal("100.00"), createInvoiceItems(new UUID[]{thatBundleId,thisBundleId,thatBundleId})));
       invoices.add(createInvoice(now.minusDays(2), new BigDecimal("1000.00"), createInvoiceItems(new UUID[]{thisBundleId})));
       invoices.add(createInvoice(now.minusDays(1), new BigDecimal("10000.00"), createInvoiceItems(new UUID[]{thatBundleId, thisBundleId})));
       
       
       Clock clock = new ClockMock();
       InvoiceUserApi invoiceApi = BrainDeadProxyFactory.createBrainDeadProxyFor(InvoiceUserApi.class);
       ((ZombieControl)invoiceApi).addResult("getUnpaidInvoicesByAccountId", invoices);
       
       SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
       ((ZombieControl)bundle).addResult("getId", thisBundleId);
       ((ZombieControl)bundle).addResult("getAccountId", UUID.randomUUID());
       
       EntitlementUserApi entitlementApi = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementUserApi.class);
       Subscription subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
       ((ZombieControl)entitlementApi).addResult("getBaseSubscription",subscription);
       
       Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
       PriceList pricelist = new MockPriceList();
       ((ZombieControl)subscription).addResult("getCurrentPlan", plan);
       ((ZombieControl)subscription).addResult("getCurrentPriceList", pricelist);
       ((ZombieControl)subscription).addResult("getCurrentPhase", plan.getFinalPhase());
      
       BillingStateCalculatorBundle calc = new BillingStateCalculatorBundle(entitlementApi, invoiceApi, clock);
            
       BillingStateBundle state = calc.calculateBillingState(bundle); 
       
       Assert.assertEquals(state.getNumberOfUnpaidInvoices(),4);
       Assert.assertEquals(state.getBalanceOfUnpaidInvoices().intValue(), 11100);
       Assert.assertEquals(state.getDateOfEarliestUnpaidInvoice().compareTo(now.minusDays(5)), 0);
       Assert.assertEquals(state.getResponseForLastFailedPayment(),PaymentResponse.INSUFFICIENT_FUNDS); //TODO needs more when implemented
       Assert.assertEquals(state.getTags().length,0);//TODO needs more when implemented
       Assert.assertEquals(state.getBasePlanBillingPeriod(), plan.getBillingPeriod());
       Assert.assertEquals(state.getBasePlanPhaseType(), plan.getFinalPhase().getPhaseType());
       Assert.assertEquals(state.getBasePlanPriceList(), pricelist);
       Assert.assertEquals(state.getBasePlanProduct(), plan.getProduct());
       
    }
    
    
    
    
    
    
    
    
}
