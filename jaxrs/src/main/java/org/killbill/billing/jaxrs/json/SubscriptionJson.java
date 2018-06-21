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
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="Subscription", parent = JsonBase.class)
public class SubscriptionJson extends JsonBase {

    private final UUID accountId;
    private final UUID bundleId;
    private final UUID subscriptionId;
    private final String externalKey;
    private final LocalDate startDate;
    @ApiModelProperty(required = true)
    private final String productName;
    private final ProductCategory productCategory;
    @ApiModelProperty(required = true)
    private final BillingPeriod billingPeriod;
    private final PhaseType phaseType;
    @ApiModelProperty(required = true)
    private final String priceList;
    @ApiModelProperty(required = true)
    private final String planName;
    private final EntitlementState state;
    private final EntitlementSourceType sourceType;
    private final LocalDate cancelledDate;
    private final LocalDate chargedThroughDate;
    private final LocalDate billingStartDate;
    private final LocalDate billingEndDate;
    private final Integer billCycleDayLocal;
    private final List<EventSubscriptionJson> events;
    private final List<PhasePriceJson> prices;
    private final List<PhasePriceJson> priceOverrides;

    @ApiModel(value="EventSubscription", parent = JsonBase.class)
    public static class EventSubscriptionJson extends JsonBase {

        private final UUID eventId;
        private final BillingPeriod billingPeriod;
        private final LocalDate effectiveDate;
        private final String plan;
        private final String product;
        private final String priceList;
        private final String phase;
        private final SubscriptionEventType eventType;
        private final Boolean isBlockedBilling;
        private final Boolean isBlockedEntitlement;
        private final String serviceName;
        private final String serviceStateName;

