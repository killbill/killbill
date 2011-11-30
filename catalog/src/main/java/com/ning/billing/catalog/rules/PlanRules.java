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

package com.ning.billing.catalog.rules;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.PriceList;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.IllegalPlanChange;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanChangeResult;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PlanRules extends ValidatingConfig<Catalog>  {

	@XmlElementWrapper(name="changePolicy")
	@XmlElement(name="changePolicyCase", required=false)
	private CaseChangePlanPolicy[] changeCase;
	
	@XmlElementWrapper(name="changeAlignment")
	@XmlElement(name="changeAlignmentCase", required=false)
	private CaseChangePlanAlignment[] changeAlignmentCase;

	@XmlElementWrapper(name="cancelPolicy")
	@XmlElement(name="cancelPolicyCase", required=false)
	private CaseCancelPolicy[] cancelCase;

	@XmlElementWrapper(name="createAlignment")
	@XmlElement(name="createAlignmentCase", required=false)
	private CaseCreateAlignment[] createAlignmentCase;
	
	@XmlElementWrapper(name="billingAlignment")
	@XmlElement(name="billingAlignmentCase", required=false)
	private CaseBillingAlignment[] billingAlignmentCase;

	@XmlElementWrapper(name="priceList")
	@XmlElement(name="priceListCase", required=false)
	private CasePriceList[] priceListCase;

	public PlanAlignmentCreate getPlanCreateAlignment(PlanSpecifier specifier, Catalog catalog) throws CatalogApiException {
		return Case.getResult(createAlignmentCase, specifier, catalog);      
    }
	
	public ActionPolicy getPlanCancelPolicy(PlanPhaseSpecifier planPhase, Catalog catalog) throws CatalogApiException {
		return CasePhase.getResult(cancelCase, planPhase, catalog);      
	}

	public BillingAlignment getBillingAlignment(PlanPhaseSpecifier planPhase, Catalog catalog) throws CatalogApiException {
		return CasePhase.getResult(billingAlignmentCase, planPhase, catalog);      
	}

	public PlanChangeResult planChange(PlanPhaseSpecifier from, PlanSpecifier to, Catalog catalog) throws CatalogApiException {
		PriceList priceList = catalog.getPriceListFromName(to.getPriceListName());
		if( priceList== null ) {
			priceList = findPriceList(from.toPlanSpecifier(), catalog);
			to = new PlanSpecifier(to.getProductName(), to.getProductCategory(), to.getBillingPeriod(), priceList.getName());
		} 
		
		ActionPolicy policy = getPlanChangePolicy(from, to, catalog);
		if(policy == ActionPolicy.ILLEGAL) {
			throw new IllegalPlanChange(from, to);
		}
		
		PlanAlignmentChange alignment = getPlanChangeAlignment(from, to, catalog);
		
		return new PlanChangeResult(priceList, policy, alignment);
	}
	
	public PlanAlignmentChange getPlanChangeAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to, Catalog catalog) throws CatalogApiException {
		return CaseChange.getResult(changeAlignmentCase, from, to, catalog);      
    }

	public ActionPolicy getPlanChangePolicy(PlanPhaseSpecifier from,
			PlanSpecifier to, Catalog catalog) throws CatalogApiException {
		if(from.getProductName().equals(to.getProductName()) &&
				from.getBillingPeriod() == to.getBillingPeriod() &&
				from.getPriceListName().equals(to.getPriceListName())) {
			return ActionPolicy.ILLEGAL;
		}
		
		return CaseChange.getResult(changeCase, from, to, catalog); 
	}
	
	private PriceList findPriceList(PlanSpecifier specifier, Catalog catalog) throws CatalogApiException {
		PriceList result = Case.getResult(priceListCase, specifier, catalog);
		if (result == null) {
			result = catalog.getPriceListFromName(specifier.getPriceListName());
		}
		return result;
	}
	
	
	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
	    //TODO: MDW - Validation: check that the plan change special case pairs are unique!
	    //TODO: MDW - Validation: check that the each product appears in at most one tier.
		//TODO: MDW - Unit tests for rules
		//TODO: MDW - validate that there is a default policy for change AND cancel

		return errors;
	}

	
	/////////////////////////////////////////////////////////////////////////////////////
	// Setters for testing
	/////////////////////////////////////////////////////////////////////////////////////
	
	protected PlanRules setChangeCase(CaseChangePlanPolicy[] changeCase) {
		this.changeCase = changeCase;
		return this;
	}

	protected PlanRules setChangeAlignmentCase(
			CaseChangePlanAlignment[] changeAlignmentCase) {
		this.changeAlignmentCase = changeAlignmentCase;
		return this;
	}

	protected PlanRules setCancelCase(CaseCancelPolicy[] cancelCase) {
		this.cancelCase = cancelCase;
		return this;
	}

	protected PlanRules setCreateAlignmentCase(CaseCreateAlignment[] createAlignmentCase) {
		this.createAlignmentCase = createAlignmentCase;
		return this;
	}

	protected PlanRules setBillingAlignmentCase(
			CaseBillingAlignment[] billingAlignmentCase) {
		this.billingAlignmentCase = billingAlignmentCase;
		return this;
	}

	protected PlanRules setPriceListCase(CasePriceList[] priceListCase) {
		this.priceListCase = priceListCase;
		return this;
	}

}
