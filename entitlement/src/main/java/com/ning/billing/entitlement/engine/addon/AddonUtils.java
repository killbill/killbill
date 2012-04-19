/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.entitlement.engine.addon;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.entitlement.exceptions.EntitlementError;

public class AddonUtils {

    private static final Logger logger = LoggerFactory.getLogger(AddonUtils.class);

    private final CatalogService catalogService;

    @Inject
    public AddonUtils(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public void checkAddonCreationRights(SubscriptionData baseSubscription, Plan targetAddOnPlan)
    throws EntitlementUserApiException, CatalogApiException {

        if (baseSubscription.getState() != SubscriptionState.ACTIVE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_AO_BP_NON_ACTIVE, targetAddOnPlan.getName());
        }

        Product baseProduct = baseSubscription.getCurrentPlan().getProduct();
        if (isAddonIncluded(baseProduct, targetAddOnPlan)) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_AO_ALREADY_INCLUDED,
                    targetAddOnPlan.getName(), baseSubscription.getCurrentPlan().getProduct().getName());
        }

        if (!isAddonAvailable(baseProduct, targetAddOnPlan)) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_AO_NOT_AVAILABLE,
                    targetAddOnPlan.getName(), baseSubscription.getCurrentPlan().getProduct().getName());
        }
    }

    public boolean isAddonAvailable(final String basePlanName, final DateTime requestedDate, final Plan targetAddOnPlan) {
        try {
            Plan plan = catalogService.getFullCatalog().findPlan(basePlanName, requestedDate);
            Product product = plan.getProduct();
            return isAddonAvailable(product, targetAddOnPlan);
        } catch (CatalogApiException e) {
            throw new EntitlementError(e);
        }
    }

    public boolean isAddonAvailable(final Product baseProduct, final Plan targetAddOnPlan) {
        Product targetAddonProduct = targetAddOnPlan.getProduct();
        Product[] availableAddOns = baseProduct.getAvailable();

        for (Product curAv : availableAddOns) {
            if (curAv.getName().equals(targetAddonProduct.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAddonIncluded(final String basePlanName,  final DateTime requestedDate, final Plan targetAddOnPlan) {
        try {
            Plan plan = catalogService.getFullCatalog().findPlan(basePlanName, requestedDate);
            Product product = plan.getProduct();
            return isAddonIncluded(product, targetAddOnPlan);
        } catch (CatalogApiException e) {
            throw new EntitlementError(e);
        }
    }

    public boolean isAddonIncluded(final Product baseProduct, final Plan targetAddOnPlan) {
        Product targetAddonProduct = targetAddOnPlan.getProduct();
        Product[] includedAddOns = baseProduct.getIncluded();
        for (Product curAv : includedAddOns) {
            if (curAv.getName().equals(targetAddonProduct.getName())) {
                return true;
            }
        }
        return false;
    }
}
