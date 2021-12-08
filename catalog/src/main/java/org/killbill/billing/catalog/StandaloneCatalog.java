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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.rules.PlanRules;
import org.killbill.billing.catalog.rules.DefaultPlanRules;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlRootElement(name = "catalog")
@XmlAccessorType(XmlAccessType.NONE)
public class StandaloneCatalog extends ValidatingConfig<StandaloneCatalog> implements StaticCatalog, Externalizable {

    @XmlElement(required = true)
    private Date effectiveDate;

    @XmlElement(required = true)
    private String catalogName;

    @XmlElement(required = false)
    private BillingMode recurringBillingMode;

    @XmlElementWrapper(name = "currencies", required = true)
    @XmlElement(name = "currency", required = false)
    private Currency[] supportedCurrencies;

    @XmlElementWrapper(name = "units", required = false)
    @XmlElement(name = "unit", required = false)
    private DefaultUnit[] units;

    @XmlElementWrapper(name = "products", required = true)
    @XmlElement(type = DefaultProduct.class, name = "product", required = false)
    private CatalogEntityCollection<Product> products;

    @XmlElement(name = "rules",  required = true)
    private DefaultPlanRules planRules;

    @XmlElementWrapper(name = "plans", required = true)
    @XmlElement(type = DefaultPlan.class, name = "plan", required = false)
    private CatalogEntityCollection<Plan> plans;

    @XmlElement(name = "priceLists", required = true)
    private DefaultPriceListSet priceLists;

    public StandaloneCatalog() {
        this.plans = new CatalogEntityCollection<Plan>();
        this.products = new CatalogEntityCollection<Product>();
    }

    protected StandaloneCatalog(final Date effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    @Override
    public String getCatalogName() {
        return catalogName;
    }

    public StandaloneCatalog setCatalogName(final String catalogName) {
        this.catalogName = catalogName;
        return this;
    }

    @Override
    public Date getEffectiveDate() {
        return effectiveDate;
    }

    public StandaloneCatalog setEffectiveDate(final Date effectiveDate) {
        this.effectiveDate = effectiveDate;
        return this;
    }

    @Override
    public Collection<Product> getProducts() {
        return products.getEntries();
    }

    public StandaloneCatalog setProducts(final Iterable<Product> products) {
        this.products = new CatalogEntityCollection<Product>(products);
        return this;
    }

    public CatalogEntityCollection<Product> getCatalogEntityCollectionProduct() {
        return products;
    }

    @Override
    public Unit[] getUnits() {
        return units;
    }

    public StandaloneCatalog setUnits(final DefaultUnit[] units) {
        this.units = units;
        return this;
    }

    @Override
    public Currency[] getSupportedCurrencies() {
        return supportedCurrencies;
    }

    public StandaloneCatalog setSupportedCurrencies(final Currency[] supportedCurrencies) {
        this.supportedCurrencies = supportedCurrencies;
        return this;
    }

    @Override
    public Collection<Plan> getPlans() {
        return plans.getEntries();
    }

    public StandaloneCatalog setPlans(final Iterable<Plan> plans) {
        this.plans = new CatalogEntityCollection<Plan>(plans);
        return this;
    }

    public CatalogEntityCollection<Plan> getPlansMap() {
        return plans;
    }

    public boolean isTemplateCatalog() {
        return (products == null || products.isEmpty()) &&
               (plans == null || plans.isEmpty()) &&
               (supportedCurrencies == null || supportedCurrencies.length == 0);
    }

    @Override
    public PlanRules getPlanRules() {
        return planRules;
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // RULES
    //
    //////////////////////////////////////////////////////////////////////////////

    public StandaloneCatalog setPlanRules(final DefaultPlanRules planRules) {
        this.planRules = planRules;
        return this;
    }

    public DefaultPriceListSet getPriceLists() {
        return this.priceLists;
    }

    public StandaloneCatalog setPriceLists(final DefaultPriceListSet priceLists) {
        this.priceLists = priceLists;
        return this;
    }

    @Override
    public Plan createOrFindPlan(final PlanSpecifier spec, final PlanPhasePriceOverridesWithCallContext unused) throws CatalogApiException {
        final Plan result;
        if (spec.getPlanName() != null) {
            result = findPlan(spec.getPlanName());
        } else {
            if (spec.getProductName() == null) {
                throw new CatalogApiException(ErrorCode.CAT_NULL_PRODUCT_NAME);
            }
            if (spec.getBillingPeriod() == null) {
                throw new CatalogApiException(ErrorCode.CAT_NULL_BILLING_PERIOD);
            }
            final String inputOrDefaultPricelist = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            final Product product = findProduct(spec.getProductName());
            result = ((DefaultPriceListSet) priceLists).getPlanFrom(product, spec.getBillingPeriod(), inputOrDefaultPricelist);
        }
        if (result == null) {
            throw new CatalogApiException(ErrorCode.CAT_PLAN_NOT_FOUND,
                                          spec.getPlanName() != null ? spec.getPlanName() : "undefined",
                                          spec.getProductName() != null ? spec.getProductName() : "undefined",
                                          spec.getBillingPeriod() != null ? spec.getBillingPeriod() : "undefined",
                                          spec.getPriceListName() != null ? spec.getPriceListName() : "undefined");
        }
        return result;
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // UNIT LIMIT
    //
    //////////////////////////////////////////////////////////////////////////////

    @Override
    public DefaultPlan findPlan(final String name) throws CatalogApiException {
        if (name == null || plans == null) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, name);
        }
        final DefaultPlan result = (DefaultPlan) plans.findByName(name);
        if (result != null) {
            return result;
        }
        throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, name);
    }

