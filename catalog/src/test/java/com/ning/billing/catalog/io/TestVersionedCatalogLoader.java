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
package com.ning.billing.catalog.io;

import static org.testng.AssertJUnit.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import org.joda.time.DateTime;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import com.google.common.io.Resources;
import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.VersionedCatalog;
import com.ning.billing.catalog.api.InvalidConfigException;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.clock.DefaultClock;

public class TestVersionedCatalogLoader {
	private final VersionedCatalogLoader loader = new VersionedCatalogLoader(new DefaultClock());

	
	@Test(enabled=true)
	public void testAppendToURI() throws MalformedURLException, IOException, URISyntaxException {
		URL u1 = new URL("http://www.ning.com/foo");
		assertEquals("http://www.ning.com/foo/bar",loader.appendToURI(u1, "bar").toString());

		URL u2 = new URL("http://www.ning.com/foo/");
		assertEquals("http://www.ning.com/foo/bar",loader.appendToURI(u2, "bar").toString());
		
	}
	
	

	
	@Test(enabled=true)
	public void testFindXmlFileReferences() throws MalformedURLException, URISyntaxException {
		String page = "dg.xml\n" + 
				"replica.foo\n" + 
				"snv1/\n" + 
				"viking.xml\n" ;
		List<URI> urls = loader.findXmlFileReferences(page, new URL("http://ning.com/"));
		assertEquals(2, urls.size());
		assertEquals("http://ning.com/dg.xml", urls.get(0).toString());
		assertEquals("http://ning.com/viking.xml", urls.get(1).toString());
		
	}
	
	@Test(enabled=true)
	public void testExtractHrefs() {
		String page = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">" + 
				"<html>" + 
				" <head>" + 
				"  <title>Index of /config/trunk/xno</title>" + 
				" </head>" + 
				" <body>" + 
				"<h1>Index of /config/trunk/xno</h1>" + 
				"<ul><li><a href=\"/config/trunk/\"> Parent Directory</a></li>" + 
				"<li><a href=\"dg.xml\"> dg.xml</a></li>" + 
				"<li><a href=\"replica.foo\"> replica/</a></li>" + 
				"<li><a href=\"replica2/\"> replica2/</a></li>" + 
				"<li><a href=\"replica_dyson/\"> replica_dyson/</a></li>" + 
				"<li><a href=\"snv1/\"> snv1/</a></li>" + 
				"<li><a href=\"viking.xml\"> viking.xml</a></li>" + 
				"</ul>" + 
				"<address>Apache/2.2.3 (CentOS) Server at <a href=\"mailto:kate@ning.com\">gepo.ningops.net</a> Port 80</address>" + 
				"</body></html>" ;
		List<String> hrefs = loader.extractHrefs(page);
		assertEquals(8, hrefs.size());
		assertEquals("/config/trunk/", hrefs.get(0));
		assertEquals("dg.xml", hrefs.get(1));
	}
	
	@Test(enabled=true)
	public void testFindXmlUrlReferences() throws MalformedURLException, URISyntaxException {
		String page = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">" + 
				"<html>" + 
				" <head>" + 
				"  <title>Index of /config/trunk/xno</title>" + 
				" </head>" + 
				" <body>" + 
				"<h1>Index of /config/trunk/xno</h1>" + 
				"<ul><li><a href=\"/config/trunk/\"> Parent Directory</a></li>" + 
				"<li><a href=\"dg.xml\"> dg.xml</a></li>" + 
				"<li><a href=\"replica.foo\"> replica/</a></li>" + 
				"<li><a href=\"replica2/\"> replica2/</a></li>" + 
				"<li><a href=\"replica_dyson/\"> replica_dyson/</a></li>" + 
				"<li><a href=\"snv1/\"> snv1/</a></li>" + 
				"<li><a href=\"viking.xml\"> viking.xml</a></li>" + 
				"</ul>" + 
				"<address>Apache/2.2.3 (CentOS) Server at <a href=\"mailto:kate@ning.com\">gepo.ningops.net</a> Port 80</address>" + 
				"</body></html>" ;
		List<URI> uris = loader.findXmlUrlReferences(page, new URL("http://ning.com/"));
		assertEquals(2, uris.size());
		assertEquals("http://ning.com/dg.xml", uris.get(0).toString());
		assertEquals("http://ning.com/viking.xml", uris.get(1).toString());
		
	}
	
	@Test(enabled=true)
	public void testLoad() throws MalformedURLException, IOException, SAXException, InvalidConfigException, JAXBException, TransformerException, URISyntaxException, ServiceException {
		VersionedCatalog c = loader.load(Resources.getResource("versionedCatalog").toString());
		assertEquals(4, c.size());
		Iterator<StandaloneCatalog> it = c.iterator();
		it.next(); //discard the baseline
		DateTime dt = new DateTime("2011-01-01T00:00:00+00:00");
		assertEquals(dt.toDate(),it.next().getEffectiveDate());
		dt = new DateTime("2011-02-02T00:00:00+00:00");
		assertEquals(dt.toDate(),it.next().getEffectiveDate());
		dt = new DateTime("2011-03-03T00:00:00+00:00");
		assertEquals(dt.toDate(),it.next().getEffectiveDate());
	}
}
