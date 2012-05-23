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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.ChangedField;
import com.ning.billing.analytics.dao.BusinessAccountDao;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.util.tag.Tag;

public class BusinessAccountRecorder {
    private static final Logger log = LoggerFactory.getLogger(BusinessAccountRecorder.class);

    private final BusinessAccountDao dao;
    private final AccountUserApi accountApi;
    private final InvoiceUserApi invoiceUserApi;
    private final PaymentApi paymentApi;
    private final TagUserApi tagUserApi;

    @Inject
    public BusinessAccountRecorder(final BusinessAccountDao dao, final AccountUserApi accountApi,
                                   final InvoiceUserApi invoiceUserApi, final PaymentApi paymentApi,
                                   final TagUserApi tagUserApi) {
        this.dao = dao;
        this.accountApi = accountApi;
        this.invoiceUserApi = invoiceUserApi;
        this.paymentApi = paymentApi;
        this.tagUserApi = tagUserApi;
    }

    public void accountCreated(final AccountData data) {
        Account account;
        try {
            account = accountApi.getAccountByKey(data.getExternalKey());
            Map<String, Tag> tags = tagUserApi.getTags(account.getId(), ObjectType.ACCOUNT);
            final BusinessAccount bac = createBusinessAccountFromAccount(account, new ArrayList<Tag>(tags.values()));

            log.info("ACCOUNT CREATION " + bac);
            dao.createAccount(bac);
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount",e);
        }
    }

    /**
     * Notification handler for Account changes
     *
     * @param accountId     account id changed
     * @param changedFields list of changed fields
     */
    public void accountUpdated(final UUID accountId, final List<ChangedField> changedFields) {
        // None of the fields updated interest us so far - see DefaultAccountChangeNotification
        // TODO We'll need notifications for tags changes eventually
    }

    /**
     * Notification handler for Payment creations
     *
     * @param paymentInfo payment object (from the payment plugin)
     */
    public void accountUpdated(final PaymentInfoEvent paymentInfo) {
        try {
            final PaymentAttempt paymentAttempt = paymentApi.getPaymentAttemptForPaymentId(paymentInfo.getId());
            if (paymentAttempt == null) {
                return;
            }

            final Account account = accountApi.getAccountById(paymentAttempt.getAccountId());
            accountUpdated(account.getId());
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount",e);
        } catch (PaymentApiException e) {
            log.warn("Error encountered creating BusinessAccount",e);            
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
            final Map<String, Tag> tags = tagUserApi.getTags(accountId, ObjectType.ACCOUNT);

            if (account == null) {
                log.warn("Couldn't find account {}", accountId);
                return;
            }

            BusinessAccount bac = dao.getAccount(account.getExternalKey());
            if (bac == null) {
                bac = createBusinessAccountFromAccount(account, new ArrayList<Tag>(tags.values()));
                log.info("ACCOUNT CREATION " + bac);
                dao.createAccount(bac);
            } else {
                updateBusinessAccountFromAccount(account, bac);
                log.info("ACCOUNT UPDATE " + bac);
                dao.saveAccount(bac);
            }
        } catch (AccountApiException e) {
            log.warn("Error encountered creating BusinessAccount",e);
        }

    }

    private BusinessAccount createBusinessAccountFromAccount(final Account account, final List<Tag> tags) {
        final BusinessAccount bac = new BusinessAccount(
                account.getExternalKey(),
                invoiceUserApi.getAccountBalance(account.getId()),
                tags,
                // These fields will be updated below
                null,
                null,
                null,
                null,
                null,
                null
        );
        updateBusinessAccountFromAccount(account, bac);

        return bac;
    }

    private void updateBusinessAccountFromAccount(final Account account, final BusinessAccount bac) {
        DateTime lastInvoiceDate = null;
        BigDecimal totalInvoiceBalance = BigDecimal.ZERO;
        String lastPaymentStatus = null;
        String paymentMethod = null;
        String creditCardType = null;
        String billingAddressCountry = null;

        // Retrieve invoices information
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId());
        if (invoices != null && invoices.size() > 0) {
            final List<String> invoiceIds = new ArrayList<String>();
            for (final Invoice invoice : invoices) {
                invoiceIds.add(invoice.getId().toString());
                totalInvoiceBalance = totalInvoiceBalance.add(invoice.getBalance());

                if (lastInvoiceDate == null || invoice.getInvoiceDate().isAfter(lastInvoiceDate)) {
                    lastInvoiceDate = invoice.getInvoiceDate();
                }
            }

            // Retrieve payments information for these invoices
            try {
                final PaymentInfoEvent payment = paymentApi.getLastPaymentInfo(invoiceIds);
                if (payment != null) {
                    lastPaymentStatus = payment.getStatus();
                    paymentMethod = payment.getPaymentMethod();
                    creditCardType = payment.getCardType();
                    billingAddressCountry = payment.getCardCountry();
                }

                bac.setLastPaymentStatus(lastPaymentStatus);
                bac.setPaymentMethod(paymentMethod);
                bac.setCreditCardType(creditCardType);
                bac.setBillingAddressCountry(billingAddressCountry);
                bac.setLastInvoiceDate(lastInvoiceDate);
                bac.setTotalInvoiceBalance(totalInvoiceBalance);

                bac.setBalance(invoiceUserApi.getAccountBalance(account.getId()));
            } catch (PaymentApiException ex) {
                // TODO: handle this exception
            }
        }
    }
}
