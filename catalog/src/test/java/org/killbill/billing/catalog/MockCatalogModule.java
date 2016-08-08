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

package org.killbill.billing.catalog;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.KillBillModule;
import org.mockito.Mockito;

public class MockCatalogModule extends KillBillModule {

    public MockCatalogModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        final Catalog catalog = Mockito.mock(Catalog.class);

        final CatalogService catalogService = Mockito.mock(CatalogService.class);
        try {
            Mockito.when(catalogService.getCurrentCatalog(Mockito.any(Boolean.class), Mockito.any(Boolean.class), Mockito.any(InternalCallContext.class))).thenReturn(new MockCatalog());
            Mockito.when(catalogService.getFullCatalog(Mockito.any(Boolean.class), Mockito.any(Boolean.class), Mockito.any(InternalCallContext.class))).thenReturn(catalog);
            bind(CatalogService.class).toInstance(catalogService);
        } catch (CatalogApiException e) {
            throw new RuntimeException(e);
        }
    }
}
