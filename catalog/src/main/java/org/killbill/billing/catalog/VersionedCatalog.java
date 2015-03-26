/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
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
import org.killbill.clock.Clock;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlRootElement(name = "catalog")
@XmlAccessorType(XmlAccessType.NONE)
public class VersionedCatalog extends ValidatingConfig<StandaloneCatalogWithPriceOverride> implements Catalog, StaticCatalog {

    private final Clock clock;
    private String catalogName;
    private BillingMode recurringBillingMode;
    private final Long tenantRecordId;

    @XmlElement(name = "catalogVersion", required = true)
    private final List<StandaloneCatalogWithPriceOverride> versions = new ArrayList<StandaloneCatalogWithPriceOverride>();

    // Required for JAXB deserialization
    public VersionedCatalog() {
        this.clock = null;
        this.tenantRecordId = null;
    }

    public VersionedCatalog(final Clock clock, final Long tenantRecordId) {
        this.clock = clock;
        this.tenantRecordId = tenantRecordId;
    }


    //
    // Private methods
    //
    private StandaloneCatalogWithPriceOverride versionForDate(final DateTime date) throws CatalogApiException {
        return versions.get(indexOfVersionForDate(date.toDate()));
    }

    private List<StandaloneCatalogWithPriceOverride> versionsBeforeDate(final Date date) throws CatalogApiException {
        final List<StandaloneCatalogWithPriceOverride> result = new ArrayList<StandaloneCatalogWithPriceOverride>();
        final int index = indexOfVersionForDate(date);
        for (int i = 0; i <= index; i++) {
            result.add(versions.get(i));
        }
        return result;
    }

