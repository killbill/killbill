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

import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.util.audit.AuditLog;

/**
 * Describe a state change between two BusinessSubscription
 */
public class BusinessSubscriptionTransitionModelDao extends BusinessModelDaoBase {

    private static final String SUBSCRIPTION_TABLE_NAME = "bst";
    private Long subscriptionEventRecordId;
    private UUID bundleId;
    private String bundleExternalKey;
    private UUID subscriptionId;
    private DateTime requestedTimestamp;
    private String event;
    private String prevProductName;
    private String prevProductType;
    private String prevProductCategory;
    private String prevSlug;
    private String prevPhase;
    private String prevBillingPeriod;
    private BigDecimal prevPrice;
    private String prevPriceList;
    private BigDecimal prevMrr;
    private String prevCurrency;
    private Boolean prevBusinessActive;
    private DateTime prevStartDate;
    private String prevState;
    private String nextProductName;
    private String nextProductType;
    private String nextProductCategory;
    private String nextSlug;
    private String nextPhase;
    private String nextBillingPeriod;
    private BigDecimal nextPrice;
    private String nextPriceList;
    private BigDecimal nextMrr;
    private String nextCurrency;
    private Boolean nextBusinessActive;
    private DateTime nextStartDate;
    private DateTime nextEndDate;
    private String nextState;

    public BusinessSubscriptionTransitionModelDao() { /* When reading from the database */ }

    public BusinessSubscriptionTransitionModelDao(final Long subscriptionEventRecordId,
                                                  final UUID bundleId,
                                                  final String bundleExternalKey,
                                                  final UUID subscriptionId,
                                                  final DateTime requestedTimestamp,
                                                  final BusinessSubscriptionEvent event,
                                                  @Nullable final BusinessSubscription previousSubscription,
                                                  final BusinessSubscription nextSubscription,
                                                  final DateTime createdDate,
                                                  final String createdBy,
                                                  final String createdReasonCode,
                                                  final String createdComments,
                                                  final UUID accountId,
                                                  final String accountName,
                                                  final String accountExternalKey,
                                                  final Long accountRecordId,
                                                  final Long tenantRecordId,
                                                  @Nullable final ReportGroup reportGroup) {
        super(createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId,
              reportGroup);
        this.subscriptionEventRecordId = subscriptionEventRecordId;
        this.bundleId = bundleId;
        this.bundleExternalKey = bundleExternalKey;
        this.subscriptionId = subscriptionId;

        this.requestedTimestamp = requestedTimestamp;
        this.event = event.toString();

        if (previousSubscription != null) {
            this.prevProductName = previousSubscription.getProductName();
            this.prevProductType = previousSubscription.getProductType();
            this.prevProductCategory = previousSubscription.getProductCategory();
            this.prevSlug = previousSubscription.getSlug();
            this.prevPhase = previousSubscription.getPhase();
            this.prevBillingPeriod = previousSubscription.getBillingPeriod();
            this.prevPrice = previousSubscription.getPrice();
            this.prevPriceList = previousSubscription.getPriceList();
            this.prevMrr = previousSubscription.getMrr();
            this.prevCurrency = previousSubscription.getCurrency();
            this.prevBusinessActive = previousSubscription.getBusinessActive();
            this.prevStartDate = previousSubscription.getStartDate();
            this.prevState = previousSubscription.getState();
        } else {
            this.prevProductName = null;
            this.prevProductType = null;
            this.prevProductCategory = null;
            this.prevSlug = null;
            this.prevPhase = null;
            this.prevBillingPeriod = null;
            this.prevPrice = null;
            this.prevPriceList = null;
            this.prevMrr = null;
            this.prevCurrency = null;
            this.prevBusinessActive = null;
            this.prevStartDate = null;
            this.prevState = null;
        }

        this.nextProductName = nextSubscription.getProductName();
        this.nextProductType = nextSubscription.getProductType();
        this.nextProductCategory = nextSubscription.getProductCategory();
        this.nextSlug = nextSubscription.getSlug();
        this.nextPhase = nextSubscription.getPhase();
        this.nextBillingPeriod = nextSubscription.getBillingPeriod();
        this.nextPrice = nextSubscription.getPrice();
        this.nextPriceList = nextSubscription.getPriceList();
        this.nextMrr = nextSubscription.getMrr();
        this.nextCurrency = nextSubscription.getCurrency();
        this.nextBusinessActive = nextSubscription.getBusinessActive();
        this.nextStartDate = nextSubscription.getStartDate();
        this.nextEndDate = nextSubscription.getEndDate();
        this.nextState = nextSubscription.getState();
    }

