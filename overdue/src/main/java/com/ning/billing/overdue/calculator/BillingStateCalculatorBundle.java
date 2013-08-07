/*
 * Copyright 2010-2013 Ning, Inc.
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

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.subscription.api.user.SubscriptionUserApiException;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.overdue.config.api.BillingStateBundle;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.overdue.config.api.PaymentResponse;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.tag.Tag;

import com.google.inject.Inject;

public class BillingStateCalculatorBundle extends BillingStateCalculator<SubscriptionBundle> {

    private final SubscriptionInternalApi subscriptionApi;
    private final AccountInternalApi accountApi;

    @Inject
    public BillingStateCalculatorBundle(final SubscriptionInternalApi subscriptionApi, final InvoiceInternalApi invoiceApi,
                                        final AccountInternalApi accountApi, final Clock clock) {
        super(invoiceApi, clock);
        this.subscriptionApi = subscriptionApi;
        this.accountApi = accountApi;
    }


    @Override
    public BillingStateBundle calculateBillingState(final SubscriptionBundle bundle, final InternalTenantContext context) throws OverdueException {
        try {
            final Account account = accountApi.getAccountById(bundle.getAccountId(), context);
            final SortedSet<Invoice> unpaidInvoices = unpaidInvoicesForBundle(bundle.getId(), bundle.getAccountId(), account.getTimeZone(), context);

            final SubscriptionBase basePlan = getBasePlanIfExist(bundle.getId(), context);

            final UUID id = bundle.getId();
            final int numberOfUnpaidInvoices = unpaidInvoices.size();
            final BigDecimal unpaidInvoiceBalance = sumBalance(unpaidInvoices);
            LocalDate dateOfEarliestUnpaidInvoice = null;
            UUID idOfEarliestUnpaidInvoice = null;
            final Invoice invoice = earliest(unpaidInvoices);
            if (invoice != null) {
                dateOfEarliestUnpaidInvoice = invoice.getInvoiceDate();
                idOfEarliestUnpaidInvoice = invoice.getId();
            }
            final PaymentResponse responseForLastFailedPayment = PaymentResponse.INSUFFICIENT_FUNDS; //TODO MDW
            final Tag[] tags = new Tag[]{}; //TODO MDW

            final Product basePlanProduct;
            final BillingPeriod basePlanBillingPeriod;
            final PriceList basePlanPriceList;
            final PhaseType basePlanPhaseType;
            //
            // basePlan is null for standalone subscriptions
            // basePlan.getCurrentPlan() is null if basePlan has been cancelled
            if (basePlan == null || basePlan.getCurrentPlan() == null) {
                basePlanProduct = null;
                basePlanBillingPeriod = null;
                basePlanPriceList = null;
                basePlanPhaseType = null;
            } else {
                basePlanProduct = basePlan.getCurrentPlan().getProduct();
                basePlanBillingPeriod = basePlan.getCurrentPlan().getBillingPeriod();
                basePlanPriceList = basePlan.getCurrentPriceList();
                basePlanPhaseType = basePlan.getCurrentPhase().getPhaseType();
            }

            return new BillingStateBundle(id,
                                          numberOfUnpaidInvoices,
                                          unpaidInvoiceBalance,
                                          dateOfEarliestUnpaidInvoice,
                                          account.getTimeZone(),
                                          idOfEarliestUnpaidInvoice,
                                          responseForLastFailedPayment,
                                          tags,
                                          basePlanProduct,
                                          basePlanBillingPeriod,
                                          basePlanPriceList,
                                          basePlanPhaseType);
        } catch (SubscriptionUserApiException e) {
            throw new OverdueException(e);
        } catch (AccountApiException e) {
            throw new OverdueException(e);
        }
    }

    public SortedSet<Invoice> unpaidInvoicesForBundle(final UUID bundleId, final UUID accountId, final DateTimeZone accountTimeZone, final InternalTenantContext context) {
        final SortedSet<Invoice> unpaidInvoices = unpaidInvoicesForAccount(accountId, accountTimeZone, context);
        final SortedSet<Invoice> result = new TreeSet<Invoice>(new InvoiceDateComparator());
        result.addAll(unpaidInvoices);
        for (final Invoice invoice : unpaidInvoices) {
            if (!invoiceHasAnItemFromBundle(invoice, bundleId)) {
                result.remove(invoice);
            }
        }
        return result;
    }

    private SubscriptionBase getBasePlanIfExist(UUID bundleId, final InternalTenantContext context) throws SubscriptionUserApiException {
        try {
            final SubscriptionBase basePlan = subscriptionApi.getBaseSubscription(bundleId, context);
            return basePlan;
        } catch (SubscriptionUserApiException e) {
            if (e.getCode() == ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION.getCode()) {
                // No base plan probably a STANDALONE subscription in a bundle
                return null;
            }
            throw e;
        }
    }

    private boolean invoiceHasAnItemFromBundle(final Invoice invoice, final UUID bundleId) {
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (item.getBundleId() != null && item.getBundleId().equals(bundleId)) {
                return true;
            }
        }
        return false;
    }
}
