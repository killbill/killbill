/*
 * Copyright 2010-2013 Ning, Inc.
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
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.util.config.catalog.ValidatingConfig;
import com.ning.billing.util.config.catalog.ValidationError;
import com.ning.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPriceList extends ValidatingConfig<StandaloneCatalog> implements PriceList {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlAttribute(required = false)
    private Boolean retired = false;

    @XmlElementWrapper(name = "plans", required = true)
    @XmlIDREF
    @XmlElement(name = "plan", required = true)
    private DefaultPlan[] plans;

    public DefaultPriceList() {
    }

    public DefaultPriceList(final DefaultPlan[] plans, final String name) {
        this.plans = plans;
        this.name = name;
    }

    @Override
    public DefaultPlan[] getPlans() {
        return plans;
    }

    @Override
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
    public DefaultPlan findPlan(final Product product, final BillingPeriod period) {
        for (final DefaultPlan cur : getPlans()) {
            if (cur.getProduct().equals(product) &&
                    (cur.getBillingPeriod() == null || cur.getBillingPeriod().equals(period))) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        for (final DefaultPlan cur : getPlans()) {
            final int numPlans = findNumberOfPlans(cur.getProduct(), cur.getBillingPeriod());
            if (numPlans > 1) {
                errors.add(new ValidationError(
                        String.format("There are %d plans in pricelist %s and have the same product/billingPeriod (%s, %s)",
                                      numPlans, getName(), cur.getProduct().getName(), cur.getBillingPeriod()), catalog.getCatalogURI(),
                        DefaultPriceListSet.class, getName()));
            }
        }
        return errors;
    }

    private int findNumberOfPlans(final Product product, final BillingPeriod period) {
        int count = 0;
        for (final DefaultPlan cur : getPlans()) {
            if (cur.getProduct().equals(product) &&
                    (cur.getBillingPeriod() == null || cur.getBillingPeriod().equals(period))) {
                count++;
            }
        }
        return count;
    }

    protected DefaultPriceList setRetired(final boolean retired) {
        this.retired = retired;
        return this;
    }

    public DefaultPriceList setName(final String name) {
        this.name = name;
        return this;
    }

    public DefaultPriceList setPlans(final DefaultPlan[] plans) {
        this.plans = plans;
        return this;
    }


}
