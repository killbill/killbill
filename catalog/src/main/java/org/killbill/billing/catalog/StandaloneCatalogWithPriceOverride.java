/*
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

import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.override.PriceOverrideSvc;
import org.killbill.billing.catalog.rules.DefaultPlanRules;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.catalog.CatalogDateHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class StandaloneCatalogWithPriceOverride extends StandaloneCatalog implements StaticCatalog, Externalizable {

    private Long tenantRecordId;

    /* Since we offer endpoints that attempt to serialize catalog objects, we need to explicitly tell Jackson to ignore those fields */
    @JsonIgnore
    private InternalCallContextFactory internalCallContextFactory;
    @JsonIgnore
    private PriceOverrideSvc priceOverride;

    // Required for deserialization
    public StandaloneCatalogWithPriceOverride() {
    }

    public StandaloneCatalogWithPriceOverride(final StaticCatalog catalog, final PriceOverrideSvc priceOverride, final Long tenantRecordId, final InternalCallContextFactory internalCallContextFactory) {
        // Initialize from input catalog
        setCatalogName(catalog.getCatalogName());
        setEffectiveDate(catalog.getEffectiveDate());
        setProducts(catalog.getProducts());
        setPlans(catalog.getPlans());
        setPriceLists(((StandaloneCatalog) catalog).getPriceLists());
        setPlanRules((DefaultPlanRules) catalog.getPlanRules());
        setSupportedCurrencies(catalog.getSupportedCurrencies());
        setUnits((DefaultUnit[]) catalog.getUnits());
        this.tenantRecordId = tenantRecordId;
        this.priceOverride = priceOverride;
        this.internalCallContextFactory = internalCallContextFactory;
        initialize(this);
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public InternalCallContextFactory getInternalCallContextFactory() {
        return internalCallContextFactory;
    }

    @Override
    public Plan createOrFindPlan(final PlanSpecifier spec, final PlanPhasePriceOverridesWithCallContext overrides) throws CatalogApiException {
        final Plan defaultPlan = super.createOrFindPlan(spec, null);
        if (overrides == null ||
            overrides.getOverrides() == null ||
            overrides.getOverrides().isEmpty()) {
            return defaultPlan;
        }

        final InternalCallContext internalCallContext = overrides.getCallContext() != null ? internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(overrides.getCallContext()) : null;
        return priceOverride.getOrCreateOverriddenPlan(this, defaultPlan, CatalogDateHelper.toUTCDateTime(getEffectiveDate()), overrides.getOverrides(), internalCallContext);
    }


    @Override
    public DefaultPlan findPlan(final String planName) throws CatalogApiException {
        if (priceOverride.isOverriddenPlan(planName)) {
            final DefaultPlan plan = maybeGetOverriddenPlan(planName);
            if (plan != null) {
                return plan;
            }
        }
        return super.findPlan(planName);
    }

    @Override
    public Product findProduct(final String productName) throws CatalogApiException {
        return super.findProduct(productName);
    }

    @Override
    public PlanPhase findPhase(final String phaseName) throws CatalogApiException {
        final String planName = DefaultPlanPhase.planName(phaseName);
        if (priceOverride.isOverriddenPlan(planName)) {
            final DefaultPlan plan = maybeGetOverriddenPlan(planName);
            if (plan != null) {
                return plan.findPhase(phaseName);
            }
        }
        return super.findPhase(phaseName);
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

    public void initialize(final StandaloneCatalog catalog, final PriceOverrideSvc priceOverride, final InternalCallContextFactory internalCallContextFactory) {
        super.initialize(catalog);
        this.priceOverride = priceOverride;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeLong(tenantRecordId);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        this.tenantRecordId = in.readLong();
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
