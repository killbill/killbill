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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IPriceListSet;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PriceListSet extends ValidatingConfig<Catalog> {
	@XmlElement(required=true, name="defaultPriceList")
	private PriceListDefault defaultPricelist;
	
	@XmlElement(required=false, name="childPriceList")
	private PriceList[] childPriceLists;
	
	public PriceListSet() {
		if(childPriceLists == null) {
			childPriceLists = new PriceList[0];
		}
	}

	public PriceListSet(PriceListDefault defaultPricelist, PriceList[] childPriceLists) {
		this.defaultPricelist = defaultPricelist;
		this.childPriceLists = childPriceLists;
	}

	public Plan getPlanListFrom(String priceListName, IProduct product,
			BillingPeriod period) {
		Plan result = null;
		PriceList pl = findPriceListFrom(priceListName);
		if(pl != null) {
			result = pl.findPlan(product, period);
		}
		if(result != null) {
			return result;
		}
		
		return defaultPricelist.findPlan(product, period);
	}

	public PriceList findPriceListFrom (String priceListName) {
		if (defaultPricelist.getName().equals(priceListName)) {
			return defaultPricelist;
		} 
		for(PriceList pl : childPriceLists) {
			if(pl.getName().equals(priceListName)) {
				return pl;
			}
		}
		return null;
	}

	@Override
	public ValidationErrors validate(Catalog root, ValidationErrors errors) {
		//Check that the default pricelist name is not in use in the children
		for(PriceList pl : childPriceLists) {
			if(pl.getName().equals(IPriceListSet.DEFAULT_PRICELIST_NAME)){
				errors.add(new ValidationError("Pricelists cannot use the reserved name '" + IPriceListSet.DEFAULT_PRICELIST_NAME + "'", root.getCatalogURI(), PriceListSet.class, pl.getName()));
			}
		}
		return errors;
	}

	public PriceList getDefaultPricelist() {
		return defaultPricelist;
	}

	public PriceList[] getChildPriceLists() {
		return childPriceLists;
	}


	
}
