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

package org.killbill.billing.catalog.io;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.VersionedCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Files;
import com.google.common.io.Resources;

public class TestVersionedCatalogLoader extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testAppendToURI() throws IOException, URISyntaxException {
        final URL u1 = new URL("http://www.ning.com/foo");
        Assert.assertEquals(loader.appendToURI(u1, "bar").toString(), "http://www.ning.com/foo/bar");

        final URL u2 = new URL("http://www.ning.com/foo/");
        Assert.assertEquals(loader.appendToURI(u2, "bar").toString(), "http://www.ning.com/foo/bar");
    }

    @Test(groups = "fast")
    public void testFindXmlFileReferences() throws MalformedURLException, URISyntaxException {
        final String page = "dg.xml\n" +
                            "replica.foo\n" +
                            "snv1/\n" +
                            "viking.xml\n";
        final List<URI> urls = loader.findXmlFileReferences(page, new URL("http://ning.com/"));
        Assert.assertEquals(urls.size(), 2);
        Assert.assertEquals(urls.get(0).toString(), "http://ning.com/dg.xml");
        Assert.assertEquals(urls.get(1).toString(), "http://ning.com/viking.xml");
    }

    @Test(groups = "fast")
    public void testExtractHrefs() {
        final String page = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">" +
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
                            "</body></html>";
        final List<String> hrefs = loader.extractHrefs(page);
        Assert.assertEquals(hrefs.size(), 8);
        Assert.assertEquals(hrefs.get(0), "/config/trunk/");
        Assert.assertEquals(hrefs.get(1), "dg.xml");
    }

    @Test(groups = "fast")
    public void testFindXmlUrlReferences() throws MalformedURLException, URISyntaxException {
        final String page = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">" +
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
                            "</body></html>";
        final List<URI> uris = loader.findXmlUrlReferences(page, new URL("http://ning.com/"));
        Assert.assertEquals(uris.size(), 2);
        Assert.assertEquals(uris.get(0).toString(), "http://ning.com/dg.xml");
        Assert.assertEquals(uris.get(1).toString(), "http://ning.com/viking.xml");
    }

    @Test(groups = "fast")
    public void testLoad() throws CatalogApiException {
        final VersionedCatalog c = loader.loadDefaultCatalog(Resources.getResource("versionedCatalog").toString());
        Assert.assertEquals(c.size(), 4);
        final Iterator<StandaloneCatalog> it = c.iterator();
        DateTime dt = new DateTime("2011-01-01T00:00:00+00:00");
        Assert.assertEquals(it.next().getEffectiveDate(), dt.toDate());
        dt = new DateTime("2011-02-02T00:00:00+00:00");
        Assert.assertEquals(it.next().getEffectiveDate(), dt.toDate());
        dt = new DateTime("2011-02-03T00:00:00+00:00");
        Assert.assertEquals(it.next().getEffectiveDate(), dt.toDate());
        dt = new DateTime("2011-03-03T00:00:00+00:00");
        Assert.assertEquals(it.next().getEffectiveDate(), dt.toDate());
    }

    @Test(groups = "fast")
    public void testLoadCatalogFromClasspathResourceFolder() throws CatalogApiException {
        final VersionedCatalog c = loader.loadDefaultCatalog("SpyCarBasic.xml");
        Assert.assertEquals(c.size(), 1);
        final DateTime dt = new DateTime("2013-02-08T00:00:00+00:00");
        Assert.assertEquals(c.getEffectiveDate(), dt.toDate());
        Assert.assertEquals(c.getCatalogName(), "SpyCarBasic");
    }

    @Test(groups = "fast", expectedExceptions = CatalogApiException.class)
    public void testLoadCatalogFromClasspathResourceBadFolder() throws CatalogApiException {
        loader.loadDefaultCatalog("SpyCarCustom.xml");
    }

    @Test(groups = "fast")
    public void testLoadCatalogFromInsideResourceFolder() throws CatalogApiException {
        final VersionedCatalog c = loader.loadDefaultCatalog("com/acme/SpyCarCustom.xml");
        Assert.assertEquals(c.size(), 1);
        final DateTime dt = new DateTime("2015-10-04T00:00:00+00:00");
        Assert.assertEquals(c.getEffectiveDate(), dt.toDate());
        Assert.assertEquals(c.getCatalogName(), "SpyCarCustom");
    }

    @Test(groups = "fast", expectedExceptions = CatalogApiException.class)
    public void testLoadCatalogFromInsideResourceWithBadFolderName() throws CatalogApiException {
        loader.loadDefaultCatalog("com/acme2/SpyCarCustom.xml");
    }

    @Test(groups = "fast")
    public void testLoadCatalogFromExternalFile() throws CatalogApiException, IOException, URISyntaxException {
        final File originFile = new File(Resources.getResource("SpyCarBasic.xml").toURI());
        final File destinationFile = new File(Files.createTempDir().toString() + "/SpyCarBasicRelocated.xml");
        destinationFile.deleteOnExit();
        Files.copy(originFile, destinationFile);
        final VersionedCatalog c = loader.loadDefaultCatalog(destinationFile.toURI().toString());
        Assert.assertEquals(c.getCatalogName(), "SpyCarBasic");
    }
}
