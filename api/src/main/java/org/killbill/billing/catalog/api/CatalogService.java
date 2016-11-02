/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.platform.api.KillbillService;

/**
 * The interface {@code CatalogService} is a {@code KillbillService} required to handle catalog operations.
 */
public interface CatalogService extends KillbillService {

    public Catalog getFullCatalog(boolean useDefaultCatalog, final boolean filterTemplateCatalog, InternalTenantContext context) throws CatalogApiException;

    public StaticCatalog getCurrentCatalog(boolean useDefaultCatalog, final boolean filterTemplateCatalog, InternalTenantContext context) throws CatalogApiException;
}
