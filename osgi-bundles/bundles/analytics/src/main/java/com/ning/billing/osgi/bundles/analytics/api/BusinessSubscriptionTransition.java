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

    private final long totalOrdering;
    private final UUID bundleId;
    private final String bundleExternalKey;
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

        this.totalOrdering = bstModelDao.getTotalOrdering();
        this.bundleId = bstModelDao.getBundleId();
        this.bundleExternalKey = bstModelDao.getBundleExternalKey();
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
            this.prevBusinessActive = bstModelDao.getPreviousSubscription().getBusinessActive();
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
            this.prevBusinessActive = null;
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
            this.nextBusinessActive = bstModelDao.getNextSubscription().getBusinessActive();
            this.nextStartDate = bstModelDao.getNextSubscription().getStartDate();
            this.nextEndDate = bstModelDao.getNextSubscription().getEndDate();
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
            this.nextBusinessActive = null;
            this.nextStartDate = null;
            this.nextEndDate = null;
            this.nextState = null;
        }
    }
}
