/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceApiHelper;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentStatus;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.WithAccountLock;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoicePaymentModelDao;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public InvoiceStatus getInvoiceStatus(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return dao.getInvoiceStatus(invoiceId, context);
    }

    @Override
    public Invoice getInvoiceById(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return getInvoiceByIdInternal(invoiceId, context);
    }

    private DefaultInvoice getInvoiceByIdInternal(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return new DefaultInvoice(dao.getById(invoiceId, context));
    }

    @Override
    public Collection<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate upToDate, final InternalTenantContext context) {
        final List<InvoiceModelDao> unpaidInvoicesByAccountId = dao.getUnpaidInvoicesByAccountId(accountId, null, upToDate, context);
        final Collection<Invoice> invoices = new LinkedList<>();
        for (final InvoiceModelDao invoiceModelDao : unpaidInvoicesByAccountId) {
            invoices.add(new DefaultInvoice(invoiceModelDao));
        }
        return invoices;
    }

    @Override
    public void recordPaymentAttemptInit(final UUID invoiceId, final BigDecimal amount, final Currency currency, final Currency processedCurrency, final UUID paymentId, final UUID paymentAttemptId, final String transactionExternalKey, final DateTime paymentDate, final InternalCallContext context) throws InvoiceApiException {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, paymentDate, amount, currency, processedCurrency, transactionExternalKey, InvoicePaymentStatus.INIT);
        dao.notifyOfPaymentInit(new InvoicePaymentModelDao(invoicePayment), paymentAttemptId, context);
    }

    @Override
    public void recordPaymentAttemptCompletion(final UUID invoiceId, final BigDecimal amount, final Currency currency, final Currency processedCurrency, final UUID paymentId, final UUID paymentAttemptId, final String transactionExternalKey, final DateTime paymentDate, final InvoicePaymentStatus status, final InternalCallContext context) throws InvoiceApiException {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, paymentDate, amount, currency, processedCurrency, transactionExternalKey, status);
        dao.notifyOfPaymentCompletion(new InvoicePaymentModelDao(invoicePayment), paymentAttemptId, context);
    }

    @Override
    public InvoicePayment getInvoicePaymentForAttempt(final UUID paymentId, final InternalTenantContext context) throws InvoiceApiException {
        return getInvoicePayment(paymentId, InvoicePaymentType.ATTEMPT, context);
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
    public InvoicePayment recordRefund(final UUID paymentId, final UUID paymentAttemptId, final BigDecimal amount, final boolean isInvoiceAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final String transactionExternalKey, final InvoicePaymentStatus status, final InternalCallContext context) throws InvoiceApiException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL, paymentId, amount);
        }

        final InvoicePaymentModelDao refund = dao.createRefund(paymentId, paymentAttemptId, amount, isInvoiceAdjusted, invoiceItemIdsWithAmounts, transactionExternalKey, status, context);
        // See https://github.com/killbill/killbill/issues/265
        final CallContext callContext = internalCallContextFactory.createCallContext(context);
        final DefaultInvoice invoice = getInvoiceByIdInternal(refund.getInvoiceId(), context);
        final UUID accountId = invoice.getAccountId();
        final WithAccountLock withAccountLock = new WithAccountLock() {
            @Override
            public Iterable<DefaultInvoice> prepareInvoices() throws InvoiceApiException {
                return List.of(invoice);
            }
        };

        final LinkedList<PluginProperty> pluginProperties = new LinkedList<PluginProperty>();
        invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, false, withAccountLock, pluginProperties, true, callContext);
        return new DefaultInvoicePayment(refund);
    }

    @Override
    public InvoicePayment recordChargeback(final UUID paymentId, final UUID paymentAttemptId, final String chargebackTransactionExternalKey, final BigDecimal amount, final Currency currency, final InternalCallContext context) throws InvoiceApiException {
        return new DefaultInvoicePayment(dao.postChargeback(paymentId, paymentAttemptId, chargebackTransactionExternalKey, amount, currency, context));
    }

    @Override
    public InvoicePayment recordChargebackReversal(final UUID paymentId, final UUID paymentAttemptId, final String chargebackTransactionExternalKey, final InternalCallContext context) throws InvoiceApiException {
        return new DefaultInvoicePayment(dao.postChargebackReversal(paymentId, paymentAttemptId, chargebackTransactionExternalKey, context));
    }


    @Override
    public Map<UUID, BigDecimal> validateInvoiceItemAdjustments(final UUID paymentId, final Map<UUID, BigDecimal> idWithAmount, final InternalTenantContext context) throws InvoiceApiException {
        // We want to validate that only refund with invoice *item* adjustments are allowed (as opposed to refund with invoice adjustment)
        if (idWithAmount.isEmpty()) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEMS_ADJUSTMENT_MISSING);
        }
        final InvoicePayment invoicePayment = getInvoicePayment(paymentId, InvoicePaymentType.ATTEMPT, context);
        return dao.computeItemAdjustments(invoicePayment.getInvoiceId().toString(), idWithAmount, context);
    }

    @VisibleForTesting
    InvoicePayment getInvoicePayment(final UUID paymentId, final InvoicePaymentType type, final InternalTenantContext context) throws InvoiceApiException {
        final List<InvoicePaymentModelDao> invoicePayments = dao.getInvoicePaymentsByPaymentId(paymentId, context);
        return invoicePayments.stream()
                .filter(input -> input.getType() == type && input.getStatus() == InvoicePaymentStatus.SUCCESS)
                .findFirst()
                .map(DefaultInvoicePayment::new)
                .orElse(null);
    }

    @Override
    public void commitInvoice(final UUID invoiceId, final InternalCallContext context) throws InvoiceApiException {
        dao.changeInvoiceStatus(invoiceId, InvoiceStatus.COMMITTED, context);
    }

    @Override
    public InvoicePayment getInvoicePayment(final UUID invoicePaymentId, final TenantContext context) {
        final InvoicePaymentModelDao result =  dao.getInvoicePayment(invoicePaymentId, internalCallContextFactory.createInternalTenantContext(invoicePaymentId, ObjectType.INVOICE_PAYMENT, context));
        return result != null ? new DefaultInvoicePayment(result) : null;
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final TenantContext context) {
        final InternalTenantContext ctx = internalCallContextFactory.createInternalTenantContext(paymentId, ObjectType.PAYMENT, context);
        final List<InvoicePaymentModelDao> invoicePayments = dao.getInvoicePaymentsByPaymentId(paymentId, ctx);
        return invoicePayments.stream()
                .map(DefaultInvoicePayment::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<InvoicePayment> getInvoicePaymentsByAccount(final UUID accountId, final TenantContext context) {
        final InternalTenantContext ctx = internalCallContextFactory.createInternalTenantContext(accountId, ObjectType.ACCOUNT, context);
        final List<InvoicePaymentModelDao> invoicePayments = dao.getInvoicePaymentsByAccount(ctx);
        return invoicePayments.stream()
                .map(DefaultInvoicePayment::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<InvoicePayment> getInvoicePaymentsByInvoice(final UUID invoiceId, final InternalTenantContext context) {
        final List<InvoicePaymentModelDao> invoicePayments = dao.getInvoicePaymentsByInvoice(invoiceId, context);
        return invoicePayments.stream()
                .map(DefaultInvoicePayment::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public InvoicePayment getInvoicePaymentByCookieId(final String cookieId, final TenantContext context) {
        final InvoicePaymentModelDao invoicePaymentModelDao = dao.getInvoicePaymentByCookieId(cookieId, internalCallContextFactory.createInternalTenantContext(context.getAccountId(), ObjectType.ACCOUNT, context));
        return invoicePaymentModelDao == null ? null : new DefaultInvoicePayment(invoicePaymentModelDao);
    }
}
