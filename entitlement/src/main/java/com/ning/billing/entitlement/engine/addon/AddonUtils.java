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

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;

public class AddonUtils {


    private final CatalogService catalogService;

    @Inject
    public AddonUtils(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public boolean isAddonAvailable(SubscriptionData baseSubscription, Plan targetAddOnPlan) {

        if (baseSubscription.getState() == SubscriptionState.CANCELLED) {
            return false;
        }

        Product targetAddonProduct = targetAddOnPlan.getProduct();
        Product baseProduct = baseSubscription.getCurrentPlan().getProduct();
        Product[] availableAddOns = baseProduct.getAvailable();

        for (Product curAv : availableAddOns) {
            if (curAv.getName().equals(targetAddonProduct.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAddonIncluded(SubscriptionData baseSubscription, Plan targetAddOnPlan) {

        if (baseSubscription.getState() == SubscriptionState.CANCELLED) {
            return false;
        }

        Product targetAddonProduct = targetAddOnPlan.getProduct();
        Product baseProduct = baseSubscription.getCurrentPlan().getProduct();

        Product[] includedAddOns = baseProduct.getIncluded();
        for (Product curAv : includedAddOns) {
            if (curAv.getName().equals(targetAddonProduct.getName())) {
                return true;
            }
        }
        return false;
    }
}
