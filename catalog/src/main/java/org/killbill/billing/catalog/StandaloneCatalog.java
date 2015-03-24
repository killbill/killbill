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
import java.util.ArrayList;
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
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.rules.PlanRules;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

import com.google.common.collect.ImmutableList;

@XmlRootElement(name = "catalog")
@XmlAccessorType(XmlAccessType.NONE)
public class StandaloneCatalog extends ValidatingConfig<StandaloneCatalog> implements StaticCatalog {
    @XmlElement(required = true)
    private Date effectiveDate;

    @XmlElement(required = true)
    private String catalogName;

    @XmlElement(required = true)
    private BillingMode recurringBillingMode;

    private URI catalogURI;

    @XmlElementWrapper(name = "currencies", required = true)
    @XmlElement(name = "currency", required = true)
    private Currency[] supportedCurrencies;

    @XmlElementWrapper(name = "units", required = false)
    @XmlElement(name = "unit", required = true)
    private DefaultUnit[] units;

    @XmlElementWrapper(name = "products", required = true)
    @XmlElement(name = "product", required = true)
    private DefaultProduct[] products;

    @XmlElement(name = "rules", required = true)
    private PlanRules planRules;

    @XmlElementWrapper(name = "plans", required = true)
    @XmlElement(name = "plan", required = true)
    private DefaultPlan[] plans;

    @XmlElement(name = "priceLists", required = true)
    private DefaultPriceListSet priceLists;

    public StandaloneCatalog() {
    }

