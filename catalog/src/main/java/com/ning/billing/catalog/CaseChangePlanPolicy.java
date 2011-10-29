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
import javax.xml.bind.annotation.XmlSeeAlso;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.ProductCategory;

@XmlSeeAlso(CaseChange.class)
public class CaseChangePlanPolicy extends CaseChange<ActionPolicy> {
	
	@XmlElement(required=true)
	private ActionPolicy policy;
	
	public CaseChangePlanPolicy() {}

	protected CaseChangePlanPolicy(
			Product from, Product to, 
			ProductCategory fromProductCategory, ProductCategory toProductCategory, 
			BillingPeriod fromBP,BillingPeriod toBP, 
			PriceList fromPriceList, PriceList toPriceList,
			PhaseType fromType,
			ActionPolicy result) {
		super(from, to, 
				fromProductCategory, toProductCategory,
				fromBP, toBP, 
				fromPriceList, toPriceList, 
				fromType, 
				result);
		policy = result;
	}



	@Override
	protected ActionPolicy getResult() {
		return policy;
	}

}
