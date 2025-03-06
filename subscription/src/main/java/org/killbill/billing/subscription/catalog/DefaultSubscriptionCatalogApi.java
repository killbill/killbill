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

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.PriceOverrideSvcStatus;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.clock.Clock;

import com.google.inject.Inject;

public class DefaultSubscriptionCatalogApi implements SubscriptionCatalogApi {

    private final CatalogInternalApi catalogInternalApi;
    private final Clock clock;

    @Inject
    public DefaultSubscriptionCatalogApi(final CatalogInternalApi catalogInternalApi, final Clock clock) {
        this.catalogInternalApi = catalogInternalApi;
        this.clock = clock;
    }

    public static SubscriptionCatalog wrapCatalog(final VersionedCatalog catalog, final Clock clock) {
        if (catalog instanceof SubscriptionCatalog) {
            // wrapping idempotency... for safety
            return (SubscriptionCatalog) catalog;
        } else {
            return new SubscriptionCatalog(catalog, clock);
        }
    }

    @Override
    public PriceOverrideSvcStatus getPriceOverrideSvcStatus() {
        return catalogInternalApi.getPriceOverrideSvcStatus();
    }

    @Override
    public SubscriptionCatalog getFullCatalog(final InternalTenantContext context) throws CatalogApiException {
        final VersionedCatalog catalog = catalogInternalApi.getFullCatalog(true, true, context);
        return wrapCatalog(catalog, clock);
    }
}
