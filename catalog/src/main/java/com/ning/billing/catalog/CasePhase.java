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

import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.util.config.ValidationErrors;

public abstract class CasePhase<T> extends Case<T> {

	@XmlElement(required=false)
	private PhaseType phaseType;

	public CasePhase() {}

	public CasePhase(Product product, ProductCategory productCategory,
			BillingPeriod billingPeriod, PriceList priceList,
			PhaseType phaseType, T result) {
		super(product, productCategory, billingPeriod, priceList, result);
		this.phaseType = phaseType;
	}
	
	
	public T getResult(PlanPhaseSpecifier specifier, Catalog c) {
		if (	
				(phaseType       == null || specifier.getPhaseType() == null || specifier.getPhaseType() == phaseType) &&
				satisfiesCase(new PlanSpecifier(specifier), c)
				) {
			return getResult(); 
		}
		return null;
	}
	
	public static <K> K getResult(CasePhase<K>[] cases, PlanPhaseSpecifier planSpec, Catalog catalog) {
    	if(cases != null) {
    		for(int i = cases.length - 1; i >=0; i --) {
    			K result = cases[i].getResult(planSpec, catalog);
    			if(result != null) { 
    				return result; 
    			}        					
    		}
    	}
        return null;
        
    }

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		// TODO Auto-generated method stub
		return null;
	}
}
