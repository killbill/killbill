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

import com.ning.billing.catalog.DefaultPriceList;
import com.ning.billing.catalog.DefaultProduct;
import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

public abstract class Case<T> extends ValidatingConfig<StandaloneCatalog> {

	protected DefaultProduct product;
	protected ProductCategory productCategory;
	protected BillingPeriod billingPeriod;
	protected DefaultPriceList priceList;

	protected abstract T getResult();

	public T getResult(PlanSpecifier planPhase, StandaloneCatalog c) throws CatalogApiException {
		if (satisfiesCase(planPhase, c)	) {
			return getResult(); 
		}
		return null;
	}
	
	protected boolean satisfiesCase(PlanSpecifier planPhase, StandaloneCatalog c) throws CatalogApiException {
		return (product         == null || product.equals(c.findProduct(planPhase.getProductName()))) &&
		(productCategory == null || productCategory.equals(planPhase.getProductCategory())) &&
		(billingPeriod   == null || billingPeriod.equals(planPhase.getBillingPeriod())) &&
		(priceList       == null || priceList.equals(c.getPriceListFromName(planPhase.getPriceListName())));
	}

	public static <K> K getResult(Case<K>[] cases, PlanSpecifier planSpec, StandaloneCatalog catalog) throws CatalogApiException {
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
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors) {
		return errors;
	}

	protected Case<T> setProduct(DefaultProduct product) {
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

	protected Case<T> setPriceList(DefaultPriceList priceList) {
		this.priceList = priceList;
		return this;
	}

	
	
}
