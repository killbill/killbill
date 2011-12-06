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
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.DefaultPriceList;
import com.ning.billing.catalog.DefaultProduct;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class CaseChange<T>  extends ValidatingConfig<StandaloneCatalog> {

	@XmlElement(required=false)
	private PhaseType phaseType;

	@XmlElement(required=false)
	@XmlIDREF
	private DefaultProduct fromProduct;

	@XmlElement(required=false)
	private ProductCategory fromProductCategory;

	@XmlElement(required=false)
	private BillingPeriod fromBillingPeriod;

	@XmlElement(required=false)
	@XmlIDREF
	private DefaultPriceList fromPriceList;

	@XmlElement(required=false)
	@XmlIDREF
	private DefaultProduct toProduct;

	@XmlElement(required=false)
	private ProductCategory toProductCategory;

	@XmlElement(required=false)
	private BillingPeriod toBillingPeriod;

	@XmlElement(required=false)
	@XmlIDREF
	private DefaultPriceList toPriceList;

	protected abstract T getResult();
	
	public T getResult(PlanPhaseSpecifier from,
			PlanSpecifier to, StandaloneCatalog catalog) throws CatalogApiException {
		if(	
				(phaseType     	     == null || from.getPhaseType() == phaseType) &&
				(fromProduct 	     == null || fromProduct.equals(catalog.findProduct(from.getProductName()))) &&
				(fromProductCategory == null || fromProductCategory.equals(from.getProductCategory())) &&
				(fromBillingPeriod   == null || fromBillingPeriod.equals(from.getBillingPeriod())) &&
				(toProduct           == null || toProduct.equals(catalog.findProduct(to.getProductName()))) &&
				(toProductCategory   == null || toProductCategory.equals(to.getProductCategory())) &&
				(toBillingPeriod     == null || toBillingPeriod.equals(to.getBillingPeriod())) &&
				(fromPriceList       == null || fromPriceList.equals(catalog.getPriceListFromName(from.getPriceListName()))) &&
				(toPriceList         == null || toPriceList.equals(catalog.getPriceListFromName(to.getPriceListName())))
				) {
			return getResult();
		}
		return null;
	}
	
	static public <K> K getResult(CaseChange<K>[] cases, PlanPhaseSpecifier from,
			PlanSpecifier to, StandaloneCatalog catalog) throws CatalogApiException {
    	if(cases != null) {
    		for(CaseChange<K> cc : cases) {
    			K result = cc.getResult(from, to, catalog);
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

	protected CaseChange<T> setPhaseType(PhaseType phaseType) {
		this.phaseType = phaseType;
		return this;
	}

	protected CaseChange<T> setFromProduct(DefaultProduct fromProduct) {
		this.fromProduct = fromProduct;
		return this;
	}

	protected CaseChange<T> setFromProductCategory(ProductCategory fromProductCategory) {
		this.fromProductCategory = fromProductCategory;
		return this;
	}

	protected CaseChange<T> setFromBillingPeriod(BillingPeriod fromBillingPeriod) {
		this.fromBillingPeriod = fromBillingPeriod;
		return this;
	}

	protected CaseChange<T> setFromPriceList(DefaultPriceList fromPriceList) {
		this.fromPriceList = fromPriceList;
		return this;
	}

	protected CaseChange<T> setToProduct(DefaultProduct toProduct) {
		this.toProduct = toProduct;
		return this;
	}

	protected CaseChange<T> setToProductCategory(ProductCategory toProductCategory) {
		this.toProductCategory = toProductCategory;
		return this;
	}

	protected CaseChange<T> setToBillingPeriod(BillingPeriod toBillingPeriod) {
		this.toBillingPeriod = toBillingPeriod;
		return this;
	}

	protected CaseChange<T> setToPriceList(DefaultPriceList toPriceList) {
		this.toPriceList = toPriceList;
		return this;
	}

}
