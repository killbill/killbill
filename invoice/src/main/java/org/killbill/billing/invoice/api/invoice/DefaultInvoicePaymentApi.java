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

package org.killbill.billing.invoice.api.invoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentApi;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoicePaymentModelDao;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultInvoicePaymentApi implements InvoicePaymentApi {

    private final InvoiceDao dao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultInvoicePaymentApi(final InvoiceDao dao, final InternalCallContextFactory internalCallContextFactory) {
        this.dao = dao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public InvoicePayment createPurchaseForInvoice(final Account account,
                                                   final UUID invoiceId,
                                                   final UUID paymentMethodId,
                                                   final UUID paymentId,
                                                   final BigDecimal amount,
                                                   final Currency currency,
                                                   final DateTime effectiveDate,
                                                   final String paymentExternalKey,
                                                   final String paymentTransactionExternalKey,
                                                   final Iterable<PluginProperty> properties,
                                                   final PaymentOptions paymentOptions,
                                                   final CallContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public InvoicePayment createRefundForInvoice(final boolean isAdjusted,
                                                 final Map<UUID, BigDecimal> adjustments,
                                                 final Account account,
                                                 final UUID paymentId,
                                                 final BigDecimal amount,
                                                 final Currency currency,
                                                 final DateTime effectiveDate,
                                                 final String paymentTransactionExternalKey,
                                                 final Iterable<PluginProperty> properties,
                                                 final PaymentOptions paymentOptions,
                                                 final CallContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public InvoicePayment createCreditForInvoice(final boolean isAdjusted,
                                                 final Map<UUID, BigDecimal> adjustments,
                                                 final Account account,
                                                 final UUID originalPaymentId,
                                                 final UUID paymentMethodId,
                                                 final UUID paymentId,
                                                 final BigDecimal amount,
                                                 final Currency currency,
                                                 final DateTime effectiveDate,
                                                 final String paymentExternalKey,
                                                 final String paymentTransactionExternalKey,
                                                 final Iterable<PluginProperty> properties,
                                                 final PaymentOptions paymentOptions,
                                                 final CallContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final TenantContext context) {
        return ImmutableList.<InvoicePayment>copyOf(Collections2.transform(dao.getInvoicePaymentsByPaymentId(paymentId, internalCallContextFactory.createInternalTenantContext(paymentId, ObjectType.PAYMENT, context)),
                                                                           new Function<InvoicePaymentModelDao, InvoicePayment>() {
                                                                               @Override
                                                                               public InvoicePayment apply(final InvoicePaymentModelDao input) {
                                                                                   return new DefaultInvoicePayment(input);
                                                                               }
                                                                           }
                                                                          ));
    }

    @Override
    public List<InvoicePayment> getInvoicePaymentsByAccount(final UUID accountId, final TenantContext context) {
        return ImmutableList.<InvoicePayment>copyOf(Collections2.transform(dao.getInvoicePaymentsByAccount(internalCallContextFactory.createInternalTenantContext(accountId, ObjectType.ACCOUNT, context)),
                                                                           new Function<InvoicePaymentModelDao, InvoicePayment>() {
                                                                               @Override
                                                                               public InvoicePayment apply(final InvoicePaymentModelDao input) {
                                                                                   return new DefaultInvoicePayment(input);
                                                                               }
                                                                           }
                                                                          ));
    }
}
