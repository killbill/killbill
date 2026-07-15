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

import java.util.Collections;
import java.util.Set;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PriceOverrideSvcStatus;

public interface SubscriptionCatalogApi {

    public SubscriptionCatalog getFullCatalog(InternalTenantContext context) throws CatalogApiException;

    /**
     * Like {@link #getFullCatalog} but forwards {@code planNames} as a hint to the catalog plugin
     * so it can return a minimal catalog. Falls back to {@link #getFullCatalog} when
     * {@code planNames} is empty.
     */
    default SubscriptionCatalog getCatalogForPlans(Set<String> planNames, InternalTenantContext context) throws CatalogApiException {
        return getFullCatalog(context);
    }

    public PriceOverrideSvcStatus getPriceOverrideSvcStatus();

}
