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

import java.net.URI;
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
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.rules.DefaultPlanRules;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlRootElement(name = "catalog")
@XmlAccessorType(XmlAccessType.NONE)
public class StandaloneCatalog extends ValidatingConfig<StandaloneCatalog> implements StaticCatalog {

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

    @XmlElement(name = "rules", required = true)
    private DefaultPlanRules planRules;

    @XmlElementWrapper(name = "plans", required = true)
    @XmlElement(type = DefaultPlan.class, name = "plan", required = false)
    private CatalogEntityCollection<Plan> plans;

    @XmlElement(name = "priceLists", required = true)
    private DefaultPriceListSet priceLists;

    private URI catalogURI;

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
    public Collection<Product> getCurrentProducts() {
        return products.getEntries();
    }

    public CatalogEntityCollection<Product> getCatalogEntityCollectionProduct() {
        return products;
    }

    @Override
    public DefaultUnit[] getCurrentUnits() {
        return units;
    }

    @Override
    public Currency[] getCurrentSupportedCurrencies() {
        return supportedCurrencies;
    }

    @Override
    public Collection<Plan> getCurrentPlans() {
        return plans.getEntries();
    }

    public CatalogEntityCollection<Plan> getCatalogEntityCollectionPlan() {
        return plans;
    }

    public boolean isTemplateCatalog() {
        return (products == null || products.isEmpty()) &&
               (plans == null || plans.isEmpty()) &&
               (supportedCurrencies == null || supportedCurrencies.length == 0);
    }

    public URI getCatalogURI() {
        return catalogURI;
    }

    public DefaultPlanRules getPlanRules() {
        return planRules;
    }

    public StandaloneCatalog setPlanRules(final DefaultPlanRules planRules) {
        this.planRules = planRules;
        return this;
    }

    public DefaultPriceList findCurrentPriceList(final String priceListName) throws CatalogApiException {
        return priceLists.findPriceListFrom(priceListName);
    }

    public DefaultPriceListSet getPriceLists() {
        return this.priceLists;
    }

    public StandaloneCatalog setPriceLists(final DefaultPriceListSet priceLists) {
        this.priceLists = priceLists;
        return this;
    }

    @Override
    public Plan createOrFindCurrentPlan(final PlanSpecifier spec, final PlanPhasePriceOverridesWithCallContext unused) throws CatalogApiException {
        final Plan result;
        if (spec.getPlanName() != null) {
            result = findCurrentPlan(spec.getPlanName());
        } else {
            if (spec.getProductName() == null) {
                throw new CatalogApiException(ErrorCode.CAT_NULL_PRODUCT_NAME);
            }
            if (spec.getBillingPeriod() == null) {
                throw new CatalogApiException(ErrorCode.CAT_NULL_BILLING_PERIOD);
            }
            final String inputOrDefaultPricelist = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            final Product product = findCurrentProduct(spec.getProductName());
            result = priceLists.getPlanFrom(product, spec.getBillingPeriod(), inputOrDefaultPricelist);
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
    // RULES
    //
    //////////////////////////////////////////////////////////////////////////////

    @Override
    public DefaultPlan findCurrentPlan(final String name) throws CatalogApiException {
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
    public Product findCurrentProduct(final String name) throws CatalogApiException {
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
    public PlanPhase findCurrentPhase(final String name) throws CatalogApiException {
        if (name == null || plans == null) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PHASE, name);
        }
        final String planName = DefaultPlanPhase.planName(name);
        final Plan plan = findCurrentPlan(planName);
        return plan.findPhase(name);
    }

    @Override
    public PriceList findCurrentPricelist(final String name)
            throws CatalogApiException {
        if (name == null || priceLists == null) {
            throw new CatalogApiException(ErrorCode.CAT_PRICE_LIST_NOT_FOUND, name);
        }
        return priceLists.findPriceListFrom(name);
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase) throws CatalogApiException {
        return planRules.getPlanCancelPolicy(planPhase, this);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier) throws CatalogApiException {
        return planRules.getPlanCreateAlignment(specifier, this);
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase) throws CatalogApiException {
        return planRules.getBillingAlignment(planPhase, this);
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // UNIT LIMIT
    //
    //////////////////////////////////////////////////////////////////////////////

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to)
            throws CatalogApiException {
        return planRules.planChange(from, to, this);
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        validateCollection(catalog, errors, (DefaultProduct[]) products.toArray(new DefaultProduct[0]));
        validateCollection(catalog, errors, (DefaultPlan[]) plans.toArray(new DefaultPlan[0]));
        priceLists.validate(catalog, errors);
        planRules.validate(catalog, errors);
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {

        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);

        catalogURI = sourceURI;
        planRules.initialize(catalog, sourceURI);
        priceLists.initialize(catalog, sourceURI);
        for (final DefaultUnit cur : units) {
            cur.initialize(catalog, sourceURI);
        }
        for (final Product p : products.getEntries()) {
            ((DefaultProduct) p).initialize(catalog, sourceURI);
        }
        for (final Plan p : plans.getEntries()) {
            ((DefaultPlan) p).initialize(catalog, sourceURI);
        }
    }

    public BillingMode getRecurringBillingMode() {
        return recurringBillingMode;
    }

    public StandaloneCatalog setRecurringBillingMode(final BillingMode recurringBillingMode) {
        this.recurringBillingMode = recurringBillingMode;
        return this;
    }

    public StandaloneCatalog setProducts(final Iterable<Product> products) {
        this.products = new CatalogEntityCollection<Product>(products);
        return this;
    }

    public StandaloneCatalog setSupportedCurrencies(final Currency[] supportedCurrencies) {
        this.supportedCurrencies = supportedCurrencies;
        return this;
    }

    public StandaloneCatalog setPlans(final Iterable<Plan> plans) {
        this.plans = new CatalogEntityCollection<Plan>(plans);
        return this;
    }

    public StandaloneCatalog setUnits(final DefaultUnit[] units) {
        this.units = units;
        return this;
    }

    @Override
    public List<Listing> getAvailableAddOnListings(final String baseProductName, @Nullable final String priceListName) {
        final List<Listing> availAddons = new ArrayList<Listing>();

        try {
            final Product product = findCurrentProduct(baseProductName);
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

        for (final Plan plan : getCurrentPlans()) {
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
        if (catalogURI != null ? !catalogURI.equals(that.catalogURI) : that.catalogURI != null) {
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
        result = 31 * result + (catalogURI != null ? catalogURI.hashCode() : 0);
        return result;
    }
}
