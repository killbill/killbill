/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice.api;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.IInternationalPrice;
import com.ning.billing.entitlement.api.billing.BillingMode;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.UUID;


//
// TODO that class should not be under API but invoice
// (only interfaces in api, any implementation class goes in submodule)
//
public class DefaultBillingEvent implements BillingEvent {
    private final UUID subscriptionId;
    private final DateTime startDate;
    private final String planName;
    private final String planPhaseName;
    private final IInternationalPrice price;
    private final BillingPeriod billingPeriod;
    private final int billCycleDay;
    private final BillingMode billingMode;


    public DefaultBillingEvent(UUID subscriptionId, DateTime startDate, String planName, String planPhaseName, IInternationalPrice price,
                        BillingPeriod billingPeriod, int billCycleDay, BillingMode billingMode) {
        this.subscriptionId = subscriptionId;
        this.startDate = startDate;
        this.planName = planName;
        this.planPhaseName = planPhaseName;
        this.price = price;
        this.billingPeriod = billingPeriod;
        this.billCycleDay = billCycleDay;
        this.billingMode = billingMode;
    }

    public DefaultBillingEvent(BillingEvent event, DateTime startDate) {
        this.subscriptionId = event.getSubscriptionId();
        this.startDate = startDate;
        this.planName = event.getPlanName();
        this.planPhaseName = event.getPlanPhaseName();
        this.price = event.getPrice();
        this.billingPeriod = event.getBillingPeriod();
        this.billCycleDay = event.getBillCycleDay();
        this.billingMode = event.getBillingMode();
    }

    @Override
    public DateTime getEffectiveDate() {
        return startDate;
    }

    @Override
    public int getBillCycleDay() {
        return billCycleDay;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public String getPlanName() {
        return planName;
    }

    @Override
    public String getPlanPhaseName() {
        return planPhaseName;
    }

    @Override
    public IInternationalPrice getPrice() {
        return price;
    }

 // TODO handle exception correctly
    @Override
    public BigDecimal getPrice(Currency currency) {
        try {
			return price.getPrice(currency);
		} catch (CatalogApiException e)  {
			e.printStackTrace();
			return new BigDecimal(0);
		}
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    @Override
    public BillingMode getBillingMode() {
        return billingMode;
    }

    @Override
    public String getDescription() {
        return planName + "(" + planPhaseName + ")";
    }

    @Override
    public int compareTo(BillingEvent billingEvent) {
        // strict date comparison here breaks SortedTree if multiple events occur on the same day
        int compareSubscriptions = getSubscriptionId().compareTo(billingEvent.getSubscriptionId());

        if (compareSubscriptions == 0) {
            return getEffectiveDate().compareTo(billingEvent.getEffectiveDate());
        } else {
            return compareSubscriptions;
        }
    }
}