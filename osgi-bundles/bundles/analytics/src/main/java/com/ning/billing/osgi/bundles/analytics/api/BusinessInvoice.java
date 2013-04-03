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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;

import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceModelDao;

public class BusinessInvoice extends BusinessEntityBase {

    private final UUID invoiceId;
    private final Integer invoiceNumber;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final String currency;
    private final BigDecimal balance;
    private final BigDecimal amountPaid;
    private final BigDecimal amountCharged;
    private final BigDecimal originalAmountCharged;
    private final BigDecimal amountCredited;
    private final List<BusinessInvoiceItem> invoiceItems = new LinkedList<BusinessInvoiceItem>();

    public BusinessInvoice(final BusinessInvoiceModelDao businessInvoiceModelDao,
                           final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItemModelDaos) {
        super(businessInvoiceModelDao.getCreatedDate(),
              businessInvoiceModelDao.getCreatedBy(),
              businessInvoiceModelDao.getCreatedReasonCode(),
              businessInvoiceModelDao.getCreatedComments(),
              businessInvoiceModelDao.getAccountId(),
              businessInvoiceModelDao.getAccountName(),
              businessInvoiceModelDao.getAccountExternalKey());
        this.invoiceId = businessInvoiceModelDao.getInvoiceId();
        this.invoiceNumber = businessInvoiceModelDao.getInvoiceNumber();
        this.invoiceDate = businessInvoiceModelDao.getInvoiceDate();
        this.targetDate = businessInvoiceModelDao.getTargetDate();
        this.currency = businessInvoiceModelDao.getCurrency();
        this.balance = businessInvoiceModelDao.getBalance();
        this.amountPaid = businessInvoiceModelDao.getAmountPaid();
        this.amountCharged = businessInvoiceModelDao.getAmountCharged();
        this.originalAmountCharged = businessInvoiceModelDao.getOriginalAmountCharged();
        this.amountCredited = businessInvoiceModelDao.getAmountCredited();
        for (final BusinessInvoiceItemBaseModelDao businessInvoiceItemModelDao : businessInvoiceItemModelDaos) {
            invoiceItems.add(new BusinessInvoiceItem(businessInvoiceItemModelDao));
        }
    }
}
