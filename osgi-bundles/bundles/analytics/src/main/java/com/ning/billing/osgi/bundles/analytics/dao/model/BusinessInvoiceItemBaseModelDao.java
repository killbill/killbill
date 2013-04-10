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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.util.audit.AuditLog;

public abstract class BusinessInvoiceItemBaseModelDao extends BusinessModelDaoBase {

    protected static final String INVOICE_ADJUSTMENTS_TABLE_NAME = "bia";
    protected static final String INVOICE_ITEMS_TABLE_NAME = "bii";
    protected static final String INVOICE_ITEM_ADJUSTMENTS_TABLE_NAME = "biia";
    protected static final String ACCOUNT_CREDITS_TABLE_NAME = "biic";

    public static final String[] ALL_INVOICE_ITEMS_TABLE_NAMES = new String[]{INVOICE_ADJUSTMENTS_TABLE_NAME, INVOICE_ITEMS_TABLE_NAME, INVOICE_ITEM_ADJUSTMENTS_TABLE_NAME, ACCOUNT_CREDITS_TABLE_NAME};

    private Long invoiceItemRecordId;
    private Long secondInvoiceItemRecordId;
    private UUID itemId;
    private UUID invoiceId;
    private Integer invoiceNumber;
    private DateTime invoiceCreatedDate;
    private LocalDate invoiceDate;
    private LocalDate invoiceTargetDate;
    private String invoiceCurrency;
    private BigDecimal invoiceBalance;
    private BigDecimal invoiceAmountPaid;
    private BigDecimal invoiceAmountCharged;
    private BigDecimal invoiceOriginalAmountCharged;
    private BigDecimal invoiceAmountCredited;
    private String itemType;
    private Boolean revenueRecognizable;
    private String bundleExternalKey;
    private String productName;
    private String productType;
    private String productCategory;
    private String slug;
    private String phase;
    private String billingPeriod;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal amount;
    private String currency;
    private UUID linkedItemId;

