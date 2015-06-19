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

package org.killbill.billing.catalog.rules;

import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;

public class DefaultCaseCancelPolicy extends DefaultCasePhase<BillingActionPolicy> implements CaseCancelPolicy {

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
}
