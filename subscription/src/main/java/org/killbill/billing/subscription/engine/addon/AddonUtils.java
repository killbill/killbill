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

package org.killbill.billing.subscription.engine.addon;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;

import com.google.inject.Inject;

public class AddonUtils {

    private final CatalogService catalogService;

    @Inject
    public AddonUtils(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public void checkAddonCreationRights(final DefaultSubscriptionBase baseSubscription, final Plan targetAddOnPlan)
            throws SubscriptionBaseApiException, CatalogApiException {

        if (baseSubscription.getState() != EntitlementState.ACTIVE) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_AO_BP_NON_ACTIVE, targetAddOnPlan.getName());
        }

        final Product baseProduct = baseSubscription.getCurrentPlan().getProduct();
        if (isAddonIncluded(baseProduct, targetAddOnPlan)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_AO_ALREADY_INCLUDED,
                                                   targetAddOnPlan.getName(), baseSubscription.getCurrentPlan().getProduct().getName());
        }

        if (!isAddonAvailable(baseProduct, targetAddOnPlan)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_AO_NOT_AVAILABLE,
                                                   targetAddOnPlan.getName(), baseSubscription.getCurrentPlan().getProduct().getName());
        }
    }

    public boolean isAddonAvailableFromProdName(final String baseProductName, final DateTime requestedDate, final Plan targetAddOnPlan, final InternalTenantContext context) {
        try {
            final Product product = catalogService.getFullCatalog(context).findProduct(baseProductName, requestedDate);
            return isAddonAvailable(product, targetAddOnPlan);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseError(e);
        }
    }

    public boolean isAddonAvailableFromPlanName(final String basePlanName, final DateTime requestedDate, final Plan targetAddOnPlan, final InternalTenantContext context) {
        try {
            final Plan plan = catalogService.getFullCatalog(context).findPlan(basePlanName, requestedDate);
            final Product product = plan.getProduct();
            return isAddonAvailable(product, targetAddOnPlan);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseError(e);
        }
    }

    public boolean isAddonAvailable(final Product baseProduct, final Plan targetAddOnPlan) {
        final Product targetAddonProduct = targetAddOnPlan.getProduct();
        final Product[] availableAddOns = baseProduct.getAvailable();

        for (final Product curAv : availableAddOns) {
            if (curAv.getName().equals(targetAddonProduct.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAddonIncludedFromProdName(final String baseProductName, final DateTime requestedDate, final Plan targetAddOnPlan, final InternalTenantContext context) {
        try {
            final Product product = catalogService.getFullCatalog(context).findProduct(baseProductName, requestedDate);
            return isAddonIncluded(product, targetAddOnPlan);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseError(e);
        }

    }

    public boolean isAddonIncludedFromPlanName(final String basePlanName, final DateTime requestedDate, final Plan targetAddOnPlan, final InternalTenantContext context) {
        try {
            final Plan plan = catalogService.getFullCatalog(context).findPlan(basePlanName, requestedDate);
            final Product product = plan.getProduct();
            return isAddonIncluded(product, targetAddOnPlan);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseError(e);
        }
    }

    public boolean isAddonIncluded(final Product baseProduct, final Plan targetAddOnPlan) {
        final Product targetAddonProduct = targetAddOnPlan.getProduct();
        final Product[] includedAddOns = baseProduct.getIncluded();
        for (final Product curAv : includedAddOns) {
            if (curAv.getName().equals(targetAddonProduct.getName())) {
                return true;
            }
        }
        return false;
    }
}
