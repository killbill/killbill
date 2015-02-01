/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.invoice.api.svcs;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceApiHelper;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.WithAccountLock;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoicePaymentModelDao;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DefaultInvoiceInternalApi implements InvoiceInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceInternalApi.class);

    private final InvoiceDao dao;
    private final InvoiceApiHelper invoiceApiHelper;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultInvoiceInternalApi(final InvoiceDao dao,
                                     final InvoiceApiHelper invoiceApiHelper,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.dao = dao;
        this.invoiceApiHelper = invoiceApiHelper;
        this.internalCallContextFactory = internalCallContextFactory;
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
    public void notifyOfPayment(final UUID invoiceId, final BigDecimal amount, final Currency currency, final Currency processedCurrency, final UUID paymentId, final DateTime paymentDate, final InternalCallContext context) throws InvoiceApiException {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, paymentDate, amount, currency, processedCurrency);
        notifyOfPayment(invoicePayment, context);
    }

    @Override
    public void notifyOfPayment(final InvoicePayment invoicePayment, final InternalCallContext context) throws InvoiceApiException {
        dao.notifyOfPayment(new InvoicePaymentModelDao(invoicePayment), context);
    }

    @Override
    public InvoicePayment getInvoicePaymentForAttempt(final UUID paymentId, final InternalTenantContext context) throws InvoiceApiException {
        return getInvoicePayment(paymentId, InvoicePaymentType.ATTEMPT, context);
    }

    @Override
    public InvoicePayment getInvoicePaymentForRefund(final UUID paymentId, final InternalTenantContext context) throws InvoiceApiException {
        return getInvoicePayment(paymentId, InvoicePaymentType.REFUND, context);
    }

    @Override
    public InvoicePayment getInvoicePaymentForChargeback(final UUID paymentId, final InternalTenantContext context) throws InvoiceApiException {
        return getInvoicePayment(paymentId, InvoicePaymentType.CHARGED_BACK, context);
    }

    @Override
    public Invoice getInvoiceForPaymentId(final UUID paymentId, final InternalTenantContext context) throws InvoiceApiException {
        final UUID invoiceIdStr = dao.getInvoiceIdByPaymentId(paymentId, context);
        return invoiceIdStr == null ? null : new DefaultInvoice(dao.getById(invoiceIdStr, context));
    }

    @Override
    public InvoicePayment createRefund(final UUID paymentId, final BigDecimal amount, final boolean isInvoiceAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final String transactionExternalKey, final InternalCallContext context) throws InvoiceApiException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL, paymentId, amount);
        }

        final InvoicePaymentModelDao refund = dao.createRefund(paymentId, amount, isInvoiceAdjusted, invoiceItemIdsWithAmounts, transactionExternalKey, context);

        // See https://github.com/killbill/killbill/issues/265
        final CallContext callContext = internalCallContextFactory.createCallContext(context);
        final Invoice invoice = getInvoiceById(refund.getInvoiceId(), context);
        final UUID accountId = invoice.getAccountId();
        final WithAccountLock withAccountLock = new WithAccountLock() {
            @Override
            public Iterable<Invoice> prepareInvoices() throws InvoiceApiException {
                return ImmutableList.<Invoice>of(invoice);
            }
        };
        final List<InvoiceItem> createdInvoiceItems = invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, withAccountLock, callContext);

        return new DefaultInvoicePayment(refund);
    }

    @Override
    public InvoicePayment createChargeback(final UUID paymentId, final BigDecimal amount, final Currency currency, final InternalCallContext context) throws InvoiceApiException {
        return new DefaultInvoicePayment(dao.postChargeback(paymentId, amount, currency, context));
    }

    @Override
    public void consumeExistingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) throws InvoiceApiException {
        dao.consumeExstingCBAOnAccountWithUnpaidInvoices(accountId, context);
    }

    private InvoicePayment getInvoicePayment(final UUID paymentId, final InvoicePaymentType type, final InternalTenantContext context) throws InvoiceApiException {
        final Collection<InvoicePayment> invoicePayments = Collections2.transform(dao.getInvoicePayments(paymentId, context), new Function<InvoicePaymentModelDao, InvoicePayment>() {
            @Override
            public InvoicePayment apply(final InvoicePaymentModelDao input) {
                return new DefaultInvoicePayment(input);
            }
        });
        if (invoicePayments.isEmpty()) {
            return null;
        }
        return Iterables.tryFind(invoicePayments, new Predicate<InvoicePayment>() {
            @Override
            public boolean apply(final InvoicePayment input) {
                return input.getType() == type;
            }
        }).orNull();
    }
}
