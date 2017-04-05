/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.util.cache.ExternalizableInput;
import org.killbill.billing.util.cache.ExternalizableOutput;
import org.killbill.billing.util.cache.MapperHolder;
import org.killbill.clock.Clock;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlRootElement(name = "catalogs")
@XmlAccessorType(XmlAccessType.NONE)
public class VersionedCatalog extends ValidatingConfig<VersionedCatalog> implements Catalog, StaticCatalog, Externalizable {

    private static final long serialVersionUID = 3181874902672322725L;

    private final Clock clock;

    @XmlElementWrapper(name = "versions", required = true)
    @XmlElement(name = "version", required = true)
    private final List<StandaloneCatalog> versions;

    @XmlElement(required = true)
    private String catalogName;

    @XmlElement(required = true)
    private BillingMode recurringBillingMode;

    // Required for JAXB deserialization
    public VersionedCatalog() {
        this.clock = null;
        this.versions = new ArrayList<StandaloneCatalog>();
    }

    public VersionedCatalog(final Clock clock) {
        this.clock = clock;
        this.versions = new ArrayList<StandaloneCatalog>();
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
            if (c.getEffectiveDate().getTime() <= date.getTime()) {
                return i;
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, date.toString());
    }

    private class PlanRequestWrapper {

        private final PlanSpecifier spec;
        private final PlanPhasePriceOverridesWithCallContext overrides;

        public PlanRequestWrapper(final String planName) {
            this.spec = new PlanSpecifier(planName);
            this.overrides = null;
        }

        public PlanRequestWrapper(final PlanSpecifier spec,
                                  final PlanPhasePriceOverridesWithCallContext overrides) {
            this.spec = spec;
            this.overrides = overrides;
        }


        public Plan findPlan(final StandaloneCatalog catalog) throws CatalogApiException {
            return catalog.createOrFindCurrentPlan(spec, overrides);
        }

        public PlanSpecifier getSpec() {
            return spec;
        }
    }

    private CatalogPlanEntry findCatalogPlanEntry(final PlanRequestWrapper wrapper,
                                                  final DateTime requestedDate,
                                                  final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final List<StandaloneCatalog> catalogs = versionsBeforeDate(requestedDate.toDate());
        if (catalogs.isEmpty()) {
            throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, requestedDate.toDate().toString());
        }

        for (int i = catalogs.size() - 1; i >= 0; i--) { // Working backwards to find the latest applicable plan
            final StandaloneCatalog c = catalogs.get(i);
            final Plan plan;
            try {
                plan = wrapper.findPlan(c);
            } catch (final CatalogApiException e) {
                if (e.getCode() != ErrorCode.CAT_NO_SUCH_PLAN.getCode()) {
                    throw e;
                } else {
                    // If we can't find an entry it probably means the plan has been retired so we keep looking...
                    continue;
                }
            }

            final DateTime catalogEffectiveDate = CatalogDateHelper.toUTCDateTime(c.getEffectiveDate());
            if (!subscriptionStartDate.isBefore(catalogEffectiveDate)) { // Its a new subscription this plan always applies
                return new CatalogPlanEntry(c, plan);
            } else { //Its an existing subscription
                if (plan.getEffectiveDateForExistingSubscriptions() != null) { //if it is null any change to this does not apply to existing subscriptions
                    final DateTime existingSubscriptionDate = CatalogDateHelper.toUTCDateTime(plan.getEffectiveDateForExistingSubscriptions());
                    if (requestedDate.isAfter(existingSubscriptionDate)) { // this plan is now applicable to existing subs
                        return new CatalogPlanEntry(c, plan);
                    }
                }
            }
        }

