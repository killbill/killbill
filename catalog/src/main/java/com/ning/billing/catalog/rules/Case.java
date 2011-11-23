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

import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.PriceList;
import com.ning.billing.catalog.Product;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

public abstract class Case<T> extends ValidatingConfig<Catalog> {

	protected Product product;
	protected ProductCategory productCategory;
	protected BillingPeriod billingPeriod;
	protected PriceList priceList;

	protected abstract T getResult();

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
    		for(Case<K> c : cases) {
    			K result = c.getResult(planSpec, catalog);
    			if(result != null) { 
    				return result; 
    			}        					
    		}
    	}
        return null;
        
    }
	
	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		return errors;
	}

	protected Case<T> setProduct(Product product) {
		this.product = product;
		return this;
	}

	protected Case<T> setProductCategory(ProductCategory productCategory) {
		this.productCategory = productCategory;
		return this;
	}

	protected Case<T> setBillingPeriod(BillingPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
		return this;
	}

	protected Case<T> setPriceList(PriceList priceList) {
		this.priceList = priceList;
		return this;
	}

	
	
}
