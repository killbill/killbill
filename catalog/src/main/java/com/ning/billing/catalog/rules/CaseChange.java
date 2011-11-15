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

import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.PriceList;
import com.ning.billing.catalog.Product;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class CaseChange<T>  extends ValidatingConfig<Catalog> {

	@XmlElement(required=false)
	private PhaseType phaseType;

	@XmlElement(required=false)
	@XmlIDREF
	private Product fromProduct;

	@XmlElement(required=false)
	private ProductCategory fromProductCategory;

	@XmlElement(required=false)
	private BillingPeriod fromBillingPeriod;

	@XmlElement(required=false)
	@XmlIDREF
	private PriceList fromPriceList;

	@XmlElement(required=false)
	@XmlIDREF
	private Product toProduct;

	@XmlElement(required=false)
	private ProductCategory toProductCategory;

	@XmlElement(required=false)
	private BillingPeriod toBillingPeriod;

	@XmlElement(required=false)
	@XmlIDREF
	private PriceList toPriceList;
	
	public CaseChange(){}
	
	protected CaseChange (
			Product from, Product to, 
			ProductCategory fromProductCategory, ProductCategory toProductCategory, 
			BillingPeriod fromBP, BillingPeriod toBP, 
			PriceList fromPriceList, PriceList toPriceList,
			PhaseType fromType,
			T result) {
		this.fromProduct = from;
		this.toProduct = to;
		this.fromProductCategory = fromProductCategory;
		this.toProductCategory = toProductCategory;
		this.fromBillingPeriod = fromBP;
		this.toBillingPeriod = toBP;
		this.phaseType = fromType;
		this.fromPriceList = fromPriceList;
		this.toPriceList = toPriceList;
	}

	protected abstract T getResult();
	
	public T getResult(PlanPhaseSpecifier from,
			PlanSpecifier to, Catalog catalog) {
		if(	
				(phaseType     	     == null || from.getPhaseType() == phaseType) &&
				(fromProduct 	     == null || fromProduct.equals(catalog.getProductFromName(from.getProductName()))) &&
				(fromProductCategory == null || fromProductCategory.equals(from.getProductCategory())) &&
				(fromBillingPeriod   == null || fromBillingPeriod.equals(from.getBillingPeriod())) &&
				(toProduct           == null || toProduct.equals(catalog.getProductFromName(to.getProductName()))) &&
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
			PlanSpecifier to, Catalog catalog) {
    	if(cases != null) {
    		for(int i = cases.length - 1; i >=0; i --) {
    			K result = cases[i].getResult(from, to, catalog);
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
		return errors;
	}



}
