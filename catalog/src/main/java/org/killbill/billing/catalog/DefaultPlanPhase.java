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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.Fixed;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.util.config.catalog.ValidatingConfig;
import org.killbill.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPlanPhase extends ValidatingConfig<StandaloneCatalog> implements PlanPhase {

    @XmlAttribute(required = true)
    private PhaseType type;

    @XmlElement(required = true)
    private DefaultDuration duration;

    @XmlElement(required = false)
    private DefaultFixed fixed;

    @XmlElement(required = false)
    private DefaultRecurring recurring;

    @XmlElementWrapper(name = "usages", required = false)
    @XmlElement(name = "usage", required = false)
    private DefaultUsage[] usages;

    //Not exposed in XML
    private Plan plan;

    public static String phaseName(final String planName, final PhaseType phasetype) {
        return planName + "-" + phasetype.toString().toLowerCase();
    }

    public static String planName(final String phaseName) throws CatalogApiException {
        for (final PhaseType type : PhaseType.values()) {
            if (phaseName.endsWith(type.toString().toLowerCase())) {
                return phaseName.substring(0, phaseName.length() - type.toString().length() - 1);
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_BAD_PHASE_NAME, phaseName);
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.IPlanPhase#getCohort()
      */
    @Override
    public PhaseType getPhaseType() {
        return type;
    }

    @Override
    public boolean compliesWithLimits(final String unit, final double value) {
        // TODO STEPH
        return true;
    }

    @Override
    public Fixed getFixed() {
        return fixed;
    }

    @Override
    public Recurring getRecurring() {
        return recurring;
    }

    @Override
    public Usage[] getUsages() {
        return usages;
    }

    /* (non-Javadoc)
          * @see org.killbill.billing.catalog.IPlanPhase#getName()
          */
    @Override
    public String getName() {
        return phaseName(plan.getName(), this.getPhaseType());
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.IPlanPhase#getPlan()
      */
    @Override
    public Plan getPlan() {
        return plan;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.killbill.billing.catalog.IPlanPhase#getDuration()
     */
    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {

        // TODO STEPH check there is either fixed and/or/usage and/or recurring defined.

        /*
        //Validation: if there BP is set to NO_BILLING_PERIOD there must be a fixed price
        if ((billingPeriod == BillingPeriod.NO_BILLING_PERIOD && fixedPrice == null)) {
            errors.add(new ValidationError(String.format("Phase %s of plan %s has no billing period. It must have a fixed price set.",
                                                         type.toString(), plan.getName()),
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, type.toString()));
        }

        //Validation: there must be at least one of recurringPrice or fixedPrice
        if ((recurringPrice == null) && fixedPrice == null) {
            errors.add(new ValidationError(String.format("Phase %s of plan %s has neither a recurring price or a fixed price.",
                                                         type.toString(), plan.getName()),
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, type.toString()));
        }
        */
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog root, final URI uri) {
        if (fixed != null) {
            fixed.initialize(root, uri);
            fixed.setPhase(this);
        }
        if (recurring != null) {
            recurring.initialize(root, uri);
            recurring.setPhase(this);
        }
        if (usages != null) {
            for (DefaultUsage usage : usages) {
                usage.initialize(root, uri);
                usage.setPhase(this);
            }
        }
    }

    protected DefaultPlanPhase setFixed(final DefaultFixed fixed) {
        this.fixed = fixed;
        return this;
    }

    protected DefaultPlanPhase setRecurring(final DefaultRecurring recurring) {
        this.recurring = recurring;
        return this;
    }

    protected DefaultPlanPhase setUsages(final DefaultUsage []  usages) {
        this.usages = usages;
        return this;
    }

    protected DefaultPlanPhase setPhaseType(final PhaseType cohort) {
        this.type = cohort;
        return this;
    }

    protected DefaultPlanPhase setDuration(final DefaultDuration duration) {
        this.duration = duration;
        return this;
    }

    protected DefaultPlanPhase setPlan(final Plan plan) {
        this.plan = plan;
        return this;
    }
}
