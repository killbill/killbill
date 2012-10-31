/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.analytics.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.util.entity.EntityBase;

public class DefaultBusinessSubscriptionTransition extends EntityBase implements BusinessSubscriptionTransition {

    private final long totalOrdering;
    private final UUID bundleId;
    private final String externalKey;
    private final UUID accountId;
    private final String accountKey;
    private final UUID subscriptionId;

    private final DateTime requestedTimestamp;
    private final String eventType;
    private final String category;

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
    private final DateTime nextStartDate;
    private final String nextState;

    public DefaultBusinessSubscriptionTransition(final BusinessSubscriptionTransitionModelDao bstModelDao) {
        this.totalOrdering = bstModelDao.getTotalOrdering();
        this.bundleId = bstModelDao.getBundleId();
        this.externalKey = bstModelDao.getExternalKey();
        this.accountId = bstModelDao.getAccountId();
        this.accountKey = bstModelDao.getAccountKey();
        this.subscriptionId = bstModelDao.getSubscriptionId();

        this.requestedTimestamp = bstModelDao.getRequestedTimestamp();
        this.eventType = bstModelDao.getEvent().getEventType().toString();
        if (bstModelDao.getEvent().getCategory() != null) {
            this.category = bstModelDao.getEvent().getCategory().toString();
        } else {
            this.category = null;
        }

        if (bstModelDao.getPreviousSubscription() != null) {
            this.prevProductName = bstModelDao.getPreviousSubscription().getProductName();
            this.prevProductType = bstModelDao.getPreviousSubscription().getProductType();
            this.prevProductCategory = bstModelDao.getPreviousSubscription().getProductCategory().toString();
            this.prevSlug = bstModelDao.getPreviousSubscription().getSlug();
            this.prevPhase = bstModelDao.getPreviousSubscription().getPhase();
            this.prevBillingPeriod = bstModelDao.getPreviousSubscription().getBillingPeriod();
            this.prevPrice = bstModelDao.getPreviousSubscription().getPrice();
            this.prevPriceList = bstModelDao.getPreviousSubscription().getPriceList();
            this.prevMrr = bstModelDao.getPreviousSubscription().getMrr();
            this.prevCurrency = bstModelDao.getPreviousSubscription().getCurrency();
            this.prevStartDate = bstModelDao.getPreviousSubscription().getStartDate();
            this.prevState = bstModelDao.getPreviousSubscription().getState().toString();
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
            this.prevStartDate = null;
            this.prevState = null;
        }

        if (bstModelDao.getNextSubscription() != null) {
            this.nextProductName = bstModelDao.getNextSubscription().getProductName();
            this.nextProductType = bstModelDao.getNextSubscription().getProductType();
            this.nextProductCategory = bstModelDao.getNextSubscription().getProductCategory().toString();
            this.nextSlug = bstModelDao.getNextSubscription().getSlug();
            this.nextPhase = bstModelDao.getNextSubscription().getPhase();
            this.nextBillingPeriod = bstModelDao.getNextSubscription().getBillingPeriod();
            this.nextPrice = bstModelDao.getNextSubscription().getPrice();
            this.nextPriceList = bstModelDao.getNextSubscription().getPriceList();
            this.nextMrr = bstModelDao.getNextSubscription().getMrr();
            this.nextCurrency = bstModelDao.getNextSubscription().getCurrency();
            this.nextStartDate = bstModelDao.getNextSubscription().getStartDate();
            this.nextState = bstModelDao.getNextSubscription().getState().toString();
        } else {
            this.nextProductName = null;
            this.nextProductType = null;
            this.nextProductCategory = null;
            this.nextSlug = null;
            this.nextPhase = null;
            this.nextBillingPeriod = null;
            this.nextPrice = null;
            this.nextPriceList = null;
            this.nextMrr = null;
            this.nextCurrency = null;
            this.nextStartDate = null;
            this.nextState = null;
        }
    }

    @Override
    public long getTotalOrdering() {
        return totalOrdering;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String getAccountKey() {
        return accountKey;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public DateTime getRequestedTimestamp() {
        return requestedTimestamp;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public String getPrevProductName() {
        return prevProductName;
    }

    @Override
    public String getPrevProductType() {
        return prevProductType;
    }

    @Override
    public String getPrevProductCategory() {
        return prevProductCategory;
    }

    @Override
    public String getPrevSlug() {
        return prevSlug;
    }

    @Override
    public String getPrevPhase() {
        return prevPhase;
    }

    @Override
    public String getPrevBillingPeriod() {
        return prevBillingPeriod;
    }

    @Override
    public BigDecimal getPrevPrice() {
        return prevPrice;
    }

    @Override
    public String getPrevPriceList() {
        return prevPriceList;
    }

    @Override
    public BigDecimal getPrevMrr() {
        return prevMrr;
    }

    @Override
    public String getPrevCurrency() {
        return prevCurrency;
    }

    @Override
    public DateTime getPrevStartDate() {
        return prevStartDate;
    }

    @Override
    public String getPrevState() {
        return prevState;
    }

    @Override
    public String getNextProductName() {
        return nextProductName;
    }

    @Override
    public String getNextProductType() {
        return nextProductType;
    }

    @Override
    public String getNextProductCategory() {
        return nextProductCategory;
    }

    @Override
    public String getNextSlug() {
        return nextSlug;
    }

    @Override
    public String getNextPhase() {
        return nextPhase;
    }

    @Override
    public String getNextBillingPeriod() {
        return nextBillingPeriod;
    }

    @Override
    public BigDecimal getNextPrice() {
        return nextPrice;
    }

    @Override
    public String getNextPriceList() {
        return nextPriceList;
    }

    @Override
    public BigDecimal getNextMrr() {
        return nextMrr;
    }

    @Override
    public String getNextCurrency() {
        return nextCurrency;
    }

    @Override
    public DateTime getNextStartDate() {
        return nextStartDate;
    }

    @Override
    public String getNextState() {
        return nextState;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBusinessSubscriptionTransition");
        sb.append("{totalOrdering=").append(totalOrdering);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", accountId=").append(accountId);
        sb.append(", accountKey='").append(accountKey).append('\'');
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", requestedTimestamp=").append(requestedTimestamp);
        sb.append(", eventType='").append(eventType).append('\'');
        sb.append(", category='").append(category).append('\'');
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
        sb.append(", nextStartDate=").append(nextStartDate);
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

        final DefaultBusinessSubscriptionTransition that = (DefaultBusinessSubscriptionTransition) o;

        if (totalOrdering != that.totalOrdering) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (category != null ? !category.equals(that.category) : that.category != null) {
            return false;
        }
        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (nextBillingPeriod != null ? !nextBillingPeriod.equals(that.nextBillingPeriod) : that.nextBillingPeriod != null) {
            return false;
        }
        if (nextCurrency != null ? !nextCurrency.equals(that.nextCurrency) : that.nextCurrency != null) {
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
        int result = (int) (totalOrdering ^ (totalOrdering >>> 32));
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (requestedTimestamp != null ? requestedTimestamp.hashCode() : 0);
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
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
        result = 31 * result + (nextStartDate != null ? nextStartDate.hashCode() : 0);
        result = 31 * result + (nextState != null ? nextState.hashCode() : 0);
        return result;
    }
}
