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

package com.ning.billing.catalog;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.io.VersionedCatalogLoader;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.config.CatalogConfig;

public class TestCatalogService extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCatalogServiceDirectory() throws ServiceException {
        final DefaultCatalogService service = new DefaultCatalogService(new CatalogConfig() {
            @Override
            public String getCatalogURI() {
                return "file:src/test/resources/versionedCatalog";
            }

        }, new VersionedCatalogLoader(new DefaultClock()));
        service.loadCatalog();
        Assert.assertNotNull(service.getFullCatalog());
        Assert.assertEquals(service.getFullCatalog().getCatalogName(), "WeaponsHireSmall");
    }

    @Test(groups = "fast")
    public void testCatalogServiceFile() throws ServiceException {
        final DefaultCatalogService service = new DefaultCatalogService(new CatalogConfig() {
            @Override
            public String getCatalogURI() {
                return "file:src/test/resources/WeaponsHire.xml";
            }

        }, new VersionedCatalogLoader(new DefaultClock()));
        service.loadCatalog();
        Assert.assertNotNull(service.getFullCatalog());
        Assert.assertEquals(service.getFullCatalog().getCatalogName(), "Firearms");
    }
}
