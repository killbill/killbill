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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import io.swagger.annotations.ApiModelProperty;

public class InvoiceItemJson extends JsonBase {

    @ApiModelProperty(dataType = "java.util.UUID", required = true)
    private final String invoiceItemId;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String invoiceId;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String linkedInvoiceItemId;
    @ApiModelProperty(dataType = "java.util.UUID", required = true)
    private final String accountId;
    @ApiModelProperty(dataType = "java.util.UUID", required = false)
    private final String childAccountId;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String bundleId;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String subscriptionId;
    private final String planName;
    private final String phaseName;
    private final String usageName;
    private final String itemType;
    private final String description;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal amount;
    private final String currency;
    private List<InvoiceItemJson> childItems;

    @JsonCreator
    public InvoiceItemJson(@JsonProperty("invoiceItemId") final String invoiceItemId,
                           @JsonProperty("invoiceId") final String invoiceId,
                           @JsonProperty("linkedInvoiceItemId") final String linkedInvoiceItemId,
                           @JsonProperty("accountId") final String accountId,
                           @JsonProperty("childAccountId") final String childAccountId,
                           @JsonProperty("bundleId") final String bundleId,
                           @JsonProperty("subscriptionId") final String subscriptionId,
                           @JsonProperty("planName") final String planName,
                           @JsonProperty("phaseName") final String phaseName,
                           @JsonProperty("usageName") final String usageName,
                           @JsonProperty("itemType") final String itemType,
                           @JsonProperty("description") final String description,
                           @JsonProperty("startDate") final LocalDate startDate,
                           @JsonProperty("endDate") final LocalDate endDate,
                           @JsonProperty("amount") final BigDecimal amount,
                           @JsonProperty("currency") final String currency,
                           @JsonProperty("childItems") final List<InvoiceItemJson> childItems,
                           @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.invoiceItemId = invoiceItemId;
        this.invoiceId = invoiceId;
        this.linkedInvoiceItemId = linkedInvoiceItemId;
        this.accountId = accountId;
        this.childAccountId = childAccountId;
        this.bundleId = bundleId;
        this.subscriptionId = subscriptionId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.usageName = usageName;
        this.itemType = itemType;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.currency = currency;
        this.childItems = childItems;
    }

    public InvoiceItemJson(final InvoiceItem item, final List<InvoiceItem> childItems, @Nullable final List<AuditLog> auditLogs) {
        this(toString(item.getId()), toString(item.getInvoiceId()), toString(item.getLinkedItemId()),
             toString(item.getAccountId()), toString(item.getChildAccountId()), toString(item.getBundleId()), toString(item.getSubscriptionId()),
             item.getPlanName(), item.getPhaseName(), item.getUsageName(), item.getInvoiceItemType().toString(),
             item.getDescription(), item.getStartDate(), item.getEndDate(),
             item.getAmount(), item.getCurrency().name(), toInvoiceItemJson(childItems), toAuditLogJson(auditLogs));
    }

    private static List<InvoiceItemJson> toInvoiceItemJson(final List<InvoiceItem> childItems) {
        if (childItems == null) {
            return null;
        }
        return ImmutableList.copyOf(Collections2.transform(childItems, new Function<InvoiceItem, InvoiceItemJson>() {
            @Override
            public InvoiceItemJson apply(final InvoiceItem input) {
                return new InvoiceItemJson(input);
            }
        }));
    }

