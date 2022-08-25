/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.catalog.api.user;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.commons.utils.io.Resources;
import org.killbill.commons.utils.io.CharStreams;
import org.killbill.xmlloader.UriAccessor;
import org.killbill.xmlloader.ValidationException;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestDefaultCatalogUserApi extends CatalogTestSuiteNoDB {

    private CatalogUserApi catalogUserApi;

    @BeforeMethod(groups = "fast")
    protected void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        catalogUserApi = new DefaultCatalogUserApi(Mockito.mock(CatalogService.class),
                                                   Mockito.mock(TenantUserApi.class),
                                                   catalogCache,
                                                   clock,
                                                   internalCallContextFactory);
    }

    @Test(groups = "fast")
    public void testValidateValidCatalog() throws Exception {
        catalogUserApi.validateCatalog(getXMLCatalog("SpyCarAdvanced.xml"), callContext);
    }

    @Test(groups = "fast")
    public void testValidateInvalidCatalog() throws Exception {
        try {
            catalogUserApi.validateCatalog(getXMLCatalog("CatalogWithValidationErrors.xml"), callContext);
        } catch (final CatalogApiException e) {
            Assert.assertTrue(e.getCause() instanceof ValidationException);
            Assert.assertEquals(((ValidationException) e.getCause()).getErrors().size(), 17);
        }
    }

    private String getXMLCatalog(final String name) throws URISyntaxException, IOException {
        final InputStream tenantInputCatalog = UriAccessor.accessUri(new URI(Resources.getResource("org/killbill/billing/catalog/" + name).toExternalForm()));
        return CharStreams.toString(new InputStreamReader(tenantInputCatalog, StandardCharsets.UTF_8));
    }
}