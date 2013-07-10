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

package com.ning.billing.catalog.io;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.VersionedCatalog;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.config.catalog.UriAccessor;
import com.ning.billing.util.config.catalog.XMLLoader;

public class VersionedCatalogLoader implements ICatalogLoader {
    private static final Object PROTOCOL_FOR_FILE = "file";
    private final String XML_EXTENSION = ".xml";
    private final Clock clock;

    @Inject
    public VersionedCatalogLoader(final Clock clock) {
        this.clock = clock;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.io.ICatalogLoader#load(java.lang.String)
      */
    @Override
    public VersionedCatalog load(final String uriString) throws ServiceException {
        try {
            List<URI> xmlURIs = null;

            if (uriString.endsWith(XML_EXTENSION)) { // Assume its an xml file
                xmlURIs = new ArrayList<URI>();
                URI uri = new URI(uriString);

                // Try to expand the full path, if possible
                final String schemeSpecificPart = uri.getSchemeSpecificPart();
                if (schemeSpecificPart != null) {
                    final String[] split = schemeSpecificPart.split("/");
                    final String fileName = split[split.length - 1];
                    try {
                        uri = new URI(Resources.getResource(fileName).toExternalForm());
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                xmlURIs.add(uri);
            } else { // Assume its a directory
                final String directoryContents = UriAccessor.accessUriAsString(uriString);
                xmlURIs = findXmlReferences(directoryContents, new URL(uriString));
            }

            final VersionedCatalog result = new VersionedCatalog(clock);
            for (final URI u : xmlURIs) {
                final StandaloneCatalog catalog = XMLLoader.getObjectFromUri(u, StandaloneCatalog.class);
                result.add(catalog);
            }

            return result;
        } catch (Exception e) {
            throw new ServiceException("Problem encountered loading catalog", e);
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


}
