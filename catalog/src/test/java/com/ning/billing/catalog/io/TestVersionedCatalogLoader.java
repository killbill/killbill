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
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.joda.time.DateTime;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.VersionedCatalog;
import com.ning.billing.catalog.api.InvalidConfigException;

public class TestVersionedCatalogLoader {
	private final VersionedCatalogLoader loader = new VersionedCatalogLoader();


	@Test(enabled=true)
	public void testPullContentsFrom() throws MalformedURLException, IOException {
		String contents = loader.pullContentsFrom(new File("src/test/resources/WeaponsHireSmall.xml").toURI().toURL());

		assertTrue(contents.length() > 0);
		
	}
	
	@Test(enabled=true)
	public void testAppendToURL() throws MalformedURLException, IOException {
		URL u1 = new URL("http://www.ning.com/foo");
		assertEquals("http://www.ning.com/foo/bar",loader.appendToURL(u1, "bar").toString());

		URL u2 = new URL("http://www.ning.com/foo/");
		assertEquals("http://www.ning.com/foo/bar",loader.appendToURL(u2, "bar").toString());
		
	}
	
	

	
	@Test(enabled=true)
	public void testFindXmlFileReferences() throws MalformedURLException {
		String page = "dg.xml\n" + 
				"replica.foo\n" + 
				"snv1/\n" + 
				"viking.xml\n" ;
		List<URL> urls = loader.findXmlFileReferences(page, new URL("http://ning.com/"));
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
	public void testFindXmlUrlReferences() throws MalformedURLException {
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
		List<URL> urls = loader.findXmlUrlReferences(page, new URL("http://ning.com/"));
		assertEquals(2, urls.size());
		assertEquals("http://ning.com/dg.xml", urls.get(0).toString());
		assertEquals("http://ning.com/viking.xml", urls.get(1).toString());
		
	}
	
	@Test(enabled=true)
	public void testLoad() throws MalformedURLException, IOException, SAXException, InvalidConfigException, JAXBException {
		VersionedCatalog c = loader.load(new File("src/test/resources/versionedCatalog").toURI().toURL());
		assertEquals(4, c.size());
		Iterator<Catalog> it = c.iterator();
		it.next(); //discard the baseline
		DateTime dt = new DateTime("2011-01-01T00:00:00+00:00");
		assertEquals(dt.toDate(),it.next().getEffectiveDate());
		dt = new DateTime("2011-02-02T00:00:00+00:00");
		assertEquals(dt.toDate(),it.next().getEffectiveDate());
		dt = new DateTime("2011-03-03T00:00:00+00:00");
		assertEquals(dt.toDate(),it.next().getEffectiveDate());
	}
}
