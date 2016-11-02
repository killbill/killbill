/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

public class SubscriptionJson extends JsonBase {

    @ApiModelProperty(dataType = "java.util.UUID")
    private final String accountId;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String bundleId;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String subscriptionId;
    private final String externalKey;
    private final LocalDate startDate;
    @ApiModelProperty(required = true)
    private final String productName;
    @ApiModelProperty(dataType = "org.killbill.billing.catalog.api.ProductCategory", required = true)
    private final String productCategory;
    @ApiModelProperty(dataType = "org.killbill.billing.catalog.api.BillingPeriod", required = true)
    private final String billingPeriod;
    @ApiModelProperty(dataType = "org.killbill.billing.catalog.api.PhaseType")
    private final String phaseType;
    @ApiModelProperty(required = true)
    private final String priceList;
    @ApiModelProperty(required = true)
    private final String planName;
    //@ApiModelProperty(dataType = "org.killbill.billing.entitlement.api.Entitlement.EntitlementState")
    @ApiModelProperty(dataType = "string", allowableValues = "PENDING,ACTIVE,BLOCKED,CANCELLED")
    private final String state;
    //@ApiModelProperty(dataType = "org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType")
    @ApiModelProperty(dataType = "string", allowableValues = "NATIVE,MIGRATED,TRANSFERRED")
    private final String sourceType;
    private final LocalDate cancelledDate;
    private final LocalDate chargedThroughDate;
    private final LocalDate billingStartDate;
    private final LocalDate billingEndDate;
    private final Integer billCycleDayLocal;
    private final List<EventSubscriptionJson> events;
    private final List<PhasePriceOverrideJson> priceOverrides;

    public static class EventSubscriptionJson extends JsonBase {

        private final String eventId;
        private final String billingPeriod;
        private final LocalDate effectiveDate;
        private final String product;
        private final String priceList;
        @ApiModelProperty(dataType = "org.killbill.billing.entitlement.api.SubscriptionEventType")
        private final String eventType;
        private final Boolean isBlockedBilling;
        private final Boolean isBlockedEntitlement;
        private final String serviceName;
        private final String serviceStateName;
        private final String phase;

        @JsonCreator
        public EventSubscriptionJson(@JsonProperty("eventId") final String eventId,
                                     @JsonProperty("billingPeriod") final String billingPeriod,
                                     @JsonProperty("effectiveDt") final LocalDate effectiveDate,
                                     @JsonProperty("product") final String product,
                                     @JsonProperty("priceList") final String priceList,
                                     @JsonProperty("eventType") final String eventType,
                                     @JsonProperty("isBlockedBilling") final Boolean isBlockedBilling,
                                     @JsonProperty("isBlockedEntitlement") final Boolean isBlockedEntitlement,
                                     @JsonProperty("serviceName") final String serviceName,
                                     @JsonProperty("serviceStateName") final String serviceStateName,
                                     @JsonProperty("phase") final String phase,
                                     @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(auditLogs);
            this.eventId = eventId;
            this.billingPeriod = billingPeriod;
            this.effectiveDate = effectiveDate;
            this.product = product;
            this.priceList = priceList;
            this.eventType = eventType;
            this.isBlockedBilling = isBlockedBilling;
            this.isBlockedEntitlement = isBlockedEntitlement;
            this.serviceName = serviceName;
            this.serviceStateName = serviceStateName;
            this.phase = phase;
        }

        public EventSubscriptionJson(final SubscriptionEvent subscriptionEvent, @Nullable final AccountAuditLogs accountAuditLogs) {

            super(toAuditLogJson(getAuditLogsForSubscriptionEvent(subscriptionEvent, accountAuditLogs)));
            final BillingPeriod billingPeriod = subscriptionEvent.getNextBillingPeriod() != null ? subscriptionEvent.getNextBillingPeriod() : subscriptionEvent.getPrevBillingPeriod();
            final Product product = subscriptionEvent.getNextProduct() != null ? subscriptionEvent.getNextProduct() : subscriptionEvent.getPrevProduct();
            final PriceList priceList = subscriptionEvent.getNextPriceList() != null ? subscriptionEvent.getNextPriceList() : subscriptionEvent.getPrevPriceList();
            final PlanPhase phase = subscriptionEvent.getNextPhase() != null ? subscriptionEvent.getNextPhase() : subscriptionEvent.getPrevPhase();
            this.eventId = subscriptionEvent.getId().toString();
            this.billingPeriod = billingPeriod != null ? billingPeriod.toString() : null;
            this.effectiveDate = subscriptionEvent.getEffectiveDate();
            this.product = product != null ? product.getName() : null;
            this.priceList = priceList != null ? priceList.getName() : null;
            this.eventType = subscriptionEvent.getSubscriptionEventType().toString();
            this.isBlockedBilling = subscriptionEvent.isBlockedBilling();
            this.isBlockedEntitlement = subscriptionEvent.isBlockedEntitlement();
            this.serviceName = subscriptionEvent.getServiceName();
            this.serviceStateName = subscriptionEvent.getServiceStateName();
            this.phase = phase != null ? phase.getName() : null;
        }


