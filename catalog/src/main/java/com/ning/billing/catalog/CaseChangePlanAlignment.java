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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.ProductCategory;

public class CaseChangePlanAlignment extends CaseChange<PlanAlignmentChange> {

	@XmlElement(required=true)
	private PlanAlignmentChange alignment;
	
	public CaseChangePlanAlignment() {}

	protected CaseChangePlanAlignment(
			Product from, Product to, 
			ProductCategory fromProductCategory, ProductCategory toProductCategory, 
			BillingPeriod fromBP,BillingPeriod toBP, 
			PriceListChild fromPriceList, PriceListChild toPriceList,
			PhaseType fromType, 
			PlanAlignmentChange result) {
		super(from, to, 
				fromProductCategory, toProductCategory, 
				fromBP, toBP, 
				fromPriceList, toPriceList,  
				fromType,
				result);
		alignment = result;
	}

	@Override
	protected PlanAlignmentChange getResult() {
		return alignment;
	}

}
