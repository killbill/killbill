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

package org.killbill.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;


public class DefaultSubscriptionEvent implements SubscriptionEvent {

    private final UUID id;
    private final UUID entitlementId;
    private final DateTime effectiveDate;
    private final DateTime requestedDate;
    private final SubscriptionEventType eventType;
    private final boolean isBlockingEntitlement;
    private final boolean isBlockingBilling;
    private final String serviceName;
    private final String serviceStateName;
    private final Product prevProduct;
    private final Plan prevPlan;
    private final PlanPhase prevPlanPhase;
    private final PriceList prevPriceList;
    private final BillingPeriod prevBillingPeriod;
    private final Product nextProduct;
    private final Plan nextPlan;
    private final PlanPhase nextPlanPhase;
    private final PriceList nextPriceList;
    private final BillingPeriod nextBillingPeriod;
    private final DateTime createdDate;
    private final InternalTenantContext internalTenantContext;

    public DefaultSubscriptionEvent(final UUID id,
                                    final UUID entitlementId,
                                    final DateTime effectiveDate,
                                    final SubscriptionEventType eventType,
                                    final boolean blockingEntitlement,
                                    final boolean blockingBilling,
                                    final String serviceName,
                                    final String serviceStateName,
                                    final Product prevProduct,
                                    final Plan prevPlan,
                                    final PlanPhase prevPlanPhase,
                                    final PriceList prevPriceList,
                                    final BillingPeriod prevBillingPeriod,
                                    final Product nextProduct,
                                    final Plan nextPlan,
                                    final PlanPhase nextPlanPhase,
                                    final PriceList nextPriceList,
                                    final BillingPeriod nextBillingPeriod,
                                    final DateTime createDate,
                                    final InternalTenantContext internalTenantContext) {
        this.id = id;
        this.entitlementId = entitlementId;
        this.effectiveDate = effectiveDate;
        this.requestedDate = effectiveDate;
        this.eventType = eventType;
        this.isBlockingEntitlement = blockingEntitlement;
        this.isBlockingBilling = blockingBilling;
        this.serviceName = serviceName;
        this.serviceStateName = serviceStateName;
        this.prevProduct = prevProduct;
        this.prevPlan = prevPlan;
        this.prevPlanPhase = prevPlanPhase;
        this.prevPriceList = prevPriceList;
        this.prevBillingPeriod = prevBillingPeriod;
        this.nextProduct = nextProduct;
        this.nextPlan = nextPlan;
        this.nextPlanPhase = nextPlanPhase;
        this.nextPriceList = nextPriceList;
        this.nextBillingPeriod = nextBillingPeriod;
        this.createdDate = createDate;
        this.internalTenantContext = internalTenantContext;
    }

    public DateTime getEffectiveDateTime() {
        return effectiveDate;
    }

