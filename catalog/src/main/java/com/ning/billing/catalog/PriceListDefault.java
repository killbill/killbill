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

import com.ning.billing.catalog.api.IPriceListSet;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PriceListDefault extends PriceList {
	
	public PriceListDefault(){}
	
	public PriceListDefault(Plan[] defaultPlans) {
		super(defaultPlans, IPriceListSet.DEFAULT_PRICELIST_NAME);
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		super.validate(catalog, errors);
		if(!getName().equals(IPriceListSet.DEFAULT_PRICELIST_NAME)) {
			errors.add(new ValidationError("The name of the default pricelist must be 'DEFAULT'", 
					catalog.getCatalogURI(), PriceList.class, getName()));
			
		}
		return errors;
	}

	@Override
	public String getName() {
		return IPriceListSet.DEFAULT_PRICELIST_NAME;
	}

}