        private static List<AuditLog> getAuditLogsForSubscriptionEvent(final SubscriptionEvent subscriptionEvent, @Nullable final AccountAuditLogs accountAuditLogs) {
            if (accountAuditLogs == null) {
                return null;
            }
            final ObjectType subscriptionEventObjectType = subscriptionEvent.getSubscriptionEventType().getObjectType();
            if (subscriptionEventObjectType == ObjectType.SUBSCRIPTION_EVENT) {
                return accountAuditLogs.getAuditLogsForSubscriptionEvent(subscriptionEvent.getId());
            } else if (subscriptionEventObjectType == ObjectType.BLOCKING_STATES) {
                return accountAuditLogs.getAuditLogsForBlockingState(subscriptionEvent.getId());
            }
            throw new IllegalStateException("Unepxected objectType " + subscriptionEventObjectType + " for SubscriptionEvent " + subscriptionEvent.getId());
        }

        public String getEventId() {
            return eventId;
        }

        public String getBillingPeriod() {
            return billingPeriod;
        }

        public LocalDate getEffectiveDate() {
            return effectiveDate;
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

        public Boolean getIsBlockedBilling() {
            return isBlockedBilling;
        }

        public Boolean getIsBlockedEntitlement() {
            return isBlockedEntitlement;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getServiceStateName() {
            return serviceStateName;
        }

        public String getPhase() {
            return phase;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("EventSubscriptionJson{");
            sb.append("eventId='").append(eventId).append('\'');
            sb.append(", billingPeriod='").append(billingPeriod).append('\'');
            sb.append(", effectiveDate=").append(effectiveDate);
            sb.append(", product='").append(product).append('\'');
            sb.append(", priceList='").append(priceList).append('\'');
            sb.append(", eventType='").append(eventType).append('\'');
            sb.append(", isBlockedBilling=").append(isBlockedBilling);
            sb.append(", isBlockedEntitlement=").append(isBlockedEntitlement);
            sb.append(", serviceName='").append(serviceName).append('\'');
            sb.append(", serviceStateName='").append(serviceStateName).append('\'');
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

            final EventSubscriptionJson that = (EventSubscriptionJson) o;

            if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
                return false;
            }
            if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
                return false;
            }
            if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) {
                return false;
            }
            if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) {
                return false;
            }
            if (isBlockedBilling != null ? !isBlockedBilling.equals(that.isBlockedBilling) : that.isBlockedBilling != null) {
                return false;
            }
            if (isBlockedEntitlement != null ? !isBlockedEntitlement.equals(that.isBlockedEntitlement) : that.isBlockedEntitlement != null) {
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
            if (serviceName != null ? !serviceName.equals(that.serviceName) : that.serviceName != null) {
                return false;
            }
            if (serviceStateName != null ? !serviceStateName.equals(that.serviceStateName) : that.serviceStateName != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = eventId != null ? eventId.hashCode() : 0;
            result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
            result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
            result = 31 * result + (product != null ? product.hashCode() : 0);
            result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
            result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
            result = 31 * result + (isBlockedBilling != null ? isBlockedBilling.hashCode() : 0);
            result = 31 * result + (isBlockedEntitlement != null ? isBlockedEntitlement.hashCode() : 0);
            result = 31 * result + (serviceName != null ? serviceName.hashCode() : 0);
            result = 31 * result + (serviceStateName != null ? serviceStateName.hashCode() : 0);
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
                            @JsonProperty("phaseType") @Nullable final String phaseType,
                            @JsonProperty("priceList") @Nullable final String priceList,
                            @JsonProperty("planName") @Nullable final String planName,
                            @JsonProperty("state") @Nullable final String state,
                            @JsonProperty("sourceType") @Nullable final String sourceType,
                            @JsonProperty("cancelledDate") @Nullable final LocalDate cancelledDate,
                            @JsonProperty("chargedThroughDate") @Nullable final LocalDate chargedThroughDate,
                            @JsonProperty("billingStartDate") @Nullable final LocalDate billingStartDate,
                            @JsonProperty("billingEndDate") @Nullable final LocalDate billingEndDate,
                            @JsonProperty("billCycleDayLocal") @Nullable final Integer billCycleDayLocal,
                            @JsonProperty("events") @Nullable final List<EventSubscriptionJson> events,
                            @JsonProperty("priceOverrides") final List<PhasePriceOverrideJson> priceOverrides,
                            @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.startDate = startDate;
        this.productName = productName;
        this.productCategory = productCategory;
        this.billingPeriod = billingPeriod;
        this.phaseType = phaseType;
        this.priceList = priceList;
        this.planName = planName;
        this.state = state;
        this.sourceType = sourceType;
        this.cancelledDate = cancelledDate;
        this.chargedThroughDate = chargedThroughDate;
        this.billingStartDate = billingStartDate;
        this.billingEndDate = billingEndDate;
        this.billCycleDayLocal = billCycleDayLocal;
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.subscriptionId = subscriptionId;
        this.externalKey = externalKey;
        this.events = events;
        this.priceOverrides = priceOverrides;
    }

    public SubscriptionJson(final Subscription subscription, @Nullable final Currency currency, @Nullable final AccountAuditLogs accountAuditLogs) throws CatalogApiException {
        super(toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForSubscription(subscription.getId())));
        this.startDate = subscription.getEffectiveStartDate();

        // last* fields can be null if the subscription starts in the future - rely on the first available event instead
        final SubscriptionEvent firstEvent = subscription.getSubscriptionEvents().isEmpty() ? null : subscription.getSubscriptionEvents().get(0);
        if (subscription.getLastActiveProduct() == null) {
            this.productName = firstEvent == null ? null : firstEvent.getNextProduct().getName();
        } else {
            this.productName = subscription.getLastActiveProduct().getName();
        }
        if (subscription.getLastActiveProductCategory() == null) {
            this.productCategory = firstEvent == null ? null : firstEvent.getNextProduct().getCategory().name();
        } else {
            this.productCategory = subscription.getLastActiveProductCategory().name();
        }
        if (subscription.getLastActivePlan() == null) {
            this.billingPeriod = firstEvent == null ? null : firstEvent.getNextPlan().getRecurringBillingPeriod().name();
        } else {
            this.billingPeriod = subscription.getLastActivePlan().getRecurringBillingPeriod().toString();
        }
        if (subscription.getLastActivePhase() == null) {
            this.phaseType = firstEvent == null ? null : firstEvent.getNextPhase().getPhaseType().name();
        } else {
            this.phaseType = subscription.getLastActivePhase().getPhaseType().toString();
        }
        if (subscription.getLastActivePriceList() == null) {
            this.priceList = firstEvent == null ? null : firstEvent.getNextPriceList().getName();
        } else {
            this.priceList = subscription.getLastActivePriceList().getName();
        }
        if (subscription.getLastActivePlan() == null) {
            this.planName = firstEvent == null ? null : firstEvent.getNextPlan().getName();
        } else {
            this.planName = subscription.getLastActivePlan().getName();
        }


        this.state = subscription.getState().name();
        this.sourceType = subscription.getSourceType().name();
        this.cancelledDate = subscription.getEffectiveEndDate();
        this.chargedThroughDate = subscription.getChargedThroughDate();
        this.billingStartDate = subscription.getBillingStartDate();
        this.billingEndDate = subscription.getBillingEndDate();
        this.billCycleDayLocal = subscription.getBillCycleDayLocal();
        this.accountId = subscription.getAccountId().toString();
        this.bundleId = subscription.getBundleId().toString();
        this.subscriptionId = subscription.getId().toString();
        this.externalKey = subscription.getExternalKey();
        this.events = new LinkedList<EventSubscriptionJson>();
        for (final SubscriptionEvent subscriptionEvent : subscription.getSubscriptionEvents()) {
            this.events.add(new EventSubscriptionJson(subscriptionEvent, accountAuditLogs));
        }

        // We fill the catalog info every time we get the currency from the account (even if this is not overridden Plan)
        this.priceOverrides = new ArrayList<PhasePriceOverrideJson>();
        if (currency != null) {
            final Plan plan = subscription.getLastActivePlan();
            if (plan != null) {
                for (final PlanPhase cur : plan.getAllPhases()) {
                    final BigDecimal fixedPrice = cur.getFixed() != null ? cur.getFixed().getPrice().getPrice(currency) : null;
                    final BigDecimal recurringPrice = cur.getRecurring() != null ? cur.getRecurring().getRecurringPrice().getPrice(currency) : null;
                    final PhasePriceOverrideJson phase = new PhasePriceOverrideJson(cur.getName(), cur.getPhaseType().toString(), fixedPrice, recurringPrice, cur.getUsages(),currency);
                    priceOverrides.add(phase);
                }
            }
        }
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

    public String getPhaseType() {
        return phaseType;
    }

    public String getPriceList() {
        return priceList;
    }

    public String getPlanName() {
        return planName;
    }

    public String getState() {
        return state;
    }

    public String getSourceType() {
        return sourceType;
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

    public Integer getBillCycleDayLocal() {
        return billCycleDayLocal;
    }

    public List<EventSubscriptionJson> getEvents() {
        return events;
    }

    public List<PhasePriceOverrideJson> getPriceOverrides() {
        return priceOverrides;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SubscriptionJson{");
        sb.append("accountId='").append(accountId).append('\'');
        sb.append(", bundleId='").append(bundleId).append('\'');
        sb.append(", subscriptionId='").append(subscriptionId).append('\'');
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", productName='").append(productName).append('\'');
        sb.append(", productCategory='").append(productCategory).append('\'');
        sb.append(", billingPeriod='").append(billingPeriod).append('\'');
        sb.append(", phaseType='").append(phaseType).append('\'');
        sb.append(", priceList='").append(priceList).append('\'');
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", state='").append(state).append('\'');
        sb.append(", sourceType='").append(sourceType).append('\'');
        sb.append(", cancelledDate=").append(cancelledDate);
        sb.append(", chargedThroughDate=").append(chargedThroughDate);
        sb.append(", billingStartDate=").append(billingStartDate);
        sb.append(", billingEndDate=").append(billingEndDate);
        sb.append(", billCycleDayLocal=").append(billCycleDayLocal);
        sb.append(", events=").append(events);
        sb.append(", priceOverrides=").append(priceOverrides);
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
        if (events != null ? !events.equals(that.events) : that.events != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (phaseType != null ? !phaseType.equals(that.phaseType) : that.phaseType != null) {
            return false;
        }
        if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
            return false;
        }
        if (productCategory != null ? !productCategory.equals(that.productCategory) : that.productCategory != null) {
            return false;
        }
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }
        if (sourceType != null ? !sourceType.equals(that.sourceType) : that.sourceType != null) {
            return false;
        }
        if (startDate != null ? startDate.compareTo(that.startDate) != 0 : that.startDate != null) {
            return false;
        }
        if (state != null ? !state.equals(that.state) : that.state != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (priceOverrides != null ? !priceOverrides.equals(that.priceOverrides) : that.priceOverrides != null) {
            return false;
        }
        if (billCycleDayLocal != null ? !billCycleDayLocal.equals(that.billCycleDayLocal) : that.billCycleDayLocal != null) {
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
        result = 31 * result + (phaseType != null ? phaseType.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (sourceType != null ? sourceType.hashCode() : 0);
        result = 31 * result + (cancelledDate != null ? cancelledDate.hashCode() : 0);
        result = 31 * result + (chargedThroughDate != null ? chargedThroughDate.hashCode() : 0);
        result = 31 * result + (billingStartDate != null ? billingStartDate.hashCode() : 0);
        result = 31 * result + (billingEndDate != null ? billingEndDate.hashCode() : 0);
        result = 31 * result + (billCycleDayLocal != null ? billCycleDayLocal.hashCode() : 0);
        result = 31 * result + (events != null ? events.hashCode() : 0);
        result = 31 * result + (priceOverrides != null ? priceOverrides.hashCode() : 0);
        return result;
    }

}
