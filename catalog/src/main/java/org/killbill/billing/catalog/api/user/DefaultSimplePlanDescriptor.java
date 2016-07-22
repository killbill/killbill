/*
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

package org.killbill.billing.catalog.api.user;

import java.math.BigDecimal;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.SimplePlanDescriptor;
import org.killbill.billing.catalog.api.TimeUnit;

public class DefaultSimplePlanDescriptor implements SimplePlanDescriptor {

    private final String planId;
    private final String productName;
    private final Currency currency;
    private final BigDecimal amount;
    private final BillingPeriod billingPeriod;
    private final int trialLength;
    private final TimeUnit trialTimeUnit;

    public DefaultSimplePlanDescriptor(final String planId,
                                       final String productName,
                                       final Currency currency,
                                       final BigDecimal amount,
                                       final BillingPeriod billingPeriod,
                                       final int trialLength,
                                       final TimeUnit trialTimeUnit) {
        this.planId = planId;
        this.productName = productName;
        this.currency = currency;
        this.amount = amount;
        this.billingPeriod = billingPeriod;
        this.trialLength = trialLength;
        this.trialTimeUnit = trialTimeUnit;
    }

    @Override
    public String getPlanId() {
        return planId;
    }

    @Override
    public String getProductName() {
        return productName;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    @Override
    public int getTrialLength() {
        return trialLength;
    }

    @Override
    public TimeUnit getTrialTimeUnit() {
        return trialTimeUnit;
    }

}