        @JsonCreator
        public EventSubscriptionJson(@JsonProperty("eventId") final UUID eventId,
                                     @JsonProperty("billingPeriod") final BillingPeriod billingPeriod,
                                     @JsonProperty("effectiveDate") final LocalDate effectiveDate,
                                     @JsonProperty("plan") final String plan,
                                     @JsonProperty("product") final String product,
                                     @JsonProperty("priceList") final String priceList,
                                     @JsonProperty("eventType") final SubscriptionEventType eventType,
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
            this.plan = plan;
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
            final Plan plan = subscriptionEvent.getNextPlan() != null ? subscriptionEvent.getNextPlan() : subscriptionEvent.getPrevPlan();
            final Product product = subscriptionEvent.getNextProduct() != null ? subscriptionEvent.getNextProduct() : subscriptionEvent.getPrevProduct();
            final PriceList priceList = subscriptionEvent.getNextPriceList() != null ? subscriptionEvent.getNextPriceList() : subscriptionEvent.getPrevPriceList();
            final PlanPhase phase = subscriptionEvent.getNextPhase() != null ? subscriptionEvent.getNextPhase() : subscriptionEvent.getPrevPhase();
            this.eventId = subscriptionEvent.getId();
            this.billingPeriod = billingPeriod;
            this.effectiveDate = subscriptionEvent.getEffectiveDate();
            this.plan = plan != null ? plan.getName() : null;
            this.product = product != null ? product.getName() : null;
            this.priceList = priceList != null ? priceList.getName() : null;
            this.eventType = subscriptionEvent.getSubscriptionEventType();
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

        public UUID getEventId() {
            return eventId;
        }

        public BillingPeriod getBillingPeriod() {
            return billingPeriod;
        }

        public LocalDate getEffectiveDate() {
            return effectiveDate;
        }

        public String getPlan() {
            return plan;
        }

        public String getProduct() {
            return product;
        }

        public String getPriceList() {
            return priceList;
        }

        public SubscriptionEventType getEventType() {
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
            sb.append(", plan='").append(plan).append('\'');
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
            if (plan != null ? !plan.equals(that.plan) : that.plan != null) {
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
            result = 31 * result + (plan != null ? plan.hashCode() : 0);
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
    public SubscriptionJson(@JsonProperty("accountId") @Nullable final UUID accountId,
                            @JsonProperty("bundleId") @Nullable final UUID bundleId,
                            @JsonProperty("subscriptionId") @Nullable final UUID subscriptionId,
                            @JsonProperty("externalKey") @Nullable final String externalKey,
                            @JsonProperty("startDate") @Nullable final LocalDate startDate,
                            @JsonProperty("productName") @Nullable final String productName,
                            @JsonProperty("productCategory") @Nullable final ProductCategory productCategory,
                            @JsonProperty("billingPeriod") @Nullable final BillingPeriod billingPeriod,
                            @JsonProperty("phaseType") @Nullable final PhaseType phaseType,
                            @JsonProperty("priceList") @Nullable final String priceList,
                            @JsonProperty("planName") @Nullable final String planName,
                            @JsonProperty("state") @Nullable final EntitlementState state,
                            @JsonProperty("sourceType") @Nullable final EntitlementSourceType sourceType,
                            @JsonProperty("cancelledDate") @Nullable final LocalDate cancelledDate,
                            @JsonProperty("chargedThroughDate") @Nullable final LocalDate chargedThroughDate,
                            @JsonProperty("billingStartDate") @Nullable final LocalDate billingStartDate,
                            @JsonProperty("billingEndDate") @Nullable final LocalDate billingEndDate,
                            @JsonProperty("billCycleDayLocal") @Nullable final Integer billCycleDayLocal,
                            @JsonProperty("events") @Nullable final List<EventSubscriptionJson> events,
                            @JsonProperty("priceOverrides") final List<PhasePriceJson> priceOverrides,
                            @JsonProperty("prices") final List<PhasePriceJson> prices,
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
        this.prices = prices;
    }

    public SubscriptionJson(final Subscription subscription, @Nullable final Currency currency, @Nullable final AccountAuditLogs accountAuditLogs) throws CatalogApiException {
        super(toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForSubscription(subscription.getId())));
        this.startDate = subscription.getEffectiveStartDate();

        // last* fields can be null if the subscription starts in the future - rely on the first available event instead
        final List<SubscriptionEvent> subscriptionEvents = subscription.getSubscriptionEvents();
        final SubscriptionEvent firstEvent = subscriptionEvents.isEmpty() ? null : subscriptionEvents.get(0);
        if (subscription.getLastActiveProduct() == null) {
            this.productName = (firstEvent == null || firstEvent.getNextProduct() == null) ? null : firstEvent.getNextProduct().getName();
        } else {
            this.productName = subscription.getLastActiveProduct().getName();
        }
        if (subscription.getLastActiveProductCategory() == null) {
            this.productCategory = (firstEvent == null || firstEvent.getNextProduct() == null) ? null : firstEvent.getNextProduct().getCategory();
        } else {
            this.productCategory = subscription.getLastActiveProductCategory();
        }
        if (subscription.getLastActivePlan() == null) {
            this.billingPeriod = (firstEvent == null || firstEvent.getNextPlan() == null) ? null : firstEvent.getNextPlan().getRecurringBillingPeriod();
        } else {
            this.billingPeriod = subscription.getLastActivePlan().getRecurringBillingPeriod();
        }
        if (subscription.getLastActivePhase() == null) {
            this.phaseType = (firstEvent == null || firstEvent.getNextPhase() == null) ? null : firstEvent.getNextPhase().getPhaseType();
        } else {
            this.phaseType = subscription.getLastActivePhase().getPhaseType();
        }
        if (subscription.getLastActivePriceList() == null) {
            this.priceList = (firstEvent == null || firstEvent.getNextPriceList() == null) ? null : firstEvent.getNextPriceList().getName();
        } else {
            this.priceList = subscription.getLastActivePriceList().getName();
        }
        if (subscription.getLastActivePlan() == null) {
            this.planName = (firstEvent == null || firstEvent.getNextPlan() == null) ? null : firstEvent.getNextPlan().getName();
        } else {
            this.planName = subscription.getLastActivePlan().getName();
        }


        this.state = subscription.getState();
        this.sourceType = subscription.getSourceType();
        this.cancelledDate = subscription.getEffectiveEndDate();
        this.chargedThroughDate = subscription.getChargedThroughDate();
        this.billingStartDate = subscription.getBillingStartDate();
        this.billingEndDate = subscription.getBillingEndDate();
        this.billCycleDayLocal = subscription.getBillCycleDayLocal();
        this.accountId = subscription.getAccountId();
        this.bundleId = subscription.getBundleId();
        this.subscriptionId = subscription.getId();
        this.externalKey = subscription.getExternalKey();
        this.events = new LinkedList<EventSubscriptionJson>();
        // We fill the catalog info every time we get the currency from the account (even if this is not overridden Plan)
        this.prices = new ArrayList<PhasePriceJson>();

        String currentPhaseName = null;
        String currentPlanName = null;
        for (final SubscriptionEvent subscriptionEvent : subscriptionEvents) {
            this.events.add(new EventSubscriptionJson(subscriptionEvent, accountAuditLogs));
            if (currency != null) {

                final Plan curPlan = subscriptionEvent.getNextPlan();
                if (curPlan != null && (currentPlanName == null || !curPlan.getName().equals(currentPlanName))) {
                    currentPlanName = curPlan.getName();
                }

                final PlanPhase curPlanPhase = subscriptionEvent.getNextPhase();
                if (curPlanPhase == null || curPlanPhase.getName().equals(currentPhaseName)) {
                    continue;
                }
                currentPhaseName = curPlanPhase.getName();

                final BigDecimal fixedPrice = curPlanPhase.getFixed() != null ? curPlanPhase.getFixed().getPrice().getPrice(currency) : null;
                final BigDecimal recurringPrice = curPlanPhase.getRecurring() != null ? curPlanPhase.getRecurring().getRecurringPrice().getPrice(currency) : null;
                final PhasePriceJson phase = new PhasePriceJson(currentPlanName, curPlanPhase.getName(), curPlanPhase.getPhaseType().toString(), fixedPrice, recurringPrice, curPlanPhase.getUsages(), currency);
                prices.add(phase);
            }
        }
        this.priceOverrides = null;
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

    public String getExternalKey() {
        return externalKey;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getProductName() {
        return productName;
    }

    public ProductCategory getProductCategory() {
        return productCategory;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public PhaseType getPhaseType() {
        return phaseType;
    }

    public String getPriceList() {
        return priceList;
    }

    public String getPlanName() {
        return planName;
    }

    public EntitlementState getState() {
        return state;
    }

    public EntitlementSourceType getSourceType() {
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

    public List<PhasePriceJson> getPriceOverrides() {
        return priceOverrides;
    }

    public List<PhasePriceJson> getPrices() {
        return prices;
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
        sb.append(", prices=").append(prices);
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
