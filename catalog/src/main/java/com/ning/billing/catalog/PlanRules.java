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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PlanAlignment;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;

@XmlAccessorType(XmlAccessType.NONE)
public class PlanRules extends ValidatingConfig  {

	@XmlElementWrapper(name="tiers", required=true)
	@XmlElement(name="tier", required=false) // may not have tiers in some catalogs
	private ProductTier[] productTiers;

	@XmlElement(name="changeRule", required=true)
	private PlanChangeRule[] rules;

	@XmlElement(name="changeCase", required=false)
	private PlanChangeCase[] changeCase;
	
	@XmlElement(name="cancelCase", required=false)
	private PlanCancelCase[] cancelCase;

	@XmlElement(name="alignmentCase", required=false)
	private PlanAlignmentCase[] alignmentCase;

	public PlanChangeRule[] getRules() {
		return rules;
	}

	public void setGeneralRules(PlanChangeRule[] generalRules) {
		this.rules = generalRules;
	}

	public PlanChangeCase[] getSpecialCase() {
		return changeCase;
	}

	protected void setSpecialCaseRules(PlanChangeCase[] specialchangeCaseCaseRules) {
		this.changeCase = specialchangeCaseCaseRules;
	}
	
	protected void setCancelCaseRules(PlanCancelCase[] cancelCase) {
		this.cancelCase = cancelCase;
	}
	
	public void setAlignmentCase(PlanAlignmentCase[] alignmentCase) {
		this.alignmentCase = alignmentCase;
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		return errors;

	}

	public ActionPolicy getPlanChangePolicy(PlanPhaseSpecifier from,
			PlanSpecifier to, Catalog catalog) {
    	if(changeCase != null) {
    		for(int i = changeCase.length - 1; i >=0; i --) {
    			ActionPolicy policy = changeCase[i].getPlanChangePolicy(from, to, catalog);
    			if (policy != null) { return policy; }        					
    		}
    	}
        for(int i = rules.length - 1; i >=0; i --) {
        	int fromProductIndex       = getProductIndex(catalog.getProductFromName(from.getProductName()));
        	int fromBillingPeriodIndex = getBillingPeriodIndex(from.getBillingPeriod());
			int toProductIndex         = getProductIndex(catalog.getProductFromName(to.getProductName()));
			int toBillingPeriodIndex   = getBillingPeriodIndex(to.getBillingPeriod());
			
        	ActionPolicy policy = rules[i].getPlanChangePolicy(
        		fromProductIndex, fromBillingPeriodIndex,
        		toProductIndex, toBillingPeriodIndex,
        		from.getPhaseType());
        	if (policy != null) { return policy; }        
        }
        return null;
        
    }

	public PlanAlignment getPlanAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to, Catalog catalog) {
    	if(alignmentCase != null) {
    		for(int i = alignmentCase.length - 1; i >=0; i --) {
    			PlanAlignment alignment = alignmentCase[i].getPlanAlignment(from, to, catalog);
    			if(alignment != null) { 
    				return alignment; 
    			}        					
    		}
    	}
        return null;
        
    }

	
	public ActionPolicy getPlanCancelPolicy(PlanPhaseSpecifier planPhase, Catalog catalog) {
    	if(cancelCase != null) {
    		for(int i = cancelCase.length - 1; i >=0; i --) {
    			ActionPolicy policy = cancelCase[i].getPlanCancelPolicy(planPhase, catalog);
    			if (policy != null) { 
    				return policy; 
    			}        					
    		}
    	}

		return null;
	}

	private int getBillingPeriodIndex(BillingPeriod src) {
		return src.ordinal();
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

	protected void setProductTiers(ProductTier[] productTiers) {
		this.productTiers = productTiers;
	}


	
    //TODO: MDW - Validation: check that the plan change special case pairs are unique!
    //TODO: MDW - Validation: check that the each product appears in at most one tier.
	//TODO: MDW - Unit tests for rules
	//TODO: MDW - validate that there is a default policy for change AND cancel

}
