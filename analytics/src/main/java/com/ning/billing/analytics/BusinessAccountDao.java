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
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.google.inject.Inject;

public class BusinessAccountDao {

    private static final Logger log = LoggerFactory.getLogger(BusinessAccountDao.class);

    private final BusinessAccountSqlDao sqlDao;
    private final AccountUserApi accountApi;
    private final InvoiceUserApi invoiceUserApi;
    private final PaymentApi paymentApi;

    @Inject
    public BusinessAccountDao(final BusinessAccountSqlDao sqlDao, final AccountUserApi accountApi,
                              final InvoiceUserApi invoiceUserApi, final PaymentApi paymentApi) {
        this.sqlDao = sqlDao;
        this.accountApi = accountApi;
        this.invoiceUserApi = invoiceUserApi;
        this.paymentApi = paymentApi;
    }

    public void accountCreated(final AccountData data, final InternalCallContext context) {
        final Account account;
        try {
            account = accountApi.getAccountByKey(data.getExternalKey(), context.toCallContext());
            accountUpdated(account.getId(), context);
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount", e);
        }
    }

    /**
     * Notification handler for Invoice creations
     *
     * @param accountId account id associated with the created invoice
     */
    public void accountUpdated(final UUID accountId, final InternalCallContext context) {
        final Account account;
        try {
            account = accountApi.getAccountById(accountId, context.toCallContext());
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount", e);
            return;
        }

        updateAccountInTransaction(account, sqlDao, context);
    }

    public void updateAccountInTransaction(final Account account, final BusinessAccountSqlDao transactional, final InternalCallContext context) {
        BusinessAccount bac = transactional.getAccount(account.getId().toString(), context);
        if (bac == null) {
            bac = new BusinessAccount(account.getId());
            updateBusinessAccountFromAccount(account, bac, context);
            log.info("ACCOUNT CREATION " + bac);
            transactional.createAccount(bac, context);
        } else {
            updateBusinessAccountFromAccount(account, bac, context);
            log.info("ACCOUNT UPDATE " + bac);
            transactional.saveAccount(bac, context);
        }
    }

    private void updateBusinessAccountFromAccount(final Account account, final BusinessAccount bac, final InternalTenantContext context) {
        bac.setName(account.getName());
        bac.setKey(account.getExternalKey());
        final Currency currency = account.getCurrency();
        bac.setCurrency(currency != null ? currency.toString() : bac.getCurrency());

        try {
            LocalDate lastInvoiceDate = bac.getLastInvoiceDate();
            BigDecimal totalInvoiceBalance = bac.getTotalInvoiceBalance();
            String lastPaymentStatus = bac.getLastPaymentStatus();
            String paymentMethodType = bac.getPaymentMethod();
            String creditCardType = bac.getCreditCardType();
            String billingAddressCountry = bac.getBillingAddressCountry();

            // Retrieve invoices information
            final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), context.toTenantContext());
            if (invoices != null && invoices.size() > 0) {

                for (final Invoice invoice : invoices) {
                    totalInvoiceBalance = totalInvoiceBalance.add(invoice.getBalance());

                    if (lastInvoiceDate == null || invoice.getInvoiceDate().isAfter(lastInvoiceDate)) {
                        lastInvoiceDate = invoice.getInvoiceDate();
                    }
                }

                // Retrieve payments information for these invoices
                DateTime lastPaymentDate = null;

                final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), context.toTenantContext());
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
            for (final PaymentMethod paymentMethod : paymentApi.getPaymentMethods(account, true, context.toTenantContext())) {
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

            bac.setBalance(invoiceUserApi.getAccountBalance(account.getId(), context.toTenantContext()));
        } catch (PaymentApiException ex) {
            log.error(String.format("Failed to handle account update for account %s", account.getId()), ex);
        }
    }
}
