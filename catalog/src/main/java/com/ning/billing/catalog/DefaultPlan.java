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
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPlan extends ValidatingConfig<StandaloneCatalog> implements Plan {


	@XmlAttribute(required=true)
	@XmlID
	private String name;

	@XmlElement(required=true)
	@XmlIDREF
    private DefaultProduct product;

	@XmlElementWrapper(name="initialPhases", required=false)
	@XmlElement(name="phase", required=true)
    private DefaultPlanPhase[] initialPhases = new DefaultPlanPhase[0];

	@XmlElement(name="finalPhase", required=true)
    private DefaultPlanPhase finalPhase;

	//If this is missing it defaults to 1
	//No other value is allowed for BASE plans.
	//No other value is allowed for Tiered ADDONS
	//A value of -1 means unlimited
	@XmlElement(required=false)
	private Integer plansAllowedInBundle = 1;

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getPhases()
	 */
    @Override
	public DefaultPlanPhase[] getInitialPhases() {
        return initialPhases;
    }

    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getProduct()
	 */
    @Override
	public Product getProduct() {
        return product;
    }

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getName()
	 */
	@Override
	public String getName() {
		return name;
	}
	@Override
	public DefaultPlanPhase getFinalPhase() {
		return finalPhase;
	}

	@Override
	public PlanPhase[] getAllPhases() {
	    int length = (initialPhases == null || initialPhases.length == 0) ? 1 : (initialPhases.length + 1);
	    PlanPhase[] allPhases = new DefaultPlanPhase[length];
        int cnt = 0;
	    if (length > 1) {
	        for (PlanPhase cur : initialPhases) {
	            allPhases[cnt++] = cur;
	        }
	    }
        allPhases[cnt++] = finalPhase;
	    return allPhases;
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

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getPhaseIterator()
	 */
	@Override
	public Iterator<PlanPhase> getInitialPhaseIterator() {
		ArrayList<PlanPhase> list = new ArrayList<PlanPhase>();
		for(DefaultPlanPhase p : initialPhases) {
			list.add(p);
		}
		return list.iterator();
	}

	@Override
	public void initialize(StandaloneCatalog catalog, URI sourceURI) {
		super.initialize(catalog, sourceURI);
		if(finalPhase != null) {
			finalPhase.setPlan(this);
			finalPhase.initialize(catalog, sourceURI);
		}
		if(initialPhases != null) {
			for(DefaultPlanPhase p : initialPhases) {
				p.setPlan(this);
				p.initialize(catalog, sourceURI);
			}
		}
	}

	@Override
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors) {
		return errors;
	}


	protected DefaultPlan setName(String name) {
		this.name = name;
		return this;
	}

	protected DefaultPlan setPlansAllowedInBundle(int plansAllowedInBundle) {
		this.plansAllowedInBundle = plansAllowedInBundle;
		return this;
	}

	protected DefaultPlan setFinalPhase(DefaultPlanPhase finalPhase) {
		this.finalPhase = finalPhase;
		return this;
	}

	protected DefaultPlan setProduct(DefaultProduct product) {
		this.product = product;
		return this;
	}

	protected DefaultPlan setInitialPhases(DefaultPlanPhase[] phases) {
		this.initialPhases = phases;
		return this;
	}
}
