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
import javax.xml.bind.annotation.XmlSeeAlso;

import org.killbill.billing.catalog.api.BillingActionPolicy;

@XmlSeeAlso(CaseChange.class)
public class CaseChangePlanPolicy extends CaseChange<BillingActionPolicy> {

    @XmlElement(required = true)
    private BillingActionPolicy policy;

    @Override
    protected BillingActionPolicy getResult() {
        return policy;
    }

    protected CaseChangePlanPolicy setPolicy(final BillingActionPolicy policy) {
        this.policy = policy;
        return this;
    }

}
