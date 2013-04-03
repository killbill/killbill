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
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceItemBaseModelDao;

public class BusinessInvoiceItem extends BusinessEntityBase {

    private final UUID itemId;
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
    private final String itemType;
    private final Boolean recognizable;
    private final String bundleExternalKey;
    private final String productName;
    private final String productType;
    private final String productCategory;
    private final String slug;
    private final String phase;
    private final String billingPeriod;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal amount;
    private final Currency currency;
    private final UUID linkedItemId;

    public BusinessInvoiceItem(final BusinessInvoiceItemBaseModelDao businessInvoiceItemBaseModelDao) {
        super(businessInvoiceItemBaseModelDao.getCreatedDate(),
              businessInvoiceItemBaseModelDao.getCreatedBy(),
              businessInvoiceItemBaseModelDao.getCreatedReasonCode(),
              businessInvoiceItemBaseModelDao.getCreatedComments(),
              businessInvoiceItemBaseModelDao.getAccountId(),
              businessInvoiceItemBaseModelDao.getAccountName(),
              businessInvoiceItemBaseModelDao.getAccountExternalKey());
        this.itemId = businessInvoiceItemBaseModelDao.getItemId();
        this.invoiceId = businessInvoiceItemBaseModelDao.getInvoiceId();
        this.invoiceNumber = businessInvoiceItemBaseModelDao.getInvoiceNumber();
        this.invoiceCreatedDate = businessInvoiceItemBaseModelDao.getInvoiceCreatedDate();
        this.invoiceDate = businessInvoiceItemBaseModelDao.getInvoiceDate();
        this.invoiceTargetDate = businessInvoiceItemBaseModelDao.getInvoiceTargetDate();
        this.invoiceCurrency = businessInvoiceItemBaseModelDao.getInvoiceCurrency();
        this.invoiceBalance = businessInvoiceItemBaseModelDao.getInvoiceBalance();
        this.invoiceAmountPaid = businessInvoiceItemBaseModelDao.getInvoiceAmountPaid();
        this.invoiceAmountCharged = businessInvoiceItemBaseModelDao.getInvoiceAmountCharged();
        this.invoiceOriginalAmountCharged = businessInvoiceItemBaseModelDao.getInvoiceOriginalAmountCharged();
        this.invoiceAmountCredited = businessInvoiceItemBaseModelDao.getInvoiceAmountCredited();
        this.itemType = businessInvoiceItemBaseModelDao.getItemType();
        this.recognizable = businessInvoiceItemBaseModelDao.getRecognizable();
        this.bundleExternalKey = businessInvoiceItemBaseModelDao.getBundleExternalKey();
        this.productName = businessInvoiceItemBaseModelDao.getProductName();
        this.productType = businessInvoiceItemBaseModelDao.getProductType();
        this.productCategory = businessInvoiceItemBaseModelDao.getProductCategory();
        this.slug = businessInvoiceItemBaseModelDao.getSlug();
        this.phase = businessInvoiceItemBaseModelDao.getPhase();
        this.billingPeriod = businessInvoiceItemBaseModelDao.getBillingPeriod();
        this.startDate = businessInvoiceItemBaseModelDao.getStartDate();
        this.endDate = businessInvoiceItemBaseModelDao.getEndDate();
        this.amount = businessInvoiceItemBaseModelDao.getAmount();
        this.currency = businessInvoiceItemBaseModelDao.getCurrency();
        this.linkedItemId = businessInvoiceItemBaseModelDao.getLinkedItemId();
    }
}
