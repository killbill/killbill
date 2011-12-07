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

import com.google.inject.Inject;
import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.VersionedCatalog;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.config.UriAccessor;
import com.ning.billing.util.config.XMLLoader;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VersionedCatalogLoader implements ICatalogLoader  {
	private static final Object PROTOCOL_FOR_FILE = "file";
	private  final String XML_EXTENSION = ".xml";
	private  final String HREF_LOW_START = "href=\""; 
	private  final String HREF_CAPS_START = "HREF=\""; 
	private  final String HREF_SEARCH_END = "\"";
	private Clock clock;
			
	@Inject 
	public VersionedCatalogLoader(Clock clock) {
		this.clock = clock;
	}
	
	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.io.ICatalogLoader#load(java.lang.String)
	 */
	@Override
	public  VersionedCatalog load(String uriString) throws ServiceException{
		try {
			List<URI> xmlURIs = null;
			
			if(uriString.endsWith(XML_EXTENSION)) { //assume its an xml file
				xmlURIs = new ArrayList<URI>();
	        	xmlURIs.add(new URI(uriString));
			} else { //assume its a directory
				String directoryContents = UriAccessor.accessUriAsString(uriString);
				xmlURIs = findXmlReferences(directoryContents, new URL(uriString));
			}
			
			VersionedCatalog result = new VersionedCatalog();
			for(URI u : xmlURIs) {
				StandaloneCatalog catalog = XMLLoader.getObjectFromUri(u, StandaloneCatalog.class);
				result.add(catalog);
			}
			Date now = clock.getUTCNow().toDate();
			result.configureEffectiveDate(now);
			return result;
		} catch (Exception e) {
			throw new ServiceException("Problem encountered loading catalog", e);
		}
	}
	
	protected  List<URI> findXmlReferences(String directoryContents, URL url) throws URISyntaxException {
		if(url.getProtocol().equals(PROTOCOL_FOR_FILE)) {
			return findXmlFileReferences(directoryContents, url);
		} 
		return findXmlUrlReferences(directoryContents, url);
	}

	protected  List<URI> findXmlUrlReferences(String directoryContents, URL url) throws URISyntaxException {
		List<URI> results = new ArrayList<URI>();
		List<String> urlFragments = extractHrefs(directoryContents);
		for(String u : urlFragments) {
			if(u.endsWith(XML_EXTENSION)) { //points to xml
				if(u.startsWith("/")) { //absolute path need to add the protocol
					results.add(new URI(url.getProtocol() + ":" + u));
				} else if (u.startsWith("http:")) { // full url
					results.add(new URI(u));
				} else { // relative url stick the name on the end
					results.add(appendToURI(url,u));
				}
			}
		}
		return results;
	}

	protected  List<String> extractHrefs(String directoryContents) {
		List<String> results = new ArrayList<String>();
		int start = 0;
		int end = 0;
		while(start >= 0) {
			start = directoryContents.indexOf(HREF_LOW_START, end);
			if (start > 0) start = start + HREF_LOW_START.length();
					
			end = directoryContents.indexOf(HREF_SEARCH_END, start);
			if(start >= 0) { // We found something
				results.add(directoryContents.substring(start, end));
			}
		}
		
		start = 0;
		end = 0;
		while(start >= 0) {
			start = directoryContents.indexOf(HREF_CAPS_START, end);
			if (start > 0) start =+ HREF_LOW_START.length();
			
			end = directoryContents.indexOf(HREF_SEARCH_END, start);
			if(start >= 0) { // We found something
				results.add(directoryContents.substring(start, end));
			}
		}
		return results;
	}

	protected  List<URI> findXmlFileReferences(String directoryContents, URL url) throws URISyntaxException {
		List<URI> results = new ArrayList<URI>();
		String[] filenames = directoryContents.split("\\n");
		for(String filename : filenames) {
			if(filename.endsWith(XML_EXTENSION)) {
				results.add(appendToURI(url,filename));
			}
		}
		return results;
	}

	protected  URI appendToURI(final URL url, final String filename) throws URISyntaxException {
		String f = filename;
		if (!url.toString().endsWith("/")) {
			f = "/" + filename;
		}
		return new URI(url.toString() + f);
	}

	
}
