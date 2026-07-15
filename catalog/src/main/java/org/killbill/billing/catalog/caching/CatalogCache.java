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

package org.killbill.billing.catalog.caching;

import java.util.Collections;
import java.util.Set;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.VersionedCatalog;

public interface CatalogCache {

    public void loadDefaultCatalog(final String url) throws CatalogApiException;

    public VersionedCatalog getCatalog(final boolean useDefaultCatalog, final boolean filterTemplateCatalog, final boolean internalUse, InternalTenantContext tenantContext) throws CatalogApiException;

    /**
     * Returns a catalog scoped to the requested plan names. Implementations backed by a catalog plugin
     * will forward {@code planNames} to the plugin so it can return a minimal catalog. When no plugin
     * is registered, or when {@code planNames} is empty, this falls back to {@link #getCatalog}.
     */
    default VersionedCatalog getCatalogForPlans(final Set<String> planNames, final boolean useDefaultCatalog, final boolean filterTemplateCatalog, final boolean internalUse, final InternalTenantContext tenantContext) throws CatalogApiException {
        return getCatalog(useDefaultCatalog, filterTemplateCatalog, internalUse, tenantContext);
    }

    public void clearCatalog(InternalTenantContext tenantContext);
}
