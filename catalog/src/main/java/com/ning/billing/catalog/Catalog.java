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
import java.util.Collection;
import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.IllegalPlanChange;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanChangeResult;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.rules.PlanRules;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class Catalog extends ValidatingConfig<Catalog> implements ICatalog {
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
	private Product[] products;

	@XmlElement(name="rules", required=true)
	private PlanRules planRules;

	@XmlElementWrapper(name="plans", required=true)
	@XmlElement(name="plan", required=true)
	private Plan[] plans;

	@XmlElement(name="priceLists", required=true)
	private PriceListSet priceLists;

	public Catalog() {}

	protected Catalog (Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.ICatalog#getCalalogName()
	 */
	@Override
	public String getCalalogName() {
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
	public Product[] getProducts() {
		return products;
	}


	@Override
	public Currency[] getSupportedCurrencies() {
		return supportedCurrencies;
	}

	@Override
	public Plan[] getPlans() {
		return plans;
	}

	public URI getCatalogURI() {
		return catalogURI;
	}

	public PlanRules getPlanRules() { 
		return planRules;
	}
	
	public PriceList getPriceListFromName(String priceListName) {
		return priceLists.findPriceListFrom(priceListName);
	}
	
	public PriceListSet getPriceLists() {
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
	public Plan findPlan(String productName, BillingPeriod period, String priceListName) throws CatalogApiException {
		IProduct product = findProduct(productName);
		Plan result = priceLists.getPlanListFrom(priceListName, product, period);
		if ( result == null) {
			throw new CatalogApiException(ErrorCode.CAT_PLAN_NOT_FOUND, productName, period.toString(), priceListName);
		}
		return result;
	}
	
	@Override
	public Plan findPlan(String name) throws CatalogApiException {
		if (name == null) {
			return null;
		}
		for(Plan p : plans) {
			if(p.getName().equals(name)) {
				return p;
			}
		}
		throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, name);
	}
	
	@Override
	public IProduct findProduct(String name) throws CatalogApiException {
		for(Product p : products) {
			if (p.getName().equals(name)) {
				return p;
			}
		}
		throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PRODUCT, name);
	}

	@Override
	public PlanPhase findPhase(String name) throws CatalogApiException {
		for(Plan p : plans) {

			if(p.getFinalPhase().getName().equals(name)) {
				return p.getFinalPhase();
			}
			if (p.getInitialPhases() != null) {
				for(PlanPhase pp : p.getInitialPhases()) {
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
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		validate(catalog,errors, products);
		validate(catalog,errors, plans);
		priceLists.validate(catalog,errors);
		planRules.validate(catalog, errors);
		return errors;
	}

	private Collection<? extends ValidationError> validate(Catalog catalog,
			ValidationErrors errors, ValidatingConfig<Catalog>[] configs) {
		for(ValidatingConfig<Catalog> config: configs) {

		}
		return null;
	}
	

	@Override
	public void initialize(Catalog catalog, URI sourceURI) {
		catalogURI = sourceURI;
		super.initialize(catalog, sourceURI);
		planRules.initialize(catalog, sourceURI);
		priceLists.initialize(catalog, sourceURI);
		for(Product p : products) {
			p.initialize(catalog, sourceURI);
		}
		for(Plan p : plans) {
			p.initialize(catalog, sourceURI);
		}
	}


	protected Catalog setProducts(Product[] products) {
		this.products = products;
		return this;
	}
	protected Catalog setSupportedCurrencies(Currency[] supportedCurrencies) {
		this.supportedCurrencies = supportedCurrencies;
		return this;
	}

	protected Catalog setPlanChangeRules(PlanRules planChangeRules) {
		this.planRules = planChangeRules;
		return this;
	}

	protected Catalog setPlans(Plan[] plans) {
		this.plans = plans;
		return this;
	}

	protected Catalog setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
		return this;
	}

	protected Catalog setPlanRules(PlanRules planRules) {
		this.planRules = planRules;
		return this;
	}

	protected Catalog setPriceLists(PriceListSet priceLists) {
		this.priceLists = priceLists;
		return this;
	}



}
