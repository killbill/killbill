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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanChangeResult;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlRootElement(name="catalog")
@XmlAccessorType(XmlAccessType.NONE)
public class VersionedCatalog extends ValidatingConfig<StandaloneCatalog> implements Catalog {
	
	private StandaloneCatalog currentCatalog;
	
	@XmlElement(name="catalogVersion", required=true)
	private final List<StandaloneCatalog> versions = new ArrayList<StandaloneCatalog>();
	
	@Inject
	public VersionedCatalog() {
		StandaloneCatalog baseline = new StandaloneCatalog(new Date(0)); // init with an empty catalog may need to 
													 // populate some empty pieces here to make validation work
		add(baseline); 
	}
	
	private StandaloneCatalog versionForDate(Date date) {
		StandaloneCatalog previous = versions.get(0);
		for(StandaloneCatalog c : versions) {
			if(c.getEffectiveDate().getTime() > date.getTime()) {
				return previous;
			}
			previous = c;
		}
		return versions.get(versions.size() - 1);
	}

	public void add(StandaloneCatalog e) {
		if(currentCatalog == null) {
			currentCatalog = e;
		}
		versions.add(e);
		Collections.sort(versions,new Comparator<StandaloneCatalog>() {
			@Override
			public int compare(StandaloneCatalog c1, StandaloneCatalog c2) {
				return c1.getEffectiveDate().compareTo(c2.getEffectiveDate());
			}
		});
	}

	public Iterator<StandaloneCatalog> iterator() {
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
	public DefaultProduct[] getProducts() {
		return currentCatalog.getProducts();
	}

	@Override
	public Currency[] getSupportedCurrencies() {
		return currentCatalog.getSupportedCurrencies();
	}

	@Override
	public DefaultPlan[] getPlans() {
		return currentCatalog.getPlans();
	}

	@Override
	public Date getEffectiveDate() {
		return currentCatalog.getEffectiveDate();
	}

	@Override
	public Plan findPlan(String productName, BillingPeriod term,
			String planSetName) throws CatalogApiException {
		return currentCatalog.findPlan(productName, term, planSetName);
	}

	@Override
	public DefaultPlan findPlan(String name) throws CatalogApiException {
		return currentCatalog.findPlan(name);
	}

	@Override
	public PlanPhase findPhase(String name) throws CatalogApiException {
		return currentCatalog.findPhase(name);
	}

	@Override
	public Product findProduct(String name) throws CatalogApiException {
		return currentCatalog.findProduct(name);
	}

	@Override
	public void initialize(StandaloneCatalog catalog, URI sourceURI) {
		for(StandaloneCatalog c : versions) {
			c.initialize(catalog, sourceURI);
		}
	}

	@Override
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors) {
		for(StandaloneCatalog c : versions) {
			errors.addAll(c.validate(c, errors));
		}
		return errors;
	}
	
	@Override
	public ActionPolicy planChangePolicy(PlanPhaseSpecifier from,
			PlanSpecifier to) throws CatalogApiException {
		return currentCatalog.planChangePolicy(from, to);
	}

	@Override
	public ActionPolicy planCancelPolicy(PlanPhaseSpecifier planPhase) throws CatalogApiException {
		return currentCatalog.planCancelPolicy(planPhase);
	}

	@Override
	public PlanAlignmentChange planChangeAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to) throws CatalogApiException {
		return currentCatalog.planChangeAlignment(from, to);
	}

	@Override
	public PlanAlignmentCreate planCreateAlignment(PlanSpecifier specifier) throws CatalogApiException {
		return currentCatalog.planCreateAlignment(specifier);
	}

	@Override
	public String getCalalogName() {
		return currentCatalog.getCalalogName();
	}

	@Override
	public BillingAlignment billingAlignment(PlanPhaseSpecifier planPhase) throws CatalogApiException {
		return currentCatalog.billingAlignment(planPhase);
	}

	@Override
	public PlanChangeResult planChange(PlanPhaseSpecifier from, PlanSpecifier to)
			throws CatalogApiException {
		return currentCatalog.planChange(from, to);
	}
	
	//TODO MDW validation - ensure all catalog versions have a single name
	//TODO MDW validation - ensure effective dates are different (actually do we want this?)
 
}