    public BusinessSubscriptionTransitionModelDao(final Account account,
                                                  final Long accountRecordId,
                                                  final SubscriptionBundle bundle,
                                                  final SubscriptionTransition transition,
                                                  final Long subscriptionEventRecordId,
                                                  final DateTime requestedTimestamp,
                                                  final BusinessSubscriptionEvent event,
                                                  @Nullable final BusinessSubscription previousSubscription,
                                                  final BusinessSubscription nextSubscription,
                                                  final AuditLog creationAuditLog,
                                                  final Long tenantRecordId,
                                                  @Nullable final ReportGroup reportGroup) {
        this(subscriptionEventRecordId,
             bundle.getId(),
             bundle.getExternalKey(),
             transition.getSubscriptionId(),
             requestedTimestamp,
             event,
             previousSubscription,
             nextSubscription,
             transition.getNextEventCreatedDate(),
             creationAuditLog.getUserName(),
             creationAuditLog.getReasonCode(),
             creationAuditLog.getComment(),
             account.getId(),
             account.getName(),
             account.getExternalKey(),
             accountRecordId,
             tenantRecordId,
             reportGroup);
    }

    @Override
    public String getTableName() {
        return SUBSCRIPTION_TABLE_NAME;
    }

    public Long getSubscriptionEventRecordId() {
        return subscriptionEventRecordId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public String getBundleExternalKey() {
        return bundleExternalKey;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public DateTime getRequestedTimestamp() {
        return requestedTimestamp;
    }

    public String getEvent() {
        return event;
    }

    public String getPrevProductName() {
        return prevProductName;
    }

    public String getPrevProductType() {
        return prevProductType;
    }

    public String getPrevProductCategory() {
        return prevProductCategory;
    }

    public String getPrevSlug() {
        return prevSlug;
    }

    public String getPrevPhase() {
        return prevPhase;
    }

    public String getPrevBillingPeriod() {
        return prevBillingPeriod;
    }

    public BigDecimal getPrevPrice() {
        return prevPrice;
    }

    public String getPrevPriceList() {
        return prevPriceList;
    }

    public BigDecimal getPrevMrr() {
        return prevMrr;
    }

    public String getPrevCurrency() {
        return prevCurrency;
    }

    public Boolean getPrevBusinessActive() {
        return prevBusinessActive;
    }

    public DateTime getPrevStartDate() {
        return prevStartDate;
    }

    public String getPrevState() {
        return prevState;
    }

    public String getNextProductName() {
        return nextProductName;
    }

    public String getNextProductType() {
        return nextProductType;
    }

    public String getNextProductCategory() {
        return nextProductCategory;
    }

    public String getNextSlug() {
        return nextSlug;
    }

    public String getNextPhase() {
        return nextPhase;
    }

    public String getNextBillingPeriod() {
        return nextBillingPeriod;
    }

    public BigDecimal getNextPrice() {
        return nextPrice;
    }

    public String getNextPriceList() {
        return nextPriceList;
    }

    public BigDecimal getNextMrr() {
        return nextMrr;
    }

    public String getNextCurrency() {
        return nextCurrency;
    }

    public Boolean getNextBusinessActive() {
        return nextBusinessActive;
    }

    public DateTime getNextStartDate() {
        return nextStartDate;
    }

    public DateTime getNextEndDate() {
        return nextEndDate;
    }

    public String getNextState() {
        return nextState;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessSubscriptionTransitionModelDao");
        sb.append("{subscriptionEventRecordId=").append(subscriptionEventRecordId);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", bundleExternalKey='").append(bundleExternalKey).append('\'');
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", requestedTimestamp=").append(requestedTimestamp);
        sb.append(", event='").append(event).append('\'');
        sb.append(", prevProductName='").append(prevProductName).append('\'');
        sb.append(", prevProductType='").append(prevProductType).append('\'');
        sb.append(", prevProductCategory='").append(prevProductCategory).append('\'');
        sb.append(", prevSlug='").append(prevSlug).append('\'');
        sb.append(", prevPhase='").append(prevPhase).append('\'');
        sb.append(", prevBillingPeriod='").append(prevBillingPeriod).append('\'');
        sb.append(", prevPrice=").append(prevPrice);
        sb.append(", prevPriceList='").append(prevPriceList).append('\'');
        sb.append(", prevMrr=").append(prevMrr);
        sb.append(", prevCurrency='").append(prevCurrency).append('\'');
        sb.append(", prevBusinessActive=").append(prevBusinessActive);
        sb.append(", prevStartDate=").append(prevStartDate);
        sb.append(", prevState='").append(prevState).append('\'');
        sb.append(", nextProductName='").append(nextProductName).append('\'');
        sb.append(", nextProductType='").append(nextProductType).append('\'');
        sb.append(", nextProductCategory='").append(nextProductCategory).append('\'');
        sb.append(", nextSlug='").append(nextSlug).append('\'');
        sb.append(", nextPhase='").append(nextPhase).append('\'');
        sb.append(", nextBillingPeriod='").append(nextBillingPeriod).append('\'');
        sb.append(", nextPrice=").append(nextPrice);
        sb.append(", nextPriceList='").append(nextPriceList).append('\'');
        sb.append(", nextMrr=").append(nextMrr);
        sb.append(", nextCurrency='").append(nextCurrency).append('\'');
        sb.append(", nextBusinessActive=").append(nextBusinessActive);
        sb.append(", nextStartDate=").append(nextStartDate);
        sb.append(", nextEndDate=").append(nextEndDate);
        sb.append(", nextState='").append(nextState).append('\'');
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

        final BusinessSubscriptionTransitionModelDao that = (BusinessSubscriptionTransitionModelDao) o;

        if (bundleExternalKey != null ? !bundleExternalKey.equals(that.bundleExternalKey) : that.bundleExternalKey != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (event != null ? !event.equals(that.event) : that.event != null) {
            return false;
        }
        if (nextBillingPeriod != null ? !nextBillingPeriod.equals(that.nextBillingPeriod) : that.nextBillingPeriod != null) {
            return false;
        }
        if (nextBusinessActive != null ? !nextBusinessActive.equals(that.nextBusinessActive) : that.nextBusinessActive != null) {
            return false;
        }
        if (nextCurrency != null ? !nextCurrency.equals(that.nextCurrency) : that.nextCurrency != null) {
            return false;
        }
        if (nextEndDate != null ? !nextEndDate.equals(that.nextEndDate) : that.nextEndDate != null) {
            return false;
        }
        if (nextMrr != null ? !nextMrr.equals(that.nextMrr) : that.nextMrr != null) {
            return false;
        }
        if (nextPhase != null ? !nextPhase.equals(that.nextPhase) : that.nextPhase != null) {
            return false;
        }
        if (nextPrice != null ? !nextPrice.equals(that.nextPrice) : that.nextPrice != null) {
            return false;
        }
        if (nextPriceList != null ? !nextPriceList.equals(that.nextPriceList) : that.nextPriceList != null) {
            return false;
        }
        if (nextProductCategory != null ? !nextProductCategory.equals(that.nextProductCategory) : that.nextProductCategory != null) {
            return false;
        }
        if (nextProductName != null ? !nextProductName.equals(that.nextProductName) : that.nextProductName != null) {
            return false;
        }
        if (nextProductType != null ? !nextProductType.equals(that.nextProductType) : that.nextProductType != null) {
            return false;
        }
        if (nextSlug != null ? !nextSlug.equals(that.nextSlug) : that.nextSlug != null) {
            return false;
        }
        if (nextStartDate != null ? !nextStartDate.equals(that.nextStartDate) : that.nextStartDate != null) {
            return false;
        }
        if (nextState != null ? !nextState.equals(that.nextState) : that.nextState != null) {
            return false;
        }
        if (prevBillingPeriod != null ? !prevBillingPeriod.equals(that.prevBillingPeriod) : that.prevBillingPeriod != null) {
            return false;
        }
        if (prevBusinessActive != null ? !prevBusinessActive.equals(that.prevBusinessActive) : that.prevBusinessActive != null) {
            return false;
        }
        if (prevCurrency != null ? !prevCurrency.equals(that.prevCurrency) : that.prevCurrency != null) {
            return false;
        }
        if (prevMrr != null ? !prevMrr.equals(that.prevMrr) : that.prevMrr != null) {
            return false;
        }
        if (prevPhase != null ? !prevPhase.equals(that.prevPhase) : that.prevPhase != null) {
            return false;
        }
        if (prevPrice != null ? !prevPrice.equals(that.prevPrice) : that.prevPrice != null) {
            return false;
        }
        if (prevPriceList != null ? !prevPriceList.equals(that.prevPriceList) : that.prevPriceList != null) {
            return false;
        }
        if (prevProductCategory != null ? !prevProductCategory.equals(that.prevProductCategory) : that.prevProductCategory != null) {
            return false;
        }
        if (prevProductName != null ? !prevProductName.equals(that.prevProductName) : that.prevProductName != null) {
            return false;
        }
        if (prevProductType != null ? !prevProductType.equals(that.prevProductType) : that.prevProductType != null) {
            return false;
        }
        if (prevSlug != null ? !prevSlug.equals(that.prevSlug) : that.prevSlug != null) {
            return false;
        }
        if (prevStartDate != null ? !prevStartDate.equals(that.prevStartDate) : that.prevStartDate != null) {
            return false;
        }
        if (prevState != null ? !prevState.equals(that.prevState) : that.prevState != null) {
            return false;
        }
        if (requestedTimestamp != null ? !requestedTimestamp.equals(that.requestedTimestamp) : that.requestedTimestamp != null) {
            return false;
        }
        if (subscriptionEventRecordId != null ? !subscriptionEventRecordId.equals(that.subscriptionEventRecordId) : that.subscriptionEventRecordId != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (subscriptionEventRecordId != null ? subscriptionEventRecordId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (bundleExternalKey != null ? bundleExternalKey.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (requestedTimestamp != null ? requestedTimestamp.hashCode() : 0);
        result = 31 * result + (event != null ? event.hashCode() : 0);
        result = 31 * result + (prevProductName != null ? prevProductName.hashCode() : 0);
        result = 31 * result + (prevProductType != null ? prevProductType.hashCode() : 0);
        result = 31 * result + (prevProductCategory != null ? prevProductCategory.hashCode() : 0);
        result = 31 * result + (prevSlug != null ? prevSlug.hashCode() : 0);
        result = 31 * result + (prevPhase != null ? prevPhase.hashCode() : 0);
        result = 31 * result + (prevBillingPeriod != null ? prevBillingPeriod.hashCode() : 0);
        result = 31 * result + (prevPrice != null ? prevPrice.hashCode() : 0);
        result = 31 * result + (prevPriceList != null ? prevPriceList.hashCode() : 0);
        result = 31 * result + (prevMrr != null ? prevMrr.hashCode() : 0);
        result = 31 * result + (prevCurrency != null ? prevCurrency.hashCode() : 0);
        result = 31 * result + (prevBusinessActive != null ? prevBusinessActive.hashCode() : 0);
        result = 31 * result + (prevStartDate != null ? prevStartDate.hashCode() : 0);
        result = 31 * result + (prevState != null ? prevState.hashCode() : 0);
        result = 31 * result + (nextProductName != null ? nextProductName.hashCode() : 0);
        result = 31 * result + (nextProductType != null ? nextProductType.hashCode() : 0);
        result = 31 * result + (nextProductCategory != null ? nextProductCategory.hashCode() : 0);
        result = 31 * result + (nextSlug != null ? nextSlug.hashCode() : 0);
        result = 31 * result + (nextPhase != null ? nextPhase.hashCode() : 0);
        result = 31 * result + (nextBillingPeriod != null ? nextBillingPeriod.hashCode() : 0);
        result = 31 * result + (nextPrice != null ? nextPrice.hashCode() : 0);
        result = 31 * result + (nextPriceList != null ? nextPriceList.hashCode() : 0);
        result = 31 * result + (nextMrr != null ? nextMrr.hashCode() : 0);
        result = 31 * result + (nextCurrency != null ? nextCurrency.hashCode() : 0);
        result = 31 * result + (nextBusinessActive != null ? nextBusinessActive.hashCode() : 0);
        result = 31 * result + (nextStartDate != null ? nextStartDate.hashCode() : 0);
        result = 31 * result + (nextEndDate != null ? nextEndDate.hashCode() : 0);
        result = 31 * result + (nextState != null ? nextState.hashCode() : 0);
        return result;
    }
}
