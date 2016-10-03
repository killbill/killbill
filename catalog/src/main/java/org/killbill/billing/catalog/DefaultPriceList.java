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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPriceList extends ValidatingConfig<StandaloneCatalog> implements PriceList {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlElementWrapper(name = "plans", required = false)
    @XmlIDREF
    @XmlElement(name = "plan", required = false)
    private CatalogEntityCollection<DefaultPlan> plans;

    public DefaultPriceList() {
    }

    public DefaultPriceList(final DefaultPlan[] plans, final String name) {
        this.plans = new CatalogEntityCollection(plans);
        this.name = name;
    }

    @Override
    public Plan[] getPlans() {
        return (Plan[]) plans.toArray(new DefaultPlan[plans.size()]);
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
    public DefaultPlan[] findPlans(final Product product, final BillingPeriod period) {
        final List<DefaultPlan> result = new ArrayList<DefaultPlan>(plans.size());
        for (final Plan cur : getPlans()) {
            if (cur.getProduct().equals(product) &&
                (cur.getRecurringBillingPeriod() != null && cur.getRecurringBillingPeriod().equals(period))) {
                result.add((DefaultPlan) cur);
            }
        }
        return result.toArray(new DefaultPlan[result.size()]);
    }

    public DefaultPlan findPlan(final String planName) {
        return plans.findByName(planName);
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        return errors;
    }

    public DefaultPriceList setName(final String name) {
        this.name = name;
        return this;
    }

    public DefaultPriceList setPlans(final DefaultPlan[] plans) {
        this.plans = new CatalogEntityCollection(plans);
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
        if (!plans.equals(that.plans)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (plans != null ? plans.hashCode() : 0);
        return result;
    }
}
