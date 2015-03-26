/*
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
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
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
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.override.PriceOverride;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

public class StandaloneCatalogWithPriceOverride extends ValidatingConfig<StandaloneCatalogWithPriceOverride> implements StaticCatalog {

    private final StandaloneCatalog standaloneCatalog;
    private final PriceOverride priceOverride;
    private final Long tenantRecordId;

    private final InternalCallContextFactory internalCallContextFactory;

    public StandaloneCatalogWithPriceOverride(final StandaloneCatalog staticCatalog, final PriceOverride priceOverride, final Long tenantRecordId, final InternalCallContextFactory internalCallContextFactory) {
        this.tenantRecordId = tenantRecordId;
        this.standaloneCatalog = staticCatalog;
        this.priceOverride = priceOverride;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public String getCatalogName() {
        return standaloneCatalog.getCatalogName();
    }

    @Override
    public BillingMode getRecurringBillingMode() {
        return standaloneCatalog.getRecurringBillingMode();
    }

    @Override
    public Date getEffectiveDate() {
        return standaloneCatalog.getEffectiveDate();
    }

    @Override
    public Currency[] getCurrentSupportedCurrencies() throws CatalogApiException {
        return standaloneCatalog.getCurrentSupportedCurrencies();
    }

    @Override
    public DefaultProduct[] getCurrentProducts() throws CatalogApiException {
        return standaloneCatalog.getCurrentProducts();
    }

    @Override
    public Unit[] getCurrentUnits() throws CatalogApiException {
        return standaloneCatalog.getCurrentUnits();
    }

    @Override
    public DefaultPlan[] getCurrentPlans() throws CatalogApiException {
        return standaloneCatalog.getCurrentPlans();
    }

    @Override
    public Plan findCurrentPlan(final String productName, final BillingPeriod period, final String priceListName, final PlanPhasePriceOverridesWithCallContext overrides) throws CatalogApiException {
        final Plan defaultPlan = standaloneCatalog.findCurrentPlan(productName, period, priceListName, null);

        if (overrides == null ||
            overrides.getOverrides() == null ||
            overrides.getOverrides().isEmpty()) {
            return defaultPlan;
        }

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(overrides.getCallContext());
        return priceOverride.getOrCreateOverriddenPlan(defaultPlan, new DateTime(getEffectiveDate()), overrides.getOverrides(), internalCallContext);
    }

    @Override
    public Plan findCurrentPlan(final String planName) throws CatalogApiException {
        final Plan defaultPlan =  standaloneCatalog.findCurrentPlan(planName);
        if (defaultPlan != null) {
            return defaultPlan;
        }
        return null; // STEPH_PO
     //   priceOverride.
    }

    @Override
    public Product findCurrentProduct(final String productName) throws CatalogApiException {
        return standaloneCatalog.findCurrentProduct(productName);
    }

    @Override
    public PlanPhase findCurrentPhase(final String phaseName) throws CatalogApiException {
        return standaloneCatalog.findCurrentPhase(phaseName);
    }

    @Override
    public PriceList findCurrentPricelist(final String priceListName) throws CatalogApiException {
        return standaloneCatalog.findCurrentPricelist(priceListName);
    }

    @Override
    public BillingActionPolicy planChangePolicy(final PlanPhaseSpecifier planPhaseSpecifier, final PlanSpecifier planSpecifier) throws CatalogApiException {
        return standaloneCatalog.planChangePolicy(planPhaseSpecifier, planSpecifier);
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier planPhaseSpecifier, final PlanSpecifier planSpecifier) throws CatalogApiException {
        return standaloneCatalog.planChange(planPhaseSpecifier, planSpecifier);
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhaseSpecifier) throws CatalogApiException {
        return standaloneCatalog.planCancelPolicy(planPhaseSpecifier);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier planSpecifier) throws CatalogApiException {
        return standaloneCatalog.planCreateAlignment(planSpecifier);
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhaseSpecifier) throws CatalogApiException {
        return standaloneCatalog.billingAlignment(planPhaseSpecifier);
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier planPhaseSpecifier, final PlanSpecifier planSpecifier) throws CatalogApiException {
        return standaloneCatalog.planChangeAlignment(planPhaseSpecifier, planSpecifier);
    }

    @Override
    public boolean canCreatePlan(final PlanSpecifier planSpecifier) throws CatalogApiException {
        return standaloneCatalog.canCreatePlan(planSpecifier);
    }

    @Override
    public List<Listing> getAvailableBasePlanListings() throws CatalogApiException {
        return standaloneCatalog.getAvailableBasePlanListings();
    }

    @Override
    public List<Listing> getAvailableAddOnListings(final String baseProductName, @Nullable final String priceListName) throws CatalogApiException {
        return standaloneCatalog.getAvailableAddOnListings(baseProductName, priceListName);
    }

    @Override
    public boolean compliesWithLimits(final String phaseName, final String unit, final double value) throws CatalogApiException {
        return standaloneCatalog.compliesWithLimits(phaseName, unit, value);
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalogWithPriceOverride root, final ValidationErrors errors) {
        return standaloneCatalog.validate(root.standaloneCatalog, errors);
    }

    @Override
    public void initialize(final StandaloneCatalogWithPriceOverride root, final URI sourceURI) {
        standaloneCatalog.initialize(root.standaloneCatalog, sourceURI);
    }

    public DefaultPriceList findCurrentPriceList(final String priceListName) throws CatalogApiException {
        return standaloneCatalog.findCurrentPriceList(priceListName);
    }


}
