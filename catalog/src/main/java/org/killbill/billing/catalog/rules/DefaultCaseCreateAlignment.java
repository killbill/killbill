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

import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.rules.CaseCreateAlignment;

public class DefaultCaseCreateAlignment extends DefaultCaseStandardNaming<PlanAlignmentCreate> implements CaseCreateAlignment {

    @XmlElement(required = true)
    private PlanAlignmentCreate alignment;

    @Override
    protected PlanAlignmentCreate getResult() {
        return alignment;
    }

    public DefaultCaseCreateAlignment setAlignment(final PlanAlignmentCreate alignment) {
        this.alignment = alignment;
        return this;
    }

    @Override
    public PlanAlignmentCreate getPlanAlignmentCreate() {
        return alignment;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCaseCreateAlignment)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultCaseCreateAlignment that = (DefaultCaseCreateAlignment) o;

        if (alignment != that.alignment) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (alignment != null ? alignment.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefaultCaseCreateAlignment {" +
               "alignment =" + alignment +
               ", product=" + getProduct() +
               ", productCategory=" + getProductCategory() +
               ", billingPeriod=" + getBillingPeriod() +
               ", priceList=" + getPriceList() +
               '}';
    }

}
