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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

import javax.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPriceList extends ValidatingConfig<StandaloneCatalog> implements PriceList  {

	@XmlAttribute(required=true)
	@XmlID
	private String name;

	@XmlAttribute(required=false)
	private Boolean retired = false;
	
	@XmlElementWrapper(name="plans", required=true)
	@XmlElement(name="plan", required=true)
	@XmlIDREF
    private DefaultPlan[] plans;
	
	public DefaultPriceList(){}

	public DefaultPriceList(DefaultPlan[] plans, String name) {
		this.plans = plans;
		this.name = name;
	}

	protected DefaultPlan[] getPlans() {
		return plans;
	}

	public boolean isRetired() {
		return retired;
	}
	
	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPriceList#getName()
	 */
	@Override
	public String getName() {
        return name;
    }

	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPriceList#findPlan(com.ning.billing.catalog.api.IProduct, com.ning.billing.catalog.api.BillingPeriod)
	 */
	@Override
	public DefaultPlan findPlan(Product product, BillingPeriod period) {
        for (DefaultPlan cur : getPlans()) {
            if (cur.getProduct().equals(product) && 
            		(cur.getBillingPeriod() == null || cur.getBillingPeriod().equals(period))) {
                return cur;
            }
        }
        return null;
    }

	@Override
	public ValidationErrors validate(StandaloneCatalog catalog, ValidationErrors errors) {
		 for (DefaultPlan cur : getPlans()) {
			 int numPlans = findNumberOfPlans(cur.getProduct(), cur.getBillingPeriod());
			 if ( numPlans > 1 ) {
				 errors.add(new ValidationError(
						 String.format("There are %d plans in pricelist %s and have the same product/billingPeriod (%s, %s)", 
								 numPlans, getName(), cur.getProduct().getName(), cur.getBillingPeriod()), catalog.getCatalogURI(),
								 DefaultPriceListSet.class, getName()));
			 }
		 }
		return errors;
	}
	
	private int findNumberOfPlans(Product product, BillingPeriod period) {
		int count = 0;
        for (DefaultPlan cur : getPlans()) {
            if (cur.getProduct().equals(product) && 
            		(cur.getBillingPeriod() == null || cur.getBillingPeriod().equals(period))) {
                count++;
            }
        }
        return count;
    }
	
	public DefaultPriceList setRetired(boolean retired) {
		this.retired = retired;
		return this;
	}


}
