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

package com.ning.billing.analytics.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.entity.Entity;

public interface BusinessInvoice extends Entity {

    public UUID getInvoiceId();

    public Integer getInvoiceNumber();

    public UUID getAccountId();

    public String getAccountKey();

    public LocalDate getInvoiceDate();

    public LocalDate getTargetDate();

    public Currency getCurrency();

    public BigDecimal getBalance();

    public BigDecimal getAmountPaid();

    public BigDecimal getAmountCharged();

    public BigDecimal getAmountCredited();

    public List<BusinessInvoiceItem> getInvoiceItems();

    public interface BusinessInvoiceItem {

        public UUID getItemId();

        public UUID getInvoiceId();

        public String getItemType();

        public String getExternalKey();

        public String getProductName();

        public String getProductType();

        public String getProductCategory();

        public String getSlug();

        public String getPhase();

        public String getBillingPeriod();

        public LocalDate getStartDate();

        public LocalDate getEndDate();

        public BigDecimal getAmount();

        public Currency getCurrency();

        public UUID getLinkedItemId();
    }
}
