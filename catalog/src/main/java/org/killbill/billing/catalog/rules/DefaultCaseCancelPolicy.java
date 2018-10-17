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

import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

public class DefaultCaseCancelPolicy extends DefaultCasePhase<BillingActionPolicy> implements CaseCancelPolicy, Externalizable {

    @XmlElement(required = true)
    private BillingActionPolicy policy;

    @Override
    protected BillingActionPolicy getResult() {
        return policy;
    }

    public DefaultCaseCancelPolicy setPolicy(final BillingActionPolicy policy) {
        this.policy = policy;
        return this;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        if (policy == BillingActionPolicy.START_OF_TERM) {
            errors.add(new ValidationError("Default catalog START_OF_TERM has not been implemented, such policy can be used during cancellation by overriding policy",
                                           DefaultCaseCancelPolicy.class, ""));
        }
        return errors;
    }

    @Override
    public BillingActionPolicy getBillingActionPolicy() {
        return policy;
    }

    @Override
    public PhaseType getPhaseType() {
        return phaseType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCaseCancelPolicy)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultCaseCancelPolicy that = (DefaultCaseCancelPolicy) o;

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
        return "DefaultCaseCancelPolicy{" +
               "policy =" + policy +
               ", phaseType =" + getPhaseType() +
               ", product=" + getProduct() +
               ", productCategory=" + getProductCategory() +
               ", billingPeriod=" + getBillingPeriod() +
               ", priceList=" + getPriceList() +
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
