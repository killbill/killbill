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

import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PlanAlignment;

import java.util.Iterator;

public class MockPlan implements IPlan
{
    private final String name;
    private final IProduct product;

    public MockPlan(final String name, final IProduct product)
    {
        this.name = name;
        this.product = product;
    }

    @Override
    public IPlanPhase[] getInitialPhases()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public IProduct getProduct()
    {
        return product;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public Iterator<IPlanPhase> getInitialPhaseIterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPlanPhase getFinalPhase()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BillingPeriod getBillingPeriod()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BillingAlignment getBillingAlignment()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlanAlignment getPlanAlignment()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPlansAllowedInBundle()
    {
        throw new UnsupportedOperationException();
    }
}
