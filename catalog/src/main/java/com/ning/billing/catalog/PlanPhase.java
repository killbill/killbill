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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IDuration;
import com.ning.billing.catalog.api.IInternationalPrice;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PlanPhase extends ValidatingConfig<Catalog> implements IPlanPhase {

	@XmlAttribute (required=true)
	private PhaseType type;

    @XmlElement(required=true)
    private Duration duration;
    
    @XmlElement(required=false)
    private BillingPeriod billingPeriod;

	@XmlElement(required=false)
	private InternationalPrice recurringPrice;

	@XmlElement(required=false)
	private InternationalPrice fixedPrice;

//	@XmlElement(required=false)
//	private InternationalPrice unitPrice;

	//Not exposed in XML
	private IPlan plan;
	
	public PlanPhase(){}

    protected PlanPhase(BillingPeriod period, PhaseType type) {
		this.billingPeriod = period;
		this.type = type;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getRecurringPrice()
	 */
    @Override
	public IInternationalPrice getRecurringPrice() {
        return recurringPrice;
    }

	public void setReccuringPrice(InternationalPrice price) {
		this.recurringPrice = price;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getInternationalPrice()
	 */
    @Override
	public IInternationalPrice getFixedPrice() {
        return fixedPrice;
    }

	public void setFixedPrice(InternationalPrice price) {
		this.fixedPrice = price;
	}

    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getBillCycleDuration()
	 */
    @Override
	public BillingPeriod getBillingPeriod() {
    	return billingPeriod;
    }

	public void setBillCycleDuration(BillingPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
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

	public void setPlan(IPlan plan) {
		this.plan = plan;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getDuration()
	 */
	@Override
	public IDuration getDuration() {
 		return duration;
	}

	public void setDuration(Duration duration) {
		this.duration = duration;
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		return errors;

	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanPhase#getCohort()
	 */
	@Override
	public PhaseType getPhaseType() {
		return type;
	}

	public void setCohort(PhaseType cohort) {
		this.type = cohort;
	}

	public void setBillingPeriod(BillingPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
	}
	
	//TODO MDW - validation: if there is a recurring price there must be a billing period
	//TODO MDW - validation: if there is no reccuring price there should be no billing period
	

}
