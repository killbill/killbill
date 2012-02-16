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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;

import java.util.Date;
import java.util.Iterator;

import org.joda.time.DateTime;

public class MockPlan implements Plan
{
    private final String name;
    private final Product product;

    public MockPlan(final String name, final Product product)
    {
        this.name = name;
        this.product = product;
    }

    @Override
    public PlanPhase[] getInitialPhases()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Product getProduct()
    {
        return product;
    }

    @Override
    public String getName()
    {
        return name;
    }
    
    @Override
    public Date getEffectiveDateForExistingSubscriptons()
    {
        return new Date();
    }

    @Override
    public Iterator<PlanPhase> getInitialPhaseIterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlanPhase getFinalPhase()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BillingPeriod getBillingPeriod()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPlansAllowedInBundle()
    {
        throw new UnsupportedOperationException();
    }

	@Override
	public PlanPhase[] getAllPhases() {
		 throw new UnsupportedOperationException();
	}

	@Override
	public PlanPhase findPhase(String name) throws CatalogApiException {
		 throw new UnsupportedOperationException();
	}

	@Override
	public boolean isRetired() {
		return false;
	}

	@Override
	public DateTime dateOfFirstRecurringNonZeroCharge(
			DateTime subscriptionStartDate) {
		 throw new UnsupportedOperationException();
	}
}
