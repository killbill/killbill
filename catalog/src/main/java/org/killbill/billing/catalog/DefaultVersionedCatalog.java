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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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
import org.killbill.billing.catalog.api.Catalog;
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
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.util.cache.ExternalizableInput;
import org.killbill.billing.util.cache.ExternalizableOutput;
import org.killbill.billing.util.cache.MapperHolder;
import org.killbill.clock.Clock;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlRootElement(name = "catalogs")
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultVersionedCatalog extends ValidatingConfig<DefaultVersionedCatalog> implements VersionedCatalog<StandaloneCatalog>, Externalizable {

    private static final long serialVersionUID = 3181874902672322725L;

    private final Clock clock;

    @XmlElementWrapper(name = "versions", required = true)
    @XmlElement(name = "version", required = true)
    private final List<StandaloneCatalog> versions;

    @XmlElement(required = true)
    private String catalogName;

    // Required for JAXB deserialization
    public DefaultVersionedCatalog() {
        this.clock = null;
        this.versions = new ArrayList<StandaloneCatalog>();
    }

    public DefaultVersionedCatalog(final Clock clock) {
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
        // If the only version we have are after the input date, we return the first version
        // This is not strictly correct from an api point of view, but there is no real good use case
        // where the system would ask for the catalog for a date prior any catalog was uploaded and
        // yet time manipulation could end of inn that state -- see https://github.com/killbill/killbill/issues/760
        if (!versions.isEmpty()) {
            return 0;
        }
        throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, date.toString());
    }

    private CatalogPlanEntry findCatalogPlanEntry(final PlanRequestWrapper wrapper,
                                                  final DateTime requestedDate,
                                                  final DateTime subscriptionStartDate) throws CatalogApiException {
        final List<StandaloneCatalog> catalogs = versionsBeforeDate(requestedDate.toDate());
        if (catalogs.isEmpty()) {
            throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, requestedDate.toDate().toString());
        }

        CatalogPlanEntry candidateInSubsequentCatalog = null;
        for (int i = catalogs.size() - 1; i >= 0; i--) { // Working backwards to find the latest applicable plan
            final StandaloneCatalog c = catalogs.get(i);

            final Plan plan;
            try {
                plan = wrapper.findPlan(c);
            } catch (final CatalogApiException e) {
                if (e.getCode() != ErrorCode.CAT_NO_SUCH_PLAN.getCode() &&
                    e.getCode() != ErrorCode.CAT_PLAN_NOT_FOUND.getCode()) {
                    throw e;
                } else {
                    // If we can't find an entry it probably means the plan has been retired so we keep looking...
                    continue;
                }
            }

            final boolean oldestCatalog = (i == 0);
            final DateTime catalogEffectiveDate = CatalogDateHelper.toUTCDateTime(c.getEffectiveDate());
            final boolean catalogOlderThanSubscriptionStartDate = !subscriptionStartDate.isBefore(catalogEffectiveDate);
            if (oldestCatalog || // Prevent issue with time granularity -- see #760
                catalogOlderThanSubscriptionStartDate) { // It's a new subscription, this plan always applies
                return new CatalogPlanEntry(c, plan);
            } else { // It's an existing subscription
                if (plan.getEffectiveDateForExistingSubscriptions() != null) { // If it is null, any change to this catalog does not apply to existing subscriptions
                    final DateTime existingSubscriptionDate = CatalogDateHelper.toUTCDateTime(plan.getEffectiveDateForExistingSubscriptions());
                    if (requestedDate.isAfter(existingSubscriptionDate)) { // This plan is now applicable to existing subs
                        return new CatalogPlanEntry(c, plan);
                    }
                } else if (candidateInSubsequentCatalog == null) {
                    // Keep the most recent one
                    candidateInSubsequentCatalog = new CatalogPlanEntry(c, plan);
                }
            }
        }

        if (candidateInSubsequentCatalog != null) {
            return candidateInSubsequentCatalog;
        }

        final PlanSpecifier spec = wrapper.getSpec();
        throw new CatalogApiException(ErrorCode.CAT_PLAN_NOT_FOUND,
                                      spec.getPlanName() != null ? spec.getPlanName() : "undefined",
                                      spec.getProductName() != null ? spec.getProductName() : "undefined",
                                      spec.getBillingPeriod() != null ? spec.getBillingPeriod() : "undefined",
                                      spec.getPriceListName() != null ? spec.getPriceListName() : "undefined");
    }

    public Clock getClock() {
        return clock;
    }

    @Override
    public List<StandaloneCatalog> getVersions() {
        return versions;
    }

    public void add(final StandaloneCatalog e) {
        if (catalogName == null && e.getCatalogName() != null) {
            catalogName = e.getCatalogName();
        }
        versions.add(e);
        Collections.sort(versions, new Comparator<StandaloneCatalog>() {
            @Override
            public int compare(final StandaloneCatalog c1, final StandaloneCatalog c2) {
                return c1.getEffectiveDate().compareTo(c2.getEffectiveDate());
            }
        });
    }

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
    public Unit[] getUnits(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getCurrentUnits();
    }

    @Override
    public Collection<Plan> getPlans(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getCurrentPlans();
    }

    @Override
    public PriceListSet getPriceLists(final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).getPriceLists();
    }

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
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(spec, overrides), requestedDate, subscriptionStartDate);
        return entry.getPlan();
    }

    @Override
    public Product findProduct(final String name, final DateTime requestedDate) throws CatalogApiException {
        return versionForDate(requestedDate).findCurrentProduct(name);
    }

    @Override
    public PlanPhase findPhase(final String phaseName,
                               final DateTime requestedDate,
                               final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final String planName = DefaultPlanPhase.planName(phaseName);
        final Plan plan = findPlan(planName, requestedDate, subscriptionStartDate);
        return plan.findPhase(phaseName);
    }

    @Override
    public PriceList findPriceListForPlan(final String planName,
                                          final DateTime requestedDate,
                                          final DateTime subscriptionStartDate)
            throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(planName), requestedDate, subscriptionStartDate);
        return entry.getStaticCatalog().findCurrentPricelist(entry.getPlan().getPriceListName());
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase,
                                                final DateTime requestedDate,
                                                final DateTime subscriptionStartDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(planPhase, requestedDate, subscriptionStartDate);
        return staticCatalog.planCancelPolicy(planPhase);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier,
                                                   final DateTime requestedDate,
                                                   final DateTime subscriptionStartDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(specifier, requestedDate, subscriptionStartDate);
        return staticCatalog.planCreateAlignment(specifier);
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase,
                                             final DateTime requestedDate,
                                             final DateTime subscriptionStartDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(planPhase, requestedDate, subscriptionStartDate);
        return staticCatalog.billingAlignment(planPhase);
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from,
                                       final PlanSpecifier to,
                                       final DateTime requestedDate,
                                       final DateTime subscriptionStartDate)
            throws CatalogApiException {
        // Use the "to" specifier, to make sure the new plan always exists
        final StaticCatalog staticCatalog = getStaticCatalog(to, requestedDate, subscriptionStartDate);
        return staticCatalog.planChange(from, to);
    }

    // Note that the PlanSpecifier billing period must refer here to the recurring phase one when a plan name isn't specified
    private StaticCatalog getStaticCatalog(final PlanSpecifier spec, final DateTime requestedDate, final DateTime subscriptionStartDate) throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(spec), requestedDate, subscriptionStartDate);
        return entry.getStaticCatalog();
    }

    @Override
    public void initialize(final DefaultVersionedCatalog catalog, final URI sourceURI) {
        //
        // Initialization is performed first on each StandaloneCatalog (XMLLoader#initializeAndValidate)
        // and then later on the VersionedCatalog, so we only initialize and validate VersionedCatalog
        // *without** recursively through each StandaloneCatalog
        //
        super.initialize(catalog, sourceURI);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    @Override
    public ValidationErrors validate(final DefaultVersionedCatalog catalog, final ValidationErrors errors) {
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
    public List<Listing> getAvailableAddOnListings(final String baseProductName, @Nullable final String priceListName) throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getAvailableAddOnListings(baseProductName, priceListName);
    }

    @Override
    public List<Listing> getAvailableBasePlanListings() throws CatalogApiException {
        return versionForDate(clock.getUTCNow()).getAvailableBasePlanListings();
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        MapperHolder.mapper().readerForUpdating(this).readValue(new ExternalizableInput(in));
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        MapperHolder.mapper().writeValue(new ExternalizableOutput(oo), this);
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

    private class PlanRequestWrapper {

        private final PlanSpecifier spec;
        private final PlanPhasePriceOverridesWithCallContext overrides;

        public PlanRequestWrapper(final String planName) {
            this(new PlanSpecifier(planName));
        }

        public PlanRequestWrapper(final PlanSpecifier spec) {
            this(spec, null);
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
}
