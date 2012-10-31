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

package com.ning.billing.analytics;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.payment.PaymentInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;

import com.google.inject.Inject;

public class BusinessAccountDao {

    private static final Logger log = LoggerFactory.getLogger(BusinessAccountDao.class);

    private final BusinessAccountSqlDao sqlDao;
    private final AccountInternalApi accountApi;
    private final InvoiceInternalApi invoiceApi;
    private final PaymentInternalApi paymentApi;

    @Inject
    public BusinessAccountDao(final BusinessAccountSqlDao sqlDao, final AccountInternalApi accountApi,
                              final InvoiceInternalApi invoiceApi, final PaymentInternalApi paymentApi) {
        this.sqlDao = sqlDao;
        this.accountApi = accountApi;
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
    }

    public void accountUpdated(final UUID accountId, final InternalCallContext context) {
        final Account account;
        try {
            account = accountApi.getAccountById(accountId, context);
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount", e);
            return;
        }

        final BusinessAccount bac = createBusinessAccountFromAccount(account, context);
        sqlDao.inTransaction(new Transaction<Void, BusinessAccountSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAccountSqlDao transactional, final TransactionStatus status) throws Exception {
                updateAccountInTransaction(bac, transactional, context);
                return null;
            }
        });
    }

    // Called also from BusinessInvoiceDao and BusinessInvoicePaymentDao.
    // Note: computing the BusinessAccount object is fairly expensive, hence should be done outside of the transaction
    public void updateAccountInTransaction(final BusinessAccount bac, final BusinessAccountSqlDao transactional, final InternalCallContext context) {
        log.info("ACCOUNT UPDATE " + bac);
        transactional.deleteAccount(bac.getAccountId().toString(), context);
        // Note! There is a window of doom here since we use read committed transactional level by default
        transactional.createAccount(bac, context);
    }

    public BusinessAccount createBusinessAccountFromAccount(final Account account, final InternalTenantContext context) {
        final BusinessAccount bac = new BusinessAccount(account);

        try {
            LocalDate lastInvoiceDate = bac.getLastInvoiceDate();
            BigDecimal totalInvoiceBalance = bac.getTotalInvoiceBalance();
            String lastPaymentStatus = bac.getLastPaymentStatus();
            String paymentMethodType = bac.getPaymentMethod();
            String creditCardType = bac.getCreditCardType();
            String billingAddressCountry = bac.getBillingAddressCountry();

            // Retrieve invoices information
            final Collection<Invoice> invoices = invoiceApi.getInvoicesByAccountId(account.getId(), context);
            if (invoices != null && invoices.size() > 0) {
                for (final Invoice invoice : invoices) {
                    totalInvoiceBalance = totalInvoiceBalance.add(invoice.getBalance());

                    if (lastInvoiceDate == null || invoice.getInvoiceDate().isAfter(lastInvoiceDate)) {
                        lastInvoiceDate = invoice.getInvoiceDate();
                    }
                }

                // Retrieve payments information for these invoices
                DateTime lastPaymentDate = null;

                final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), context);
                if (payments != null) {
                    for (final Payment cur : payments) {
                        // Use the last payment method/type/country as the default one for the account
                        if (lastPaymentDate == null || cur.getEffectiveDate().isAfter(lastPaymentDate)) {
                            lastPaymentDate = cur.getEffectiveDate();
                            lastPaymentStatus = cur.getPaymentStatus().toString();
                        }
                    }
                }
            }

            // Retrieve payment methods
            for (final PaymentMethod paymentMethod : paymentApi.getPaymentMethods(account, true, context)) {
                if (paymentMethod.getId().equals(account.getPaymentMethodId()) && paymentMethod.getPluginDetail() != null) {
                    paymentMethodType = PaymentMethodUtils.getPaymentMethodType(paymentMethod.getPluginDetail());
                    creditCardType = PaymentMethodUtils.getCardType(paymentMethod.getPluginDetail());
                    billingAddressCountry = PaymentMethodUtils.getCardCountry(paymentMethod.getPluginDetail());
                    break;
                }
            }

            bac.setLastPaymentStatus(lastPaymentStatus);
            bac.setPaymentMethod(paymentMethodType);
            bac.setCreditCardType(creditCardType);
            bac.setBillingAddressCountry(billingAddressCountry);
            bac.setLastInvoiceDate(lastInvoiceDate);
            bac.setTotalInvoiceBalance(totalInvoiceBalance);

            bac.setBalance(invoiceApi.getAccountBalance(account.getId(), context));
        } catch (PaymentApiException ex) {
            log.error(String.format("Failed to handle account update for account %s", account.getId()), ex);
        }

        return bac;
    }
}
