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

package com.ning.billing.mock;

import java.util.UUID;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;

public class MockPriceList implements PriceList {
    private final String name;
    private final Boolean isRetired;
    private final Plan plan;

    public MockPriceList() {
        this(false, UUID.randomUUID().toString(), new MockPlan());
    }

    public MockPriceList(final Boolean retired, final String name, final Plan plan) {
        isRetired = retired;
        this.name = name;
        this.plan = plan;
    }

    @Override
    public boolean isRetired() {
        return isRetired;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Plan findPlan(final Product product, final BillingPeriod period) {
        return plan;
    }

    public Plan getPlan() {
        return plan;
    }

    @Override
    public Plan[] getPlans() {
        return new Plan[] { plan };
    }
}