    @Override
    public Product findProduct(final String name) throws CatalogApiException {
        if (name == null || products == null) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PRODUCT, name);
        }
        final Product result = products.findByName(name);
        if (result != null) {
            return result;
        }
        throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PRODUCT, name);
    }

    @Override
    public PlanPhase findPhase(final String name) throws CatalogApiException {
        if (name == null || plans == null) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PHASE, name);
        }
        final String planName = DefaultPlanPhase.planName(name);
        final Plan plan = findPlan(planName);
        return plan.findPhase(name);
    }

    @Override
    public PriceList findPriceList(final String name)
            throws CatalogApiException {
        if (name == null || priceLists == null) {
            throw new CatalogApiException(ErrorCode.CAT_PRICE_LIST_NOT_FOUND, name);
        }
        return ((DefaultPriceListSet)priceLists).findPriceListFrom(name);
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        validateCollection(catalog, errors, (DefaultProduct[]) products.toArray(new DefaultProduct[0]));
        validateCollection(catalog, errors, (DefaultPlan[]) plans.toArray(new DefaultPlan[0]));
        priceLists.validate(catalog, errors);
        planRules.validate(catalog, errors);
        validatePlanDuration(catalog, errors);
        return errors;
    }

    private ValidationErrors validatePlanDuration(final StaticCatalog newCatalogVersion, final ValidationErrors errors) {
        for (final Plan plan : newCatalogVersion.getPlans()) {
            PlanPhase[] planPhases = plan.getAllPhases();
            for (int i = 0; i < planPhases.length; i++) {
                if (planPhases[i].getPhaseType().name().equals(PhaseType.EVERGREEN.name())
                    && !planPhases[i].getDuration().getUnit().name().equals(TimeUnit.UNLIMITED.name())) {
                    errors.add(new ValidationError(String.format(
                            "EVERGREEN Phase '%s' for plan '%s' in version '%s' must have duration as UNLIMITED'",
                            planPhases[i].getName(), plan.getName(), plan.getCatalog().getEffectiveDate()),
                                                   DefaultVersionedCatalog.class, ""));
                } else if (!planPhases[i].getPhaseType().name().equals(PhaseType.EVERGREEN.name())
                           && planPhases[i].getDuration().getUnit().name().equals(TimeUnit.UNLIMITED.name())) {
                    errors.add(new ValidationError(String.format(
                            "'%s' Phase '%s' for plan '%s' in version '%s' must not have duration as UNLIMITED'",
                            planPhases[i].getPhaseType().name(), planPhases[i].getName(), plan.getName(),
                            plan.getCatalog().getEffectiveDate()), DefaultVersionedCatalog.class, ""));
                }
            }
        }
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);

        planRules.initialize(catalog);
        priceLists.initialize(catalog);
        for (final DefaultUnit cur : units) {
            cur.initialize(catalog);
        }
        for (final Product p : products.getEntries()) {
            ((DefaultProduct) p).initialize(catalog);
        }
        for (final Plan p : plans.getEntries()) {
            ((DefaultPlan) p).initialize(catalog);
        }
    }

    public BillingMode getRecurringBillingMode() {
        return recurringBillingMode;
    }

    public StandaloneCatalog setRecurringBillingMode(final BillingMode recurringBillingMode) {
        this.recurringBillingMode = recurringBillingMode;
        return this;
    }

    @Override
    public List<Listing> getAvailableAddOnListings(final String baseProductName, @Nullable final String priceListName) {
        final List<Listing> availAddons = new ArrayList<Listing>();

        try {
            final Product product = findProduct(baseProductName);
            if (product != null) {
                for (final Product availAddon : product.getAvailable()) {
                    for (final BillingPeriod billingPeriod : BillingPeriod.values()) {
                        for (final PriceList priceList : getPriceLists().getAllPriceLists()) {
                            if (priceListName == null || priceListName.equals(priceList.getName())) {
                                final Collection<Plan> addonInList = priceList.findPlans(availAddon, billingPeriod);
                                for (final Plan cur : addonInList) {
                                    availAddons.add(new DefaultListing(cur, priceList));
                                }
                            }
                        }
                    }
                }
            }
        } catch (final CatalogApiException e) {
            // No such product - just return an empty list
        }
        return availAddons;
    }

    @Override
    public List<Listing> getAvailableBasePlanListings() {
        final List<Listing> availBasePlans = new ArrayList<Listing>();

        for (final Plan plan : getPlans()) {
            if (plan.getProduct().getCategory().equals(ProductCategory.BASE)) {
                for (final PriceList priceList : getPriceLists().getAllPriceLists()) {
                    for (final Plan priceListPlan : priceList.getPlans()) {
                        if (priceListPlan.getName().equals(plan.getName()) &&
                            priceListPlan.getProduct().getName().equals(plan.getProduct().getName())) {
                            availBasePlans.add(new DefaultListing(priceListPlan, priceList));
                        }
                    }
                }
            }
        }
        return availBasePlans;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StandaloneCatalog)) {
            return false;
        }

        final StandaloneCatalog that = (StandaloneCatalog) o;

        if (catalogName != null ? !catalogName.equals(that.catalogName) : that.catalogName != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (planRules != null ? !planRules.equals(that.planRules) : that.planRules != null) {
            return false;
        }
        if (!plans.equals(that.plans)) {
            return false;
        }
        if (priceLists != null ? !priceLists.equals(that.priceLists) : that.priceLists != null) {
            return false;
        }
        if (!products.equals(that.products)) {
            return false;
        }
        if (recurringBillingMode != that.recurringBillingMode) {
            return false;
        }
        if (!Arrays.equals(supportedCurrencies, that.supportedCurrencies)) {
            return false;
        }
        if (!Arrays.equals(units, that.units)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = effectiveDate != null ? effectiveDate.hashCode() : 0;
        result = 31 * result + (catalogName != null ? catalogName.hashCode() : 0);
        result = 31 * result + (recurringBillingMode != null ? recurringBillingMode.hashCode() : 0);
        result = 31 * result + (supportedCurrencies != null ? Arrays.hashCode(supportedCurrencies) : 0);
        result = 31 * result + (units != null ? Arrays.hashCode(units) : 0);
        result = 31 * result + (products != null ? products.hashCode() : 0);
        result = 31 * result + (planRules != null ? planRules.hashCode() : 0);
        result = 31 * result + (plans != null ? plans.hashCode() : 0);
        result = 31 * result + (priceLists != null ? priceLists.hashCode() : 0);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(effectiveDate);
        out.writeUTF(catalogName);
        out.writeBoolean(recurringBillingMode != null);
        if (recurringBillingMode != null) {
            out.writeUTF(recurringBillingMode.name());
        }
        out.writeObject(supportedCurrencies);
        out.writeObject(units);
        out.writeObject(products);
        out.writeObject(planRules);
        out.writeObject(plans);
        out.writeObject(priceLists);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.effectiveDate = (Date) in.readObject();
        this.catalogName = in.readUTF();
        this.recurringBillingMode = in.readBoolean() ? BillingMode.valueOf(in.readUTF()) : null;
        this.supportedCurrencies = (Currency[]) in.readObject();
        this.units = (DefaultUnit[]) in.readObject();
        this.products = (CatalogEntityCollection<Product>) in.readObject();
        this.planRules = (DefaultPlanRules) in.readObject();
        this.plans = (CatalogEntityCollection<Plan>) in.readObject();
        this.priceLists = (DefaultPriceListSet) in.readObject();
    }
}
