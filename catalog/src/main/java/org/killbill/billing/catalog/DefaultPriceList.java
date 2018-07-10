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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
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

    @XmlAttribute(required = false)
    private String prettyName;

    @XmlElementWrapper(name = "plans", required = true)
    @XmlIDREF
    @XmlElement(type=DefaultPlan.class, name = "plan", required = false)
    private CatalogEntityCollection<Plan> plans;

    public DefaultPriceList() {
        this.plans = new CatalogEntityCollection();
    }

    public DefaultPriceList(final DefaultPlan[] plans, final String name) {
        this.plans = new CatalogEntityCollection(plans);
        this.name = name;
    }

    @Override
    public Collection<Plan> getPlans() {
        return plans;
    }


    public CatalogEntityCollection<Plan> getCatalogEntityCollectionPlan() {
        return plans;
    }
    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.IPriceList#getName()
      */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrettyName() {
        return prettyName;
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.IPriceList#findPlan(org.killbill.billing.catalog.api.IProduct, org.killbill.billing.catalog.api.BillingPeriod)
      */
    @Override
    public Collection<Plan> findPlans(final Product product, final BillingPeriod period) {
        final List<Plan> result = new ArrayList<Plan>(plans.size());
        for (final Plan cur : getPlans()) {
            if (cur.getProduct().equals(product) &&
                (cur.getRecurringBillingPeriod() != null && cur.getRecurringBillingPeriod().equals(period))) {
                result.add(cur);
            }
        }
        return result;
    }

    public Plan findPlan(final String planName) {
        return plans.findByName(planName);
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        return errors;
    }


    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);

        if (prettyName == null) {
            this.prettyName = name;
        }
    }

    public DefaultPriceList setName(final String name) {
        this.name = name;
        return this;
    }

    public DefaultPriceList setPlans(final Iterable<Plan> plans) {
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

    @Override
    public String toString() {
        return "DefaultPriceList{" +
               "name='" + name + '}';
    }
}
