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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.DefaultVersionedCatalog;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.StandaloneCatalogWithPriceOverride;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.catalog.override.PriceOverrideSvc;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.CatalogConfig;
import org.killbill.commons.concurrent.Executors;
import org.killbill.commons.utils.io.Resources;
import org.killbill.xmlloader.UriAccessor;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationException;
import org.killbill.xmlloader.XMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionedCatalogLoader implements CatalogLoader, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(VersionedCatalogLoader.class);

    private static final Object PROTOCOL_FOR_FILE = "file";
    private static final String XML_EXTENSION = ".xml";

    private final PriceOverrideSvc priceOverride;
    private final ExecutorService executorService;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public VersionedCatalogLoader(final CatalogConfig config,
                                  final PriceOverrideSvc priceOverride,
                                  final InternalCallContextFactory internalCallContextFactory) {
        this.executorService = Executors.newFixedThreadPool(Objects.requireNonNullElse(config.getCatalogThreadNb(), 1), VersionedCatalogLoader.class.getName());
        this.priceOverride = priceOverride;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public VersionedCatalog loadDefaultCatalog(final String uriString) throws CatalogApiException {
        try {
            final List<URI> xmlURIs;
            if (uriString.endsWith(XML_EXTENSION)) { // Assume its an xml file
                xmlURIs = new ArrayList<URI>();
                xmlURIs.add(new URI(uriString));
            } else { // Assume its a directory
                final URL url = getURLFromString(uriString);
                final String directoryContents = UriAccessor.accessUriAsString(uriString);
                xmlURIs = findXmlReferences(directoryContents, url);
            }

            final DefaultVersionedCatalog result = new DefaultVersionedCatalog();
            for (final URI u : xmlURIs) {
                final StandaloneCatalog catalog = XMLLoader.getObjectFromUri(u, StandaloneCatalog.class);
                result.add(new StandaloneCatalogWithPriceOverride(catalog, priceOverride, InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID, internalCallContextFactory));
            }
            // Perform initialization and validation for VersionedCatalog
            XMLLoader.initializeAndValidate(result);
            return result;
        } catch (final ValidationException e) {
            logger.warn("Failed to load default catalog", e);
            throw new CatalogApiException(e, ErrorCode.CAT_INVALID_DEFAULT, uriString);
        } catch (final JAXBException e) {
            logger.warn("Failed to load default catalog", e);
            throw new CatalogApiException(e, ErrorCode.CAT_INVALID_DEFAULT, uriString);
        } catch (final IllegalArgumentException e) {
            logger.warn("Failed to load default catalog", e);
            throw new CatalogApiException(e, ErrorCode.CAT_INVALID_DEFAULT, uriString);
        } catch (final Exception e) {
            logger.warn("Failed to load default catalog", e);
            throw new IllegalStateException(e);
        }
    }

    private URL getURLFromString(final String urlString) {
        try {
            // If the string provided is already a URL (with correct scheme, ...) return the URL object
            return new URL(urlString);
        } catch (final MalformedURLException ignore) {
        }
        // If not, this must be something on the classpath
        return Resources.getResource(urlString);
    }

    public VersionedCatalog load(final Collection<String> catalogXMLs, final boolean filterTemplateCatalog, final Long tenantRecordId) throws CatalogApiException {
        try {
            final Collection<Future<StandaloneCatalog>> catalogs = new ArrayList<>(catalogXMLs.size());
            for (final String cur : catalogXMLs) {
                catalogs.add(executorService.submit(() -> {
                    final InputStream curCatalogStream = new ByteArrayInputStream(cur.getBytes(StandardCharsets.UTF_8));
                    final StandaloneCatalog catalog = XMLLoader.getObjectFromStream(curCatalogStream, StandaloneCatalog.class);
                    if (!filterTemplateCatalog || !catalog.isTemplateCatalog()) {
                        return new StandaloneCatalogWithPriceOverride(catalog, priceOverride, tenantRecordId, internalCallContextFactory);
                    }
                    return null;
                }));
            }

            final DefaultVersionedCatalog result = new DefaultVersionedCatalog();
            for (final Future<StandaloneCatalog> standaloneCatalogFuture : catalogs) {
                final StandaloneCatalog catalog = standaloneCatalogFuture.get();
                if (catalog != null) {
                    result.add(catalog);
                }
            }

            XMLLoader.initializeAndValidate(result);
            return result;
        } catch (final ValidationException e) {
            logger.warn("Failed to load catalog for tenantRecordId='{}'", tenantRecordId, e);
            for (final ValidationError ve : e.getErrors()) {
                logger.warn(ve.toString());
            }
            throw new CatalogApiException(e, ErrorCode.CAT_INVALID_FOR_TENANT, tenantRecordId);
        } catch (final InterruptedException e) {
            logger.warn("Failed to load catalog for tenantRecordId='{}'", tenantRecordId, e);
            throw new CatalogApiException(e, ErrorCode.CAT_INVALID_FOR_TENANT, tenantRecordId);
        } catch (final ExecutionException e) {
            logger.warn("Failed to load catalog for tenantRecordId='{}'", tenantRecordId, e);
            throw new CatalogApiException(e, ErrorCode.CAT_INVALID_FOR_TENANT, tenantRecordId);
        }
    }

    protected List<URI> findXmlReferences(final String directoryContents, final URL url) throws URISyntaxException {
        if (url.getProtocol().equals(PROTOCOL_FOR_FILE)) {
            return findXmlFileReferences(directoryContents, url);
        }
        return findXmlUrlReferences(directoryContents, url);
    }

    protected List<URI> findXmlUrlReferences(final String directoryContents, final URL url) throws URISyntaxException {
        final List<URI> results = new ArrayList<URI>();
        final List<String> urlFragments = extractHrefs(directoryContents);
        for (final String u : urlFragments) {
            if (u.endsWith(XML_EXTENSION)) { //points to xml
                if (u.startsWith("/")) { //absolute path need to add the protocol
                    results.add(new URI(url.getProtocol() + ":" + u));
                } else if (u.startsWith("http:")) { // full url
                    results.add(new URI(u));
                } else { // relative url stick the name on the end
                    results.add(appendToURI(url, u));
                }
            }
        }
        return results;
    }

    protected List<String> extractHrefs(final String directoryContents) {
        final List<String> results = new ArrayList<String>();
        int start = 0;
        int end = 0;
        final String HREF_SEARCH_END = "\"";
        final String HREF_LOW_START = "href=\"";
        while (start >= 0) {
            start = directoryContents.indexOf(HREF_LOW_START, end);
            if (start > 0) {
                start = start + HREF_LOW_START.length();
            }

            end = directoryContents.indexOf(HREF_SEARCH_END, start);
            if (start >= 0) { // We found something
                results.add(directoryContents.substring(start, end));
            }
        }

        start = 0;
        end = 0;
        while (start >= 0) {
            final String HREF_CAPS_START = "HREF=\"";
            start = directoryContents.indexOf(HREF_CAPS_START, end);
            if (start > 0) {
                start = +HREF_LOW_START.length();
            }

            end = directoryContents.indexOf(HREF_SEARCH_END, start);
            if (start >= 0) { // We found something
                results.add(directoryContents.substring(start, end));
            }
        }
        return results;
    }

    protected List<URI> findXmlFileReferences(final String directoryContents, final URL url) throws URISyntaxException {
        final List<URI> results = new ArrayList<URI>();
        final String[] filenames = directoryContents.split("\\n");
        for (final String filename : filenames) {
            if (filename.endsWith(XML_EXTENSION)) {
                results.add(appendToURI(url, filename));
            }
        }
        return results;
    }

    protected URI appendToURI(final URL url, final String filename) throws URISyntaxException {
        String f = filename;
        if (!url.toString().endsWith("/")) {
            f = "/" + filename;
        }
        return new URI(url.toString() + f);
    }

    @Override
    public void close() {
        executorService.shutdown();
    }
}