    protected StandaloneCatalog(final Date effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.ICatalog#getCalalogName()
      */
    @Override
    public String getCatalogName() {
        return catalogName;
    }

    @Override
    public Date getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public BillingMode getRecurringBillingMode() {
        return recurringBillingMode;
    }

    /* (non-Javadoc)
     * @see org.killbill.billing.catalog.ICatalog#getProducts()
     */
   @Override
   public DefaultProduct[] getCurrentProducts() {
       return products;
   }

   /* (non-Javadoc)
    * @see org.killbill.billing.catalog.ICatalog#getProducts()
    */
    @Override
    public DefaultUnit[] getCurrentUnits() {
        return units;
    }

    @Override
    public Currency[] getCurrentSupportedCurrencies() {
        return supportedCurrencies;
    }

    @Override
    public DefaultPlan[] getCurrentPlans() {
        return plans;
    }

    public URI getCatalogURI() {
        return catalogURI;
    }

    public PlanRules getPlanRules() {
        return planRules;
    }

    public DefaultPriceList findCurrentPriceList(final String priceListName) throws CatalogApiException {
        return priceLists.findPriceListFrom(priceListName);
    }

    public DefaultPriceListSet getPriceLists() {
        return this.priceLists;
    }

    /* (non-Javadoc)
      * @see org.killbill.billing.catalog.ICatalog#getPlan(java.lang.String, java.lang.String)
      */
    @Override
    public DefaultPlan findCurrentPlan(final String productName, final BillingPeriod period, final String priceListName, List<PlanPhasePriceOverride> overrides) throws CatalogApiException {
        if (productName == null) {
            throw new CatalogApiException(ErrorCode.CAT_NULL_PRODUCT_NAME);
        }
        if (priceLists == null) {
            throw new CatalogApiException(ErrorCode.CAT_PRICE_LIST_NOT_FOUND, priceListName);
        }
        final Product product = findCurrentProduct(productName);
        final DefaultPlan result = priceLists.getPlanFrom(priceListName, product, period);
        if (result == null) {
            final String periodString = (period == null) ? "NULL" : period.toString();
            throw new CatalogApiException(ErrorCode.CAT_PLAN_NOT_FOUND, productName, periodString, priceListName);
        }
        return result;
    }

    @Override
    public DefaultPlan findCurrentPlan(final String name) throws CatalogApiException {
        if (name == null || plans == null) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, name);
        }
        for (final DefaultPlan p : plans) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, name);
    }

    @Override
    public Product findCurrentProduct(final String name) throws CatalogApiException {
        if (name == null || products == null) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PRODUCT, name);
        }
        for (final DefaultProduct p : products) {
            if (p.getName().equals(name)) {
                return p;
            }
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


    //////////////////////////////////////////////////////////////////////////////
    //
    // RULES
    //
    //////////////////////////////////////////////////////////////////////////////
    @Override
    public BillingActionPolicy planChangePolicy(final PlanPhaseSpecifier from, final PlanSpecifier to) throws CatalogApiException {
        return planRules.getPlanChangePolicy(from, to, this);
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier from, final PlanSpecifier to) throws CatalogApiException {
        return planRules.getPlanChangeAlignment(from, to, this);
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

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to)
            throws CatalogApiException {
        return planRules.planChange(from, to, this);
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        validateCollection(catalog, errors, products);
        validateCollection(catalog, errors, plans);
        priceLists.validate(catalog, errors);
        planRules.validate(catalog, errors);
        return errors;
    }


    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        catalogURI = sourceURI;
        super.initialize(catalog, sourceURI);
        planRules.initialize(catalog, sourceURI);
        priceLists.initialize(catalog, sourceURI);
        for (final DefaultProduct p : products) {
            p.initialize(catalog, sourceURI);
        }
        for (final DefaultPlan p : plans) {
            p.initialize(catalog, sourceURI);
        }

    }


    //////////////////////////////////////////////////////////////////////////////
    //
    // UNIT LIMIT
    //
    //////////////////////////////////////////////////////////////////////////////
    
    @Override
    public boolean compliesWithLimits(final String phaseName, final String unit, final double value) throws CatalogApiException {
        PlanPhase phase = findCurrentPhase(phaseName);
        return phase.compliesWithLimits(unit, value);
    }

    protected StandaloneCatalog setProducts(final DefaultProduct[] products) {
        this.products = products;
        return this;
    }

    protected StandaloneCatalog setSupportedCurrencies(final Currency[] supportedCurrencies) {
        this.supportedCurrencies = supportedCurrencies;
        return this;
    }

    protected StandaloneCatalog setPlanChangeRules(final PlanRules planChangeRules) {
        this.planRules = planChangeRules;
        return this;
    }

    protected StandaloneCatalog setPlans(final DefaultPlan[] plans) {
        this.plans = plans;
        return this;
    }

    public StandaloneCatalog setCatalogName(final String catalogName) {
        this.catalogName = catalogName;
        return this;
    }

    protected StandaloneCatalog setEffectiveDate(final Date effectiveDate) {
        this.effectiveDate = effectiveDate;
        return this;
    }

    public StandaloneCatalog setRecurringBillingMode(final BillingMode recurringBillingMode) {
        this.recurringBillingMode = recurringBillingMode;
        return this;
    }

    protected StandaloneCatalog setPlanRules(final PlanRules planRules) {
        this.planRules = planRules;
        return this;
    }

    protected StandaloneCatalog setPriceLists(final DefaultPriceListSet priceLists) {
        this.priceLists = priceLists;
        return this;
    }

    @Override
    public boolean canCreatePlan(final PlanSpecifier specifier) throws CatalogApiException {
        final Product product = findCurrentProduct(specifier.getProductName());
        final Plan plan = findCurrentPlan(specifier.getProductName(), specifier.getBillingPeriod(), specifier.getPriceListName(), ImmutableList.<PlanPhasePriceOverride>of());
        final DefaultPriceList priceList = findCurrentPriceList(specifier.getPriceListName());

        return (!product.isRetired()) &&
                (!plan.isRetired()) &&
                (!priceList.isRetired());
    }

    @Override
    public List<Listing> getAvailableAddOnListings(final String baseProductName, @Nullable final String priceListName) {
        final List<Listing> availAddons = new ArrayList<Listing>();

        try {
            Product product = findCurrentProduct(baseProductName);
            if ( product != null ) {
                for ( Product availAddon : product.getAvailable() ) {
                    for ( BillingPeriod billingPeriod : BillingPeriod.values()) {
                        for( PriceList priceList : getPriceLists().getAllPriceLists()) {
                            if (priceListName == null || priceListName.equals(priceList.getName())) {
                                Plan addonInList = priceList.findPlan(availAddon, billingPeriod);
                                if ( (addonInList != null) ) {
                                    availAddons.add(new DefaultListing(addonInList, priceList));
                                }
                            }
                        }
                    }
                }
            }
        } catch (CatalogApiException e) {
            // No such product - just return an empty list
        }

        return availAddons;
    }

    @Override
    public List<Listing> getAvailableBasePlanListings() {
        final List<Listing> availBasePlans = new ArrayList<Listing>();

        for (Plan plan : getCurrentPlans()) {
            if (plan.getProduct().getCategory().equals(ProductCategory.BASE)) {
                for (PriceList priceList : getPriceLists().getAllPriceLists()) {
                    for (Plan priceListPlan : priceList.getPlans()) {
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
}
