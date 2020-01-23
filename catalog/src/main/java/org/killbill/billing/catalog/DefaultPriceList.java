/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPriceList extends ValidatingConfig<StandaloneCatalog> implements PriceList, Externalizable {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlAttribute(required = false)
    private String prettyName;

    @XmlElementWrapper(name = "plans", required = true)
    @XmlIDREF
    @XmlElement(type = DefaultPlan.class, name = "plan", required = false)
    private CatalogEntityCollection<Plan> plans;

    public DefaultPriceList() {
        this.plans = new CatalogEntityCollection<Plan>();
    }

    public DefaultPriceList(final DefaultPlan[] plans, final String name) {
        this.plans = new CatalogEntityCollection<Plan>(plans);
        this.name = name;
    }

    @Override
    public StaticCatalog getCatalog() {
        return root;
    }

    @Override
    public Collection<Plan> getPlans() {
        return plans;
    }

    public CatalogEntityCollection<Plan> getCatalogEntityCollectionPlan() {
        return plans;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrettyName() {
        return prettyName;
    }

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
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
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

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeBoolean(name != null);
        if (name != null) {
            out.writeUTF(name);
        }
        out.writeBoolean(prettyName != null);
        if (prettyName != null) {
            out.writeUTF(prettyName);
        }
        out.writeObject(plans);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.name = in.readBoolean() ? in.readUTF() : null;
        this.prettyName = in.readBoolean() ? in.readUTF() : null;
        this.plans = (CatalogEntityCollection<Plan>) in.readObject();
    }
}
