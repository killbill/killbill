/*
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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultRecurring extends ValidatingConfig<StandaloneCatalog> implements Recurring, Externalizable {

    @XmlElement(required = true)
    private BillingPeriod billingPeriod;

    @XmlElement(required = false)
    private DefaultInternationalPrice recurringPrice;

    // Not exposed in xml.
    private String planName;
    private PhaseType phaseType;

    // Required for deserialization
    public DefaultRecurring() {}

    public DefaultRecurring(final DefaultRecurring in, final PlanPhasePriceOverride override) {
        this.billingPeriod = in.getBillingPeriod();
        this.recurringPrice = in.getRecurringPrice() != null ? new DefaultInternationalPrice(in.getRecurringPrice(), override, false) : null;
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    @Override
    public DefaultInternationalPrice getRecurringPrice() {
        return recurringPrice;
    }

    @Override
    public void initialize(final StandaloneCatalog root) {
        super.initialize(root);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
        if (recurringPrice != null) {
            recurringPrice.initialize(root);
        }
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        // Validation: check for nulls

        if (planName == null) {
            errors.add(new ValidationError("Invalid plan for recurring section", DefaultRecurring.class, ""));
        }

        if (phaseType == null) {
            errors.add(new ValidationError("Invalid phase for recurring section", DefaultPlan.class, planName));
        }

        if (billingPeriod == null) {
            errors.add(new ValidationError(String.format("Recurring section of Phase %s of plan %s has a recurring price but no billing period", phaseType.toString(), planName),
                                           DefaultPlanPhase.class, phaseType.toString()));
        }

        // Validation: if there is a recurring price there must be a billing period
        if (recurringPrice != null) {
            // Validation: check for negative price
            recurringPrice.validate(catalog, errors);

            if (billingPeriod == null || billingPeriod == BillingPeriod.NO_BILLING_PERIOD) {
                errors.add(new ValidationError(String.format("Recurring section of Phase %s of plan %s has a recurring price but no billing period", phaseType.toString(), planName),
                                               DefaultPlanPhase.class, phaseType.toString()));
            }
        }

        // Validation: if there is no recurring price there should be no billing period
        if ((recurringPrice == null) && billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
            errors.add(new ValidationError(String.format("Recurring section of Phase %s of plan %s has no recurring price but does have a billing period. The billing period should be set to '%s'",
                                                         phaseType.toString(), planName, BillingPeriod.NO_BILLING_PERIOD),
                                           DefaultPlanPhase.class, phaseType.toString()));
        }
        return errors;
    }

    public DefaultRecurring setBillingPeriod(final BillingPeriod billingPeriod) {
        this.billingPeriod = billingPeriod;
        return this;
    }

    public DefaultRecurring setRecurringPrice(final DefaultInternationalPrice recurringPrice) {
        this.recurringPrice = recurringPrice;
        return this;
    }

    public DefaultRecurring setPlan(final String planName) {
        this.planName = planName;
        return this;
    }

    public DefaultRecurring setPhase(final PlanPhase phase) {
        this.phaseType = phase.getPhaseType();
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultRecurring)) {
            return false;
        }

        final DefaultRecurring that = (DefaultRecurring) o;

        if (billingPeriod != that.billingPeriod) {
            return false;
        }
        if (recurringPrice != null ? !recurringPrice.equals(that.recurringPrice) : that.recurringPrice != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = billingPeriod != null ? billingPeriod.hashCode() : 0;
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeBoolean(billingPeriod != null);
        if (billingPeriod != null) {
            out.writeUTF(billingPeriod.name());
        }
        out.writeObject(recurringPrice);
        out.writeUTF(planName);
        out.writeBoolean(phaseType != null);
        if (phaseType != null) {
            out.writeUTF(phaseType.name());
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.billingPeriod = in.readBoolean() ? BillingPeriod.valueOf(in.readUTF()) : null;
        this.recurringPrice = (DefaultInternationalPrice) in.readObject();
        this.planName = in.readUTF();
        this.phaseType = in.readBoolean() ? PhaseType.valueOf(in.readUTF()) : null;
    }
}
