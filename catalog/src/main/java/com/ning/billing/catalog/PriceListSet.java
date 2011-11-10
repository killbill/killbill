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

import com.ning.billing.catalog.api.IPriceList;
import com.ning.billing.catalog.api.IPriceListSet;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PriceListSet extends ValidatingConfig<Catalog> implements IPriceListSet {
	@XmlElement(required=true, name="defaultPriceList")
	private PriceListDefault defaultPricelist;
	
	@XmlElement(required=false, name="childPriceList")
	private PriceListChild[] childPriceLists;
	
	public PriceListSet() {
		if(childPriceLists == null) {
			childPriceLists = new PriceListChild[0];
		}
	}
	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPriceListSet#getDefaultPricelist()
	 */
	@Override
	public PriceListDefault getDefaultPricelist() {
		return defaultPricelist;
	}
	
	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPriceListSet#getChildPriceLists()
	 */
	@Override
	public PriceListChild[] getChildPriceLists() {
		return childPriceLists;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPriceListSet#getPriceListFromName(String priceListName)
	 */
	@Override
	public PriceList getPriceListFromName(String priceListName) {
		if(priceListName.equals(DEFAULT_PRICELIST_NAME)) {
			return getDefaultPricelist();
		}
		for(PriceListChild set : childPriceLists) {
			if(set.getName().equals(priceListName)) {
				return set;
			}
		}
        return null;
	}
	
	

	public void setDefaultPricelist(PriceListDefault defaultPricelist) {
		this.defaultPricelist = defaultPricelist;
	}

	public void setChildPriceLists(PriceListChild[] childPriceLists) {
		this.childPriceLists = childPriceLists;
	}

	@Override
	public ValidationErrors validate(Catalog root, ValidationErrors errors) {
		//Check that the default pricelist name is not in use in the children
		for(PriceListChild pl : childPriceLists) {
			if(pl.getName().equals(DEFAULT_PRICELIST_NAME)){
				errors.add(new ValidationError("Pricelists cannot use the reserved name '" + DEFAULT_PRICELIST_NAME + "'", root.getCatalogURI(), PriceListSet.class, pl.getName()));
			}
		}
		return errors;
	}
	
}
