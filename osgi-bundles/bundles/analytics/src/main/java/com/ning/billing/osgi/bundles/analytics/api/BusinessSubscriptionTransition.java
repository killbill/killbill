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

package com.ning.billing.osgi.bundles.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;

public class BusinessSubscriptionTransition extends BusinessEntityBase {

    private final UUID bundleId;
    private final String bundleExternalKey;
    private final UUID subscriptionId;

    private final DateTime requestedTimestamp;
    private final String event;

    private final String prevProductName;
    private final String prevProductType;
    private final String prevProductCategory;
    private final String prevSlug;
    private final String prevPhase;
    private final String prevBillingPeriod;
    private final BigDecimal prevPrice;
    private final String prevPriceList;
    private final BigDecimal prevMrr;
    private final String prevCurrency;
    private final Boolean prevBusinessActive;
    private final DateTime prevStartDate;
    private final String prevState;

    private final String nextProductName;
    private final String nextProductType;
    private final String nextProductCategory;
    private final String nextSlug;
    private final String nextPhase;
    private final String nextBillingPeriod;
    private final BigDecimal nextPrice;
    private final String nextPriceList;
    private final BigDecimal nextMrr;
    private final String nextCurrency;
    private final Boolean nextBusinessActive;
    private final DateTime nextStartDate;
    private final DateTime nextEndDate;
    private final String nextState;

    public BusinessSubscriptionTransition(final BusinessSubscriptionTransitionModelDao bstModelDao) {
        super(bstModelDao.getCreatedDate(),
              bstModelDao.getCreatedBy(),
              bstModelDao.getCreatedReasonCode(),
              bstModelDao.getCreatedComments(),
              bstModelDao.getAccountId(),
              bstModelDao.getAccountName(),
              bstModelDao.getAccountExternalKey());

        this.bundleId = bstModelDao.getBundleId();
        this.bundleExternalKey = bstModelDao.getBundleExternalKey();
        this.subscriptionId = bstModelDao.getSubscriptionId();

        this.requestedTimestamp = bstModelDao.getRequestedTimestamp();
        this.event = bstModelDao.getEvent();

        this.prevProductName = bstModelDao.getPrevProductName();
        this.prevProductType = bstModelDao.getPrevProductType();
        this.prevProductCategory = bstModelDao.getPrevProductCategory();
        this.prevSlug = bstModelDao.getPrevSlug();
        this.prevPhase = bstModelDao.getPrevPhase();
        this.prevBillingPeriod = bstModelDao.getPrevBillingPeriod();
        this.prevPrice = bstModelDao.getPrevPrice();
        this.prevPriceList = bstModelDao.getPrevPriceList();
        this.prevMrr = bstModelDao.getPrevMrr();
        this.prevCurrency = bstModelDao.getPrevCurrency();
        this.prevBusinessActive = bstModelDao.getPrevBusinessActive();
        this.prevStartDate = bstModelDao.getPrevStartDate();
        this.prevState = bstModelDao.getPrevState();

        this.nextProductName = bstModelDao.getNextProductName();
        this.nextProductType = bstModelDao.getNextProductType();
        this.nextProductCategory = bstModelDao.getNextProductCategory();
        this.nextSlug = bstModelDao.getNextSlug();
        this.nextPhase = bstModelDao.getNextPhase();
        this.nextBillingPeriod = bstModelDao.getNextBillingPeriod();
        this.nextPrice = bstModelDao.getNextPrice();
        this.nextPriceList = bstModelDao.getNextPriceList();
        this.nextMrr = bstModelDao.getNextMrr();
        this.nextCurrency = bstModelDao.getNextCurrency();
        this.nextBusinessActive = bstModelDao.getNextBusinessActive();
        this.nextStartDate = bstModelDao.getNextStartDate();
        this.nextEndDate = bstModelDao.getNextEndDate();
        this.nextState = bstModelDao.getNextState();
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
        sb.append("BusinessSubscriptionTransition");
        sb.append("{bundleId=").append(bundleId);
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

        final BusinessSubscriptionTransition that = (BusinessSubscriptionTransition) o;

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
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
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
