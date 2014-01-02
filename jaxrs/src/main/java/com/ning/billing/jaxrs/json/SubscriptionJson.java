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

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.Subscription;
import com.ning.billing.entitlement.api.SubscriptionEvent;
import com.ning.billing.util.audit.AccountAuditLogs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscriptionJson extends JsonBase {

    private final String accountId;
    private final String bundleId;
    private final String subscriptionId;
    private final String externalKey;
    private final LocalDate startDate;
    private final String productName;
    private final String productCategory;
    private final String billingPeriod;
    private final String priceList;
    private final LocalDate cancelledDate;
    private final LocalDate chargedThroughDate;
    private final LocalDate billingStartDate;
    private final LocalDate billingEndDate;
    private final List<EventSubscriptionJson> events;
    private final List<DeletedEventSubscriptionJson> deletedEvents;
    private final List<NewEventSubscriptionJson> newEvents;

    public static class EventSubscriptionJson extends EventBaseSubscriptionJson {

        private final String eventId;
        private final LocalDate effectiveDate;

        @JsonCreator
        public EventSubscriptionJson(@JsonProperty("eventId") final String eventId,
                                     @JsonProperty("billingPeriod") final String billingPeriod,
                                     @JsonProperty("requestedDt") final LocalDate requestedDate,
                                     @JsonProperty("effectiveDt") final LocalDate effectiveDate,
                                     @JsonProperty("product") final String product,
                                     @JsonProperty("priceList") final String priceList,
                                     @JsonProperty("eventType") final String eventType,
                                     @JsonProperty("phase") final String phase,
                                     @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(billingPeriod, requestedDate, product, priceList, eventType, phase, auditLogs);
            this.eventId = eventId;
            this.effectiveDate = effectiveDate;
        }

        public String getEventId() {
            return eventId;
        }

        public LocalDate getEffectiveDate() {
            return effectiveDate;
        }

        @Override
        public String toString() {
            return "EventSubscriptionJson [eventId=" + eventId
                   + ", effectiveDate=" + effectiveDate
                   + ", getBillingPeriod()=" + getBillingPeriod()
                   + ", getRequestedDate()=" + getRequestedDate()
                   + ", getProduct()=" + getProduct() + ", getPriceList()="
                   + getPriceList() + ", getEventType()=" + getEventType()
                   + ", getPhase()=" + getPhase() + ", getClass()="
                   + getClass() + ", hashCode()=" + hashCode()
                   + ", toString()=" + super.toString() + "]";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final EventSubscriptionJson that = (EventSubscriptionJson) o;

            if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
                return false;
            }
            if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = eventId != null ? eventId.hashCode() : 0;
            result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
            return result;
        }
    }

    public static class DeletedEventSubscriptionJson extends EventSubscriptionJson {

        @JsonCreator
        public DeletedEventSubscriptionJson(@JsonProperty("eventId") final String eventId,
                                            @JsonProperty("billingPeriod") final String billingPeriod,
                                            @JsonProperty("requestedDate") final LocalDate requestedDate,
                                            @JsonProperty("effectiveDate") final LocalDate effectiveDate,
                                            @JsonProperty("product") final String product,
                                            @JsonProperty("priceList") final String priceList,
                                            @JsonProperty("eventType") final String eventType,
                                            @JsonProperty("phase") final String phase,
                                            @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(eventId, billingPeriod, requestedDate, effectiveDate, product, priceList, eventType, phase, auditLogs);
        }
    }

    public static class NewEventSubscriptionJson extends EventBaseSubscriptionJson {

        @JsonCreator
        public NewEventSubscriptionJson(@JsonProperty("billingPeriod") final String billingPeriod,
                                        @JsonProperty("requestedDate") final LocalDate requestedDate,
                                        @JsonProperty("product") final String product,
                                        @JsonProperty("priceList") final String priceList,
                                        @JsonProperty("eventType") final String eventType,
                                        @JsonProperty("phase") final String phase,
                                        @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(billingPeriod, requestedDate, product, priceList, eventType, phase, auditLogs);
        }

        @Override
        public String toString() {
            return "NewEventSubscriptionJson [getBillingPeriod()="
                   + getBillingPeriod() + ", getRequestedDate()="
                   + getRequestedDate() + ", getProduct()=" + getProduct()
                   + ", getPriceList()=" + getPriceList()
                   + ", getEventType()=" + getEventType() + ", getPhase()="
                   + getPhase() + ", getClass()=" + getClass()
                   + ", hashCode()=" + hashCode() + ", toString()="
                   + super.toString() + "]";
        }
    }

    public abstract static class EventBaseSubscriptionJson extends JsonBase {

        private final String billingPeriod;
        private final LocalDate requestedDate;
        private final String product;
        private final String priceList;
        private final String eventType;
        private final String phase;

        @JsonCreator
        public EventBaseSubscriptionJson(@JsonProperty("billingPeriod") final String billingPeriod,
                                         @JsonProperty("requestedDate") final LocalDate requestedDate,
                                         @JsonProperty("product") final String product,
                                         @JsonProperty("priceList") final String priceList,
                                         @JsonProperty("eventType") final String eventType,
                                         @JsonProperty("phase") final String phase,
                                         @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(auditLogs);
            this.billingPeriod = billingPeriod;
            this.requestedDate = requestedDate;
            this.product = product;
            this.priceList = priceList;
            this.eventType = eventType;
            this.phase = phase;
        }

        public String getBillingPeriod() {
            return billingPeriod;
        }

        public LocalDate getRequestedDate() {
            return requestedDate;
        }

        public String getProduct() {
            return product;
        }

        public String getPriceList() {
            return priceList;
        }

        public String getEventType() {
            return eventType;
        }

        public String getPhase() {
            return phase;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("EventBaseSubscriptionJson");
            sb.append("{billingPeriod='").append(billingPeriod).append('\'');
            sb.append(", requestedDate=").append(requestedDate);
            sb.append(", product='").append(product).append('\'');
            sb.append(", priceList='").append(priceList).append('\'');
            sb.append(", eventType='").append(eventType).append('\'');
            sb.append(", phase='").append(phase).append('\'');
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

            final EventBaseSubscriptionJson that = (EventBaseSubscriptionJson) o;

            if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
                return false;
            }
            if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) {
                return false;
            }
            if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
                return false;
            }
            if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
                return false;
            }
            if (product != null ? !product.equals(that.product) : that.product != null) {
                return false;
            }
            if (requestedDate != null ? !requestedDate.equals(that.requestedDate) : that.requestedDate != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = billingPeriod != null ? billingPeriod.hashCode() : 0;
            result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
            result = 31 * result + (product != null ? product.hashCode() : 0);
            result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
            result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
            result = 31 * result + (phase != null ? phase.hashCode() : 0);
            return result;
        }
    }

    @JsonCreator
    public SubscriptionJson(@JsonProperty("accountId") @Nullable final String accountId,
                            @JsonProperty("bundleId") @Nullable final String bundleId,
                            @JsonProperty("subscriptionId") @Nullable final String subscriptionId,
                            @JsonProperty("externalKey") @Nullable final String externalKey,
                            @JsonProperty("startDate") @Nullable final LocalDate startDate,
                            @JsonProperty("productName") @Nullable final String productName,
                            @JsonProperty("productCategory") @Nullable final String productCategory,
                            @JsonProperty("billingPeriod") @Nullable final String billingPeriod,
                            @JsonProperty("priceList") @Nullable final String priceList,
                            @JsonProperty("cancelledDate") @Nullable final LocalDate cancelledDate,
                            @JsonProperty("chargedThroughDate") @Nullable final LocalDate chargedThroughDate,
                            @JsonProperty("billingStartDate") @Nullable final LocalDate billingStartDate,
                            @JsonProperty("billingEndDate") @Nullable final LocalDate billingEndDate,
                            @JsonProperty("events") @Nullable final List<EventSubscriptionJson> events,
                            @JsonProperty("newEvents") @Nullable final List<NewEventSubscriptionJson> newEvents,
                            @JsonProperty("deletedEvents") @Nullable final List<DeletedEventSubscriptionJson> deletedEvents,
                            @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.startDate = startDate;
        this.productName = productName;
        this.productCategory = productCategory;
        this.billingPeriod = billingPeriod;
        this.priceList = priceList;
        this.cancelledDate = cancelledDate;
        this.chargedThroughDate = chargedThroughDate;
        this.billingStartDate = billingStartDate;
        this.billingEndDate = billingEndDate;
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.subscriptionId = subscriptionId;
        this.externalKey = externalKey;
        this.events = events;
        this.deletedEvents = deletedEvents;
        this.newEvents = newEvents;
    }

    public SubscriptionJson(final Subscription subscription,
                            final List<SubscriptionEvent> subscriptionEvents,
                            @Nullable final AccountAuditLogs accountAuditLogs) {
        super(toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForSubscription(subscription.getId())));
        this.startDate = subscription.getEffectiveStartDate();
        this.productName = subscription.getLastActiveProduct().getName();
        this.productCategory = subscription.getLastActiveProductCategory().name();
        this.billingPeriod = subscription.getLastActivePlan().getBillingPeriod().toString();
        this.priceList = subscription.getLastActivePriceList().getName();
        this.cancelledDate = subscription.getEffectiveEndDate();
        this.chargedThroughDate = subscription.getChargedThroughDate();
        this.billingStartDate = subscription.getBillingStartDate();
        this.billingEndDate = subscription.getBillingEndDate();
        this.accountId = subscription.getAccountId().toString();
        this.bundleId = subscription.getBundleId().toString();
        this.subscriptionId = subscription.getId().toString();
        this.externalKey = subscription.getExternalKey();
        this.events = subscriptionEvents != null ? new LinkedList<EventSubscriptionJson>() : null;
        if (events != null) {
            for (final SubscriptionEvent cur : subscriptionEvents) {
                final BillingPeriod billingPeriod = cur.getNextBillingPeriod() != null ? cur.getNextBillingPeriod() : cur.getPrevBillingPeriod();
                final Product product = cur.getNextProduct() != null ? cur.getNextProduct() : cur.getPrevProduct();
                final PriceList priceList = cur.getNextPriceList() != null ? cur.getNextPriceList() : cur.getPrevPriceList();
                final PlanPhase phase = cur.getNextPhase() != null ? cur.getNextPhase() : cur.getPrevPhase();
                this.events.add(new EventSubscriptionJson(cur.getId().toString(),
                                                          billingPeriod != null ? billingPeriod.toString() : null,
                                                          cur.getRequestedDate(),
                                                          cur.getEffectiveDate(),
                                                          product != null ? product.getName() : null,
                                                          priceList != null ? priceList.getName() : null,
                                                          cur.getSubscriptionEventType().toString(),
                                                          phase != null ? phase.getName() : null,
                                                          toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForSubscriptionEvent(cur.getId()))));
            }
        }
        this.newEvents = null;
        this.deletedEvents = null;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductCategory() {
        return productCategory;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public String getPriceList() {
        return priceList;
    }

    public LocalDate getCancelledDate() {
        return cancelledDate;
    }

    public LocalDate getChargedThroughDate() {
        return chargedThroughDate;
    }

    public LocalDate getBillingStartDate() {
        return billingStartDate;
    }

    public LocalDate getBillingEndDate() {
        return billingEndDate;
    }

    public List<EventSubscriptionJson> getEvents() {
        return events;
    }

    public List<DeletedEventSubscriptionJson> getDeletedEvents() {
        return deletedEvents;
    }

    public List<NewEventSubscriptionJson> getNewEvents() {
        return newEvents;
    }

    @Override
    public String toString() {
        return "SubscriptionJson{" +
               "accountId='" + accountId + '\'' +
               ", bundleId='" + bundleId + '\'' +
               ", subscriptionId='" + subscriptionId + '\'' +
               ", externalKey='" + externalKey + '\'' +
               ", startDate=" + startDate +
               ", productName='" + productName + '\'' +
               ", productCategory='" + productCategory + '\'' +
               ", billingPeriod='" + billingPeriod + '\'' +
               ", priceList='" + priceList + '\'' +
               ", cancelledDate=" + cancelledDate +
               ", chargedThroughDate=" + chargedThroughDate +
               ", billingStartDate=" + billingStartDate +
               ", billingEndDate=" + billingEndDate +
               ", events=" + events +
               ", deletedEvents=" + deletedEvents +
               ", newEvents=" + newEvents +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SubscriptionJson that = (SubscriptionJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (billingEndDate != null ? billingEndDate.compareTo(that.billingEndDate) != 0 : that.billingEndDate != null) {
            return false;
        }
        if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
            return false;
        }
        if (billingStartDate != null ? billingStartDate.compareTo(that.billingStartDate) != 0 : that.billingStartDate != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (cancelledDate != null ? cancelledDate.compareTo(that.cancelledDate) != 0 : that.cancelledDate != null) {
            return false;
        }
        if (chargedThroughDate != null ? chargedThroughDate.compareTo(that.chargedThroughDate) != 0 : that.chargedThroughDate != null) {
            return false;
        }
        if (deletedEvents != null ? !deletedEvents.equals(that.deletedEvents) : that.deletedEvents != null) {
            return false;
        }
        if (events != null ? !events.equals(that.events) : that.events != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (newEvents != null ? !newEvents.equals(that.newEvents) : that.newEvents != null) {
            return false;
        }
        if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
            return false;
        }
        if (productCategory != null ? !productCategory.equals(that.productCategory) : that.productCategory != null) {
            return false;
        }
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }
        if (startDate != null ? startDate.compareTo(that.startDate) != 0 : that.startDate != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (productName != null ? productName.hashCode() : 0);
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        result = 31 * result + (cancelledDate != null ? cancelledDate.hashCode() : 0);
        result = 31 * result + (chargedThroughDate != null ? chargedThroughDate.hashCode() : 0);
        result = 31 * result + (billingStartDate != null ? billingStartDate.hashCode() : 0);
        result = 31 * result + (billingEndDate != null ? billingEndDate.hashCode() : 0);
        result = 31 * result + (events != null ? events.hashCode() : 0);
        result = 31 * result + (deletedEvents != null ? deletedEvents.hashCode() : 0);
        result = 31 * result + (newEvents != null ? newEvents.hashCode() : 0);
        return result;
    }
}
