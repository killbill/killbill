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
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PlanRules extends ValidatingConfig<Catalog>  {

	@XmlElementWrapper(name="tiers", required=true)
	@XmlElement(name="tier", required=false) // may not have tiers in some catalogs
	private ProductTier[] productTiers;

	@XmlElement(name="changePolicyRule", required=true)
	private PlanPolicyChangeRule[] rules;

	@XmlElement(name="changePolicyCase", required=false)
	private CaseChangePlanPolicy[] changeCase;
	
	@XmlElement(name="changeAlignmentCase", required=false)
	private CaseChangePlanAlignment[] changeAlignmentCase;

	@XmlElement(name="cancelPolicyCase", required=false)
	private CaseCancelPolicy[] cancelCase;

	@XmlElement(name="createAlignmentCase", required=false)
	private CaseCreateAlignment[] createAlignmentCase;
	
	@XmlElement(name="billingAlignmentCase", required=false)
	private CaseBillingAlignment[] billingAlignmentCase;

	@XmlElement(name="priceListCase", required=false)
	private CasePriceList[] priceListCase;

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		return errors;

	}
	
	public PlanRules(){}

	//For test
	protected PlanRules(ProductTier[] productTiers, PlanPolicyChangeRule[] rules,
			CaseChangePlanPolicy[] changeCase, CaseCancelPolicy[] cancelCase,
			CaseChangePlanAlignment[] changeAlignmentCase,
			CaseCreateAlignment[] createAlignmentCase) {
		super();
		this.productTiers = productTiers;
		this.rules = rules;
		this.changeCase = changeCase;
		this.cancelCase = cancelCase;
		this.changeAlignmentCase = changeAlignmentCase;
		this.createAlignmentCase = createAlignmentCase;
	}

	public ActionPolicy getPlanChangePolicy(PlanPhaseSpecifier from,
			PlanSpecifier to, Catalog catalog) {
		
		ActionPolicy policy = CaseChange.getResult(changeCase, from, to, catalog); 
		if (policy != null) {
			return policy;
		}

    	
        for(int i = rules.length - 1; i >=0; i --) {
        	int fromProductIndex       = getProductIndex(catalog.getProductFromName(from.getProductName()));
        	int fromBillingPeriodIndex = getBillingPeriodIndex(from.getBillingPeriod());
			int toProductIndex         = getProductIndex(catalog.getProductFromName(to.getProductName()));
			int toBillingPeriodIndex   = getBillingPeriodIndex(to.getBillingPeriod());
			
			policy = rules[i].getPlanChangePolicy(
        		fromProductIndex, fromBillingPeriodIndex,
        		toProductIndex, toBillingPeriodIndex,
        		from.getPhaseType());
        	if (policy != null) { return policy; }        
        }
        return null;
        
    }
	
	private int getProductIndex(IProduct src) {
		for(ProductTier tier : productTiers) {
			for(int i = 0; i < tier.getProducts().length; i++ ){
				if (src.equals(tier.getProducts()[i])) {
					return i;
				}
			}
		}
		return 0;
	}
	public PlanAlignmentChange getPlanChangeAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to, Catalog catalog) {
		return CaseChange.getResult(changeAlignmentCase, from, to, catalog);      
    }

	public PlanAlignmentCreate getPlanCreateAlignment(PlanSpecifier specifier, Catalog catalog) {
		return Case.getResult(createAlignmentCase, specifier, catalog);      
    }
	
	public ActionPolicy getPlanCancelPolicy(PlanPhaseSpecifier planPhase, Catalog catalog) {
		return CasePhase.getResult(cancelCase, planPhase, catalog);      
	}

	public BillingAlignment getBillingAlignment(PlanPhaseSpecifier planPhase, Catalog catalog) {
		return CasePhase.getResult(billingAlignmentCase, planPhase, catalog);      
	}

	private int getBillingPeriodIndex(BillingPeriod src) {
		return src.ordinal();
	}


	protected void setProductTiers(ProductTier[] productTiers) {
		this.productTiers = productTiers;
	}



	
    //TODO: MDW - Validation: check that the plan change special case pairs are unique!
    //TODO: MDW - Validation: check that the each product appears in at most one tier.
	//TODO: MDW - Unit tests for rules
	//TODO: MDW - validate that there is a default policy for change AND cancel

}
