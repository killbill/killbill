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
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanAlignment;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;

public class PlanAlignmentCase extends ValidatingConfig {

	@XmlElement(required=false)
	private PhaseType phaseType;

	@XmlElement(required=false)
	@XmlIDREF
	private Product fromProduct;

	@XmlElement(required=false)
	private BillingPeriod fromBillingPeriod;

	@XmlElement(required=false)
	@XmlIDREF
	private Product toProduct;

	@XmlElement(required=false)
	private BillingPeriod toBillingPeriod;

	@XmlElement(required=false)
	@XmlIDREF
	private PriceList fromPriceList;

	@XmlElement(required=false)
	@XmlIDREF
	private PriceList toPriceList;

	@XmlElement(required=true)
	private PlanAlignment alignment;
	
	public PlanAlignmentCase(){}
	
	protected PlanAlignmentCase (
			Product from, Product to, 
			BillingPeriod fromBP, BillingPeriod toBP, 
			PhaseType fromType, PhaseType toType,
			PriceList fromPriceList, PriceList toPriceList,
			PlanAlignment alignment) {
		this.fromProduct = from;
		this.toProduct = to;
		this.fromBillingPeriod = fromBP;
		this.toBillingPeriod = toBP;
		this.phaseType = fromType;
		this.fromPriceList = fromPriceList;
		this.toPriceList = toPriceList;
		this.alignment = alignment;
	}

	public Product getFromProduct() {
		return fromProduct;
	}

	public BillingPeriod getFromBillingPeriod() {
		return fromBillingPeriod;
	}
	
	public Product getToProduct() {
		return toProduct;
	}

	public BillingPeriod getToBillingPeriod() {
		return toBillingPeriod;
	}

	public PhaseType getFromPhaseType() {
		return phaseType;
	}

	public PlanAlignment getPlanAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to, Catalog catalog) {
		if(	
				(phaseType     	   == null || from.getPhaseType() == phaseType) &&
				(fromProduct 	   == null || fromProduct.equals(catalog.getProductFromName(from.getProductName()))) &&
				(fromBillingPeriod == null || fromBillingPeriod.equals(from.getBillingPeriod())) &&
				(toProduct         == null || toProduct.equals(catalog.getProductFromName(to.getProductName()))) &&
				(toBillingPeriod   == null || toBillingPeriod.equals(to.getBillingPeriod())) &&
				(fromPriceList     == null || fromPriceList.equals(catalog.getPriceListFromName(from.getPriceListName()))) &&
				(toPriceList       == null || toPriceList.equals(catalog.getPriceListFromName(to.getPriceListName())))
				) {
			return alignment;
		}
		return null;
	}
	
	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		// TODO Auto-generated method stub
		return errors;
	}
}
