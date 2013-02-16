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
package com.ning.billing.catalog;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Listing;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanChangeResult;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.StaticCatalog;
import com.ning.billing.catalog.api.Unit;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.config.catalog.ValidatingConfig;
import com.ning.billing.util.config.catalog.ValidationErrors;


@XmlRootElement(name = "catalog")
@XmlAccessorType(XmlAccessType.NONE)
public class VersionedCatalog extends ValidatingConfig<StandaloneCatalog> implements Catalog, StaticCatalog {

    private final Clock clock;
    private String catalogName;

    @XmlElement(name = "catalogVersion", required = true)
    private final List<StandaloneCatalog> versions = new ArrayList<StandaloneCatalog>();

    public VersionedCatalog(final Clock clock) {
        this.clock = clock;
    }

    //
    // Private methods
    //
    private StandaloneCatalog versionForDate(final DateTime date) throws CatalogApiException {
        return versions.get(indexOfVersionForDate(date.toDate()));
    }

    private List<StandaloneCatalog> versionsBeforeDate(final Date date) throws CatalogApiException {
        final List<StandaloneCatalog> result = new ArrayList<StandaloneCatalog>();
        final int index = indexOfVersionForDate(date);
        for (int i = 0; i <= index; i++) {
            result.add(versions.get(i));
        }
        return result;
    }

    private int indexOfVersionForDate(final Date date) throws CatalogApiException {
        for (int i = versions.size() - 1; i >= 0; i--) {
            final StandaloneCatalog c = versions.get(i);
            if (c.getEffectiveDate().getTime() < date.getTime()) {
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

        public PlanRequestWrapper(final String name) {
            super();
            this.name = name;
        }

        public PlanRequestWrapper(final String productName, final BillingPeriod bp,
                                  final String priceListName) {
            super();
            this.productName = productName;
            this.bp = bp;
            this.priceListName = priceListName;
        }

        public Plan findPlan(final StandaloneCatalog catalog) throws CatalogApiException {
            if (name != null) {
                return catalog.findCurrentPlan(name);
            } else {
                return catalog.findCurrentPlan(productName, bp, priceListName);
            }
        }
    }

    private Plan findPlan(final PlanRequestWrapper wrapper,
                          final DateTime requestedDate,
                          final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final List<StandaloneCatalog> catalogs = versionsBeforeDate(requestedDate.toDate());
        if (catalogs.size() == 0) {
            throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, requestedDate.toDate().toString());
        }

        for (int i = catalogs.size() - 1; i >= 0; i--) { // Working backwards to find the latest applicable plan
            final StandaloneCatalog c = catalogs.get(i);
            Plan plan = null;
            try {
                plan = wrapper.findPlan(c);
            } catch (CatalogApiException e) {
                if (e.getCode() != ErrorCode.CAT_NO_SUCH_PLAN.getCode()) {
                    throw e;
                } else {
                    break;
                }
            }

            final DateTime catalogEffectiveDate = new DateTime(c.getEffectiveDate());
            if (subscriptionStartDate.isAfter(catalogEffectiveDate)) { // Its a new subscription this plan always applies
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
    public void add(final StandaloneCatalog e) throws CatalogApiException {
        if (catalogName == null) {
            catalogName = e.getCatalogName();
        } else {
            if (!catalogName.equals(getCatalogName())) {
                throw new CatalogApiException(ErrorCode.CAT_CATALOG_NAME_MISMATCH, catalogName, e.getCatalogName());
            }
        }
        versions.add(e);
        Collections.sort(versions, new Comparator<StandaloneCatalog>() {
            @Override
            public int compare(final StandaloneCatalog c1, final StandaloneCatalog c2) {
                return c1.getEffectiveDate().compareTo(c2.getEffectiveDate());
            }
        });
    }

    public Iterator<StandaloneCatalog> iterator() {
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
                         final DateTime requestedDate)
            throws CatalogApiException {
        return versionForDate(requestedDate).findCurrentPlan(productName, term, priceListName);
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
                         final DateTime requestedDate,
                         final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return findPlan(new PlanRequestWrapper(productName, term, priceListName), requestedDate, subscriptionStartDate);
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
    public ActionPolicy planChangePolicy(final PlanPhaseSpecifier from,
                                         final PlanSpecifier to, final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).planChangePolicy(from, to);
    }

    @Override
    public ActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase, final DateTime requestedDate) throws CatalogApiException {
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
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        for (final StandaloneCatalog c : versions) {
            c.initialize(catalog, sourceURI);
        }
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        for (final StandaloneCatalog c : versions) {
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
                                final String priceList) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).findCurrentPlan(productName, term, priceList);
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
    public ActionPolicy planChangePolicy(final PlanPhaseSpecifier from,
                                         final PlanSpecifier to) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).planChangePolicy(from, to);
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to)
            throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).planChange(from, to);
    }

    @Override
    public ActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase)
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
    public List<Listing> getAvailableAddonListings(final String baseProductName) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getAvailableAddonListings(baseProductName);
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
