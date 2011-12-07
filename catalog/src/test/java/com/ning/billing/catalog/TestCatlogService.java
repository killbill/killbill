/*
 * Copyright 2010-2011 Ning, Inc.
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

import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.io.VersionedCatalogLoader;
import com.ning.billing.config.CatalogConfig;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.clock.DefaultClock;

public class TestCatlogService {

	@Test
	public void testCatalogServiceDirectory() throws ServiceException {
		DefaultCatalogService service = new DefaultCatalogService(new CatalogConfig() {
			@Override
			public String getCatalogURI() {
				return "file:src/test/resources/versionedCatalog";
			}
			
		}, new VersionedCatalogLoader(new DefaultClock()));
		service.loadCatalog();
		Assert.assertNotNull(service.getCatalog());
		Assert.assertEquals(service.getCatalog().getCalalogName(), "WeaponsHireSmall");
	}
	
	@Test
	public void testCatalogServiceFile() throws ServiceException {
		DefaultCatalogService service = new DefaultCatalogService(new CatalogConfig() {
			@Override
			public String getCatalogURI() {
				return "file:src/test/resources/WeaponsHire.xml";
			}
			
		}, new VersionedCatalogLoader(new DefaultClock()));
		service.loadCatalog();
		Assert.assertNotNull(service.getCatalog());
		Assert.assertEquals(service.getCatalog().getCalalogName(), "Firearms");
	}
}
