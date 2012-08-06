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

package com.ning.billing.analytics;

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.analytics.model.BusinessInvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.util.clock.Clock;

public class BusinessInvoicePaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(BusinessInvoicePaymentRecorder.class);

    private final BusinessInvoicePaymentSqlDao invoicePaymentSqlDao;
    private final AccountUserApi accountApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final PaymentApi paymentApi;
    private final Clock clock;
    private final BusinessInvoiceRecorder invoiceRecorder;
    private final BusinessAccountRecorder accountRecorder;

    @Inject
    public BusinessInvoicePaymentRecorder(final BusinessInvoicePaymentSqlDao invoicePaymentSqlDao, final AccountUserApi accountApi,
                                          final InvoicePaymentApi invoicePaymentApi, final PaymentApi paymentApi, final Clock clock,
                                          final BusinessInvoiceRecorder invoiceRecorder, final BusinessAccountRecorder accountRecorder) {
        this.invoicePaymentSqlDao = invoicePaymentSqlDao;
        this.accountApi = accountApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.paymentApi = paymentApi;
        this.clock = clock;
        this.invoiceRecorder = invoiceRecorder;
        this.accountRecorder = accountRecorder;
    }

    public void invoicePaymentPosted(final UUID accountId, final UUID paymentId, @Nullable final String extFirstPaymentRefId, @Nullable final String extSecondPaymentRefId, final String message) {
        final Account account;
        try {
            account = accountApi.getAccountById(accountId);
        } catch (AccountApiException e) {
            log.warn("Ignoring payment {}: account {} does not exist", paymentId, accountId);
            return;
        }

        final Payment payment;
        try {
            payment = paymentApi.getPayment(paymentId);
        } catch (PaymentApiException e) {
            log.warn("Ignoring payment {}: payment does not exist", paymentId);
            return;
        }

        final InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePayment(paymentId);

        final PaymentMethod paymentMethod;
        try {
            paymentMethod = paymentApi.getPaymentMethod(account, payment.getPaymentMethodId(), true);
        } catch (PaymentApiException e) {
            log.warn("Ignoring payment {}: payment method {} does not exist", paymentId, payment.getPaymentMethodId());
            return;
        }

        createPayment(account, invoicePayment, payment, paymentMethod, extFirstPaymentRefId, extSecondPaymentRefId, message);
    }

    private void createPayment(final Account account, @Nullable final InvoicePayment invoicePayment, final Payment payment,
                               final PaymentMethod paymentMethod, final String extFirstPaymentRefId, final String extSecondPaymentRefId, final String message) {
        final PaymentMethodPlugin pluginDetail = paymentMethod.getPluginDetail();
        final String cardCountry = PaymentMethodUtils.getCardCountry(pluginDetail);
        final String cardType = PaymentMethodUtils.getCardType(pluginDetail);
        final String paymentMethodString = PaymentMethodUtils.getPaymentMethodType(pluginDetail);

        invoicePaymentSqlDao.inTransaction(new Transaction<Void, BusinessInvoicePaymentSqlDao>() {
            @Override
            public Void inTransaction(final BusinessInvoicePaymentSqlDao transactional, final TransactionStatus status) throws Exception {
                // Delete the existing payment if it exists - this is to make the call idempotent
                transactional.deleteInvoicePayment(payment.getId().toString());

                // invoicePayment may be null on payment failures
                final String invoicePaymentType;
                final UUID linkedInvoicePaymentId;
                if (invoicePayment != null) {
                    invoicePaymentType = invoicePayment.getType().toString();
                    linkedInvoicePaymentId = invoicePayment.getLinkedInvoicePaymentId();
                } else {
                    invoicePaymentType = null;
                    linkedInvoicePaymentId = null;
                }

                // Create the bip record
                final BusinessInvoicePayment businessInvoicePayment = new BusinessInvoicePayment(
                        account.getExternalKey(),
                        payment.getAmount(),
                        extFirstPaymentRefId,
                        extSecondPaymentRefId,
                        cardCountry,
                        cardType,
                        clock.getUTCNow(),
                        payment.getCurrency(),
                        payment.getEffectiveDate(),
                        payment.getInvoiceId(),
                        message,
                        payment.getId(),
                        paymentMethodString,
                        "Electronic",
                        paymentMethod.getPluginName(),
                        payment.getPaymentStatus().toString(),
                        payment.getAmount(),
                        clock.getUTCNow(),
                        invoicePaymentType,
                        linkedInvoicePaymentId);
                transactional.createInvoicePayment(businessInvoicePayment);

                // Update bin to get the latest invoice(s) balance(s)
                final BusinessInvoiceSqlDao invoiceSqlDao = transactional.become(BusinessInvoiceSqlDao.class);
                invoiceRecorder.rebuildInvoicesForAccountInTransaction(account.getId(), invoiceSqlDao);

                // Update bac to get the latest account balance, total invoice balance, etc.
                final BusinessAccountSqlDao accountSqlDao = transactional.become(BusinessAccountSqlDao.class);
                accountRecorder.updateAccountInTransaction(account, accountSqlDao);

                log.info("Added payment {}", businessInvoicePayment);
                return null;
            }
        });
    }
}
