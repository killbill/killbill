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
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class PriceList extends ValidatingConfig<Catalog>  {

	@XmlAttribute(required=true)
	@XmlID
	private String name;

	@XmlElementWrapper(name="plans", required=true)
	@XmlElement(name="plan", required=true)
	@XmlIDREF
    private Plan[] plans;
	
	public PriceList(){}

	public PriceList(Plan[] plans, String name) {
		this.plans = plans;
		this.name = name;
	}

	protected Plan[] getPlans() {
		return plans;
	}
	
	public String getName() {
        return name;
    }

	public Plan findPlan(IProduct product, BillingPeriod period) {
        for (Plan cur : getPlans()) {
            if (cur.getProduct().equals(product) && 
            		(cur.getBillingPeriod() == null || cur.getBillingPeriod().equals(period))) {
                return cur;
            }
        }
        return null;
    }

	@Override
	public ValidationErrors validate(Catalog root, ValidationErrors errors) {
		return errors;
	}


}
