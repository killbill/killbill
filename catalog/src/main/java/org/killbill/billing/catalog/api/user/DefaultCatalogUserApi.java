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

package org.killbill.billing.catalog.api.user;

import javax.inject.Inject;

import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultCatalogUserApi implements CatalogUserApi {

    private final CatalogService catalogService;

    @Inject
    public DefaultCatalogUserApi(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public Catalog getCatalog(final String catalogName, final TenantContext context) {
        // STEPH TODO this is  hack until we decides what do do exactly:
        // Probably we want one catalog for tenant but but TBD
        return catalogService.getFullCatalog();
    }
}
