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

package org.killbill.billing.catalog;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.catalog.io.VersionedCatalogLoader;
import org.mockito.Mockito;

public class MockCatalogService extends DefaultCatalogService {

    private final VersionedCatalog catalog;

    public MockCatalogService(final VersionedCatalog catalog) {
        super(null, null, null, null, Mockito.mock(VersionedCatalogLoader.class));
        this.catalog = catalog;
    }

    @Override
    public synchronized void loadCatalog() throws ServiceException {
    }

    @Override
    public String getName() {
        return "Mock Catalog";
    }

    @Override
    public VersionedCatalog getFullCatalogForInternalUse(final boolean useDefaultCatalog, final boolean filterTemplateCatalog, InternalTenantContext context) {
        return catalog;
    }

    @Override
    public VersionedCatalog getFullCatalog(final boolean useDefaultCatalog, final boolean filterTemplateCatalog, InternalTenantContext context) {
        return catalog;
    }
}
