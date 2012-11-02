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

package com.ning.billing.analytics;

import java.math.BigDecimal;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Price;

public class MockPhase implements PlanPhase {

    private final PhaseType cohort;
    private final Plan plan;
    private final Duration duration;
    private final double price;

    public MockPhase(final PhaseType cohort, final Plan plan, final Duration duration, final double price) {
        this.cohort = cohort;
        this.plan = plan;
        this.duration = duration;
        this.price = price;
    }

    @Override
    public InternationalPrice getRecurringPrice() {
        return new InternationalPrice() {
            @Override
            public Price[] getPrices() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BigDecimal getPrice(final Currency currency) {
                return BigDecimal.valueOf(price);
            }

            @Override
            public boolean isZero() {
                return price == 0.0;
            }

        };
    }

    @Override
    public InternationalPrice getFixedPrice() {
        return new InternationalPrice() {
            @Override
            public Price[] getPrices() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BigDecimal getPrice(final Currency currency) {
                return BigDecimal.valueOf(price);
            }

            @Override
            public boolean isZero() {
                return price == 0.0;
            }
        };
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return null;
    }

    @Override
    public String getName() {
        if (plan == null) {
            return null;
        } else {
            return plan.getName() + "-" + cohort;
        }
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public PhaseType getPhaseType() {
        return cohort;
    }
}
