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
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.overdue.BillingState;
import com.ning.billing.catalog.api.overdue.BillingStateBundle;
import com.ning.billing.catalog.api.overdue.PaymentResponse;
import com.ning.billing.entitlement.api.overdue.EntitlementOverdueApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.tag.Tag;

public class BillingStateCalculatorBundle  extends BillingStateCalculator<SubscriptionBundle>{

    private EntitlementOverdueApi entitlementApi;

    @Inject 
    public BillingStateCalculatorBundle(EntitlementOverdueApi entitlementApi, InvoiceUserApi invoiceApi, Clock clock) {
        super(invoiceApi, clock);
        this.entitlementApi = entitlementApi;
    }
    
    @Override
    public BillingState<SubscriptionBundle> calculateBillingState(SubscriptionBundle bundle) {
        
        SortedSet<Invoice> unpaidInvoices = unpaidInvoicesFor(bundle.getId());
 
        Subscription basePlan = entitlementApi.getBaseSubscription(bundle.getId());
        
        UUID id = bundle.getId();
        int numberOfUnpaidInvoices = unpaidInvoices.size(); 
        BigDecimal unpaidInvoiceBalance = sumBalance(unpaidInvoices);
        DateTime dateOfEarliestUnpaidInvoice = earliest(unpaidInvoices);
        PaymentResponse responseForLastFailedPayment = PaymentResponse.INSUFFICIENT_FUNDS; //TODO MDW
        Tag[] tags = new Tag[]{}; //TODO MDW
        Product basePlanProduct = basePlan.getCurrentPlan().getProduct();
        BillingPeriod basePlanBillingPeriod = basePlan.getCurrentPlan().getBillingPeriod();
        PriceList basePlanPriceList = basePlan.getCurrentPriceList();
        PhaseType basePlanPhaseType = basePlan.getCurrentPhase().getPhaseType();
        

        return new BillingStateBundle( 
            id, 
            numberOfUnpaidInvoices, 
            unpaidInvoiceBalance,
            dateOfEarliestUnpaidInvoice,
            responseForLastFailedPayment,
            tags, 
            basePlanProduct,
            basePlanBillingPeriod, 
            basePlanPriceList, 
            basePlanPhaseType);
        
    }
}
