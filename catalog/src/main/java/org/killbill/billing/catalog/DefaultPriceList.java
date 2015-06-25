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

package org.killbill.billing.catalog;

import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

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
      * @see org.killbill.billing.catalog.IPriceList#getName()
      */
    @Override
    public String getName() {
        return name;
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.IPriceList#findPlan(org.killbill.billing.catalog.api.IProduct, org.killbill.billing.catalog.api.BillingPeriod)
      */
    @Override
    public DefaultPlan findPlan(final Product product, final BillingPeriod period) {
        for (final DefaultPlan cur : getPlans()) {
            if (cur.getProduct().equals(product) &&
                    (cur.getRecurringBillingPeriod() == null || cur.getRecurringBillingPeriod().equals(period))) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        for (final DefaultPlan cur : getPlans()) {
            final int numPlans = findNumberOfPlans(cur.getProduct(), cur.getRecurringBillingPeriod());
            if (numPlans > 1) {
                errors.add(new ValidationError(
                        String.format("There are %d plans in pricelist %s and have the same product/billingPeriod (%s, %s)",
                                      numPlans, getName(), cur.getProduct().getName(), cur.getRecurringBillingPeriod()), catalog.getCatalogURI(),
                        DefaultPriceListSet.class, getName()));
            }
        }
        return errors;
    }

    private int findNumberOfPlans(final Product product, final BillingPeriod period) {
        int count = 0;
        for (final DefaultPlan cur : getPlans()) {
            if (cur.getProduct().equals(product) &&
                    (cur.getRecurringBillingPeriod() == null || cur.getRecurringBillingPeriod().equals(period))) {
                count++;
            }
        }
        return count;
    }

    public DefaultPriceList setRetired(final boolean retired) {
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPriceList)) {
            return false;
        }

        final DefaultPriceList that = (DefaultPriceList) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (!Arrays.equals(plans, that.plans)) {
            return false;
        }
        if (retired != null ? !retired.equals(that.retired) : that.retired != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (retired != null ? retired.hashCode() : 0);
        result = 31 * result + (plans != null ? Arrays.hashCode(plans) : 0);
        return result;
    }
}