    public InvoiceItem toInvoiceItem() {
        return new InvoiceItem() {
            @Override
            public InvoiceItemType getInvoiceItemType() {
                return itemType != null ? InvoiceItemType.valueOf(itemType) : null;
            }

            @Override
            public UUID getInvoiceId() {
                return invoiceId != null ? UUID.fromString(invoiceId) : null;
            }

            @Override
            public UUID getAccountId() {
                return accountId != null ? UUID.fromString(accountId) : null;
            }

            @Override
            public UUID getChildAccountId() {
                return childAccountId != null ? UUID.fromString(childAccountId) : null;
            }

            @Override
            public LocalDate getStartDate() {
                return startDate;
            }

            @Override
            public LocalDate getEndDate() {
                return endDate;
            }

            @Override
            public BigDecimal getAmount() {
                return amount;
            }

            @Override
            public Currency getCurrency() {
                return Currency.valueOf(currency);
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public UUID getBundleId() {
                return bundleId != null ? UUID.fromString(bundleId) : null;
            }

            @Override
            public UUID getSubscriptionId() {
                return subscriptionId != null ? UUID.fromString(subscriptionId) : null;
            }

            @Override
            public String getPlanName() {
                return planName;
            }

            @Override
            public String getPhaseName() {
                return phaseName;
            }

            @Override
            public String getUsageName() {
                return usageName;
            }

            @Override
            public BigDecimal getRate() {
                return null;
            }

            @Override
            public UUID getLinkedItemId() {
                return linkedInvoiceItemId != null ? UUID.fromString(linkedInvoiceItemId) : null;
            }

            @Override
            public boolean matches(final Object o) {
                return false;
            }

            @Override
            public UUID getId() {
                return null;
            }

            @Override
            public DateTime getCreatedDate() {
                return null;
            }

            @Override
            public DateTime getUpdatedDate() {
                return null;
            }
        };
    }

    public InvoiceItemJson(final InvoiceItem input) {
        this(input, null, null);
    }

    public String getInvoiceItemId() {
        return invoiceItemId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getLinkedInvoiceItemId() {
        return linkedInvoiceItemId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getChildAccountId() {
        return childAccountId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getPlanName() {
        return planName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public String getUsageName() {
        return usageName;
    }

    public String getItemType() {
        return itemType;
    }

    public String getDescription() {
        return description;
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

    public List<InvoiceItemJson> getChildItems() {
        return childItems;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InvoiceItemJson");
        sb.append("{invoiceItemId='").append(invoiceItemId).append('\'');
        sb.append(", invoiceId='").append(invoiceId).append('\'');
        sb.append(", linkedInvoiceItemId='").append(linkedInvoiceItemId).append('\'');
        sb.append(", accountId='").append(accountId).append('\'');
        sb.append(", childAccountId='").append(childAccountId).append('\'');
        sb.append(", bundleId='").append(bundleId).append('\'');
        sb.append(", subscriptionId='").append(subscriptionId).append('\'');
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", phaseName='").append(phaseName).append('\'');
        sb.append(", usageName='").append(usageName).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", childItems=").append(childItems);
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

        final InvoiceItemJson that = (InvoiceItemJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (childAccountId != null ? !childAccountId.equals(that.childAccountId) : that.childAccountId != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        if (!((endDate == null && that.endDate == null) ||
              (endDate != null && that.endDate != null && endDate.compareTo(that.endDate) == 0))) {
            return false;
        }
        if (invoiceItemId != null ? !invoiceItemId.equals(that.invoiceItemId) : that.invoiceItemId != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (linkedInvoiceItemId != null ? !linkedInvoiceItemId.equals(that.linkedInvoiceItemId) : that.linkedInvoiceItemId != null) {
            return false;
        }
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) {
            return false;
        }
        if (usageName != null ? !usageName.equals(that.usageName) : that.usageName != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
            return false;
        }
        if (!((startDate == null && that.startDate == null) ||
              (startDate != null && that.startDate != null && startDate.compareTo(that.startDate) == 0))) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (childItems != null ? !childItems.equals(that.childItems) : that.childItems != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId != null ? invoiceId.hashCode() : 0;
        result = 31 * result + (invoiceItemId != null ? invoiceItemId.hashCode() : 0);
        result = 31 * result + (linkedInvoiceItemId != null ? linkedInvoiceItemId.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (childAccountId != null ? childAccountId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (usageName != null ? usageName.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (childItems != null ? childItems.hashCode() : 0);
        return result;
    }
}
