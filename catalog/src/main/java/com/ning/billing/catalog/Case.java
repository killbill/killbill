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
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;

public abstract class Case<T> extends ValidatingConfig {

	@XmlElement(required=false)
	@XmlIDREF
	private Product product;

	@XmlElement(required=false)
	private ProductCategory productCategory;

	@XmlElement(required=false)
	private BillingPeriod billingPeriod;
	
	@XmlElement(required=false)
	private PriceList priceList;

	protected abstract T getResult();
	
	public Case() {}
	
	protected Case (
			Product product, 
			ProductCategory productCategory, 
			BillingPeriod billingPeriod, 
			PriceList priceList, 
			T result
			) {
		this.product = product;
		this.productCategory = productCategory;
		this.billingPeriod = billingPeriod;
		this.priceList = priceList;
	}


	public T getResult(PlanSpecifier planPhase, Catalog c) {
		if (satisfiesCase(planPhase, c)	) {
			return getResult(); 
		}
		return null;
	}
	
	protected boolean satisfiesCase(PlanSpecifier planPhase, Catalog c) {
		return (product         == null || product.equals(c.getProductFromName(planPhase.getProductName()))) &&
		(productCategory == null || productCategory.equals(planPhase.getProductCategory())) &&
		(billingPeriod   == null || billingPeriod.equals(planPhase.getBillingPeriod())) &&
		(priceList       == null || priceList.equals(c.getPriceListFromName(planPhase.getPriceListName())));
	}

	public static <K> K getResult(Case<K>[] cases, PlanSpecifier planSpec, Catalog catalog) {
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
