/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.catalog.plugin;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.StandaloneCatalogWithPriceOverride;
import org.killbill.billing.catalog.VersionedCatalog;
import org.killbill.billing.catalog.override.PriceOverride;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.api.VersionedPluginCatalog;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class VersionedCatalogMapper {

    private final Clock clock;

    private final PriceOverride priceOverride;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public VersionedCatalogMapper(final Clock clock, final PriceOverride priceOverride, final InternalCallContextFactory internalCallContextFactory) {
        this.clock = clock;
        this.priceOverride = priceOverride;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public VersionedCatalog toVersionedCatalog(final VersionedPluginCatalog pluginCatalog, final InternalTenantContext internalTenantContext) {
        final VersionedCatalog result = new VersionedCatalog(clock, pluginCatalog.getCatalogName(), pluginCatalog.getRecurringBillingMode(), toStandaloneCatalogWithPriceOverrideList(pluginCatalog, internalTenantContext), internalTenantContext);
        return result;
    }

    private List<StandaloneCatalogWithPriceOverride> toStandaloneCatalogWithPriceOverrideList(final VersionedPluginCatalog pluginCatalog, final InternalTenantContext internalTenantContext) {
        return ImmutableList.copyOf(Iterables.transform(pluginCatalog.getStandalonePluginCatalogs(), new Function<StandalonePluginCatalog, StandaloneCatalogWithPriceOverride>() {
            @Override
            public StandaloneCatalogWithPriceOverride apply(final StandalonePluginCatalog input) {
                return toStandaloneCatalogWithPriceOverride(pluginCatalog, input, internalTenantContext);
            }
        }));
    }

    private StandaloneCatalogWithPriceOverride toStandaloneCatalogWithPriceOverride(final VersionedPluginCatalog pluginCatalog, final StandalonePluginCatalog input, final InternalTenantContext internalTenantContext) {
        final StandaloneCatalogMapper mapper = new StandaloneCatalogMapper(pluginCatalog.getCatalogName(), pluginCatalog.getRecurringBillingMode());
        final StandaloneCatalog standaloneCatalog = mapper.toStandaloneCatalog(input, null);
        final StandaloneCatalogWithPriceOverride result = new StandaloneCatalogWithPriceOverride(standaloneCatalog, priceOverride, internalTenantContext.getTenantRecordId(), internalCallContextFactory);
        return result;
    }
}
