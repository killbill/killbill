/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.mock;

import java.util.Date;
import java.util.Iterator;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;

public class MockPlan implements Plan {

    private final String name;
    private final Product product;

    public MockPlan() {
        this(UUID.randomUUID().toString(), new MockProduct());
    }

    public MockPlan(final String name, final Product product) {
        this.name = name;
        this.product = product;
    }

    @Override
    public StaticCatalog getCatalog() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BillingMode getRecurringBillingMode() {
        return BillingMode.IN_ADVANCE;
    }

    @Override
    public PlanPhase[] getInitialPhases() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Product getProduct() {
        return product;
    }

    @Override
    public String getPriceListName() {
        throw new UnsupportedOperationException();
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
    public Date getEffectiveDateForExistingSubscriptions() {
        return new Date();
    }

    @Override
    public Iterator<PlanPhase> getInitialPhaseIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlanPhase getFinalPhase() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BillingPeriod getRecurringBillingPeriod() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPlansAllowedInBundle() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlanPhase[] getAllPhases() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlanPhase findPhase(final String name) throws CatalogApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DateTime dateOfFirstRecurringNonZeroCharge(final DateTime subscriptionStartDate, PhaseType initialPhaseType) {
        throw new UnsupportedOperationException();
    }
}
