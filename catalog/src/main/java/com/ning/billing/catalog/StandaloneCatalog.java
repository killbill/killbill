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

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.*;
import com.ning.billing.catalog.rules.PlanRules;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

import javax.xml.bind.annotation.*;
import java.net.URI;
import java.util.Collection;
import java.util.Date;

@XmlRootElement(name="catalog")
@XmlAccessorType(XmlAccessType.NONE)
public class StandaloneCatalog extends ValidatingConfig<StandaloneCatalog> implements Catalog {
	@XmlElement(required=true)
	private Date effectiveDate;

	@XmlElement(required=true)
	private String catalogName;

	private URI catalogURI;

	@XmlElementWrapper(name="currencies", required=true)
	@XmlElement(name="currency", required=true)
	private Currency[] supportedCurrencies;

	@XmlElementWrapper(name="products", required=true)
	@XmlElement(name="product", required=true)
	private DefaultProduct[] products;

	@XmlElement(name="rules", required=true)
	private PlanRules planRules;

	@XmlElementWrapper(name="plans", required=true)
	@XmlElement(name="plan", required=true)
	private DefaultPlan[] plans;

	@XmlElement(name="priceLists", required=true)
	private DefaultPriceListSet priceLists;

	public StandaloneCatalog() {}

	protected StandaloneCatalog (Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getCalalogName()
	 */
	@Override
	public String getCatalogName() {
		return catalogName;
	}

	@Override
	public Date getEffectiveDate() {
		return effectiveDate;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getProducts()
	 */
	@Override
	public DefaultProduct[] getProducts() {
		return products;
	}


	@Override
	public Currency[] getSupportedCurrencies() {
		return supportedCurrencies;
	}

	@Override
	public DefaultPlan[] getPlans() {
		return plans;
	}

	public URI getCatalogURI() {
		return catalogURI;
	}

	public PlanRules getPlanRules() { 
		return planRules;
	}
	
	public DefaultPriceList getPriceListFromName(String priceListName) {
		return priceLists.findPriceListFrom(priceListName);
	}
	
	public DefaultPriceListSet getPriceLists() {
		return this.priceLists;
	}

	@Override
	public void configureEffectiveDate(Date date) {
		// Nothing to do here this is a method that is only implemented on VersionedCatalog
	}


	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getPlan(java.lang.String, java.lang.String)
	 */
	@Override
	public DefaultPlan findPlan(String productName, BillingPeriod period, String priceListName) throws CatalogApiException {
		Product product = findProduct(productName);
		DefaultPlan result = priceLists.getPlanListFrom(priceListName, product, period);
		if ( result == null) {
			throw new CatalogApiException(ErrorCode.CAT_PLAN_NOT_FOUND, productName, period.toString(), priceListName);
		}
		return result;
	}
	
	@Override
	public DefaultPlan findPlan(String name) throws CatalogApiException {
		if (name == null) {
			return null;
		}
		for(DefaultPlan p : plans) {
			if(p.getName().equals(name)) {
				return p;
			}
		}
		throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, name);
	}
	
	@Override
	public Product findProduct(String name) throws CatalogApiException {
		for(DefaultProduct p : products) {
			if (p.getName().equals(name)) {
				return p;
			}
		}
		throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PRODUCT, name);
	}

	@Override
	public DefaultPlanPhase findPhase(String name) throws CatalogApiException {
		for(DefaultPlan p : plans) {

			if(p.getFinalPhase().getName().equals(name)) {
				return p.getFinalPhase();
			}
			if (p.getInitialPhases() != null) {
				for(DefaultPlanPhase pp : p.getInitialPhases()) {
					if(pp.getName().equals(name)) {
						return pp;
					}
				}
			}
		}

		throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PHASE, name);
	}

	//////////////////////////////////////////////////////////////////////////////
	//
	// RULES
	//
	//////////////////////////////////////////////////////////////////////////////
	@Override
	public ActionPolicy planChangePolicy(PlanPhaseSpecifier from, PlanSpecifier to) throws CatalogApiException {
		return planRules.getPlanChangePolicy(from, to, this);
	}

	@Override
	public PlanAlignmentChange planChangeAlignment(PlanPhaseSpecifier from, PlanSpecifier to) throws CatalogApiException {
		return planRules.getPlanChangeAlignment(from, to, this);
	}

	@Override
	public ActionPolicy planCancelPolicy(PlanPhaseSpecifier planPhase) throws CatalogApiException {
		return planRules.getPlanCancelPolicy(planPhase, this);
	}

	@Override
	public PlanAlignmentCreate planCreateAlignment(PlanSpecifier specifier) throws CatalogApiException {
		return planRules.getPlanCreateAlignment(specifier, this);
	}

	@Override
	public BillingAlignment billingAlignment(PlanPhaseSpecifier planPhase) throws CatalogApiException {
		return planRules.getBillingAlignment(planPhase, this);
	}

	@Override
	public PlanChangeResult planChange(PlanPhaseSpecifier from, PlanSpecifier to)
			throws CatalogApiException {
		return planRules.planChange(from, to, this);
	}

	@Override
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors) {
		validate(catalog,errors, products);
		validate(catalog,errors, plans);
		priceLists.validate(catalog,errors);
		planRules.validate(catalog, errors);
		return errors;
	}

	private Collection<? extends ValidationError> validate(StandaloneCatalog catalog,
			ValidationErrors errors, ValidatingConfig<StandaloneCatalog>[] configs) {
		for(ValidatingConfig<StandaloneCatalog> config: configs) {

		}
		return null;
	}
	

	@Override
	public void initialize(StandaloneCatalog catalog, URI sourceURI) {
		catalogURI = sourceURI;
		super.initialize(catalog, sourceURI);
		planRules.initialize(catalog, sourceURI);
		priceLists.initialize(catalog, sourceURI);
		for(DefaultProduct p : products) {
			p.initialize(catalog, sourceURI);
		}
		for(DefaultPlan p : plans) {
			p.initialize(catalog, sourceURI);
		}
	}


	protected StandaloneCatalog setProducts(DefaultProduct[] products) {
		this.products = products;
		return this;
	}
	protected StandaloneCatalog setSupportedCurrencies(Currency[] supportedCurrencies) {
		this.supportedCurrencies = supportedCurrencies;
		return this;
	}

	protected StandaloneCatalog setPlanChangeRules(PlanRules planChangeRules) {
		this.planRules = planChangeRules;
		return this;
	}

	protected StandaloneCatalog setPlans(DefaultPlan[] plans) {
		this.plans = plans;
		return this;
	}

	protected StandaloneCatalog setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
		return this;
	}

	protected StandaloneCatalog setPlanRules(PlanRules planRules) {
		this.planRules = planRules;
		return this;
	}

	protected StandaloneCatalog setPriceLists(DefaultPriceListSet priceLists) {
		this.priceLists = priceLists;
		return this;
	}



}
