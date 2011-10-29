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

import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IProduct;

@XmlAccessorType(XmlAccessType.NONE)
public class Plan extends ValidatingConfig implements IPlan {


	@XmlAttribute(required=true)
	@XmlID
	private String name;

	@XmlElement(required=true)
	@XmlIDREF
    private Product product;
	
	@XmlElementWrapper(name="initialPhases", required=false)
	@XmlElement(name="phase", required=true)
    private PlanPhase[] initialPhases;
	
	@XmlElement(name="finalPhase", required=true)
    private PlanPhase finalPhase;
	
	//If this is missing it defaults to 1
	//No other value is allowed for BASE plans.
	//No other value is allowed for Tiered ADDONS
	//A value of -1 means unlimited
	@XmlElement(required=false)
	private Integer plansAllowedInBundle = 1;
	
	public Plan(){}
	
	protected Plan(String name, Product product, PlanPhase finalPhase) {
		this.name = name;
		this.product = product;
		this.finalPhase = finalPhase;
	}

	@Override
	public void initialize(Catalog catalog) {
		super.initialize(catalog);
		if(finalPhase != null) {
			finalPhase.setPlan(this);
		}
		if(initialPhases != null) {
			for(PlanPhase p : initialPhases) {
				p.setPlan(this);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getPhases()
	 */
    @Override
	public PlanPhase[] getInitialPhases() {
        return initialPhases;
    }

    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getProduct()
	 */
    @Override
	public IProduct getProduct() {
        return product;
    }

	public void setInitialPhases(PlanPhase[] phases) {
		this.initialPhases = phases;
		
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getPhaseIterator()
	 */
	@Override
	public Iterator<IPlanPhase> getInitialPhaseIterator() {
		ArrayList<IPlanPhase> list = new ArrayList<IPlanPhase>();
		for(PlanPhase p : initialPhases) {
			list.add(p);
		}
		return list.iterator();
	}
	
	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		return errors;
	}
	
	@Override
	public PlanPhase getFinalPhase() {
		return finalPhase;
	}

	public void setFinalPhase(PlanPhase finalPhase) {
		this.finalPhase = finalPhase;
	}
	
	@Override
	public BillingPeriod getBillingPeriod(){
		return finalPhase.getBillingPeriod();
	}

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getPlansAllowedInBundle()
	 */
	@Override
	public int getPlansAllowedInBundle() {
		return plansAllowedInBundle;
	}

	public void setPlansAllowedInBundle(int plansAllowedInBundle) {
		this.plansAllowedInBundle = plansAllowedInBundle;
	}
}
