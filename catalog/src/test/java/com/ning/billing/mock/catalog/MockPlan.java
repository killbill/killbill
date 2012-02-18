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

package com.ning.billing.mock.catalog;

import java.util.Date;
import java.util.Iterator;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;

public class MockPlan implements Plan {

	@Override
	public PlanPhase[] getInitialPhases() {
		return null;
	}

	@Override
	public Product getProduct() {
		return null;
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public boolean isRetired() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Iterator<PlanPhase> getInitialPhaseIterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PlanPhase getFinalPhase() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BillingPeriod getBillingPeriod() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getPlansAllowedInBundle() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public PlanPhase[] getAllPhases() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getEffectiveDateForExistingSubscriptons() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PlanPhase findPhase(String name) throws CatalogApiException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTime dateOfFirstRecurringNonZeroCharge(
			DateTime subscriptionStartDate) {
		// TODO Auto-generated method stub
		return null;
	}

}
