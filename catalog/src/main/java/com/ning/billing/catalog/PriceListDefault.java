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

import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.NONE)
public class PriceListDefault extends DefaultPriceList {
	
	public PriceListDefault(){}
	
	public PriceListDefault(DefaultPlan[] defaultPlans) {
		super(defaultPlans, PriceListSet.DEFAULT_PRICELIST_NAME);
	}

	@Override
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors) {
		super.validate(catalog, errors);
		if(!getName().equals(PriceListSet.DEFAULT_PRICELIST_NAME)) {
			errors.add(new ValidationError("The name of the default pricelist must be 'DEFAULT'", 
					catalog.getCatalogURI(), DefaultPriceList.class, getName()));
			
		}
		return errors;
	}

	@Override
	public String getName() {
		return PriceListSet.DEFAULT_PRICELIST_NAME;
	}

}
