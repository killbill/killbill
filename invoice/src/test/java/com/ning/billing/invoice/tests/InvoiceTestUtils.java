/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.invoice.tests;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.dao.InvoiceItemModelDao;
import com.ning.billing.invoice.dao.InvoiceItemSqlDao;
import com.ning.billing.invoice.dao.InvoiceModelDao;
import com.ning.billing.invoice.dao.InvoiceSqlDao;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;

import com.google.common.collect.ImmutableList;

public class InvoiceTestUtils {

    private InvoiceTestUtils() {}

    public static Invoice createAndPersistInvoice(final InvoiceSqlDao invoiceSqlDao,
                                                  final InvoiceItemSqlDao invoiceItemSqlDao,
                                                  final Clock clock,
                                                  final BigDecimal amount,
                                                  final Currency currency,
                                                  final CallContext callContext,
                                                  final InternalCallContextFactory internalCallContextFactory) {
        return createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, ImmutableList.<BigDecimal>of(amount),
                                       currency, callContext, internalCallContextFactory);
    }

    public static Invoice createAndPersistInvoice(final InvoiceSqlDao invoiceSqlDao,
                                                  final InvoiceItemSqlDao invoiceItemSqlDao,
                                                  final Clock clock,
                                                  final List<BigDecimal> amounts,
                                                  final Currency currency,
                                                  final CallContext callContext,
                                                  final InternalCallContextFactory internalCallContextFactory) {
        final Invoice invoice = Mockito.mock(Invoice.class);
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();

        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getTargetDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getCurrency()).thenReturn(currency);
        Mockito.when(invoice.isMigrationInvoice()).thenReturn(false);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        for (final BigDecimal amount : amounts) {
            final InvoiceItem invoiceItem = createInvoiceItem(clock, invoiceId, accountId, amount, currency);
            invoiceItemSqlDao.create(new InvoiceItemModelDao(invoiceItem), internalCallContextFactory.createInternalCallContext(accountId, callContext));
            invoiceItems.add(invoiceItem);
        }
        Mockito.when(invoice.getInvoiceItems()).thenReturn(invoiceItems);

        try {
            invoiceSqlDao.create(new InvoiceModelDao(invoice), internalCallContextFactory.createInternalCallContext(accountId, callContext));
        } catch (EntityPersistenceException e) {
            Assert.fail(e.getMessage());
        }

        return invoice;
    }

    public static InvoiceItem createInvoiceItem(final Clock clock, final UUID invoiceId, final UUID accountId, final BigDecimal amount, final Currency currency) {
        return new FixedPriceInvoiceItem(invoiceId, accountId, UUID.randomUUID(), UUID.randomUUID(),
                                         "charge back test", "charge back phase", clock.getUTCToday(), amount, currency);
    }

    public static InvoicePayment createAndPersistPayment(final InvoiceInternalApi invoicePaymentApi,
                                                         final Clock clock,
                                                         final UUID invoiceId,
                                                         final BigDecimal amount,
                                                         final Currency currency,
                                                         final InternalCallContext callContext) throws InvoiceApiException {
        final InvoicePayment payment = Mockito.mock(InvoicePayment.class);
        Mockito.when(payment.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getType()).thenReturn(InvoicePaymentType.ATTEMPT);
        Mockito.when(payment.getInvoiceId()).thenReturn(invoiceId);
        Mockito.when(payment.getPaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getPaymentCookieId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getPaymentDate()).thenReturn(clock.getUTCNow());
        Mockito.when(payment.getAmount()).thenReturn(amount);
        Mockito.when(payment.getCurrency()).thenReturn(currency);

        invoicePaymentApi.notifyOfPayment(payment, callContext);

        return payment;
    }
}
