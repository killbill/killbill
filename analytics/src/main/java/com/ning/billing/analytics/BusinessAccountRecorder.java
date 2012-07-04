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
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentInfoEvent;

public class BusinessAccountRecorder {
    private static final Logger log = LoggerFactory.getLogger(BusinessAccountRecorder.class);

    private final BusinessAccountSqlDao sqlDao;
    private final AccountUserApi accountApi;
    private final InvoiceUserApi invoiceUserApi;
    private final PaymentApi paymentApi;

    @Inject
    public BusinessAccountRecorder(final BusinessAccountSqlDao sqlDao, final AccountUserApi accountApi,
                                   final InvoiceUserApi invoiceUserApi, final PaymentApi paymentApi) {
        this.sqlDao = sqlDao;
        this.accountApi = accountApi;
        this.invoiceUserApi = invoiceUserApi;
        this.paymentApi = paymentApi;
    }

    public void accountCreated(final AccountData data) {
        final Account account;
        try {
            account = accountApi.getAccountByKey(data.getExternalKey());
            final BusinessAccount bac = new BusinessAccount(account.getId());
            updateBusinessAccountFromAccount(account, bac);

            log.info("ACCOUNT CREATION " + bac);
            sqlDao.createAccount(bac);
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount", e);
        }
    }

    /**
     * Notification handler for Payment creations
     *
     * @param paymentInfo payment object (from the payment plugin)
     */
    public void accountUpdated(final PaymentInfoEvent paymentInfo) {
        try {
            final Account account = accountApi.getAccountById(paymentInfo.getAccountId());
            accountUpdated(account.getId());
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount", e);
        }
    }

    /**
     * Notification handler for Invoice creations
     *
     * @param accountId account id associated with the created invoice
     */
    public void accountUpdated(final UUID accountId) {
        try {
            final Account account = accountApi.getAccountById(accountId);

            BusinessAccount bac = sqlDao.getAccount(accountId.toString());
            if (bac == null) {
                bac = new BusinessAccount(accountId);
                updateBusinessAccountFromAccount(account, bac);
                log.info("ACCOUNT CREATION " + bac);
                sqlDao.createAccount(bac);
            } else {
                updateBusinessAccountFromAccount(account, bac);
                log.info("ACCOUNT UPDATE " + bac);
                sqlDao.saveAccount(bac);
            }
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount", e);
        }
    }

    private void updateBusinessAccountFromAccount(final Account account, final BusinessAccount bac) {
        bac.setName(account.getName());
        bac.setKey(account.getExternalKey());

        try {
            DateTime lastInvoiceDate = bac.getLastInvoiceDate();
            BigDecimal totalInvoiceBalance = bac.getTotalInvoiceBalance();
            String lastPaymentStatus = bac.getLastPaymentStatus();
            String paymentMethod = bac.getPaymentMethod();
            String creditCardType = bac.getCreditCardType();
            String billingAddressCountry = bac.getBillingAddressCountry();

            // Retrieve invoices information
            final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId());
            if (invoices != null && invoices.size() > 0) {

                for (final Invoice invoice : invoices) {
                    totalInvoiceBalance = totalInvoiceBalance.add(invoice.getBalance());

                    if (lastInvoiceDate == null || invoice.getInvoiceDate().isAfter(lastInvoiceDate)) {
                        lastInvoiceDate = invoice.getInvoiceDate();
                    }
                }

                // Retrieve payments information for these invoices
                DateTime lastPaymentDate = null;

                final List<Payment> payments = paymentApi.getAccountPayments(account.getId());
                if (payments != null) {
                    for (final Payment cur : payments) {
                        // Use the last payment method/type/country as the default one for the account
                        if (lastPaymentDate == null || cur.getEffectiveDate().isAfter(lastPaymentDate)) {
                            lastPaymentDate = cur.getEffectiveDate();
                            lastPaymentStatus = cur.getPaymentStatus().toString();
                            // STEPH talk to Pierre
                            paymentMethod = null;
                            creditCardType = null;
                            billingAddressCountry = null;
                        }
                    }
                }
            }

            bac.setLastPaymentStatus(lastPaymentStatus);
            bac.setPaymentMethod(paymentMethod);
            bac.setCreditCardType(creditCardType);
            bac.setBillingAddressCountry(billingAddressCountry);
            bac.setLastInvoiceDate(lastInvoiceDate);
            bac.setTotalInvoiceBalance(totalInvoiceBalance);

            bac.setBalance(invoiceUserApi.getAccountBalance(account.getId()));

        } catch (PaymentApiException ex) {
            log.error(String.format("Failed to handle account update for account %s", account.getId()), ex);
        }
    }
}
