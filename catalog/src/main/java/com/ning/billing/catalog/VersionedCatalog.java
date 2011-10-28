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
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.IProductType;
import com.ning.billing.catalog.api.PlanAlignment;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;

public class VersionedCatalog extends ValidatingConfig implements ICatalog {
	
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
	
	public void applyEffectiveDate(Date date) {
		currentCatalog = versionForDate(date); // 
	}

	public int size() {
		return versions.size();
	}

	public boolean equals(Object arg0) {
		return currentCatalog.equals(arg0);
	}

	public ProductType[] getProductTypes() {
		return currentCatalog.getProductTypes();
	}

	public Product[] getProducts() {
		return currentCatalog.getProducts();
	}

	public void setProducts(Product[] products) {
		currentCatalog.setProducts(products);
	}

	public PriceList[] getPriceLists() {
		return currentCatalog.getPriceLists();
	}

	public void setPlanSets(PriceList[] planSets) {
		currentCatalog.setPlanSets(planSets);
	}

	public PriceList getPriceListFromName(String planSetName) {
		return currentCatalog.getPriceListFromName(planSetName);
	}

	public List<IProduct> getProductsForType(IProductType productType) {
		return currentCatalog.getProductsForType(productType);
	}

	public Plan getPlan(String productName, BillingPeriod term,
			String planSetName) {
		return currentCatalog.getPlan(productName, term, planSetName);
	}

	public void setProductTypes(ProductType[] productTypes) {
		currentCatalog.setProductTypes(productTypes);
	}

	public Currency[] getSupportedCurrencies() {
		return currentCatalog.getSupportedCurrencies();
	}

	public Plan[] getPlans() {
		return currentCatalog.getPlans();
	}

	public IPlan getPlanFromName(String name) {
		return currentCatalog.getPlanFromName(name);
	}

	public IPlanPhase getPhaseFromName(String name) {
		return currentCatalog.getPhaseFromName(name);
	}

	public Date getEffectiveDate() {
		return currentCatalog.getEffectiveDate();
	}

	public int hashCode() {
		return currentCatalog.hashCode();
	}

	public void initialize(Catalog catalog) {
		currentCatalog.initialize(catalog);
	}

	public void setSupportedCurrencies(Currency[] supportedCurrencies) {
		currentCatalog.setSupportedCurrencies(supportedCurrencies);
	}

	public void setPlanChangeRules(PlanRules planChangeRules) {
		currentCatalog.setPlanChangeRules(planChangeRules);
	}

	public void setPlans(Plan[] plans) {
		currentCatalog.setPlans(plans);
	}

	public void setEffectiveDate(Date effectiveDate) {
		currentCatalog.setEffectiveDate(effectiveDate);
	}

	public String toString() {
		return currentCatalog.toString();
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		for(Catalog c : versions) {
			errors.addAll(c.validate());
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
	public PlanAlignment getPlanAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to) {
		return currentCatalog.getPlanAlignment(from, to);
	}
 
}