    private int indexOfVersionForDate(final Date date) throws CatalogApiException {
        for (int i = versions.size() - 1; i >= 0; i--) {
            final StandaloneCatalogWithPriceOverride c = versions.get(i);
            if (c.getEffectiveDate().getTime() <= date.getTime()) {
                return i;
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, date.toString());
    }

    private class PlanRequestWrapper {

        String name;
        String productName;
        BillingPeriod bp;
        String priceListName;
        PlanPhasePriceOverridesWithCallContext overrides;

        public PlanRequestWrapper(final String name) {
            this.name = name;
        }

        public PlanRequestWrapper(final String productName, final BillingPeriod bp,
                                  final String priceListName, final PlanPhasePriceOverridesWithCallContext overrides) {
            this.productName = productName;
            this.bp = bp;
            this.priceListName = priceListName;
            this.overrides = overrides;
        }

        public Plan findPlan(final StandaloneCatalogWithPriceOverride catalog) throws CatalogApiException {
            if (name != null) {
                return catalog.findCurrentPlan(name);
            } else {
                return catalog.findCurrentPlan(productName, bp, priceListName, overrides);
            }
        }
    }

    private Plan findPlan(final PlanRequestWrapper wrapper,
                          final DateTime requestedDate,
                          final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final List<StandaloneCatalogWithPriceOverride> catalogs = versionsBeforeDate(requestedDate.toDate());
        if (catalogs.size() == 0) {
            throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, requestedDate.toDate().toString());
        }

        for (int i = catalogs.size() - 1; i >= 0; i--) { // Working backwards to find the latest applicable plan
            final StandaloneCatalogWithPriceOverride c = catalogs.get(i);
            Plan plan;
            try {
                plan = wrapper.findPlan(c);
            } catch (CatalogApiException e) {
                if (e.getCode() != ErrorCode.CAT_NO_SUCH_PLAN.getCode()) {
                    throw e;
                } else {
                    // If we can't find an entry it probably means the plan has been retired so we keep looking...
                    continue;
                }
            }

            DateTime catalogEffectiveDate = new DateTime(c.getEffectiveDate());
            if (!subscriptionStartDate.isBefore(catalogEffectiveDate)) { // Its a new subscription this plan always applies
                return plan;
            } else { //Its an existing subscription
                if (plan.getEffectiveDateForExistingSubscriptons() != null) { //if it is null any change to this does not apply to existing subscriptions
                    final DateTime existingSubscriptionDate = new DateTime(plan.getEffectiveDateForExistingSubscriptons());
                    if (requestedDate.isAfter(existingSubscriptionDate)) { // this plan is now applicable to existing subs
                        return plan;
                    }
                }
            }
        }

        throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, requestedDate.toDate().toString());
    }

    //
    // Public methods not exposed in interface
    //
    public void add(final StandaloneCatalogWithPriceOverride e) throws CatalogApiException {
        if (catalogName == null) {
            catalogName = e.getCatalogName();
        } else {
            if (!catalogName.equals(e.getCatalogName())) {
                throw new CatalogApiException(ErrorCode.CAT_CATALOG_NAME_MISMATCH, catalogName, e.getCatalogName());
            }
        }
        if (recurringBillingMode == null) {
            recurringBillingMode = e.getRecurringBillingMode();
        } else {
            if (!recurringBillingMode.equals(e.getRecurringBillingMode())) {
                throw new CatalogApiException(ErrorCode.CAT_CATALOG_RECURRING_MODE_MISMATCH, recurringBillingMode, e.getRecurringBillingMode());
            }
        }
        versions.add(e);
        Collections.sort(versions, new Comparator<StandaloneCatalogWithPriceOverride>() {
            @Override
            public int compare(final StandaloneCatalogWithPriceOverride c1, final StandaloneCatalogWithPriceOverride c2) {
                return c1.getEffectiveDate().compareTo(c2.getEffectiveDate());
            }
        });
    }

    public Iterator<StandaloneCatalogWithPriceOverride> iterator() {
        return versions.iterator();
    }

    public int size() {
        return versions.size();
    }

    //
    // Simple getters
    //
    @Override
    public String getCatalogName() {
        return catalogName;
    }

    @Override
    public DefaultProduct[] getProducts(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getCurrentProducts();
    }

    @Override
    public Currency[] getSupportedCurrencies(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getCurrentSupportedCurrencies();
    }

    @Override
    public DefaultPlan[] getPlans(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getCurrentPlans();
    }

    //
    // Find a plan
    //
    @Override
    public Plan findPlan(final String name,
                         final DateTime requestedDate)
            throws CatalogApiException {
        return versionForDate(requestedDate).findCurrentPlan(name);
    }

    @Override
    public Plan findPlan(final String productName,
                         final BillingPeriod term,
                         final String priceListName,
                         final PlanPhasePriceOverridesWithCallContext overrides,
                         final DateTime requestedDate)
            throws CatalogApiException {
        return versionForDate(requestedDate).findCurrentPlan(productName, term, priceListName, overrides);
    }

    @Override
    public Plan findPlan(final String name,
                         final DateTime requestedDate,
                         final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return findPlan(new PlanRequestWrapper(name), requestedDate, subscriptionStartDate);
    }

    @Override
    public Plan findPlan(final String productName,
                         final BillingPeriod term,
                         final String priceListName,
                         final PlanPhasePriceOverridesWithCallContext overrides,
                         final DateTime requestedDate,
                         final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return findPlan(new PlanRequestWrapper(productName, term, priceListName, overrides), requestedDate, subscriptionStartDate);
    }

    //
    // Find a product
    //
    @Override
    public Product findProduct(final String name, final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).findCurrentProduct(name);
    }

    //
    // Find a phase
    //
    @Override
    public PlanPhase findPhase(final String phaseName,
                               final DateTime requestedDate,
                               final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final String planName = DefaultPlanPhase.planName(phaseName);
        final Plan plan = findPlan(planName, requestedDate, subscriptionStartDate);
        return plan.findPhase(phaseName);
    }

    //
    // Find a price list
    //
    @Override
    public PriceList findPriceList(final String name, final DateTime requestedDate)
            throws CatalogApiException {
        return versionForDate(requestedDate).findCurrentPriceList(name);
    }

    //
    // Rules
    //
    @Override
    public BillingActionPolicy planChangePolicy(final PlanPhaseSpecifier from,
                                                final PlanSpecifier to, final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).planChangePolicy(from, to);
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase, final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).planCancelPolicy(planPhase);
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier from,
                                                   final PlanSpecifier to, final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).planChangeAlignment(from, to);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier, final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).planCreateAlignment(specifier);
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase, final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).billingAlignment(planPhase);
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate)
            throws CatalogApiException {
        return versionForDate(requestedDate).planChange(from, to);
    }

    @Override
    public boolean canCreatePlan(final PlanSpecifier specifier, final DateTime requestedDate)
            throws CatalogApiException {
        return versionForDate(requestedDate).canCreatePlan(specifier);
    }

    //
    // VerifiableConfig API
    //
    @Override
    public void initialize(final StandaloneCatalogWithPriceOverride catalog, final URI sourceURI) {
        for (final StandaloneCatalogWithPriceOverride c : versions) {
            c.initialize(catalog, sourceURI);
        }
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalogWithPriceOverride catalog, final ValidationErrors errors) {
        for (final StandaloneCatalogWithPriceOverride c : versions) {
            errors.addAll(c.validate(c, errors));
        }
        //TODO MDW validation - ensure all catalog versions have a single name
        //TODO MDW validation - ensure effective dates are different (actually do we want this?)
        //TODO MDW validation - check that all products are there
        //TODO MDW validation - check that all plans are there
        //TODO MDW validation - check that all currencies are there
        //TODO MDW validation - check that all pricelists are there
        return errors;
    }

    //
    // Static catalog API
    //
    @Override
    public Date getEffectiveDate() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getEffectiveDate();
    }

    @Override
    public BillingMode getRecurringBillingMode() {
        return recurringBillingMode;
    }

    @Override
    public Currency[] getCurrentSupportedCurrencies() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getCurrentSupportedCurrencies();
    }

    @Override
    public Product[] getCurrentProducts() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getCurrentProducts();
    }

    @Override
    public Unit[] getCurrentUnits() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getCurrentUnits();
    }

    @Override
    public Plan[] getCurrentPlans() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getCurrentPlans();
    }

    @Override
    public Plan findCurrentPlan(final String productName, final BillingPeriod term,
                                final String priceList, PlanPhasePriceOverridesWithCallContext overrides) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).findCurrentPlan(productName, term, priceList, overrides);
    }

    @Override
    public Plan findCurrentPlan(final String name) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).findCurrentPlan(name);
    }

    @Override
    public Product findCurrentProduct(final String name) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).findCurrentProduct(name);
    }

    @Override
    public PlanPhase findCurrentPhase(final String name) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).findCurrentPhase(name);
    }

    @Override
    public PriceList findCurrentPricelist(final String name)
            throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).findCurrentPriceList(name);
    }

    @Override
    public BillingActionPolicy planChangePolicy(final PlanPhaseSpecifier from,
                                                final PlanSpecifier to) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).planChangePolicy(from, to);
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to)
            throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).planChange(from, to);
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase)
            throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).planCancelPolicy(planPhase);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier)
            throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).planCreateAlignment(specifier);
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase)
            throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).billingAlignment(planPhase);
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier from,
                                                   final PlanSpecifier to) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).planChangeAlignment(from, to);
    }

    @Override
    public boolean canCreatePlan(final PlanSpecifier specifier)
            throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).canCreatePlan(specifier);
    }

    @Override
    public List<Listing> getAvailableAddOnListings(final String baseProductName, @Nullable final String priceListName) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getAvailableAddOnListings(baseProductName, priceListName);
    }

    @Override
    public List<Listing> getAvailableBasePlanListings() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getAvailableBasePlanListings();
    }

    @Override
    public boolean compliesWithLimits(String phaseName, String unit, double value) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).compliesWithLimits(phaseName, unit, value);
    }
}
