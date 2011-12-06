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

import static org.testng.AssertJUnit.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Date;

import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import org.joda.time.DateTime;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import com.google.common.io.Resources;
import com.ning.billing.catalog.api.InvalidConfigException;
import com.ning.billing.catalog.io.VersionedCatalogLoader;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.clock.DefaultClock;

public class TestVersionedCatalog {
	private final VersionedCatalogLoader loader = new VersionedCatalogLoader(new DefaultClock());

	@Test(enabled=true)
	public void testAddCatalog() throws MalformedURLException, IOException, SAXException, InvalidConfigException, JAXBException, TransformerException, URISyntaxException, ServiceException {
		VersionedCatalog vc = loader.load(Resources.getResource("versionedCatalog").toString());
		vc.add(new StandaloneCatalog(new Date()));
		assertEquals(5, vc.size());
	}
	
	@Test(enabled=true)
	public void testApplyEffectiveDate() throws MalformedURLException, IOException, SAXException, InvalidConfigException, JAXBException, TransformerException, URISyntaxException, ServiceException {
		VersionedCatalog vc = loader.load(Resources.getResource("versionedCatalog").toString());
		Date d = new Date(1L);
		vc.configureEffectiveDate(d);
		assertEquals(new Date(0), vc.getEffectiveDate()); // Start at the begining of time
		
		DateTime dt = new DateTime("2011-01-01T00:00:00+00:00");
		d = new Date(dt.getMillis() + 1000);
		vc.configureEffectiveDate(d);
		assertEquals(dt.toDate(),vc.getEffectiveDate());
		
		dt = new DateTime("2011-02-02T00:00:00+00:00");
		d = new Date(dt.getMillis() + 1000);
		vc.configureEffectiveDate(d);
		assertEquals(dt.toDate(),vc.getEffectiveDate());
		
		dt = new DateTime("2011-03-03T00:00:00+00:00");
		d = new Date(dt.getMillis() + 1000);
		vc.configureEffectiveDate(d);
		assertEquals(dt.toDate(),vc.getEffectiveDate());
		
	}

}
