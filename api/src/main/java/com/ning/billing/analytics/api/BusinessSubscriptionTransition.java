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

package com.ning.billing.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.util.entity.Entity;

public interface BusinessSubscriptionTransition extends Entity {

    public long getTotalOrdering();

    public UUID getBundleId();

    public String getExternalKey();

    public UUID getAccountId();

    public String getAccountKey();

    public UUID getSubscriptionId();

    public DateTime getRequestedTimestamp();

    public String getEventType();

    public String getCategory();

    public String getPrevProductName();

    public String getPrevProductType();

    public String getPrevProductCategory();

    public String getPrevSlug();

    public String getPrevPhase();

    public String getPrevBillingPeriod();

    public BigDecimal getPrevPrice();

    public String getPrevPriceList();

    public BigDecimal getPrevMrr();

    public String getPrevCurrency();

    public DateTime getPrevStartDate();

    public String getPrevState();

    public String getNextProductName();

    public String getNextProductType();

    public String getNextProductCategory();

    public String getNextSlug();

    public String getNextPhase();

    public String getNextBillingPeriod();

    public BigDecimal getNextPrice();

    public String getNextPriceList();

    public BigDecimal getNextMrr();

    public String getNextCurrency();

    public DateTime getNextStartDate();

    public String getNextState();
}
