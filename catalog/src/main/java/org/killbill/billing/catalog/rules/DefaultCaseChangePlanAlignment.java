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

import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;

public class DefaultCaseChangePlanAlignment extends DefaultCaseChange<PlanAlignmentChange> implements CaseChangePlanAlignment {

    @XmlElement(required = true)
    private PlanAlignmentChange alignment;

    @Override
    protected PlanAlignmentChange getResult() {
        return alignment;
    }

    public DefaultCaseChangePlanAlignment setAlignment(final PlanAlignmentChange alignment) {
        this.alignment = alignment;
        return this;
    }

    @Override
    public PlanAlignmentChange getAlignment() {
        return alignment;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCaseChangePlanAlignment)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultCaseChangePlanAlignment that = (DefaultCaseChangePlanAlignment) o;

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
        return "DefaultCaseChangePlanAlignment {" +
               "alignment=" + alignment +
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

}
