/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.net.URI;
import java.util.Arrays;

import javax.annotation.Nullable;
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
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPlanPhase extends ValidatingConfig<StandaloneCatalog> implements PlanPhase {

    @XmlAttribute(required = false)
    private String prettyName;

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

    // Not exposed in XML
    private Plan plan;

    public DefaultPlanPhase() {
        usages = new DefaultUsage[0];
    }

    public DefaultPlanPhase(final DefaultPlan parentPlan, final DefaultPlanPhase in, @Nullable final PlanPhasePriceOverride override) {
        this.type = in.getPhaseType();
        this.duration = (DefaultDuration) in.getDuration();
        this.fixed = override != null && override.getFixedPrice() != null ? new DefaultFixed((DefaultFixed) in.getFixed(), override) : (DefaultFixed) in.getFixed();
        this.recurring = override != null && override.getRecurringPrice() != null ? new DefaultRecurring((DefaultRecurring) in.getRecurring(), override) : (DefaultRecurring) in.getRecurring();
        this.usages = new DefaultUsage[in.getUsages().length];
        for (int i = 0; i < in.getUsages().length; i++) {
            final Usage curUsage = in.getUsages()[i];
            if(override != null && override.getUsagePriceOverrides()!= null) {
                final UsagePriceOverride usagePriceOverride = Iterables.tryFind(override.getUsagePriceOverrides(), new Predicate<UsagePriceOverride>() {
                 @Override
                 public boolean apply(final UsagePriceOverride input) {
                     return input !=null && input.getName().equals(curUsage.getName());
                 }
                 }).orNull();
                usages[i] = (usagePriceOverride !=null) ? new DefaultUsage(in.getUsages()[i], usagePriceOverride, override.getCurrency()) : (DefaultUsage)curUsage;
            }
            else {
                usages[i] = (DefaultUsage)curUsage;
            }
        }
        this.plan = parentPlan;
    }

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

    @Override
    public PhaseType getPhaseType() {
        return type;
    }

    @Override
    public boolean compliesWithLimits(final String unit, final double value) {
        // First check usage section
        for (DefaultUsage usage : usages) {
            if (!usage.compliesWithLimits(unit, value)) {
                return false;
            }
        }
        // Second, check if there are limits defined at the product section.
        return plan.getProduct().compliesWithLimits(unit, value);
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

    @Override
    public String getName() {
        return phaseName(plan.getName(), this.getPhaseType());
    }

    @Override
    public String getPrettyName() {
        return prettyName != null ? prettyName : getName();
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {

        if (plan == null) {
            errors.add(new ValidationError(String.format("Invalid plan for phase '%s'", type), catalog.getCatalogURI(), DefaultPlanPhase.class, ""));
        }

        if (fixed == null && recurring == null && usages.length == 0) {
            errors.add(new ValidationError(String.format("Phase %s of plan %s need to define at least either a fixed or recurrring or usage section.",
                                                         type.toString(), plan.getName()),
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, type.toString()));
        }
        if (fixed != null) {
            fixed.validate(catalog, errors);
        }
        if (recurring != null) {
            recurring.validate(catalog, errors);
        }
        duration.validate(catalog, errors);

        validateCollection(catalog, errors, usages);
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog root, final URI uri) {

        super.initialize(root, uri);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);

        if (fixed != null) {
            fixed.initialize(root, uri);
        }
        if (recurring != null) {
            recurring.initialize(root, uri);
            recurring.setPlan(plan);
            recurring.setPhase(this);
        }
        for (DefaultUsage usage : usages) {
            usage.initialize(root, uri);
            usage.setPhase(this);
        }
        duration.initialize(root, uri);
    }

    public DefaultPlanPhase setPrettyName(final String prettyName) {
        this.prettyName = prettyName;
        return this;
    }

    public DefaultPlanPhase setFixed(final DefaultFixed fixed) {
        this.fixed = fixed;
        return this;
    }

    public DefaultPlanPhase setRecurring(final DefaultRecurring recurring) {
        this.recurring = recurring;
        return this;
    }

    public DefaultPlanPhase setUsages(final DefaultUsage[] usages) {
        this.usages = usages;
        return this;
    }

    public DefaultPlanPhase setPhaseType(final PhaseType cohort) {
        this.type = cohort;
        return this;
    }

    public DefaultPlanPhase setDuration(final DefaultDuration duration) {
        this.duration = duration;
        return this;
    }

    public DefaultPlanPhase setPlan(final Plan plan) {
        this.plan = plan;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPlanPhase)) {
            return false;
        }

        final DefaultPlanPhase that = (DefaultPlanPhase) o;

        if (duration != null ? !duration.equals(that.duration) : that.duration != null) {
            return false;
        }
        if (fixed != null ? !fixed.equals(that.fixed) : that.fixed != null) {
            return false;
        }
        if (recurring != null ? !recurring.equals(that.recurring) : that.recurring != null) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        /*
        if (!Arrays.equals(usages, that.usages)) {
            return false;
        }
        */

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (duration != null ? duration.hashCode() : 0);
        result = 31 * result + (fixed != null ? fixed.hashCode() : 0);
        result = 31 * result + (recurring != null ? recurring.hashCode() : 0);
        //result = 31 * result + (usages != null ? Arrays.hashCode(usages) : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultPlanPhase{");
        sb.append("type=").append(type);
        sb.append(", duration=").append(duration);
        sb.append(", fixed=").append(fixed);
        sb.append(", recurring=").append(recurring);
        sb.append(", usages=").append(Arrays.toString(usages));
        sb.append(", plan=").append(plan.getName());
        sb.append('}');
        return sb.toString();
    }
}
