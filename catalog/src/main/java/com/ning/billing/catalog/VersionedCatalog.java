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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

public class VersionedCatalog extends ValidatingConfig<Catalog> implements ICatalog {
	
	private Catalog currentCatalog;
	
	private final List<Catalog> versions = new ArrayList<Catalog>();
	
	public VersionedCatalog() {
		Catalog baseline = new Catalog(new Date(0)); // init with an empty catalog may need to 
													 // populate some empty pieces here to make validation work
		add(baseline);
	}
	
	private Catalog versionForDate(Date date) {
		Catalog previous = versions.get(0);
		for(Catalog c : versions) {
			if(c.getEffectiveDate().getTime() > date.getTime()) {
				return previous;
			}
			previous = c;
		}
		return versions.get(versions.size() - 1);
	}

	public void add(Catalog e) {
		if(currentCatalog == null) {
			currentCatalog = e;
		}
		versions.add(e);
		Collections.sort(versions,new Comparator<Catalog>() {
			@Override
			public int compare(Catalog c1, Catalog c2) {
				return c1.getEffectiveDate().compareTo(c2.getEffectiveDate());
			}
		});
	}

	public Iterator<Catalog> iterator() {
		return versions.iterator();
	}
	
	@Override
	public void configureEffectiveDate(Date date) {
		currentCatalog = versionForDate(date); // 
	}

	public int size() {
		return versions.size();
	}

	@Override
	public Product[] getProducts() {
		return currentCatalog.getProducts();
	}

	@Override
	public PriceList[] getPriceLists() {
		return currentCatalog.getPriceLists();
	}

	@Override
	public PriceList getPriceListFromName(String planSetName) {
		return currentCatalog.getPriceListFromName(planSetName);
	}

	@Override
	public Plan getPlan(String productName, BillingPeriod term,
			String planSetName) {
		return currentCatalog.getPlan(productName, term, planSetName);
	}

	@Override
	public Currency[] getSupportedCurrencies() {
		return currentCatalog.getSupportedCurrencies();
	}

	@Override
	public Plan[] getPlans() {
		return currentCatalog.getPlans();
	}

	@Override
	public Plan getPlanFromName(String name) {
		return currentCatalog.getPlanFromName(name);
	}


	@Override
	public IPlanPhase getPhaseFromName(String name) {
		return currentCatalog.getPhaseFromName(name);
	}

	@Override
	public Date getEffectiveDate() {
		return currentCatalog.getEffectiveDate();
	}

	@Override
	public void initialize(Catalog catalog) {
		for(Catalog c : versions) {
			c.initialize(catalog);
		}
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		for(Catalog c : versions) {
			errors.addAll(c.validate(c, errors));
		}
		return errors;
	}
	
	@Override
    public PlanPhase getPhaseFor(String name, Date date) {
    	Catalog c = versionForDate(date);
    	return c.getPhaseFromName(name);
    }

	@Override
	public ActionPolicy getPlanChangePolicy(PlanPhaseSpecifier from,
			PlanSpecifier to) {
		return currentCatalog.getPlanChangePolicy(from, to);
	}

	@Override
	public IProduct getProductFromName(String name) {
		return currentCatalog.getProductFromName(name);
	}

	@Override
	public ActionPolicy getPlanCancelPolicy(PlanPhaseSpecifier planPhase) {
		return currentCatalog.getPlanCancelPolicy(planPhase);
	}

	@Override
	public PlanAlignmentChange getPlanChangeAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to) {
		return currentCatalog.getPlanChangeAlignment(from, to);
	}

	@Override
	public PlanAlignmentCreate getPlanCreateAlignment(PlanSpecifier specifier) {
		return currentCatalog.getPlanCreateAlignment(specifier);
	}

	@Override
	public String getCalalogName() {
		return currentCatalog.getCalalogName();
	}

	@Override
	public BillingAlignment getBillingAlignment(PlanPhaseSpecifier planPhase) {
		return currentCatalog.getBillingAlignment(planPhase);
	}
	
	//TODO MDW validation - ensure all catalog versions have a single name
	//TODO MDW validation - ensure effective dates are different (actually do we want this?)
 
}