    public DateTime getRequestedDateTime() {
        return requestedDate;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getEntitlementId() {
        return entitlementId;
    }

    @Override
    public LocalDate getEffectiveDate() {
        return effectiveDate != null ? internalTenantContext.toLocalDate(effectiveDate) : null;
    }

    @Override
    public SubscriptionEventType getSubscriptionEventType() {
        return eventType;
    }

    @Override
    public boolean isBlockedBilling() {
        return isBlockingBilling;
    }

    @Override
    public boolean isBlockedEntitlement() {
        return isBlockingEntitlement;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getServiceStateName() {
        return serviceStateName;
    }

    @Override
    public Product getPrevProduct() {
        return prevProduct;
    }

    @Override
    public Plan getPrevPlan() {
        return prevPlan;
    }

    @Override
    public PlanPhase getPrevPhase() {
        return prevPlanPhase;
    }

    @Override
    public PriceList getPrevPriceList() {
        return prevPriceList;
    }

    @Override
    public BillingPeriod getPrevBillingPeriod() {
        return prevBillingPeriod;
    }

    @Override
    public Product getNextProduct() {
        return nextProduct;
    }

    @Override
    public Plan getNextPlan() {
        return nextPlan;
    }

    @Override
    public PlanPhase getNextPhase() {
        return nextPlanPhase;
    }

    @Override
    public PriceList getNextPriceList() {
        return nextPriceList;
    }

    @Override
    public BillingPeriod getNextBillingPeriod() {
        return nextBillingPeriod;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultSubscriptionEvent that = (DefaultSubscriptionEvent) o;

        if (isBlockingBilling != that.isBlockingBilling) {
            return false;
        }
        if (isBlockingEntitlement != that.isBlockingEntitlement) {
            return false;
        }
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (entitlementId != null ? !entitlementId.equals(that.entitlementId) : that.entitlementId != null) {
            return false;
        }
        if (eventType != that.eventType) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (nextBillingPeriod != that.nextBillingPeriod) {
            return false;
        }
        if (nextPlan != null ? !nextPlan.equals(that.nextPlan) : that.nextPlan != null) {
            return false;
        }
        if (nextPlanPhase != null ? !nextPlanPhase.equals(that.nextPlanPhase) : that.nextPlanPhase != null) {
            return false;
        }
        if (nextPriceList != null ? !nextPriceList.equals(that.nextPriceList) : that.nextPriceList != null) {
            return false;
        }
        if (nextProduct != null ? !nextProduct.equals(that.nextProduct) : that.nextProduct != null) {
            return false;
        }
        if (prevBillingPeriod != that.prevBillingPeriod) {
            return false;
        }
        if (prevPlan != null ? !prevPlan.equals(that.prevPlan) : that.prevPlan != null) {
            return false;
        }
        if (prevPlanPhase != null ? !prevPlanPhase.equals(that.prevPlanPhase) : that.prevPlanPhase != null) {
            return false;
        }
        if (prevPriceList != null ? !prevPriceList.equals(that.prevPriceList) : that.prevPriceList != null) {
            return false;
        }
        if (prevProduct != null ? !prevProduct.equals(that.prevProduct) : that.prevProduct != null) {
            return false;
        }
        if (requestedDate != null ? !requestedDate.equals(that.requestedDate) : that.requestedDate != null) {
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

    public boolean overlaps(final DefaultSubscriptionEvent that) {
        if (this == that) {
            return true;
        }
        if (that == null || getClass() != that.getClass()) {
            return false;
        }

        if (isBlockingBilling != that.isBlockingBilling) {
            return false;
        }
        if (isBlockingEntitlement != that.isBlockingEntitlement) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) < 0 : that.effectiveDate != null) {
            return false;
        }
        if (entitlementId != null ? !entitlementId.equals(that.entitlementId) : that.entitlementId != null) {
            return false;
        }
        if (eventType != that.eventType) {
            return false;
        }
        if (nextBillingPeriod != that.nextBillingPeriod) {
            return false;
        }
        if (nextPlan != null ? !nextPlan.equals(that.nextPlan) : that.nextPlan != null) {
            return false;
        }
        if (nextPlanPhase != null ? !nextPlanPhase.equals(that.nextPlanPhase) : that.nextPlanPhase != null) {
            return false;
        }
        if (nextPriceList != null ? !nextPriceList.equals(that.nextPriceList) : that.nextPriceList != null) {
            return false;
        }
        if (nextProduct != null ? !nextProduct.equals(that.nextProduct) : that.nextProduct != null) {
            return false;
        }
        if (prevBillingPeriod != that.prevBillingPeriod) {
            return false;
        }
        if (prevPlan != null ? !prevPlan.equals(that.prevPlan) : that.prevPlan != null) {
            return false;
        }
        if (prevPlanPhase != null ? !prevPlanPhase.equals(that.prevPlanPhase) : that.prevPlanPhase != null) {
            return false;
        }
        if (prevPriceList != null ? !prevPriceList.equals(that.prevPriceList) : that.prevPriceList != null) {
            return false;
        }
        if (prevProduct != null ? !prevProduct.equals(that.prevProduct) : that.prevProduct != null) {
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
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (entitlementId != null ? entitlementId.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (isBlockingEntitlement ? 1 : 0);
        result = 31 * result + (isBlockingBilling ? 1 : 0);
        result = 31 * result + (serviceName != null ? serviceName.hashCode() : 0);
        result = 31 * result + (serviceStateName != null ? serviceStateName.hashCode() : 0);
        result = 31 * result + (prevProduct != null ? prevProduct.hashCode() : 0);
        result = 31 * result + (prevPlan != null ? prevPlan.hashCode() : 0);
        result = 31 * result + (prevPlanPhase != null ? prevPlanPhase.hashCode() : 0);
        result = 31 * result + (prevPriceList != null ? prevPriceList.hashCode() : 0);
        result = 31 * result + (prevBillingPeriod != null ? prevBillingPeriod.hashCode() : 0);
        result = 31 * result + (nextProduct != null ? nextProduct.hashCode() : 0);
        result = 31 * result + (nextPlan != null ? nextPlan.hashCode() : 0);
        result = 31 * result + (nextPlanPhase != null ? nextPlanPhase.hashCode() : 0);
        result = 31 * result + (nextPriceList != null ? nextPriceList.hashCode() : 0);
        result = 31 * result + (nextBillingPeriod != null ? nextBillingPeriod.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        return result;
    }
}
