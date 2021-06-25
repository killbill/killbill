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

package org.killbill.billing.subscription.catalog;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.catalog.api.rules.PlanRules;
import org.killbill.billing.util.catalog.CatalogDateHelper;
import org.killbill.clock.Clock;

import static org.killbill.billing.ErrorCode.CAT_NO_SUCH_PLAN;

//
// Wrapper catalog api with low level apis only required from this module and often requiring subscription details
// Notes:
// * subscription code should only use SubscriptionCatalog (and never Catalog) although nothing wrong would happen if it was the case, but just for consistency
// * this class is really an extension of Catalog api and as such it still throws CatalogApiException
//
public class SubscriptionCatalog {

    private final VersionedCatalog catalog;
    private final List<StaticCatalog> versions;
    private final Clock clock;

    // package scope
    SubscriptionCatalog(final VersionedCatalog catalog, final Clock clock) {
        this.catalog = catalog;
        this.versions = catalog.getVersions();
        this.clock = clock;
    }

    public List<StaticCatalog> getVersions() {
        return versions;
    }

    public VersionedCatalog getCatalog() {
        return catalog;
    }

    //
    // Public apis accessed through delegation
    //

    //
    // Private (subscription-specific) apis that require state associated with this a given subscription
    //
    public Plan findPlan(final String planName, final DateTime requestedDate, final DateTime transitionTime) throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(planName), requestedDate, transitionTime);
        return entry.getPlan();

    }

    public Plan getNextPlanVersion(final Plan curPlan) {

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

        try {
            return nextCatalogVersion.findPlan(curPlan.getName());
        } catch (final CatalogApiException ignored) {
            return null;
        }
    }

    public PlanChangeResult getPlanChangeResult(final PlanPhaseSpecifier from,
                                                final PlanSpecifier to,
                                                final DateTime requestedDate)
            throws CatalogApiException {
        // Use the "to" specifier, to make sure the new plan always exists
        final StaticCatalog staticCatalog = versionForDate(requestedDate);
        return getPlanChangeResult(from, to, staticCatalog);
    }


    private PlanChangeResult getPlanChangeResult(final PlanPhaseSpecifier from, final PlanSpecifier to, final StaticCatalog staticCatalog)
            throws CatalogApiException {
        final PlanRules planRules = staticCatalog.getPlanRules();
        return planRules.getPlanChangeResult(from, to);
    }

    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase,
                                                final DateTime requestedDate,
                                                final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(planPhase, requestedDate, subscriptionChangePlanDate);
        return planCancelPolicy(planPhase, staticCatalog);
    }

    private BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase, final StaticCatalog staticCatalog)
            throws CatalogApiException {
        final PlanRules planRules = staticCatalog.getPlanRules();
        return planRules.getPlanCancelPolicy(planPhase);
    }

    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier,
                                                   final DateTime requestedDate,
                                                   final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(specifier, requestedDate, subscriptionChangePlanDate);
        return planCreateAlignment(specifier, staticCatalog);
    }

    private PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier, final StaticCatalog staticCatalog)
            throws CatalogApiException {
        final PlanRules planRules = staticCatalog.getPlanRules();
        return planRules.getPlanCreateAlignment(specifier);
    }

    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase,
                                             final DateTime requestedDate,
                                             final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final StaticCatalog staticCatalog = getStaticCatalog(planPhase, requestedDate, subscriptionChangePlanDate);
        return billingAlignment(planPhase, staticCatalog);
    }


    private BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase, final StaticCatalog staticCatalog)
            throws CatalogApiException {
        final PlanRules planRules = staticCatalog.getPlanRules();
        return planRules.getBillingAlignment(planPhase);
    }

    private StaticCatalog getStaticCatalog(final PlanSpecifier spec, final DateTime requestedDate, final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final CatalogPlanEntry entry = findCatalogPlanEntry(new PlanRequestWrapper(spec), requestedDate, subscriptionChangePlanDate);
        return entry.getStaticCatalog();
    }

    private CatalogPlanEntry findCatalogPlanEntry(final PlanRequestWrapper wrapper,
                                                  final DateTime requestedDate,
                                                  final DateTime subscriptionChangePlanDate) throws CatalogApiException {
        final List<StaticCatalog> catalogs = versionsBeforeDate(requestedDate);
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

    private List<StaticCatalog> versionsBeforeDate(final DateTime date) {

        final List<StaticCatalog> result = new ArrayList<StaticCatalog>();

        // Fetch latest version allowed -- to benefit from custom logic implemented in VersionedCatalog
        final StaticCatalog latestVersion = versionForDate(date);
        for (StaticCatalog v : versions) {
            // Add all versions prior or equal to the one returned.
            if (v.getEffectiveDate().compareTo(latestVersion.getEffectiveDate()) <= 0) {
                result.add(v);
            }
        }
        return result;
    }

    public StaticCatalog versionForDate(final DateTime date) {
        return catalog.getVersion(date.toDate());
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
            return catalog.createOrFindPlan(spec, overrides);
        }

        public PlanSpecifier getSpec() {
            return spec;
        }
    }

}
