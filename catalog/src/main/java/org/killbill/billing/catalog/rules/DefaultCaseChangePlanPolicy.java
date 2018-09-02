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

package org.killbill.billing.catalog.rules;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;

import org.killbill.billing.catalog.CatalogSafetyInitializer;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.rules.CaseChangePlanPolicy;
import org.killbill.xmlloader.ValidationErrors;

@XmlSeeAlso(DefaultCaseChange.class)
public class DefaultCaseChangePlanPolicy extends DefaultCaseChange<BillingActionPolicy> implements CaseChangePlanPolicy, Externalizable {

    @XmlElement(required = true)
    private BillingActionPolicy policy;

    // Required for deserialization
    public DefaultCaseChangePlanPolicy() {
    }

    @Override
    protected BillingActionPolicy getResult() {
        return policy;
    }

    public DefaultCaseChangePlanPolicy setPolicy(final BillingActionPolicy policy) {
        this.policy = policy;
        return this;
    }

    @Override
    public BillingActionPolicy getBillingActionPolicy() {
        return policy;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCaseChangePlanPolicy)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultCaseChangePlanPolicy that = (DefaultCaseChangePlanPolicy) o;

        if (policy != that.policy) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (policy != null ? policy.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefaultCaseChangePlanPolicy {" +
               "policy=" + policy +
               ", phaseType=" + getPhaseType() +
               ", fromProduct=" + getFromProduct() +
               ", fromProductCategory=" + getFromProductCategory() +
               ", fromBillingPeriod=" + getFromBillingPeriod() +
               ", fromPriceList=" + getFromPriceList() +
               ", toProduct=" + getToProduct() +
               ", toProductCategory=" + getToProductCategory() +
               ", toBillingPeriod=" + getToBillingPeriod() +
               ", toPriceList=" + getToPriceList() +
               '}';
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeBoolean(policy != null);
        if (policy != null) {
            out.writeUTF(policy.name());
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        this.policy = in.readBoolean() ? BillingActionPolicy.valueOf(in.readUTF()) : null;
    }
}
