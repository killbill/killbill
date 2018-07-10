/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.regex.Matcher;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.override.DefaultPriceOverride;
import org.killbill.billing.catalog.override.PriceOverride;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class StandaloneCatalogWithPriceOverride extends StandaloneCatalog implements StaticCatalog {

    private final Long tenantRecordId;

    /* Since we offer endpoints that attempt to serialize catalog objects, we need to explicitly tell Jackson to ignore those fields */
    @JsonIgnore
    private final InternalCallContextFactory internalCallContextFactory;
    @JsonIgnore
    private final PriceOverride priceOverride;

    public StandaloneCatalogWithPriceOverride(final StandaloneCatalog catalog, final PriceOverride priceOverride, final Long tenantRecordId, final InternalCallContextFactory internalCallContextFactory) {
        // Initialize from input catalog
        setCatalogName(catalog.getCatalogName());
        setEffectiveDate(catalog.getEffectiveDate());
        setProducts(catalog.getCurrentProducts());
        setPlans(catalog.getCurrentPlans());
        setPriceLists(catalog.getPriceLists());
        setPlanRules(catalog.getPlanRules());
        setSupportedCurrencies(catalog.getCurrentSupportedCurrencies());
        setUnits(catalog.getCurrentUnits());
        this.tenantRecordId = tenantRecordId;
        this.priceOverride = priceOverride;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public InternalCallContextFactory getInternalCallContextFactory() {
        return internalCallContextFactory;
    }

    @Override
    public Plan createOrFindCurrentPlan(final PlanSpecifier spec, final PlanPhasePriceOverridesWithCallContext overrides) throws CatalogApiException {
        final Plan defaultPlan = super.createOrFindCurrentPlan(spec, null);
        if (overrides == null ||
            overrides.getOverrides() == null ||
            overrides.getOverrides().isEmpty()) {
            return defaultPlan;
        }

        final InternalCallContext internalCallContext = overrides.getCallContext() != null ? internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(overrides.getCallContext()) : null;
        return priceOverride.getOrCreateOverriddenPlan(this, defaultPlan, CatalogDateHelper.toUTCDateTime(getEffectiveDate()), overrides.getOverrides(), internalCallContext);
    }

    @Override
    public DefaultPlan findCurrentPlan(final String planName) throws CatalogApiException {
        final Matcher m = DefaultPriceOverride.CUSTOM_PLAN_NAME_PATTERN.matcher(planName);
        if (m.matches()) {
            final DefaultPlan plan = maybeGetOverriddenPlan(planName);
            if (plan != null) {
                return plan;
            }
        }
        return super.findCurrentPlan(planName);
    }

    @Override
    public Product findCurrentProduct(final String productName) throws CatalogApiException {
        return super.findCurrentProduct(productName);
    }

    @Override
    public PlanPhase findCurrentPhase(final String phaseName) throws CatalogApiException {
        final String planName = DefaultPlanPhase.planName(phaseName);
        final Matcher m = DefaultPriceOverride.CUSTOM_PLAN_NAME_PATTERN.matcher(planName);
        if (m.matches()) {
            final DefaultPlan plan = maybeGetOverriddenPlan(planName);
            if (plan != null) {
                return plan.findPhase(phaseName);
            }
        }
        return super.findCurrentPhase(phaseName);
    }

    private DefaultPlan maybeGetOverriddenPlan(final String planName) throws CatalogApiException {
        final InternalTenantContext internalTenantContext = createInternalTenantContext();

        try {
            return priceOverride.getOverriddenPlan(planName, this, internalTenantContext);
        } catch (final RuntimeException e) {
            if (e.getCause() == null ||
                e.getCause().getCause() == null ||
                !(e.getCause().getCause() instanceof CatalogApiException) ||
                ((CatalogApiException) e.getCause().getCause()).getCode() != ErrorCode.CAT_NO_SUCH_PLAN.getCode()) {
                throw e;
            } else {
                // Otherwise, ambiguous name? See https://github.com/killbill/killbill/issues/842.
                return null;
            }
        }
    }

    private InternalTenantContext createInternalTenantContext() {
        return internalCallContextFactory.createInternalTenantContext(tenantRecordId, null);
    }
}
