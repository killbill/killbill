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

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IDuration;
import com.ning.billing.catalog.api.IInternationalPrice;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PlanPhase extends ValidatingConfig<Catalog> implements IPlanPhase {

	@XmlAttribute (required=true)
	private PhaseType type;

    @XmlElement(required=true)
    private Duration duration;
    
    @XmlElement(required=true)
    private BillingPeriod billingPeriod = BillingPeriod.NO_BILLING_PERIOD;

	@XmlElement(required=false)
	private InternationalPrice recurringPrice;

	@XmlElement(required=false)
	private InternationalPrice fixedPrice;

//  Not supported: variable pricing
//	@XmlElement(required=false)
//	private InternationalPrice unitPrice;

	//Not exposed in XML
	private IPlan plan;

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getRecurringPrice()
	 */
    @Override
	public IInternationalPrice getRecurringPrice() {
        return recurringPrice;
    }

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getInternationalPrice()
	 */
    @Override
	public IInternationalPrice getFixedPrice() {
        return fixedPrice;
    }

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getCohort()
	 */
	@Override
	public PhaseType getPhaseType() {
		return type;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getBillCycleDuration()
	 */
    @Override
	public BillingPeriod getBillingPeriod() {
    	return billingPeriod;
    }

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getName()
	 */
	@Override
	public String getName() {
		return plan.getName() + "-" + type.toString().toLowerCase();
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getPlan()
	 */
	@Override
	public IPlan getPlan() {
		return plan;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getDuration()
	 */
	@Override
	public IDuration getDuration() {
 		return duration;
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		//Validation: check for nulls
		if(billingPeriod == null) {
			errors.add(new ValidationError(String.format("Phase %s of plan %s has a reccurring price but no billing period", type.toString(), plan.getName()), 
					catalog.getCatalogURI(), PlanPhase.class, type.toString()));
		}
		
		
		
		
		//Validation: if there is a recurring price there must be a billing period
		if(recurringPrice != null && (billingPeriod == null || billingPeriod ==BillingPeriod.NO_BILLING_PERIOD)) {
			errors.add(new ValidationError(String.format("Phase %s of plan %s has a reccurring price but no billing period", type.toString(), plan.getName()), 
					catalog.getCatalogURI(), PlanPhase.class, type.toString()));
		}
		//Validation: if there is no reccuring price there should be no billing period
		if(recurringPrice == null && billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
			errors.add(new ValidationError(String.format("Phase %s of plan %s has no reccurring price but does have a billing period. The billing period shoudl be set to '%s'", 
					type.toString(), plan.getName(), BillingPeriod.NO_BILLING_PERIOD), 
					catalog.getCatalogURI(), PlanPhase.class, type.toString()));
		}
		
		return errors;

	}
	

	@Override
	public void initialize(Catalog root, URI uri) {
		if (fixedPrice != null) { fixedPrice.initialize(root, uri);  }	
		if (recurringPrice != null) { recurringPrice.initialize(root, uri); }
	}

	protected PlanPhase setFixedPrice(InternationalPrice price) {
		this.fixedPrice = price;
		return this;
	}

	protected PlanPhase setReccuringPrice(InternationalPrice price) {
		this.recurringPrice = price;
		return this;
	}

	protected PlanPhase setPhaseType(PhaseType cohort) {
		this.type = cohort;
		return this;
	}

	protected PlanPhase setBillingPeriod(BillingPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
		return this;
	}

	protected PlanPhase setDuration(Duration duration) {
		this.duration = duration;
		return this;
	}

	protected PlanPhase setPlan(IPlan plan) {
		this.plan = plan;
		return this;
	}
	
	protected PlanPhase setBillCycleDuration(BillingPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
		return this;
	}

}
