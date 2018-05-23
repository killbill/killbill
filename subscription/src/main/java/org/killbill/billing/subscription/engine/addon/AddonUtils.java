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

package org.killbill.billing.subscription.engine.addon;

import java.util.Collection;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;

public class AddonUtils {

    public void checkAddonCreationRights(final SubscriptionBase baseSubscription, final Plan targetAddOnPlan, final DateTime requestedDate, final InternalTenantContext context)
            throws SubscriptionBaseApiException {
        if (baseSubscription.getState() == EntitlementState.CANCELLED ||
            (baseSubscription.getState() == EntitlementState.PENDING && context.toLocalDate(baseSubscription.getStartDate()).compareTo(context.toLocalDate(requestedDate)) < 0)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_AO_BP_NON_ACTIVE, targetAddOnPlan.getName());
        }

        final Plan currentOrPendingPlan = baseSubscription.getCurrentOrPendingPlan();
        final Product baseProduct = currentOrPendingPlan.getProduct();
        if (isAddonIncluded(baseProduct, targetAddOnPlan)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_AO_ALREADY_INCLUDED,
                                                   targetAddOnPlan.getName(), currentOrPendingPlan.getProduct().getName());
        }

        if (!isAddonAvailable(baseProduct, targetAddOnPlan)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_AO_NOT_AVAILABLE,
                                                   targetAddOnPlan.getName(), currentOrPendingPlan.getProduct().getName());
        }
    }

    public boolean isAddonAvailable(final Product baseProduct, final Plan targetAddOnPlan) {
        final Product targetAddonProduct = targetAddOnPlan.getProduct();
        final Collection<Product> availableAddOns = baseProduct.getAvailable();

        for (final Product curAv : availableAddOns) {
            if (curAv.getName().equals(targetAddonProduct.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAddonIncluded(final Product baseProduct, final Plan targetAddOnPlan) {
        final Product targetAddonProduct = targetAddOnPlan.getProduct();
        final Collection<Product> includedAddOns = baseProduct.getIncluded();
        for (final Product curAv : includedAddOns) {
            if (curAv.getName().equals(targetAddonProduct.getName())) {
                return true;
            }
        }
        return false;
    }

    public int countExistingAddOnsWithSamePlanName(final Iterable<DefaultSubscriptionBase> subscriptionsForBundle, final String planName) {
        int countExistingAddOns = 0;
        for (final SubscriptionBase subscription : subscriptionsForBundle) {
            if (subscription.getCurrentPlan().getName().equalsIgnoreCase(planName)
                && subscription.getLastActiveProduct().getCategory() != null
                && ProductCategory.ADD_ON.equals(subscription.getLastActiveProduct().getCategory())) {
                countExistingAddOns++;
            }
        }
        return countExistingAddOns;
    }
}
