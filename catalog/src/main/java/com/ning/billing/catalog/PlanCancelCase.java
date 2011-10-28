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

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;

public class PlanCancelCase extends ValidatingConfig {

	@XmlElement(required=false)
	private PhaseType phaseType;

	@XmlElement(required=false)
	@XmlIDREF
	private Product product;

	@XmlElement(required=false)
	private BillingPeriod billingPeriod;
	
	@XmlElement(required=false)
	private PriceList priceList;

	@XmlElement(required=true)
	private ActionPolicy policy;
	
	public PlanCancelCase() {}
	
	protected PlanCancelCase (
			Product product, 
			BillingPeriod billingPeriod, 
			PhaseType phaseType, 
			ActionPolicy policy
			) {
		this.product = product;
		this.billingPeriod = billingPeriod;
		this.phaseType = phaseType;
		this.policy = policy;
	}

	public Product getFromProduct() {
		return product;
	}
	public void setFromProduct(Product product) {
		this.product = product;
	}

	public BillingPeriod getFromBillingPeriod() {
		return billingPeriod;
	}
	public void setFromBillingPeriod(BillingPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
	}
	
	public Product getToProduct() {
		return product;
	}
	public void setToProduct(Product product) {
		this.product = product;
	}

	public BillingPeriod getToBillingPeriod() {
		return billingPeriod;
	}
	public void setToBillingPeriod(BillingPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
	}

	public ActionPolicy getPolicy() {
		return policy;
	}
	public void setPolicy(ActionPolicy policy) {
		this.policy = policy;
	}

	public PhaseType getPhaseType() {
		return phaseType;
	}


	public ActionPolicy getPlanCancelPolicy(PlanPhaseSpecifier planPhase, Catalog c) {
		if(	
				(phaseType       == null || planPhase.getPhaseType() == phaseType) &&
				(product         == null || product.equals(c.getProductFromName(planPhase.getProductName()))) &&
				(billingPeriod   == null || billingPeriod.equals(planPhase.getBillingPeriod())) &&
				(priceList       == null || priceList.equals(c.getPriceListFromName(planPhase.getPriceListName())))
				) {
			return getPolicy();
		}
		return null;
	}
	
	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		// TODO Auto-generated method stub
		return null;
	}

}
