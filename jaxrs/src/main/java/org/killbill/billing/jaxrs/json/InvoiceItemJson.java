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
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="InvoiceItem", parent = JsonBase.class)
public class InvoiceItemJson extends JsonBase {

    @ApiModelProperty( required = true)
    private final UUID invoiceItemId;
    private final UUID invoiceId;
    private final UUID linkedInvoiceItemId;
    @ApiModelProperty(required = true)
    private final UUID accountId;
    private final UUID childAccountId;
    private final UUID bundleId;
    private final UUID subscriptionId;
    private final String productName;
    private final String planName;
    private final String phaseName;
    private final String usageName;
    private final String prettyProductName;
    private final String prettyPlanName;
    private final String prettyPhaseName;
    private final String prettyUsageName;
    private final InvoiceItemType itemType;
    private final String description;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal amount;
    private final BigDecimal rate;
    private final Currency currency;
    private final Integer quantity;
    private final String itemDetails;
    private List<InvoiceItemJson> childItems;

    @JsonCreator
    public InvoiceItemJson(@JsonProperty("invoiceItemId") final UUID invoiceItemId,
                           @JsonProperty("invoiceId") final UUID invoiceId,
                           @JsonProperty("linkedInvoiceItemId") final UUID linkedInvoiceItemId,
                           @JsonProperty("accountId") final UUID accountId,
                           @JsonProperty("childAccountId") final UUID childAccountId,
                           @JsonProperty("bundleId") final UUID bundleId,
                           @JsonProperty("subscriptionId") final UUID subscriptionId,
                           @JsonProperty("productName") final String productName,
                           @JsonProperty("planName") final String planName,
                           @JsonProperty("phaseName") final String phaseName,
                           @JsonProperty("usageName") final String usageName,
                           @JsonProperty("prettyProductName") final String prettyProductName,
                           @JsonProperty("prettyPlanName") final String prettyPlanName,
                           @JsonProperty("prettyPhaseName") final String prettyPhaseName,
                           @JsonProperty("prettyUsageName") final String prettyUsageName,
                           @JsonProperty("itemType") final InvoiceItemType itemType,
                           @JsonProperty("description") final String description,
                           @JsonProperty("startDate") final LocalDate startDate,
                           @JsonProperty("endDate") final LocalDate endDate,
                           @JsonProperty("amount") final BigDecimal amount,
                           @JsonProperty("rate") final  BigDecimal rate,
                           @JsonProperty("currency") final Currency currency,
                           @JsonProperty("quantity") final Integer quantity,
                           @JsonProperty("itemDetails") final String itemDetails,
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
        this.productName = productName;
        this.planName = planName;
        this.phaseName = phaseName;
        this.usageName = usageName;
        this.prettyProductName = prettyProductName;
        this.prettyPlanName = prettyPlanName;
        this.prettyPhaseName = prettyPhaseName;
        this.prettyUsageName = prettyUsageName;
        this.itemType = itemType;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.rate = rate;
        this.currency = currency;
        this.childItems = childItems;
        this.quantity = quantity;
        this.itemDetails = itemDetails;
    }

    public InvoiceItemJson(final InvoiceItem item, final List<InvoiceItem> childItems, @Nullable final List<AuditLog> auditLogs) {
        this(item.getId(), item.getInvoiceId(), item.getLinkedItemId(),
             item.getAccountId(), item.getChildAccountId(), item.getBundleId(), item.getSubscriptionId(),
             item.getProductName(), item.getPlanName(), item.getPhaseName(), item.getUsageName(),
             item.getPrettyProductName(), item.getPrettyPlanName(), item.getPrettyPhaseName(), item.getPrettyUsageName(),
             item.getInvoiceItemType(),
             item.getDescription(), item.getStartDate(), item.getEndDate(),
             item.getAmount(), item.getRate(), item.getCurrency(),
             item.getQuantity(), item.getItemDetails(), toInvoiceItemJson(childItems), toAuditLogJson(auditLogs));
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
                return itemType;
            }

            @Override
            public UUID getInvoiceId() {
                return invoiceId;
            }

            @Override
            public UUID getAccountId() {
                return accountId;
            }

            @Override
            public UUID getChildAccountId() {
                return childAccountId;
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
                return currency;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public UUID getBundleId() {
                return bundleId;
            }

            @Override
            public UUID getSubscriptionId() {
                return subscriptionId;
            }

            @Override
            public String getProductName() {
                return null;
            }

            @Override
            public String getPrettyProductName() {
                return null;
            }

            @Override
            public String getPlanName() {
                return planName;
            }

            @Override
            public String getPrettyPlanName() {
                return prettyPlanName;
            }

            @Override
            public String getPhaseName() {
                return phaseName;
            }

            @Override
            public String getPrettyPhaseName() {
                return prettyPhaseName;
            }

            @Override
            public String getUsageName() {
                return usageName;
            }

            @Override
            public String getPrettyUsageName() {
                return prettyUsageName;
            }

            @Override
            public BigDecimal getRate() {
                return rate;
            }

            @Override
            public UUID getLinkedItemId() {
                return linkedInvoiceItemId;
            }

            @Override
            public Integer getQuantity() { return quantity; }

            @Override
            public String getItemDetails() { return itemDetails; }

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

    public UUID getInvoiceItemId() {
        return invoiceItemId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getLinkedInvoiceItemId() {
        return linkedInvoiceItemId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getChildAccountId() {
        return childAccountId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getProductName() {
        return productName;
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

    public String getPrettyProductName() {
        return prettyProductName;
    }

    public String getPrettyPlanName() {
        return prettyPlanName;
    }

    public String getPrettyPhaseName() {
        return prettyPhaseName;
    }

    public String getPrettyUsageName() {
        return prettyUsageName;
    }

    public InvoiceItemType getItemType() {
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

    public BigDecimal getRate() { return rate; }

    public Currency getCurrency() {
        return currency;
    }

    public List<InvoiceItemJson> getChildItems() {
        return childItems;
    }

    public Integer getQuantity() { return quantity; }

    public String getItemDetails() { return itemDetails; }

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
        sb.append(", productName='").append(productName).append('\'');
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", phaseName='").append(phaseName).append('\'');
        sb.append(", usageName='").append(usageName).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", amount=").append(amount);
        sb.append(", rate=").append(rate);
        sb.append(", currency=").append(currency);
        sb.append(", quantity=").append(quantity);
        sb.append(", itemDetails=").append(itemDetails);
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
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
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
        if (quantity != null ? !quantity.equals(that.quantity) : that.quantity != null) {
            return false;
        }
        if (itemDetails != null ? !itemDetails.equals(that.itemDetails) : that.itemDetails != null) {
            return false;
        }
        if (rate != null ? rate.compareTo(that.rate) != 0 : that.rate != null) {
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
        result = 31 * result + (productName != null ? productName.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (usageName != null ? usageName.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (rate != null ? rate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (quantity != null ? quantity.hashCode() : 0);
        result = 31 * result + (itemDetails != null ? itemDetails.hashCode() : 0);
        result = 31 * result + (childItems != null ? childItems.hashCode() : 0);
        return result;
    }
}
