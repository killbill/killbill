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

package com.ning.billing.osgi.bundles.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoicePaymentBaseModelDao;

public class BusinessInvoicePayment extends BusinessEntityBase {

    private final UUID invoicePaymentId;
    private final UUID invoiceId;
    private final Integer invoiceNumber;
    private final DateTime invoiceCreatedDate;
    private final LocalDate invoiceDate;
    private final LocalDate invoiceTargetDate;
    private final String invoiceCurrency;
    private final BigDecimal invoiceBalance;
    private final BigDecimal invoiceAmountPaid;
    private final BigDecimal invoiceAmountCharged;
    private final BigDecimal invoiceOriginalAmountCharged;
    private final BigDecimal invoiceAmountCredited;
    private final String invoicePaymentType;
    private final Long paymentNumber;
    private final UUID linkedInvoicePaymentId;
    private final BigDecimal amount;
    private final Currency currency;

    public BusinessInvoicePayment(final BusinessInvoicePaymentBaseModelDao businessInvoicePaymentBaseModelDao) {
        super(businessInvoicePaymentBaseModelDao.getCreatedDate(),
              businessInvoicePaymentBaseModelDao.getCreatedBy(),
              businessInvoicePaymentBaseModelDao.getCreatedReasonCode(),
              businessInvoicePaymentBaseModelDao.getCreatedComments(),
              businessInvoicePaymentBaseModelDao.getAccountId(),
              businessInvoicePaymentBaseModelDao.getAccountName(),
              businessInvoicePaymentBaseModelDao.getAccountExternalKey());
        this.invoicePaymentId = businessInvoicePaymentBaseModelDao.getInvoicePaymentId();
        this.invoiceId = businessInvoicePaymentBaseModelDao.getInvoiceId();
        this.invoiceNumber = businessInvoicePaymentBaseModelDao.getInvoiceNumber();
        this.invoiceCreatedDate = businessInvoicePaymentBaseModelDao.getInvoiceCreatedDate();
        this.invoiceDate = businessInvoicePaymentBaseModelDao.getInvoiceDate();
        this.invoiceTargetDate = businessInvoicePaymentBaseModelDao.getInvoiceTargetDate();
        this.invoiceCurrency = businessInvoicePaymentBaseModelDao.getInvoiceCurrency();
        this.invoiceBalance = businessInvoicePaymentBaseModelDao.getInvoiceBalance();
        this.invoiceAmountPaid = businessInvoicePaymentBaseModelDao.getInvoiceAmountPaid();
        this.invoiceAmountCharged = businessInvoicePaymentBaseModelDao.getInvoiceAmountCharged();
        this.invoiceOriginalAmountCharged = businessInvoicePaymentBaseModelDao.getInvoiceOriginalAmountCharged();
        this.invoiceAmountCredited = businessInvoicePaymentBaseModelDao.getInvoiceAmountCredited();
        this.invoicePaymentType = businessInvoicePaymentBaseModelDao.getInvoicePaymentType();
        this.paymentNumber = businessInvoicePaymentBaseModelDao.getPaymentNumber();
        this.linkedInvoicePaymentId = businessInvoicePaymentBaseModelDao.getLinkedInvoicePaymentId();
        this.amount = businessInvoicePaymentBaseModelDao.getAmount();
        this.currency = businessInvoicePaymentBaseModelDao.getCurrency();
    }
}
