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

package com.ning.billing.invoice.api.svcs;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.ErrorCode;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceInternalApi;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentType;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.InvoiceModelDao;
import com.ning.billing.invoice.dao.InvoicePaymentModelDao;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.util.timezone.DateAndTimeZoneContext;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class DefaultInvoiceInternalApi implements InvoiceInternalApi {

    private final InvoiceDao dao;
    private final NextBillingDatePoster nextBillingDatePoster;
    private final SubscriptionBaseInternalApi subscriptionBaseApi;
    private final Clock clock;

    @Inject
    public DefaultInvoiceInternalApi(final InvoiceDao dao, final SubscriptionBaseInternalApi subscriptionBaseApi,
                                     final Clock clock,
                                     final NextBillingDatePoster nextBillingDatePoster) {
        this.dao = dao;
        this.clock = clock;
        this.subscriptionBaseApi = subscriptionBaseApi;
        this.nextBillingDatePoster = nextBillingDatePoster;
    }

    @Override
    public Invoice getInvoiceById(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return new DefaultInvoice(dao.getById(invoiceId, context));
    }

    @Override
    public Collection<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate upToDate, final InternalTenantContext context) {
        return Collections2.transform(dao.getUnpaidInvoicesByAccountId(accountId, upToDate, context), new Function<InvoiceModelDao, Invoice>() {
            @Override
            public Invoice apply(final InvoiceModelDao input) {
                return new DefaultInvoice(input);
            }
        });
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {
        return dao.getAccountBalance(accountId, context);
    }

    @Override
    public void notifyOfPayment(final UUID invoiceId, final BigDecimal amount, final Currency currency, final UUID paymentId, final DateTime paymentDate, final InternalCallContext context) throws InvoiceApiException {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, paymentDate, amount, currency);
        notifyOfPayment(invoicePayment, context);
    }

    @Override
    public void notifyOfPayment(final InvoicePayment invoicePayment, final InternalCallContext context) throws InvoiceApiException {
        dao.notifyOfPayment(new InvoicePaymentModelDao(invoicePayment), context);
    }

    @Override
    public InvoicePayment getInvoicePaymentForAttempt(final UUID paymentId, final InternalTenantContext context) throws InvoiceApiException {
        final Collection<InvoicePayment> invoicePayments = Collections2.transform(dao.getInvoicePayments(paymentId, context), new Function<InvoicePaymentModelDao, InvoicePayment>() {
            @Override
            public InvoicePayment apply(final InvoicePaymentModelDao input) {
                return new DefaultInvoicePayment(input);
            }
        });
        if (invoicePayments.size() == 0) {
            return null;
        }
        return Collections2.filter(invoicePayments, new Predicate<InvoicePayment>() {
            @Override
            public boolean apply(final InvoicePayment input) {
                return input.getType() == InvoicePaymentType.ATTEMPT;
            }
        }).iterator().next();
    }

    @Override
    public Invoice getInvoiceForPaymentId(final UUID paymentId, final InternalTenantContext context) throws InvoiceApiException {
        final UUID invoiceIdStr = dao.getInvoiceIdByPaymentId(paymentId, context);
        return invoiceIdStr == null ? null : new DefaultInvoice(dao.getById(invoiceIdStr, context));
    }

    @Override
    public InvoicePayment createRefund(final UUID paymentId, final BigDecimal amount, final boolean isInvoiceAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final UUID paymentCookieId, final InternalCallContext context) throws InvoiceApiException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL);
        }
        return new DefaultInvoicePayment(dao.createRefund(paymentId, amount, isInvoiceAdjusted, invoiceItemIdsWithAmounts, paymentCookieId, context));
    }

    @Override
    public void consumeExistingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) throws InvoiceApiException {
        dao.consumeExstingCBAOnAccountWithUnpaidInvoices(accountId, context);
    }

    @Override
    public void scheduleInvoiceForAccount(final UUID accountId, final DateTimeZone accountTimeZone, final InternalCallContext context) throws InvoiceApiException {
        final Map<UUID, List<SubscriptionBase>> subscriptions = subscriptionBaseApi.getSubscriptionsForAccount(context);
        SubscriptionBase targetSubscription = null;
        for (UUID key : subscriptions.keySet()) {
            for (SubscriptionBase cur : subscriptions.get(key)) {
                if (cur.getCategory() == ProductCategory.ADD_ON) {
                    continue;
                }
                if (cur.getState() != EntitlementState.ACTIVE) {
                    continue;
                }
                if (cur.getCurrentPhase() != null && cur.getCurrentPhase().getBillingPeriod() != BillingPeriod.NO_BILLING_PERIOD) {
                    targetSubscription = cur;
                    break;
                }
            }
        }
        if (targetSubscription != null) {
            final DateAndTimeZoneContext timeZoneContext = new DateAndTimeZoneContext(targetSubscription.getStartDate(), accountTimeZone, clock);
            nextBillingDatePoster.insertNextBillingNotification(accountId, targetSubscription.getId(), timeZoneContext.computeUTCDateTimeFromNow(), context.getUserToken());
        }
    }
}