    public static BusinessInvoiceItemBaseModelDao create(final Account account,
                                                         final Long accountRecordId,
                                                         final Invoice invoice,
                                                         final InvoiceItem invoiceItem,
                                                         final Long invoiceItemRecordId,
                                                         final Long secondInvoiceItemRecordId,
                                                         @Nullable final SubscriptionBundle bundle,
                                                         @Nullable final Plan plan,
                                                         @Nullable final PlanPhase planPhase,
                                                         final AuditLog creationAuditLog,
                                                         final Long tenantRecordId) {
        if (InvoiceItemType.REFUND_ADJ.equals(invoiceItem.getInvoiceItemType())) {
            return new BusinessInvoiceAdjustmentModelDao(account,
                                                         accountRecordId,
                                                         invoice,
                                                         invoiceItem,
                                                         invoiceItemRecordId,
                                                         secondInvoiceItemRecordId,
                                                         bundle,
                                                         plan,
                                                         planPhase,
                                                         creationAuditLog,
                                                         tenantRecordId);
        } else if (InvoiceItemType.EXTERNAL_CHARGE.equals(invoiceItem.getInvoiceItemType()) ||
                   InvoiceItemType.FIXED.equals(invoiceItem.getInvoiceItemType()) ||
                   InvoiceItemType.RECURRING.equals(invoiceItem.getInvoiceItemType())) {
            return new BusinessInvoiceItemModelDao(account,
                                                   accountRecordId,
                                                   invoice,
                                                   invoiceItem,
                                                   invoiceItemRecordId,
                                                   secondInvoiceItemRecordId,
                                                   bundle,
                                                   plan,
                                                   planPhase,
                                                   creationAuditLog,
                                                   tenantRecordId);
        } else if (InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType())) {
            return new BusinessInvoiceItemAdjustmentModelDao(account,
                                                             accountRecordId,
                                                             invoice,
                                                             invoiceItem,
                                                             invoiceItemRecordId,
                                                             secondInvoiceItemRecordId,
                                                             bundle,
                                                             plan,
                                                             planPhase,
                                                             creationAuditLog,
                                                             tenantRecordId);
        } else if (InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType()) ||
                   InvoiceItemType.CREDIT_ADJ.equals(invoiceItem.getInvoiceItemType())) {
            return new BusinessInvoiceItemCreditModelDao(account,
                                                         accountRecordId,
                                                         invoice,
                                                         invoiceItem,
                                                         invoiceItemRecordId,
                                                         secondInvoiceItemRecordId,
                                                         bundle,
                                                         plan,
                                                         planPhase,
                                                         creationAuditLog,
                                                         tenantRecordId);
        } else {
            // We don't care
            return null;
        }
    }

    public BusinessInvoiceItemBaseModelDao() { /* When reading from the database */ }

    public BusinessInvoiceItemBaseModelDao(final Long invoiceItemRecordId,
                                           final Long secondInvoiceItemRecordId,
                                           final UUID itemId,
                                           final UUID invoiceId,
                                           final Integer invoiceNumber,
                                           final DateTime invoiceCreatedDate,
                                           final LocalDate invoiceDate,
                                           final LocalDate invoiceTargetDate,
                                           final String invoiceCurrency,
                                           final BigDecimal invoiceBalance,
                                           final BigDecimal invoiceAmountPaid,
                                           final BigDecimal invoiceAmountCharged,
                                           final BigDecimal invoiceOriginalAmountCharged,
                                           final BigDecimal invoiceAmountCredited,
                                           final String itemType,
                                           final Boolean revenueRecognizable,
                                           final String bundleExternalKey,
                                           final String productName,
                                           final String productType,
                                           final String productCategory,
                                           final String slug,
                                           final String phase,
                                           final String billingPeriod,
                                           final LocalDate startDate,
                                           final LocalDate endDate,
                                           final BigDecimal amount,
                                           final String currency,
                                           final UUID linkedItemId,
                                           final DateTime createdDate,
                                           final String createdBy,
                                           final String createdReasonCode,
                                           final String createdComments,
                                           final UUID accountId,
                                           final String accountName,
                                           final String accountExternalKey,
                                           final Long accountRecordId,
                                           final Long tenantRecordId) {
        super(createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId);
        this.invoiceItemRecordId = invoiceItemRecordId;
        this.secondInvoiceItemRecordId = secondInvoiceItemRecordId;
        this.itemId = itemId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceCreatedDate = invoiceCreatedDate;
        this.invoiceDate = invoiceDate;
        this.invoiceTargetDate = invoiceTargetDate;
        this.invoiceCurrency = invoiceCurrency;
        this.invoiceBalance = invoiceBalance;
        this.invoiceAmountPaid = invoiceAmountPaid;
        this.invoiceAmountCharged = invoiceAmountCharged;
        this.invoiceOriginalAmountCharged = invoiceOriginalAmountCharged;
        this.invoiceAmountCredited = invoiceAmountCredited;
        this.itemType = itemType;
        this.revenueRecognizable = revenueRecognizable;
        this.bundleExternalKey = bundleExternalKey;
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

    public BusinessInvoiceItemBaseModelDao(final Account account,
                                           final Long accountRecordId,
                                           final Invoice invoice,
                                           final InvoiceItem invoiceItem,
                                           final Long invoiceItemRecordId,
                                           final Long secondInvoiceItemRecordId,
                                           @Nullable final SubscriptionBundle bundle,
                                           @Nullable final Plan plan,
                                           @Nullable final PlanPhase planPhase,
                                           final AuditLog creationAuditLog,
                                           final Long tenantRecordId) {
        this(invoiceItemRecordId,
             secondInvoiceItemRecordId,
             invoiceItem.getId(),
             invoice.getId(),
             invoice.getInvoiceNumber(),
             invoice.getCreatedDate(),
             invoice.getInvoiceDate(),
             invoice.getTargetDate(),
             invoice.getCurrency() == null ? null : invoice.getCurrency().toString(),
             invoice.getBalance(),
             invoice.getPaidAmount(),
             invoice.getChargedAmount(),
             invoice.getOriginalChargedAmount(),
             invoice.getCreditAdjAmount(),
             invoiceItem.getInvoiceItemType().toString(),
             null /* TODO */,
             bundle == null ? null : bundle.getExternalKey(),
             plan != null ? plan.getProduct().getName() : null,
             plan != null ? plan.getProduct().getCatalogName() : null,
             plan != null ? plan.getProduct().getCategory().toString() : null,
             planPhase != null ? planPhase.getName() : null,
             planPhase != null ? planPhase.getPhaseType().toString() : null,
             planPhase != null ? planPhase.getBillingPeriod().toString() : null,
             invoiceItem.getStartDate(),
             /* Populate end date for fixed items for convenience (null in invoice_items table) */
             (invoiceItem.getEndDate() == null && planPhase != null) ? invoiceItem.getStartDate().plus(planPhase.getDuration().toJodaPeriod()) : invoiceItem.getEndDate(),
             invoiceItem.getAmount(),
             invoiceItem.getCurrency() == null ? null : invoiceItem.getCurrency().toString(),
             invoiceItem.getLinkedItemId(),
             invoiceItem.getCreatedDate(),
             creationAuditLog.getUserName(),
             creationAuditLog.getReasonCode(),
             creationAuditLog.getComment(),
             account.getId(),
             account.getName(),
             account.getExternalKey(),
             accountRecordId,
             tenantRecordId);
    }

    public Long getInvoiceItemRecordId() {
        return invoiceItemRecordId;
    }

    public Long getSecondInvoiceItemRecordId() {
        return secondInvoiceItemRecordId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    public DateTime getInvoiceCreatedDate() {
        return invoiceCreatedDate;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getInvoiceTargetDate() {
        return invoiceTargetDate;
    }

    public String getInvoiceCurrency() {
        return invoiceCurrency;
    }

    public BigDecimal getInvoiceBalance() {
        return invoiceBalance;
    }

    public BigDecimal getInvoiceAmountPaid() {
        return invoiceAmountPaid;
    }

    public BigDecimal getInvoiceAmountCharged() {
        return invoiceAmountCharged;
    }

    public BigDecimal getInvoiceOriginalAmountCharged() {
        return invoiceOriginalAmountCharged;
    }

    public BigDecimal getInvoiceAmountCredited() {
        return invoiceAmountCredited;
    }

    public String getItemType() {
        return itemType;
    }

    public Boolean getRevenueRecognizable() {
        return revenueRecognizable;
    }

    public String getBundleExternalKey() {
        return bundleExternalKey;
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

    public UUID getLinkedItemId() {
        return linkedItemId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoiceItemBaseModelDao");
        sb.append("{invoiceItemRecordId=").append(invoiceItemRecordId);
        sb.append(", secondInvoiceItemRecordId=").append(secondInvoiceItemRecordId);
        sb.append(", itemId=").append(itemId);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", invoiceNumber=").append(invoiceNumber);
        sb.append(", invoiceCreatedDate=").append(invoiceCreatedDate);
        sb.append(", invoiceDate=").append(invoiceDate);
        sb.append(", invoiceTargetDate=").append(invoiceTargetDate);
        sb.append(", invoiceCurrency='").append(invoiceCurrency).append('\'');
        sb.append(", invoiceBalance=").append(invoiceBalance);
        sb.append(", invoiceAmountPaid=").append(invoiceAmountPaid);
        sb.append(", invoiceAmountCharged=").append(invoiceAmountCharged);
        sb.append(", invoiceOriginalAmountCharged=").append(invoiceOriginalAmountCharged);
        sb.append(", invoiceAmountCredited=").append(invoiceAmountCredited);
        sb.append(", itemType='").append(itemType).append('\'');
        sb.append(", revenueRecognizable=").append(revenueRecognizable);
        sb.append(", bundleExternalKey='").append(bundleExternalKey).append('\'');
        sb.append(", productName='").append(productName).append('\'');
        sb.append(", productType='").append(productType).append('\'');
        sb.append(", productCategory='").append(productCategory).append('\'');
        sb.append(", slug='").append(slug).append('\'');
        sb.append(", phase='").append(phase).append('\'');
        sb.append(", billingPeriod='").append(billingPeriod).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", linkedItemId=").append(linkedItemId);
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
        if (!super.equals(o)) {
            return false;
        }

        final BusinessInvoiceItemBaseModelDao that = (BusinessInvoiceItemBaseModelDao) o;

        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
            return false;
        }
        if (bundleExternalKey != null ? !bundleExternalKey.equals(that.bundleExternalKey) : that.bundleExternalKey != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (invoiceAmountCharged != null ? !invoiceAmountCharged.equals(that.invoiceAmountCharged) : that.invoiceAmountCharged != null) {
            return false;
        }
        if (invoiceAmountCredited != null ? !invoiceAmountCredited.equals(that.invoiceAmountCredited) : that.invoiceAmountCredited != null) {
            return false;
        }
        if (invoiceAmountPaid != null ? !invoiceAmountPaid.equals(that.invoiceAmountPaid) : that.invoiceAmountPaid != null) {
            return false;
        }
        if (invoiceBalance != null ? !invoiceBalance.equals(that.invoiceBalance) : that.invoiceBalance != null) {
            return false;
        }
        if (invoiceCreatedDate != null ? !invoiceCreatedDate.equals(that.invoiceCreatedDate) : that.invoiceCreatedDate != null) {
            return false;
        }
        if (invoiceCurrency != null ? !invoiceCurrency.equals(that.invoiceCurrency) : that.invoiceCurrency != null) {
            return false;
        }
        if (invoiceDate != null ? !invoiceDate.equals(that.invoiceDate) : that.invoiceDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceItemRecordId != null ? !invoiceItemRecordId.equals(that.invoiceItemRecordId) : that.invoiceItemRecordId != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (invoiceOriginalAmountCharged != null ? !invoiceOriginalAmountCharged.equals(that.invoiceOriginalAmountCharged) : that.invoiceOriginalAmountCharged != null) {
            return false;
        }
        if (invoiceTargetDate != null ? !invoiceTargetDate.equals(that.invoiceTargetDate) : that.invoiceTargetDate != null) {
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
        if (revenueRecognizable != null ? !revenueRecognizable.equals(that.revenueRecognizable) : that.revenueRecognizable != null) {
            return false;
        }
        if (secondInvoiceItemRecordId != null ? !secondInvoiceItemRecordId.equals(that.secondInvoiceItemRecordId) : that.secondInvoiceItemRecordId != null) {
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
        int result = super.hashCode();
        result = 31 * result + (invoiceItemRecordId != null ? invoiceItemRecordId.hashCode() : 0);
        result = 31 * result + (secondInvoiceItemRecordId != null ? secondInvoiceItemRecordId.hashCode() : 0);
        result = 31 * result + (itemId != null ? itemId.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (invoiceCreatedDate != null ? invoiceCreatedDate.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (invoiceTargetDate != null ? invoiceTargetDate.hashCode() : 0);
        result = 31 * result + (invoiceCurrency != null ? invoiceCurrency.hashCode() : 0);
        result = 31 * result + (invoiceBalance != null ? invoiceBalance.hashCode() : 0);
        result = 31 * result + (invoiceAmountPaid != null ? invoiceAmountPaid.hashCode() : 0);
        result = 31 * result + (invoiceAmountCharged != null ? invoiceAmountCharged.hashCode() : 0);
        result = 31 * result + (invoiceOriginalAmountCharged != null ? invoiceOriginalAmountCharged.hashCode() : 0);
        result = 31 * result + (invoiceAmountCredited != null ? invoiceAmountCredited.hashCode() : 0);
        result = 31 * result + (itemType != null ? itemType.hashCode() : 0);
        result = 31 * result + (revenueRecognizable != null ? revenueRecognizable.hashCode() : 0);
        result = 31 * result + (bundleExternalKey != null ? bundleExternalKey.hashCode() : 0);
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
