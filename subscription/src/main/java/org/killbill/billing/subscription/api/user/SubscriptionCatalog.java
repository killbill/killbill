/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.CatalogDateHelper;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.rules.DefaultPlanRules;
import org.killbill.clock.Clock;

import static org.killbill.billing.ErrorCode.CAT_NO_SUCH_PLAN;

// TODO_CATALOG Unclear if this is really the right approach, for now this at least provides the separation (catalog v.s subscription apis) we want
public class SubscriptionCatalog implements Catalog {

    private final Catalog delegate;
    private final Clock clock;

    public SubscriptionCatalog(final Catalog delegate, final Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    //
    // Public apis accessed through delegation
    //
    @Override
    public String getCatalogName() {
        return delegate.getCatalogName();
    }

    @Override
    public Date getStandaloneCatalogEffectiveDate(final DateTime requestedDate) throws CatalogApiException {
        return delegate.getStandaloneCatalogEffectiveDate(requestedDate);
    }

    @Override
    public Currency[] getSupportedCurrencies(final DateTime requestedDate) throws CatalogApiException {
        return delegate.getSupportedCurrencies(requestedDate);
    }

    @Override
    public Unit[] getUnits(final DateTime requestedDate) throws CatalogApiException {
        return delegate.getUnits(requestedDate);
    }

    @Override
    public Collection<Product> getProducts(final DateTime requestedDate) throws CatalogApiException {
        return delegate.getProducts(requestedDate);
    }

    @Override
    public Collection<Plan> getPlans(final DateTime requestedDate) throws CatalogApiException {
        return delegate.getPlans(requestedDate);
    }

    @Override
    public PriceListSet getPriceLists(final DateTime requestedDate) throws CatalogApiException {
        return delegate.getPriceLists(requestedDate);
    }

    @Override
    public Plan findPlan(final String planName, final DateTime requestedDate) throws CatalogApiException {
        return delegate.findPlan(planName, requestedDate);
    }

    @Override
    public Plan createOrFindPlan(final PlanSpecifier planSpecifier, final PlanPhasePriceOverridesWithCallContext planPhasePriceOverridesWithCallContext, final DateTime requestedDate) throws CatalogApiException {
        return delegate.createOrFindPlan(planSpecifier, planPhasePriceOverridesWithCallContext, requestedDate);
    }

    @Override
    public Product findProduct(final String planName, final DateTime requestedDate) throws CatalogApiException {
        return delegate.findProduct(planName, requestedDate);
    }

    @Override
    public List<StaticCatalog> getVersions() {
        return delegate.getVersions();
    }

    //
    // Private (subscription-specific) apis that require state associated with this a given subscription
    //
    public Plan findPlan(final String planName, final DateTime requestedDate, final DateTime transitionTime) throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(planName), requestedDate, transitionTime);
        return entry.getPlan();

    }

    public PriceList findPriceListForPlan(final String planName,
                                          final DateTime requestedDate,
                                          final DateTime subscriptionChangePlanDate)
            throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(planName), requestedDate, subscriptionChangePlanDate);
        return entry.getStaticCatalog().findCurrentPricelist(entry.getPlan().getPriceListName());
    }

    public Plan getNextPlanVersion(final Plan curPlan) {

        final List<StaticCatalog> versions = delegate.getVersions();
        boolean foundCurVersion = false;
        StaticCatalog nextCatalogVersion = null;
        for (int i = 0; i < versions.size(); i++) {
            final StaticCatalog curCatalogversion = versions.get(i);
            if (foundCurVersion) {
                nextCatalogVersion = curCatalogversion;
                break;
            }
            if (curCatalogversion.getEffectiveDate().compareTo(curPlan.getCatalog().getEffectiveDate()) == 0) {
                foundCurVersion = true;
            }
        }
        if (nextCatalogVersion == null) {
            return null;
        }
        // TODO_CATALOG Need to remove dependency to catalog module !
        return ((StandaloneCatalog) nextCatalogVersion).getPlans().findByName(curPlan.getName());
    }

    // TODO_CATALOG see #1190
    public PlanChangeResult planChange(final PlanPhaseSpecifier from,
                                       final PlanSpecifier to,
                                       final DateTime requestedDate)
            throws CatalogApiException {
        // Use the "to" specifier, to make sure the new plan always exists
        final StaticCatalog staticCatalog = versionForDate(requestedDate);
        return planChange(from, to, staticCatalog);
    }

    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to)
            throws CatalogApiException {
        final StaticCatalog standaloneCatalog = versionForDate(clock.getUTCNow());
        return planChange(from, to, standaloneCatalog);
    }

    private PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to, final StaticCatalog standaloneCatalog)
            throws CatalogApiException {
        final DefaultPlanRules planRules = (DefaultPlanRules) standaloneCatalog.getPlanRules();
        return planRules.planChange(from, to, standaloneCatalog);
    }

    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase,
                                                final DateTime requestedDate,
                                                final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(planPhase, requestedDate, subscriptionChangePlanDate);
        return planCancelPolicy(planPhase, staticCatalog);
    }

    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase)
            throws CatalogApiException {
        final StaticCatalog standaloneCatalog = versionForDate(clock.getUTCNow());
        return planCancelPolicy(planPhase, standaloneCatalog);
    }

    private BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase, final StaticCatalog standaloneCatalog)
            throws CatalogApiException {
        final DefaultPlanRules planRules = (DefaultPlanRules) standaloneCatalog.getPlanRules();
        return planRules.getPlanCancelPolicy(planPhase, standaloneCatalog);
    }

    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier,
                                                   final DateTime requestedDate,
                                                   final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(specifier, requestedDate, subscriptionChangePlanDate);
        return planCreateAlignment(specifier, staticCatalog);
    }

    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier)
            throws CatalogApiException {
        final StaticCatalog standaloneCatalog = versionForDate(clock.getUTCNow());
        return planCreateAlignment(specifier, standaloneCatalog);
    }

    private PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier, final StaticCatalog standaloneCatalog)
            throws CatalogApiException {
        final DefaultPlanRules planRules = (DefaultPlanRules) standaloneCatalog.getPlanRules();
        return planRules.getPlanCreateAlignment(specifier, standaloneCatalog);
    }

    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase,
                                             final DateTime requestedDate,
                                             final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(planPhase, requestedDate, subscriptionChangePlanDate);
        return billingAlignment(planPhase, staticCatalog);
    }

    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase)
            throws CatalogApiException {
        final StaticCatalog standaloneCatalog = versionForDate(clock.getUTCNow());
        return billingAlignment(planPhase, standaloneCatalog);
    }

    private BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase, final StaticCatalog standaloneCatalog)
            throws CatalogApiException {
        final DefaultPlanRules planRules = (DefaultPlanRules) standaloneCatalog.getPlanRules();
        return planRules.getBillingAlignment(planPhase, standaloneCatalog);
    }

    // TODO_CATALOG: Private methods currently duplicated with VersionnedCatalog
    //
    private StaticCatalog getStaticCatalog(final PlanSpecifier spec, final DateTime requestedDate, final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(spec), requestedDate, subscriptionChangePlanDate);
        return entry.getStaticCatalog();
    }

    private CatalogPlanEntry findCatalogPlanEntry(final PlanRequestWrapper wrapper,
                                                  final DateTime requestedDate,
                                                  final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final List<StaticCatalog> catalogs = versionsBeforeDate(requestedDate.toDate());
        if (catalogs.isEmpty()) {
            throw new CatalogApiException(ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE, requestedDate.toDate().toString());
        }

        CatalogPlanEntry candidateInSubsequentCatalog = null;
        for (int i = catalogs.size() - 1; i >= 0; i--) { // Working backwards to find the latest applicable plan
            final StaticCatalog c = catalogs.get(i);

            final Plan plan;
            try {
                plan = wrapper.findPlan(c);
            } catch (final CatalogApiException e) {
                if (e.getCode() != CAT_NO_SUCH_PLAN.getCode() &&
                    e.getCode() != ErrorCode.CAT_PLAN_NOT_FOUND.getCode()) {
                    throw e;
                } else {
                    // If we can't find an entry it probably means the plan has been retired so we keep looking...
                    continue;
                }
            }

            final boolean oldestCatalog = (i == 0);
            final DateTime catalogEffectiveDate = CatalogDateHelper.toUTCDateTime(c.getEffectiveDate());
            final boolean catalogOlderThanSubscriptionChangePlanDate = !subscriptionChangePlanDate.isBefore(catalogEffectiveDate);
            if (oldestCatalog || // Prevent issue with time granularity -- see #760
                catalogOlderThanSubscriptionChangePlanDate) { // It's a new subscription, this plan always applies
                return new CatalogPlanEntry(c, plan);
            } else { // It's an existing subscription
                if (plan.getEffectiveDateForExistingSubscriptions() != null) { // If it is null, any change to this catalog does not apply to existing subscriptions
                    final DateTime existingSubscriptionDate = CatalogDateHelper.toUTCDateTime(plan.getEffectiveDateForExistingSubscriptions());
                    if (requestedDate.compareTo(existingSubscriptionDate) >= 0) { // This plan is now applicable to existing subs
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

    private List<StaticCatalog> versionsBeforeDate(final Date date) throws CatalogApiException {

        final List<StaticCatalog> versions = delegate.getVersions();
        final List<StaticCatalog> result = new ArrayList<StaticCatalog>();
        final int index = indexOfVersionForDate(date);
        for (int i = 0; i <= index; i++) {
            result.add(versions.get(i));
        }
        return result;
    }

    private StaticCatalog versionForDate(final DateTime date) throws CatalogApiException {
        final List<StaticCatalog> versions = delegate.getVersions();
        return versions.get(indexOfVersionForDate(date.toDate()));
    }

    private int indexOfVersionForDate(final Date date) throws CatalogApiException {

        final List<StaticCatalog> versions = delegate.getVersions();
        for (int i = versions.size() - 1; i >= 0; i--) {
            final StaticCatalog c = versions.get(i);
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

        public Plan findPlan(final StaticCatalog catalog) throws CatalogApiException {
            return catalog.createOrFindCurrentPlan(spec, overrides);
        }

        public PlanSpecifier getSpec() {
            return spec;
        }
    }

}
