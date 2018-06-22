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

package org.killbill.billing.catalog.api;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultCatalogInternalApi implements CatalogInternalApi {

    private final Logger logger = LoggerFactory.getLogger(DefaultCatalogInternalApi.class);

    private final CatalogService catalogService;

    @Inject
    public DefaultCatalogInternalApi(final CatalogService catalogService,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.catalogService = catalogService;
    }

    @Override
    public Catalog getFullCatalog(final boolean useDefaultCatalog, final boolean filterTemplateCatalog, final InternalTenantContext context) throws CatalogApiException {
        return catalogService.getFullCatalogForInternalUse(useDefaultCatalog, filterTemplateCatalog, context);
    }

    @Override
    public StaticCatalog getCurrentCatalog(final boolean useDefaultCatalog, final boolean filterTemplateCatalog, final InternalTenantContext context) throws CatalogApiException {
        return catalogService.getCurrentCatalogForInternalUse(useDefaultCatalog, filterTemplateCatalog, context);
    }
}
