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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;

public class InvoiceItemJsonSimple {
    private final UUID invoiceId;
    private final UUID accountId;
    private final UUID bundleId;
    private final UUID subscriptionId;
    private final String planName;
    private final String phaseName;
    private final String description;
    private final DateTime startDate;
    private final DateTime endDate;
    private final BigDecimal amount;
    private final Currency currency;

    public InvoiceItemJsonSimple(@JsonProperty("invoiceId") UUID invoiceId,
                                 @JsonProperty("accountId") UUID accountId,
                                 @JsonProperty("bundleId") UUID bundleId,
                                 @JsonProperty("subscriptionId") UUID subscriptionId,
                                 @JsonProperty("planName") String planName,
                                 @JsonProperty("phaseName") String phaseName,
                                 @JsonProperty("description") String description,
                                 @JsonProperty("startDate") DateTime startDate,
                                 @JsonProperty("endDate") DateTime endDate,
                                 @JsonProperty("amount") BigDecimal amount,
                                 @JsonProperty("currency") Currency currency) {
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.subscriptionId = subscriptionId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.currency = currency;
    }

    public InvoiceItemJsonSimple(InvoiceItem item) {
        this.invoiceId = item.getInvoiceId();
        this.accountId = item.getAccountId();
        this.bundleId = item.getBundleId();
        this.subscriptionId = item.getSubscriptionId();
        this.planName = item.getPlanName();
        this.phaseName = item.getPhaseName();
        this.description = item.getDescription();
        this.startDate = item.getStartDate();
        this.endDate = item.getEndDate();
        this.amount = item.getAmount();
        this.currency = item.getCurrency();
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getPlanName() {
        return planName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public String getDescription() {
        return description;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }
}
