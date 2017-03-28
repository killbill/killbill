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

package org.killbill.billing.mock;

import java.util.Collection;
import java.util.UUID;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;

import com.google.common.collect.ImmutableList;

public class MockPriceList implements PriceList {
    private final String name;
    private final Plan plan;

    public MockPriceList() {
        this(UUID.randomUUID().toString(), new MockPlan());
    }

    public MockPriceList(final String name, final Plan plan) {
        this.name = name;
        this.plan = plan;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrettyName() {
        return name;
    }

    @Override
    public Collection<Plan> findPlans(final Product product, final BillingPeriod period) {
        return ImmutableList.of(plan);
    }

    public Plan getPlan() {
        return plan;
    }

    @Override
    public Collection<Plan> getPlans() {
        return ImmutableList.of(plan);
    }

}
