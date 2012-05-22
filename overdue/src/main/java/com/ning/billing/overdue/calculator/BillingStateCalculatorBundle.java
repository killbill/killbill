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
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.overdue.config.api.BillingStateBundle;
import com.ning.billing.overdue.config.api.OverdueError;
import com.ning.billing.overdue.config.api.PaymentResponse;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.tag.Tag;

public class BillingStateCalculatorBundle  extends BillingStateCalculator<SubscriptionBundle>{

    private EntitlementUserApi entitlementApi;

    @Inject 
    public BillingStateCalculatorBundle(EntitlementUserApi entitlementApi, InvoiceUserApi invoiceApi, Clock clock) {
        super(invoiceApi, clock);
        this.entitlementApi = entitlementApi;
    }

    @Override
    public BillingStateBundle calculateBillingState(SubscriptionBundle bundle) throws OverdueError {
        try {
            SortedSet<Invoice> unpaidInvoices = unpaidInvoicesForBundle(bundle.getId(), bundle.getAccountId());

            Subscription basePlan = entitlementApi.getBaseSubscription(bundle.getId());

            UUID id = bundle.getId();
            int numberOfUnpaidInvoices = unpaidInvoices.size(); 
            BigDecimal unpaidInvoiceBalance = sumBalance(unpaidInvoices);
            DateTime dateOfEarliestUnpaidInvoice = null;
            UUID idOfEarliestUnpaidInvoice = null;
            Invoice invoice = earliest(unpaidInvoices);
            if(invoice != null) {
                dateOfEarliestUnpaidInvoice = invoice.getInvoiceDate();
                idOfEarliestUnpaidInvoice = invoice.getId();
            }
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
                    idOfEarliestUnpaidInvoice,
                    responseForLastFailedPayment,
                    tags, 
                    basePlanProduct,
                    basePlanBillingPeriod, 
                    basePlanPriceList, 
                    basePlanPhaseType);
        } catch (EntitlementUserApiException e) {
            throw new OverdueError(e);
        }

    }

    public SortedSet<Invoice> unpaidInvoicesForBundle(UUID bundleId, UUID accountId) {
        SortedSet<Invoice> unpaidInvoices = unpaidInvoicesForAccount(accountId);
        SortedSet<Invoice> result = new TreeSet<Invoice>(new InvoiceDateComparator());
        result.addAll(unpaidInvoices);
        for(Invoice invoice : unpaidInvoices) {
            if(!invoiceHasAnItemFromBundle(invoice, bundleId)) {
                result.remove(invoice);
            }
        }
        return result;
    }

    private boolean invoiceHasAnItemFromBundle(Invoice invoice, UUID bundleId) {
        for(InvoiceItem item : invoice.getInvoiceItems()) {
            if(item.getBundleId().equals(bundleId)) {
                return true;
            }
        }
        return false;
    }


}
