/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;

public class DefaultInvoiceItem implements InvoiceItem {

    protected UUID accountId;
    protected BigDecimal amount;
    protected UUID bundleId;
    protected DateTime catalogEffectiveDate;
    protected UUID childAccountId;
    protected DateTime createdDate;
    protected Currency currency;
    protected String description;
    protected LocalDate endDate;
    protected UUID id;
    protected UUID invoiceId;
    protected InvoiceItemType invoiceItemType;
    protected String itemDetails;
    protected UUID linkedItemId;
    protected String phaseName;
    protected String planName;
    protected String prettyPhaseName;
    protected String prettyPlanName;
    protected String prettyProductName;
    protected String prettyUsageName;
    protected String productName;
    protected BigDecimal quantity;
    protected BigDecimal rate;
    protected LocalDate startDate;
    protected UUID subscriptionId;
    protected DateTime updatedDate;
    protected String usageName;

    public DefaultInvoiceItem(final DefaultInvoiceItem that) {
        this.accountId = that.accountId;
        this.amount = that.amount;
        this.bundleId = that.bundleId;
        this.catalogEffectiveDate = that.catalogEffectiveDate;
        this.childAccountId = that.childAccountId;
        this.createdDate = that.createdDate;
        this.currency = that.currency;
        this.description = that.description;
        this.endDate = that.endDate;
        this.id = that.id;
        this.invoiceId = that.invoiceId;
        this.invoiceItemType = that.invoiceItemType;
        this.itemDetails = that.itemDetails;
        this.linkedItemId = that.linkedItemId;
        this.phaseName = that.phaseName;
        this.planName = that.planName;
        this.prettyPhaseName = that.prettyPhaseName;
        this.prettyPlanName = that.prettyPlanName;
        this.prettyProductName = that.prettyProductName;
        this.prettyUsageName = that.prettyUsageName;
        this.productName = that.productName;
        this.quantity = that.quantity;
        this.rate = that.rate;
        this.startDate = that.startDate;
        this.subscriptionId = that.subscriptionId;
        this.updatedDate = that.updatedDate;
        this.usageName = that.usageName;
    }
    protected DefaultInvoiceItem(final DefaultInvoiceItem.Builder<?> builder) {
        this.accountId = builder.accountId;
        this.amount = builder.amount;
        this.bundleId = builder.bundleId;
        this.catalogEffectiveDate = builder.catalogEffectiveDate;
        this.childAccountId = builder.childAccountId;
        this.createdDate = builder.createdDate;
        this.currency = builder.currency;
        this.description = builder.description;
        this.endDate = builder.endDate;
        this.id = builder.id;
        this.invoiceId = builder.invoiceId;
        this.invoiceItemType = builder.invoiceItemType;
        this.itemDetails = builder.itemDetails;
        this.linkedItemId = builder.linkedItemId;
        this.phaseName = builder.phaseName;
        this.planName = builder.planName;
        this.prettyPhaseName = builder.prettyPhaseName;
        this.prettyPlanName = builder.prettyPlanName;
        this.prettyProductName = builder.prettyProductName;
        this.prettyUsageName = builder.prettyUsageName;
        this.productName = builder.productName;
        this.quantity = builder.quantity;
        this.rate = builder.rate;
        this.startDate = builder.startDate;
        this.subscriptionId = builder.subscriptionId;
        this.updatedDate = builder.updatedDate;
        this.usageName = builder.usageName;
    }
    protected DefaultInvoiceItem() { }
    @Override
    public UUID getAccountId() {
        return this.accountId;
    }
    @Override
    public BigDecimal getAmount() {
        return this.amount;
    }
    @Override
    public UUID getBundleId() {
        return this.bundleId;
    }
    @Override
    public DateTime getCatalogEffectiveDate() {
        return this.catalogEffectiveDate;
    }
    @Override
    public UUID getChildAccountId() {
        return this.childAccountId;
    }
    @Override
    public DateTime getCreatedDate() {
        return this.createdDate;
    }
    @Override
    public Currency getCurrency() {
        return this.currency;
    }
    @Override
    public String getDescription() {
        return this.description;
    }
    @Override
    public LocalDate getEndDate() {
        return this.endDate;
    }
    @Override
    public UUID getId() {
        return this.id;
    }
    @Override
    public UUID getInvoiceId() {
        return this.invoiceId;
    }
    @Override
    public InvoiceItemType getInvoiceItemType() {
        return this.invoiceItemType;
    }
    @Override
    public String getItemDetails() {
        return this.itemDetails;
    }
    @Override
    public UUID getLinkedItemId() {
        return this.linkedItemId;
    }
    @Override
    public String getPhaseName() {
        return this.phaseName;
    }
    @Override
    public String getPlanName() {
        return this.planName;
    }
    @Override
    public String getPrettyPhaseName() {
        return this.prettyPhaseName;
    }
    @Override
    public String getPrettyPlanName() {
        return this.prettyPlanName;
    }
    @Override
    public String getPrettyProductName() {
        return this.prettyProductName;
    }
    @Override
    public String getPrettyUsageName() {
        return this.prettyUsageName;
    }
    @Override
    public String getProductName() {
        return this.productName;
    }
    @Override
    public BigDecimal getQuantity() {
        return this.quantity;
    }
    @Override
    public BigDecimal getRate() {
        return this.rate;
    }
    @Override
    public LocalDate getStartDate() {
        return this.startDate;
    }
    @Override
    public UUID getSubscriptionId() {
        return this.subscriptionId;
    }
    @Override
    public DateTime getUpdatedDate() {
        return this.updatedDate;
    }
    @Override
    public String getUsageName() {
        return this.usageName;
    }
    @Override
    public boolean matches(final Object other) {
        throw new UnsupportedOperationException("matches(java.lang.Object) must be implemented.");
    }
    @Override
    public boolean equals(final Object o) {
        if ( this == o ) {
            return true;
        }
        if ( ( o == null ) || ( this.getClass() != o.getClass() ) ) {
            return false;
        }
        final DefaultInvoiceItem that = (DefaultInvoiceItem) o;
        if( !Objects.equals(this.accountId, that.accountId) ) {
            return false;
        }
        if( ( this.amount != null ) ? ( 0 != this.amount.compareTo(that.amount) ) : ( that.amount != null ) ) {
            return false;
        }
        if( !Objects.equals(this.bundleId, that.bundleId) ) {
            return false;
        }
        if( ( this.catalogEffectiveDate != null ) ? ( 0 != this.catalogEffectiveDate.compareTo(that.catalogEffectiveDate) ) : ( that.catalogEffectiveDate != null ) ) {
            return false;
        }
        if( !Objects.equals(this.childAccountId, that.childAccountId) ) {
            return false;
        }
        if( ( this.createdDate != null ) ? ( 0 != this.createdDate.compareTo(that.createdDate) ) : ( that.createdDate != null ) ) {
            return false;
        }
        if( !Objects.equals(this.currency, that.currency) ) {
            return false;
        }
        if( !Objects.equals(this.description, that.description) ) {
            return false;
        }
        if( ( this.endDate != null ) ? ( 0 != this.endDate.compareTo(that.endDate) ) : ( that.endDate != null ) ) {
            return false;
        }
        if( !Objects.equals(this.id, that.id) ) {
            return false;
        }
        if( !Objects.equals(this.invoiceId, that.invoiceId) ) {
            return false;
        }
        if( !Objects.equals(this.invoiceItemType, that.invoiceItemType) ) {
            return false;
        }
        if( !Objects.equals(this.itemDetails, that.itemDetails) ) {
            return false;
        }
        if( !Objects.equals(this.linkedItemId, that.linkedItemId) ) {
            return false;
        }
        if( !Objects.equals(this.phaseName, that.phaseName) ) {
            return false;
        }
        if( !Objects.equals(this.planName, that.planName) ) {
            return false;
        }
        if( !Objects.equals(this.prettyPhaseName, that.prettyPhaseName) ) {
            return false;
        }
        if( !Objects.equals(this.prettyPlanName, that.prettyPlanName) ) {
            return false;
        }
        if( !Objects.equals(this.prettyProductName, that.prettyProductName) ) {
            return false;
        }
        if( !Objects.equals(this.prettyUsageName, that.prettyUsageName) ) {
            return false;
        }
        if( !Objects.equals(this.productName, that.productName) ) {
            return false;
        }
        if( !Objects.equals(this.quantity, that.quantity) ) {
            return false;
        }
        if( ( this.rate != null ) ? ( 0 != this.rate.compareTo(that.rate) ) : ( that.rate != null ) ) {
            return false;
        }
        if( ( this.startDate != null ) ? ( 0 != this.startDate.compareTo(that.startDate) ) : ( that.startDate != null ) ) {
            return false;
        }
        if( !Objects.equals(this.subscriptionId, that.subscriptionId) ) {
            return false;
        }
        if( ( this.updatedDate != null ) ? ( 0 != this.updatedDate.compareTo(that.updatedDate) ) : ( that.updatedDate != null ) ) {
            return false;
        }
        if( !Objects.equals(this.usageName, that.usageName) ) {
            return false;
        }
        return true;
    }
    @Override
    public int hashCode() {
        int result = 1;
        result = ( 31 * result ) + Objects.hashCode(this.accountId);
        result = ( 31 * result ) + Objects.hashCode(this.amount);
        result = ( 31 * result ) + Objects.hashCode(this.bundleId);
        result = ( 31 * result ) + Objects.hashCode(this.catalogEffectiveDate);
        result = ( 31 * result ) + Objects.hashCode(this.childAccountId);
        result = ( 31 * result ) + Objects.hashCode(this.createdDate);
        result = ( 31 * result ) + Objects.hashCode(this.currency);
        result = ( 31 * result ) + Objects.hashCode(this.description);
        result = ( 31 * result ) + Objects.hashCode(this.endDate);
        result = ( 31 * result ) + Objects.hashCode(this.id);
        result = ( 31 * result ) + Objects.hashCode(this.invoiceId);
        result = ( 31 * result ) + Objects.hashCode(this.invoiceItemType);
        result = ( 31 * result ) + Objects.hashCode(this.itemDetails);
        result = ( 31 * result ) + Objects.hashCode(this.linkedItemId);
        result = ( 31 * result ) + Objects.hashCode(this.phaseName);
        result = ( 31 * result ) + Objects.hashCode(this.planName);
        result = ( 31 * result ) + Objects.hashCode(this.prettyPhaseName);
        result = ( 31 * result ) + Objects.hashCode(this.prettyPlanName);
        result = ( 31 * result ) + Objects.hashCode(this.prettyProductName);
        result = ( 31 * result ) + Objects.hashCode(this.prettyUsageName);
        result = ( 31 * result ) + Objects.hashCode(this.productName);
        result = ( 31 * result ) + Objects.hashCode(this.quantity);
        result = ( 31 * result ) + Objects.hashCode(this.rate);
        result = ( 31 * result ) + Objects.hashCode(this.startDate);
        result = ( 31 * result ) + Objects.hashCode(this.subscriptionId);
        result = ( 31 * result ) + Objects.hashCode(this.updatedDate);
        result = ( 31 * result ) + Objects.hashCode(this.usageName);
        return result;
    }
    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer(this.getClass().getSimpleName());
        sb.append("{");
        sb.append("accountId=").append(this.accountId);
        sb.append(", ");
        sb.append("amount=").append(this.amount);
        sb.append(", ");
        sb.append("bundleId=").append(this.bundleId);
        sb.append(", ");
        sb.append("catalogEffectiveDate=").append(this.catalogEffectiveDate);
        sb.append(", ");
        sb.append("childAccountId=").append(this.childAccountId);
        sb.append(", ");
        sb.append("createdDate=").append(this.createdDate);
        sb.append(", ");
        sb.append("currency=").append(this.currency);
        sb.append(", ");
        sb.append("description=");
        if( this.description == null ) {
            sb.append(this.description);
        }else{
            sb.append("'").append(this.description).append("'");
        }
        sb.append(", ");
        sb.append("endDate=").append(this.endDate);
        sb.append(", ");
        sb.append("id=").append(this.id);
        sb.append(", ");
        sb.append("invoiceId=").append(this.invoiceId);
        sb.append(", ");
        sb.append("invoiceItemType=").append(this.invoiceItemType);
        sb.append(", ");
        sb.append("itemDetails=");
        if( this.itemDetails == null ) {
            sb.append(this.itemDetails);
        }else{
            sb.append("'").append(this.itemDetails).append("'");
        }
        sb.append(", ");
        sb.append("linkedItemId=").append(this.linkedItemId);
        sb.append(", ");
        sb.append("phaseName=");
        if( this.phaseName == null ) {
            sb.append(this.phaseName);
        }else{
            sb.append("'").append(this.phaseName).append("'");
        }
        sb.append(", ");
        sb.append("planName=");
        if( this.planName == null ) {
            sb.append(this.planName);
        }else{
            sb.append("'").append(this.planName).append("'");
        }
        sb.append(", ");
        sb.append("prettyPhaseName=");
        if( this.prettyPhaseName == null ) {
            sb.append(this.prettyPhaseName);
        }else{
            sb.append("'").append(this.prettyPhaseName).append("'");
        }
        sb.append(", ");
        sb.append("prettyPlanName=");
        if( this.prettyPlanName == null ) {
            sb.append(this.prettyPlanName);
        }else{
            sb.append("'").append(this.prettyPlanName).append("'");
        }
        sb.append(", ");
        sb.append("prettyProductName=");
        if( this.prettyProductName == null ) {
            sb.append(this.prettyProductName);
        }else{
            sb.append("'").append(this.prettyProductName).append("'");
        }
        sb.append(", ");
        sb.append("prettyUsageName=");
        if( this.prettyUsageName == null ) {
            sb.append(this.prettyUsageName);
        }else{
            sb.append("'").append(this.prettyUsageName).append("'");
        }
        sb.append(", ");
        sb.append("productName=");
        if( this.productName == null ) {
            sb.append(this.productName);
        }else{
            sb.append("'").append(this.productName).append("'");
        }
        sb.append(", ");
        sb.append("quantity=").append(this.quantity);
        sb.append(", ");
        sb.append("rate=").append(this.rate);
        sb.append(", ");
        sb.append("startDate=").append(this.startDate);
        sb.append(", ");
        sb.append("subscriptionId=").append(this.subscriptionId);
        sb.append(", ");
        sb.append("updatedDate=").append(this.updatedDate);
        sb.append(", ");
        sb.append("usageName=");
        if( this.usageName == null ) {
            sb.append(this.usageName);
        }else{
            sb.append("'").append(this.usageName).append("'");
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static class Builder<T extends DefaultInvoiceItem.Builder<T>> {

        protected UUID accountId;
        protected BigDecimal amount;
        protected UUID bundleId;
        protected DateTime catalogEffectiveDate;
        protected UUID childAccountId;
        protected DateTime createdDate;
        protected Currency currency;
        protected String description;
        protected LocalDate endDate;
        protected UUID id;
        protected UUID invoiceId;
        protected InvoiceItemType invoiceItemType;
        protected String itemDetails;
        protected UUID linkedItemId;
        protected String phaseName;
        protected String planName;
        protected String prettyPhaseName;
        protected String prettyPlanName;
        protected String prettyProductName;
        protected String prettyUsageName;
        protected String productName;
        protected BigDecimal quantity;
        protected BigDecimal rate;
        protected LocalDate startDate;
        protected UUID subscriptionId;
        protected DateTime updatedDate;
        protected String usageName;

        public Builder() { }
        public Builder(final Builder that) {
            this.accountId = that.accountId;
            this.amount = that.amount;
            this.bundleId = that.bundleId;
            this.catalogEffectiveDate = that.catalogEffectiveDate;
            this.childAccountId = that.childAccountId;
            this.createdDate = that.createdDate;
            this.currency = that.currency;
            this.description = that.description;
            this.endDate = that.endDate;
            this.id = that.id;
            this.invoiceId = that.invoiceId;
            this.invoiceItemType = that.invoiceItemType;
            this.itemDetails = that.itemDetails;
            this.linkedItemId = that.linkedItemId;
            this.phaseName = that.phaseName;
            this.planName = that.planName;
            this.prettyPhaseName = that.prettyPhaseName;
            this.prettyPlanName = that.prettyPlanName;
            this.prettyProductName = that.prettyProductName;
            this.prettyUsageName = that.prettyUsageName;
            this.productName = that.productName;
            this.quantity = that.quantity;
            this.rate = that.rate;
            this.startDate = that.startDate;
            this.subscriptionId = that.subscriptionId;
            this.updatedDate = that.updatedDate;
            this.usageName = that.usageName;
        }
        public T withAccountId(final UUID accountId) {
            this.accountId = accountId;
            return (T) this;
        }
        public T withAmount(final BigDecimal amount) {
            this.amount = amount;
            return (T) this;
        }
        public T withBundleId(final UUID bundleId) {
            this.bundleId = bundleId;
            return (T) this;
        }
        public T withCatalogEffectiveDate(final DateTime catalogEffectiveDate) {
            this.catalogEffectiveDate = catalogEffectiveDate;
            return (T) this;
        }
        public T withChildAccountId(final UUID childAccountId) {
            this.childAccountId = childAccountId;
            return (T) this;
        }
        public T withCreatedDate(final DateTime createdDate) {
            this.createdDate = createdDate;
            return (T) this;
        }
        public T withCurrency(final Currency currency) {
            this.currency = currency;
            return (T) this;
        }
        public T withDescription(final String description) {
            this.description = description;
            return (T) this;
        }
        public T withEndDate(final LocalDate endDate) {
            this.endDate = endDate;
            return (T) this;
        }
        public T withId(final UUID id) {
            this.id = id;
            return (T) this;
        }
        public T withInvoiceId(final UUID invoiceId) {
            this.invoiceId = invoiceId;
            return (T) this;
        }
        public T withInvoiceItemType(final InvoiceItemType invoiceItemType) {
            this.invoiceItemType = invoiceItemType;
            return (T) this;
        }
        public T withItemDetails(final String itemDetails) {
            this.itemDetails = itemDetails;
            return (T) this;
        }
        public T withLinkedItemId(final UUID linkedItemId) {
            this.linkedItemId = linkedItemId;
            return (T) this;
        }
        public T withPhaseName(final String phaseName) {
            this.phaseName = phaseName;
            return (T) this;
        }
        public T withPlanName(final String planName) {
            this.planName = planName;
            return (T) this;
        }
        public T withPrettyPhaseName(final String prettyPhaseName) {
            this.prettyPhaseName = prettyPhaseName;
            return (T) this;
        }
        public T withPrettyPlanName(final String prettyPlanName) {
            this.prettyPlanName = prettyPlanName;
            return (T) this;
        }
        public T withPrettyProductName(final String prettyProductName) {
            this.prettyProductName = prettyProductName;
            return (T) this;
        }
        public T withPrettyUsageName(final String prettyUsageName) {
            this.prettyUsageName = prettyUsageName;
            return (T) this;
        }
        public T withProductName(final String productName) {
            this.productName = productName;
            return (T) this;
        }
        public T withQuantity(final BigDecimal quantity) {
            this.quantity = quantity;
            return (T) this;
        }
        public T withRate(final BigDecimal rate) {
            this.rate = rate;
            return (T) this;
        }
        public T withStartDate(final LocalDate startDate) {
            this.startDate = startDate;
            return (T) this;
        }
        public T withSubscriptionId(final UUID subscriptionId) {
            this.subscriptionId = subscriptionId;
            return (T) this;
        }
        public T withUpdatedDate(final DateTime updatedDate) {
            this.updatedDate = updatedDate;
            return (T) this;
        }
        public T withUsageName(final String usageName) {
            this.usageName = usageName;
            return (T) this;
        }
        public T source(final InvoiceItem that) {
            this.accountId = that.getAccountId();
            this.amount = that.getAmount();
            this.bundleId = that.getBundleId();
            this.catalogEffectiveDate = that.getCatalogEffectiveDate();
            this.childAccountId = that.getChildAccountId();
            this.createdDate = that.getCreatedDate();
            this.currency = that.getCurrency();
            this.description = that.getDescription();
            this.endDate = that.getEndDate();
            this.id = that.getId();
            this.invoiceId = that.getInvoiceId();
            this.invoiceItemType = that.getInvoiceItemType();
            this.itemDetails = that.getItemDetails();
            this.linkedItemId = that.getLinkedItemId();
            this.phaseName = that.getPhaseName();
            this.planName = that.getPlanName();
            this.prettyPhaseName = that.getPrettyPhaseName();
            this.prettyPlanName = that.getPrettyPlanName();
            this.prettyProductName = that.getPrettyProductName();
            this.prettyUsageName = that.getPrettyUsageName();
            this.productName = that.getProductName();
            this.quantity = that.getQuantity();
            this.rate = that.getRate();
            this.startDate = that.getStartDate();
            this.subscriptionId = that.getSubscriptionId();
            this.updatedDate = that.getUpdatedDate();
            this.usageName = that.getUsageName();
            return (T) this;
        }
        protected Builder validate() {
            return this;
        }
        public DefaultInvoiceItem build() {
            return new DefaultInvoiceItem(this.validate());
        }
    }

}
