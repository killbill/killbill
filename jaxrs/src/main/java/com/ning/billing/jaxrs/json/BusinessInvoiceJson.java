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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.analytics.api.BusinessInvoice;
import com.ning.billing.analytics.api.BusinessInvoice.BusinessInvoiceItem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class BusinessInvoiceJson extends JsonBase {

    private final String invoiceId;
    private final Integer invoiceNumber;
    private final String accountId;
    private final String accountKey;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final String currency;
    private final BigDecimal balance;
    private final BigDecimal amountPaid;
    private final BigDecimal amountCharged;
    private final BigDecimal amountCredited;
    private final List<BusinessInvoiceItemJson> invoiceItems;

    @JsonCreator
    public BusinessInvoiceJson(@JsonProperty("invoiceId") final String invoiceId,
                               @JsonProperty("invoiceNumber") final Integer invoiceNumber,
                               @JsonProperty("accountId") final String accountId,
                               @JsonProperty("accountKey") final String accountKey,
                               @JsonProperty("invoiceDate") final LocalDate invoiceDate,
                               @JsonProperty("targetDate") final LocalDate targetDate,
                               @JsonProperty("currency") final String currency,
                               @JsonProperty("balance") final BigDecimal balance,
                               @JsonProperty("amountPaid") final BigDecimal amountPaid,
                               @JsonProperty("amountCharged") final BigDecimal amountCharged,
                               @JsonProperty("amountCredited") final BigDecimal amountCredited,
                               @JsonProperty("invoiceItems") final List<BusinessInvoiceItemJson> invoiceItems) {
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.accountId = accountId;
        this.accountKey = accountKey;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
        this.balance = balance;
        this.amountPaid = amountPaid;
        this.amountCharged = amountCharged;
        this.amountCredited = amountCredited;
        this.invoiceItems = invoiceItems;
    }

    public BusinessInvoiceJson(final BusinessInvoice businessInvoice) {
        this.invoiceId = businessInvoice.getInvoiceId().toString();
        this.invoiceNumber = businessInvoice.getInvoiceNumber();
        this.accountId = businessInvoice.getAccountId().toString();
        this.accountKey = businessInvoice.getAccountKey();
        this.invoiceDate = businessInvoice.getInvoiceDate();
        this.targetDate = businessInvoice.getTargetDate();
        this.currency = businessInvoice.getCurrency().toString();
        this.balance = businessInvoice.getBalance();
        this.amountPaid = businessInvoice.getAmountPaid();
        this.amountCharged = businessInvoice.getAmountCharged();
        this.amountCredited = businessInvoice.getAmountCredited();
        this.invoiceItems = ImmutableList.<BusinessInvoiceItemJson>copyOf(Collections2.transform(businessInvoice.getInvoiceItems(), new Function<BusinessInvoiceItem, BusinessInvoiceItemJson>() {
            @Override
            public BusinessInvoiceItemJson apply(@Nullable final BusinessInvoiceItem input) {
                return new BusinessInvoiceItemJson(input);
            }
        }));
    }

    public static class BusinessInvoiceItemJson extends JsonBase {

        private final String itemId;
        private final String invoiceId;
        private final String itemType;
        private final String externalKey;
        private final String productName;
        private final String productType;
        private final String productCategory;
        private final String slug;
        private final String phase;
        private final String billingPeriod;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final BigDecimal amount;
        private final String currency;
        private final String linkedItemId;

        private BusinessInvoiceItemJson(@JsonProperty("itemId") final String itemId,
                                        @JsonProperty("invoiceId") final String invoiceId,
                                        @JsonProperty("itemType") final String itemType,
                                        @JsonProperty("externalKey") final String externalKey,
                                        @JsonProperty("productName") final String productName,
                                        @JsonProperty("productType") final String productType,
                                        @JsonProperty("productCategory") final String productCategory,
                                        @JsonProperty("slug") final String slug,
                                        @JsonProperty("phase") final String phase,
                                        @JsonProperty("billingPeriod") final String billingPeriod,
                                        @JsonProperty("startDate") final LocalDate startDate,
                                        @JsonProperty("endDate") final LocalDate endDate,
                                        @JsonProperty("amount") final BigDecimal amount,
                                        @JsonProperty("currency") final String currency,
                                        @JsonProperty("linkedItemId") final String linkedItemId) {
            this.itemId = itemId;
            this.invoiceId = invoiceId;
            this.itemType = itemType;
            this.externalKey = externalKey;
            this.productName = productName;
            this.productType = productType;
            this.productCategory = productCategory;
            this.slug = slug;
            this.phase = phase;
            this.billingPeriod = billingPeriod;
            this.startDate = startDate;
            this.endDate = endDate;
            this.amount = amount;
            this.currency = currency;
            this.linkedItemId = linkedItemId;
        }

        public BusinessInvoiceItemJson(final BusinessInvoiceItem businessInvoiceItem) {
            this(businessInvoiceItem.getItemId().toString(),
                 businessInvoiceItem.getInvoiceId().toString(),
                 businessInvoiceItem.getItemType().toLowerCase(),
                 businessInvoiceItem.getExternalKey(),
                 businessInvoiceItem.getProductName(),
                 businessInvoiceItem.getProductType(),
                 businessInvoiceItem.getProductCategory(),
                 businessInvoiceItem.getSlug(),
                 businessInvoiceItem.getPhase(),
                 businessInvoiceItem.getBillingPeriod(),
                 businessInvoiceItem.getStartDate(),
                 businessInvoiceItem.getEndDate(),
                 businessInvoiceItem.getAmount(),
                 businessInvoiceItem.getCurrency().toString(),
                 businessInvoiceItem.getLinkedItemId() == null ? null : businessInvoiceItem.getLinkedItemId().toString());
        }

        public String getItemId() {
            return itemId;
        }

        public String getInvoiceId() {
            return invoiceId;
        }

        public String getItemType() {
            return itemType;
        }

        public String getExternalKey() {
            return externalKey;
        }

        public String getProductName() {
            return productName;
        }

        public String getProductType() {
            return productType;
        }

        public String getProductCategory() {
            return productCategory;
        }

        public String getSlug() {
            return slug;
        }

        public String getPhase() {
            return phase;
        }

        public String getBillingPeriod() {
            return billingPeriod;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getCurrency() {
            return currency;
        }

        public String getLinkedItemId() {
            return linkedItemId;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("BusinessInvoiceItemJson");
            sb.append("{itemId='").append(itemId).append('\'');
            sb.append(", invoiceId='").append(invoiceId).append('\'');
            sb.append(", itemType='").append(itemType).append('\'');
            sb.append(", externalKey='").append(externalKey).append('\'');
            sb.append(", productName='").append(productName).append('\'');
            sb.append(", productType='").append(productType).append('\'');
            sb.append(", productCategory='").append(productCategory).append('\'');
            sb.append(", slug='").append(slug).append('\'');
            sb.append(", phase='").append(phase).append('\'');
            sb.append(", billingPeriod='").append(billingPeriod).append('\'');
            sb.append(", startDate=").append(startDate);
            sb.append(", endDate=").append(endDate);
            sb.append(", amount=").append(amount);
            sb.append(", currency='").append(currency).append('\'');
            sb.append(", linkedItemId='").append(linkedItemId).append('\'');
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final BusinessInvoiceItemJson that = (BusinessInvoiceItemJson) o;

            if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
                return false;
            }
            if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
                return false;
            }
            if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
                return false;
            }
            if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
                return false;
            }
            if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
                return false;
            }
            if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
                return false;
            }
            if (itemId != null ? !itemId.equals(that.itemId) : that.itemId != null) {
                return false;
            }
            if (itemType != null ? !itemType.equals(that.itemType) : that.itemType != null) {
                return false;
            }
            if (linkedItemId != null ? !linkedItemId.equals(that.linkedItemId) : that.linkedItemId != null) {
                return false;
            }
            if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
                return false;
            }
            if (productCategory != null ? !productCategory.equals(that.productCategory) : that.productCategory != null) {
                return false;
            }
            if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
                return false;
            }
            if (productType != null ? !productType.equals(that.productType) : that.productType != null) {
                return false;
            }
            if (slug != null ? !slug.equals(that.slug) : that.slug != null) {
                return false;
            }
            if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = itemId != null ? itemId.hashCode() : 0;
            result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
            result = 31 * result + (itemType != null ? itemType.hashCode() : 0);
            result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
            result = 31 * result + (productName != null ? productName.hashCode() : 0);
            result = 31 * result + (productType != null ? productType.hashCode() : 0);
            result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
            result = 31 * result + (slug != null ? slug.hashCode() : 0);
            result = 31 * result + (phase != null ? phase.hashCode() : 0);
            result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
            result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
            result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
            result = 31 * result + (amount != null ? amount.hashCode() : 0);
            result = 31 * result + (currency != null ? currency.hashCode() : 0);
            result = 31 * result + (linkedItemId != null ? linkedItemId.hashCode() : 0);
            return result;
        }
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getAmountCharged() {
        return amountCharged;
    }

    public BigDecimal getAmountCredited() {
        return amountCredited;
    }

    public List<BusinessInvoiceItemJson> getInvoiceItems() {
        return invoiceItems;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoiceJson");
        sb.append("{invoiceId='").append(invoiceId).append('\'');
        sb.append(", invoiceNumber=").append(invoiceNumber);
        sb.append(", accountId='").append(accountId).append('\'');
        sb.append(", accountKey='").append(accountKey).append('\'');
        sb.append(", invoiceDate=").append(invoiceDate);
        sb.append(", targetDate=").append(targetDate);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", balance=").append(balance);
        sb.append(", amountPaid=").append(amountPaid);
        sb.append(", amountCharged=").append(amountCharged);
        sb.append(", amountCredited=").append(amountCredited);
        sb.append(", invoiceItems=").append(invoiceItems);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BusinessInvoiceJson that = (BusinessInvoiceJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (amountCharged != null ? !amountCharged.equals(that.amountCharged) : that.amountCharged != null) {
            return false;
        }
        if (amountCredited != null ? !amountCredited.equals(that.amountCredited) : that.amountCredited != null) {
            return false;
        }
        if (amountPaid != null ? !amountPaid.equals(that.amountPaid) : that.amountPaid != null) {
            return false;
        }
        if (balance != null ? !balance.equals(that.balance) : that.balance != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (invoiceDate != null ? !invoiceDate.equals(that.invoiceDate) : that.invoiceDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceItems != null ? !invoiceItems.equals(that.invoiceItems) : that.invoiceItems != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (targetDate != null ? !targetDate.equals(that.targetDate) : that.targetDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId != null ? invoiceId.hashCode() : 0;
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (amountPaid != null ? amountPaid.hashCode() : 0);
        result = 31 * result + (amountCharged != null ? amountCharged.hashCode() : 0);
        result = 31 * result + (amountCredited != null ? amountCredited.hashCode() : 0);
        result = 31 * result + (invoiceItems != null ? invoiceItems.hashCode() : 0);
        return result;
    }
}