        final PlanSpecifier spec = wrapper.getSpec();
        throw new CatalogApiException(ErrorCode.CAT_PLAN_NOT_FOUND,
                                      spec.getPlanName() != null ? spec.getPlanName() : "undefined",
                                      spec.getProductName() != null ? spec.getProductName() : "undefined",
                                      spec.getBillingPeriod() != null ? spec.getBillingPeriod() : "undefined",
                                      spec.getPriceListName() != null ? spec.getPriceListName() : "undefined");
    }

    private static class CatalogPlanEntry {

        private final StaticCatalog staticCatalog;
        private final Plan plan;

        public CatalogPlanEntry(final StaticCatalog staticCatalog, final Plan plan) {
            this.staticCatalog = staticCatalog;
            this.plan = plan;
        }

        public StaticCatalog getStaticCatalog() {
            return staticCatalog;
        }

        public Plan getPlan() {
            return plan;
        }
    }


    public Clock getClock() {
        return clock;
    }

    public List<StandaloneCatalog> getVersions() {
        return versions;
    }

    //
    // Public methods not exposed in interface
    //
    public void addAll(final List<StandaloneCatalog> inputVersions) throws CatalogApiException {
        for (final StandaloneCatalog cur : inputVersions) {
            add(cur);
        }
    }

    public void add(final StandaloneCatalog e) throws CatalogApiException {
        if (catalogName == null && e.getCatalogName() != null) {
            catalogName = e.getCatalogName();
        }
        if (recurringBillingMode == null) {
            recurringBillingMode = e.getRecurringBillingMode();
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
    public Collection<Product> getProducts(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getCurrentProducts();
    }

    @Override
    public Currency[] getSupportedCurrencies(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getCurrentSupportedCurrencies();
    }

    @Override
    public Collection<Plan> getPlans(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getCurrentPlans();
    }

    @Override
    public PriceListSet getPriceLists(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getPriceLists();
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
    public Plan createOrFindPlan(final PlanSpecifier spec,
                                 final PlanPhasePriceOverridesWithCallContext overrides,
                                 final DateTime requestedDate)
            throws CatalogApiException {
        return versionForDate(requestedDate).createOrFindCurrentPlan(spec, overrides);
    }

    @Override
    public Plan findPlan(final String name,
                         final DateTime requestedDate,
                         final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(name), requestedDate, subscriptionStartDate);
        return entry.getPlan();
    }

    @Override
    public Plan createOrFindPlan(final PlanSpecifier spec,
                                 final PlanPhasePriceOverridesWithCallContext overrides,
                                 final DateTime requestedDate,
                                 final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final CatalogPlanEntry entry =  findCatalogPlanEntry(new PlanRequestWrapper(spec, overrides), requestedDate, subscriptionStartDate);
        return entry.getPlan();
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
    // Find a price list associated to a given subscription
    //
    @Override
    public PriceList findPriceListForPlan(final String planName,
                                          final DateTime requestedDate,
                                          final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(planName), requestedDate, subscriptionStartDate);
        return entry.getStaticCatalog().findCurrentPricelist(entry.getPlan().getPriceListName());
    }


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
    public void initialize(final VersionedCatalog catalog, final URI sourceURI) {
        //
        // Initialization is performed first on each StandaloneCatalog (XMLLoader#initializeAndValidate)
        // and then later on the VersionedCatalog, so we only initialize and validate VersionedCatalog
        // *without** recursively through each StandaloneCatalog
        //
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    @Override
    public ValidationErrors validate(final VersionedCatalog catalog, final ValidationErrors errors) {

        final Set<Date> effectiveDates = new TreeSet<Date>();

        for (final StandaloneCatalog c : versions) {
            if (effectiveDates.contains(c.getEffectiveDate())) {
                errors.add(new ValidationError(String.format("Catalog effective date '%s' already exists for a previous version", c.getEffectiveDate()),
                        c.getCatalogURI(), VersionedCatalog.class, ""));
            } else {
                effectiveDates.add(c.getEffectiveDate());
            }
            if (!c.getCatalogName().equals(catalogName)) {
                errors.add(new ValidationError(String.format("Catalog name '%s' is not consistent across versions ", c.getCatalogName()),
                                               c.getCatalogURI(), VersionedCatalog.class, ""));
            }
            if (!c.getRecurringBillingMode().equals(recurringBillingMode)) {
                errors.add(new ValidationError(String.format("Catalog recurringBillingMode '%s' is not consistent across versions ", c.getCatalogName()),
                                               c.getCatalogURI(), VersionedCatalog.class, ""));
            }
            errors.addAll(c.validate(c, errors));
        }
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
    public Date getStandaloneCatalogEffectiveDate(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getEffectiveDate();
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
    public Collection<Product> getCurrentProducts() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getCurrentProducts();
    }

    @Override
    public Unit[] getCurrentUnits() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getCurrentUnits();
    }

    @Override
    public Collection<Plan> getCurrentPlans() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getCurrentPlans();
    }

    @Override
    public Plan createOrFindCurrentPlan(final PlanSpecifier spec, final PlanPhasePriceOverridesWithCallContext overrides) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).createOrFindCurrentPlan(spec, overrides);
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
    public boolean compliesWithLimits(final String phaseName, final String unit, final double value) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).compliesWithLimits(phaseName, unit, value);
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
