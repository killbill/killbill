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

package com.ning.billing.catalog;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.TimeUnit;

import javax.annotation.Nullable;

public class MockPlanPhase extends DefaultPlanPhase {

    public MockPlanPhase(
    		BillingPeriod billingPeriod, 
    		PhaseType type, 
    		DefaultDuration duration, 
    		DefaultInternationalPrice recurringPrice, 
    		DefaultInternationalPrice fixedPrice) {
		setBillingPeriod(billingPeriod);
		setPhaseType(type);
		setDuration(duration);
		setReccuringPrice(recurringPrice);
		setFixedPrice(fixedPrice);
	}
    
    public MockPlanPhase() {
        this(new MockInternationalPrice(), null);
	}

    public MockPlanPhase(@Nullable MockInternationalPrice recurringPrice,
                         @Nullable MockInternationalPrice fixedPrice) {
        this(recurringPrice, fixedPrice, BillingPeriod.MONTHLY);
	}

    public MockPlanPhase(@Nullable MockInternationalPrice recurringPrice,
                         @Nullable MockInternationalPrice fixedPrice,
                         BillingPeriod billingPeriod) {
		this(recurringPrice, fixedPrice, billingPeriod, PhaseType.EVERGREEN);
	}

    public MockPlanPhase(@Nullable MockInternationalPrice recurringPrice,
                         @Nullable MockInternationalPrice fixedPrice,
                         BillingPeriod billingPeriod,
                         PhaseType phaseType) {
		setBillingPeriod(billingPeriod);
		setPhaseType(phaseType);
		setDuration(new DefaultDuration().setNumber(-1).setUnit(TimeUnit.UNLIMITED));
		setReccuringPrice(recurringPrice);
		setFixedPrice(fixedPrice);
		setPlan(new MockPlan(this));
	}

	public MockPlanPhase(MockPlan mockPlan) {
		setBillingPeriod(BillingPeriod.MONTHLY);
		setPhaseType(PhaseType.EVERGREEN);
		setDuration(new DefaultDuration().setNumber(-1).setUnit(TimeUnit.UNLIMITED));
		setReccuringPrice(new MockInternationalPrice());
		setFixedPrice(null);
		setPlan(mockPlan);
	}

	
}
