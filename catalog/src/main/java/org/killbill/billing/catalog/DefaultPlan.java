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

package org.killbill.billing.catalog;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.util.cache.ExternalizableInput;
import org.killbill.billing.util.cache.ExternalizableOutput;
import org.killbill.billing.util.cache.MapperHolder;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

import com.google.common.annotations.VisibleForTesting;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPlan extends ValidatingConfig<StandaloneCatalog> implements Plan, Externalizable {

    private static final long serialVersionUID = -4159932819592790086L;

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlAttribute(required = false)
    private String prettyName;

    @XmlElement(required = false)
    private Date effectiveDateForExistingSubscriptions;

    @XmlElement(required = true)
    @XmlIDREF
    private DefaultProduct product;

    // In order to support older versions where recurringBillingMode is defined at the StandaloneCatalog level, we don't require that field.
    @XmlElement(required = false)
    private BillingMode recurringBillingMode;

    @XmlElementWrapper(name = "initialPhases", required = false)
    @XmlElement(name = "phase", required = false)
    private DefaultPlanPhase[] initialPhases;

    @XmlElement(name = "finalPhase", required = true)
    private DefaultPlanPhase finalPhase;

    //If this is missing it defaults to 1
    //No other value is allowed for BASE plans.
    //No other value is allowed for Tiered ADDONS
    //A value of -1 means unlimited
    @XmlElement(required = false)
    private Integer plansAllowedInBundle;

    private String priceListName;

    // Not exposed in XML
    @VisibleForTesting
    StandaloneCatalog staticCatalog;

    // For deserialization
    public DefaultPlan() {
        initialPhases = new DefaultPlanPhase[0];
    }

    public DefaultPlan(final StandaloneCatalog staticCatalog) {
        this.staticCatalog = staticCatalog;
        initialPhases = new DefaultPlanPhase[0];
    }

    public DefaultPlan(final StandaloneCatalog staticCatalog, final String planName, final DefaultPlan in, final PlanPhasePriceOverride[] overrides) {
        this.staticCatalog = staticCatalog;
        this.name = planName;
        this.effectiveDateForExistingSubscriptions = in.getEffectiveDateForExistingSubscriptions();
        this.product = (DefaultProduct) in.getProduct();
        this.initialPhases = new DefaultPlanPhase[in.getInitialPhases().length];
        for (int i = 0; i < overrides.length - 1; i++) {
            final DefaultPlanPhase newPhase = new DefaultPlanPhase(this, in.getInitialPhases()[i], overrides[i]);
            initialPhases[i] = newPhase;
        }
        this.finalPhase = new DefaultPlanPhase(this, in.getFinalPhase(), overrides[overrides.length - 1]);
        this.priceListName = in.getPriceListName();
        this.recurringBillingMode = in.getRecurringBillingMode();
    }

    @Override
    public Date getEffectiveDateForExistingSubscriptions() {
        return effectiveDateForExistingSubscriptions;
    }

    @Override
    public StaticCatalog getCatalog() {
        return staticCatalog;
    }

    @Override
    public BillingMode getRecurringBillingMode() {
        return recurringBillingMode;
    }

    @Override
    public DefaultPlanPhase[] getInitialPhases() {
        return initialPhases;
    }

    @Override
    public Product getProduct() {
        return product;
    }

    @Override
    public String getPriceListName() {
        return priceListName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrettyName() {
        return prettyName;
    }

    @Override
    public DefaultPlanPhase getFinalPhase() {
        return finalPhase;
    }

    @Override
    public PlanPhase[] getAllPhases() {
        final int length = initialPhases.length + 1;
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
    public BillingPeriod getRecurringBillingPeriod() {
        return finalPhase.getRecurring() != null ? finalPhase.getRecurring().getBillingPeriod() : BillingPeriod.NO_BILLING_PERIOD;
    }

    @Override
    public int getPlansAllowedInBundle() {
        return plansAllowedInBundle;
    }

    @Override
    public Iterator<PlanPhase> getInitialPhaseIterator() {
        final Collection<PlanPhase> list = new ArrayList<PlanPhase>();
        Collections.addAll(list, initialPhases);
        return list.iterator();
    }

    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);

        if (prettyName == null) {
            this.prettyName = name;
        }
        if (finalPhase != null) {
            finalPhase.setPlan(this);
            finalPhase.initialize(catalog, sourceURI);
        }
        for (final DefaultPlanPhase p : initialPhases) {
            p.setPlan(this);
            p.initialize(catalog, sourceURI);
        }
        if (recurringBillingMode == null) {
            this.recurringBillingMode = catalog.getRecurringBillingMode();
        }

        this.priceListName = this.priceListName != null ? this.priceListName : findPriceListForPlan(catalog);
        this.staticCatalog = catalog;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        if (effectiveDateForExistingSubscriptions != null &&
            catalog.getEffectiveDate().getTime() > effectiveDateForExistingSubscriptions.getTime()) {
            errors.add(new ValidationError(String.format("Price effective date %s is before catalog effective date '%s'",
                                                         effectiveDateForExistingSubscriptions,
                                                         catalog.getEffectiveDate()),
                                           catalog.getCatalogURI(), DefaultPlan.class, ""));
        }

        if (recurringBillingMode == null) {
            errors.add(new ValidationError(String.format("Invalid reccuring billingMode for plan '%s'", name), catalog.getCatalogURI(), DefaultPlan.class, ""));
        }

        if (product == null) {
            errors.add(new ValidationError(String.format("Invalid product for plan '%s'", name), catalog.getCatalogURI(), DefaultPlan.class, ""));
        }

        for (final DefaultPlanPhase cur : initialPhases) {
            cur.validate(catalog, errors);
            if (cur.getPhaseType() == PhaseType.EVERGREEN) {
                errors.add(new ValidationError(String.format("Initial Phase %s of plan %s cannot be of type %s",
                                                             cur.getName(), name, cur.getPhaseType()),
                                               catalog.getCatalogURI(), DefaultPlan.class, ""));
            }
        }

        finalPhase.validate(catalog, errors);

        if (finalPhase.getPhaseType() == PhaseType.TRIAL || finalPhase.getPhaseType() == PhaseType.DISCOUNT) {
            errors.add(new ValidationError(String.format("Final Phase %s of plan %s cannot be of type %s",
                                                         finalPhase.getName(), name, finalPhase.getPhaseType()),
                                           catalog.getCatalogURI(), DefaultPlan.class, ""));
        }
        // Safety check
        if (plansAllowedInBundle == null) {
            throw new IllegalStateException("plansAllowedInBundle should have been automatically been initialized with DEFAULT_NON_REQUIRED_INTEGER_FIELD_VALUE (-1)");
        }
        return errors;
    }

    public void setEffectiveDateForExistingSubscriptions(
            final Date effectiveDateForExistingSubscriptions) {
        this.effectiveDateForExistingSubscriptions = effectiveDateForExistingSubscriptions;
    }

    public DefaultPlan setName(final String name) {
        this.name = name;
        return this;
    }

    public DefaultPlan setPrettyName(final String prettyName) {
        this.prettyName = prettyName;
        return this;
    }

    public DefaultPlan setFinalPhase(final DefaultPlanPhase finalPhase) {
        this.finalPhase = finalPhase;
        return this;
    }

    public DefaultPlan setProduct(final Product product) {
        this.product = (DefaultProduct) product;
        return this;
    }

    public DefaultPlan setPriceListName(final String priceListName) {
        this.priceListName = priceListName;
        return this;
    }

    public DefaultPlan setInitialPhases(final DefaultPlanPhase[] phases) {
        this.initialPhases = phases;
        return this;
    }

    public DefaultPlan setPlansAllowedInBundle(final Integer plansAllowedInBundle) {
        this.plansAllowedInBundle = plansAllowedInBundle;
        return this;
    }

    public DefaultPlan setRecurringBillingMode(final BillingMode billingMode) {
        this.recurringBillingMode = billingMode;
        return this;
    }

    @Override
    public DateTime dateOfFirstRecurringNonZeroCharge(final DateTime subscriptionStartDate, final PhaseType initialPhaseType) {
        DateTime result = subscriptionStartDate;
        boolean skipPhase = initialPhaseType != null;
        for (final PlanPhase phase : getAllPhases()) {
            if (skipPhase) {
                if (phase.getPhaseType() != initialPhaseType) {
                    continue;
                } else {
                    skipPhase = false;
                }
            }
            final Recurring recurring = phase.getRecurring();
            if (phase.getDuration().getUnit() != TimeUnit.UNLIMITED &&
                (recurring == null || recurring.getRecurringPrice() == null || recurring.getRecurringPrice().isZero())) {
                try {
                    result = phase.getDuration().addToDateTime(result);
                } catch (final CatalogApiException ignored) {
                }
            } else {
                break;
            }
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPlan)) {
            return false;
        }

        final DefaultPlan that = (DefaultPlan) o;

        if (effectiveDateForExistingSubscriptions != null ? !effectiveDateForExistingSubscriptions.equals(that.effectiveDateForExistingSubscriptions) : that.effectiveDateForExistingSubscriptions != null) {
            return false;
        }
        if (finalPhase != null ? !finalPhase.equals(that.finalPhase) : that.finalPhase != null) {
            return false;
        }
        if (!Arrays.equals(initialPhases, that.initialPhases)) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (plansAllowedInBundle != null ? !plansAllowedInBundle.equals(that.plansAllowedInBundle) : that.plansAllowedInBundle != null) {
            return false;
        }
        if (product != null ? !product.equals(that.product) : that.product != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (effectiveDateForExistingSubscriptions != null ? effectiveDateForExistingSubscriptions.hashCode() : 0);
        result = 31 * result + (initialPhases != null ? Arrays.hashCode(initialPhases) : 0);
        result = 31 * result + (finalPhase != null ? finalPhase.hashCode() : 0);
        result = 31 * result + (plansAllowedInBundle != null ? plansAllowedInBundle.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefaultPlan [name=" + name + ", effectiveDateForExistingSubscriptions="
               + effectiveDateForExistingSubscriptions + ", product=" + product.getName() + ", initialPhases="
               + Arrays.toString(initialPhases) + ", finalPhase=" + finalPhase.getName() + ", plansAllowedInBundle="
               + plansAllowedInBundle + "]";
    }

    private String findPriceListForPlan(final StandaloneCatalog catalog) {
        for (final PriceList cur : catalog.getPriceLists().getAllPriceLists()) {
            final DefaultPriceList curDefaultPriceList = (DefaultPriceList) cur;
            if (curDefaultPriceList.findPlan(name) != null) {
                return curDefaultPriceList.getName();
            }
        }
        throw new IllegalStateException("Cannot extract pricelist for plan " + name);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        MapperHolder.mapper().readerForUpdating(this).readValue(new ExternalizableInput(in));
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        MapperHolder.mapper().writeValue(new ExternalizableOutput(oo), this);
    }
}
