/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.catalog;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationError;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPlan extends ValidatingConfig<StandaloneCatalog> implements Plan {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlAttribute(required = false)
    private Boolean retired = false;

    //TODO MDW Validation - effectiveDateForExistingSubscriptons > catalog effectiveDate
    @XmlElement(required = false)
    private Date effectiveDateForExistingSubscriptons;

    @XmlElement(required = true)
    @XmlIDREF
    private DefaultProduct product;

    @XmlElementWrapper(name = "initialPhases", required = false)
    @XmlElement(name = "phase", required = true)
    private DefaultPlanPhase[] initialPhases = new DefaultPlanPhase[0];

    @XmlElement(name = "finalPhase", required = true)
    private DefaultPlanPhase finalPhase;

    //If this is missing it defaults to 1
    //No other value is allowed for BASE plans.
    //No other value is allowed for Tiered ADDONS
    //A value of -1 means unlimited
    @XmlElement(required = false)
    private Integer plansAllowedInBundle = 1;

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlan#getEffectiveDateForExistingSubscriptons()
      */
    @Override
    public Date getEffectiveDateForExistingSubscriptons() {
        return effectiveDateForExistingSubscriptons;
    }    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getPhases()
	 */

    @Override
    public DefaultPlanPhase[] getInitialPhases() {
        return initialPhases;
    }

    /* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlan#getProduct()
	 */
    @Override
    public Product getProduct() {
        return product;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlan#getName()
      */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isRetired() {
        return retired;
    }

    @Override
    public DefaultPlanPhase getFinalPhase() {
        return finalPhase;
    }

    @Override
    public PlanPhase[] getAllPhases() {
        final int length = (initialPhases == null || initialPhases.length == 0) ? 1 : (initialPhases.length + 1);
        final PlanPhase[] allPhases = new DefaultPlanPhase[length];
        int cnt = 0;
        if (length > 1) {
            for (final PlanPhase cur : initialPhases) {
                allPhases[cnt++] = cur;
            }
        }
        allPhases[cnt++] = finalPhase;
        return allPhases;
    }

    @Override
    public PlanPhase findPhase(final String name) throws CatalogApiException {
        for (final PlanPhase pp : getAllPhases()) {
            if (pp.getName().equals(name)) {
                return pp;
            }

        }
        throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PHASE, name);
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return finalPhase.getBillingPeriod();
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlan#getPlansAllowedInBundle()
      */
    @Override
    public int getPlansAllowedInBundle() {
        return plansAllowedInBundle;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlan#getPhaseIterator()
      */
    @Override
    public Iterator<PlanPhase> getInitialPhaseIterator() {
        final ArrayList<PlanPhase> list = new ArrayList<PlanPhase>();
        for (final DefaultPlanPhase p : initialPhases) {
            list.add(p);
        }
        return list.iterator();
    }

    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        if (finalPhase != null) {
            finalPhase.setPlan(this);
            finalPhase.initialize(catalog, sourceURI);
        }
        if (initialPhases != null) {
            for (final DefaultPlanPhase p : initialPhases) {
                p.setPlan(this);
                p.initialize(catalog, sourceURI);
            }
        }
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        if (effectiveDateForExistingSubscriptons != null &&
                catalog.getEffectiveDate().getTime() > effectiveDateForExistingSubscriptons.getTime()) {
            errors.add(new ValidationError(String.format("Price effective date %s is before catalog effective date '%s'",
                                                         effectiveDateForExistingSubscriptons,
                                                         catalog.getEffectiveDate().getTime()),
                                           catalog.getCatalogURI(), DefaultInternationalPrice.class, ""));
        }

        return errors;
    }

    protected void setEffectiveDateForExistingSubscriptons(
            final Date effectiveDateForExistingSubscriptons) {
        this.effectiveDateForExistingSubscriptons = effectiveDateForExistingSubscriptons;
    }

    protected DefaultPlan setName(final String name) {
        this.name = name;
        return this;
    }

    protected DefaultPlan setPlansAllowedInBundle(final int plansAllowedInBundle) {
        this.plansAllowedInBundle = plansAllowedInBundle;
        return this;
    }

    protected DefaultPlan setFinalPhase(final DefaultPlanPhase finalPhase) {
        this.finalPhase = finalPhase;
        return this;
    }

    protected DefaultPlan setProduct(final DefaultProduct product) {
        this.product = product;
        return this;
    }

    protected DefaultPlan setInitialPhases(final DefaultPlanPhase[] phases) {
        this.initialPhases = phases;
        return this;
    }

    public DefaultPlan setRetired(final boolean retired) {
        this.retired = retired;
        return this;
    }

    public DefaultPlan setPlansAllowedInBundle(final Integer plansAllowedInBundle) {
        this.plansAllowedInBundle = plansAllowedInBundle;
        return this;
    }

    @Override
    public DateTime dateOfFirstRecurringNonZeroCharge(final DateTime subscriptionStartDate) {
        DateTime result = subscriptionStartDate.toDateTime();
        for (final PlanPhase phase : getAllPhases()) {
            if (phase.getRecurringPrice() == null || phase.getRecurringPrice().isZero()) {
                result = phase.getDuration().addToDateTime(result);
            } else {
                break;
            }
        }
        return result;
    }



	@Override
    public String toString() {
        return "DefaultPlan [name=" + name + ", retired=" + retired + ", effectiveDateForExistingSubscriptons="
                + effectiveDateForExistingSubscriptons + ", product=" + product + ", initialPhases="
                + Arrays.toString(initialPhases) + ", finalPhase=" + finalPhase + ", plansAllowedInBundle="
                + plansAllowedInBundle + "]";
    }
}
