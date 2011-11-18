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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import com.google.inject.Inject;
import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.VersionedCatalog;
import com.ning.billing.catalog.api.InvalidConfigException;
import com.ning.billing.lifecycle.IService.ServiceException;
import com.ning.billing.util.clock.IClock;
import com.ning.billing.util.config.XMLLoader;

public class VersionedCatalogLoader implements ICatalogLoader  {
	private static final Object PROTOCOL_FOR_FILE = "file";
	private  final String XML_EXTENSION = ".xml";
	private  final String HREF_LOW_START = "href=\""; 
	private  final String HREF_CAPS_START = "HREF=\""; 
	private  final String HREF_SEARCH_END = "\"";
	private IClock clock;
			
	@Inject 
	public VersionedCatalogLoader(IClock clock) {
		this.clock = clock;
	}
	
	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.io.ICatalogLoader#load(java.lang.String)
	 */
	@Override
	public  VersionedCatalog load(String urlString) throws ServiceException{
		try {
			return load(new URL(urlString));
		} catch (Exception e) {
			throw new ServiceException("Problem encountered loading catalog", e);
		}
	}

	public  VersionedCatalog load(URL url) throws IOException, SAXException, InvalidConfigException, JAXBException, TransformerException, URISyntaxException {
		List<URL> xmlURLs = null;
		
		if(url.getPath().endsWith(XML_EXTENSION)) { //assume its an xml file
			xmlURLs = new ArrayList<URL>();
        	xmlURLs.add(url);
		} else { //assume its a directory
			String directoryContents = pullContentsFrom(url);
			xmlURLs = findXmlReferences(directoryContents, url);
		}
		
		VersionedCatalog result = new VersionedCatalog();
		for(URL u : xmlURLs) {
			Catalog catalog = XMLLoader.getObjectFromURL(u, Catalog.class);
			result.add(catalog);
		}
		Date now = clock.getUTCNow().toDate();
		result.configureEffectiveDate(now);
		return result;
	}
	
	protected  List<URL> findXmlReferences(String directoryContents, URL url) throws MalformedURLException {
		if(url.getProtocol().equals("file")) {
			return findXmlFileReferences(directoryContents, url);
		} 
		return findXmlUrlReferences(directoryContents, url);
	}

	protected  List<URL> findXmlUrlReferences(String directoryContents, URL url) throws MalformedURLException {
		List<URL> results = new ArrayList<URL>();
		List<String> urlFragments = extractHrefs(directoryContents);
		for(String u : urlFragments) {
			if(u.endsWith(XML_EXTENSION)) { //points to xml
				if(u.startsWith("/")) { //absolute path need to add the protocol
					results.add(new URL(url.getProtocol() + ":" + u));
				} else if (u.startsWith("http:")) { // full url
					results.add(new URL(u));
				} else { // relative url stick the name on the end
					results.add(appendToURL(url,u));
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

	protected  List<URL> findXmlFileReferences(String directoryContents, URL url) throws MalformedURLException {
		List<URL> results = new ArrayList<URL>();
		String[] filenames = directoryContents.split("\\n");
		for(String filename : filenames) {
			if(filename.endsWith(XML_EXTENSION)) {
				results.add(appendToURL(url,filename));
			}
		}
		return results;
	}

	protected  URL appendToURL(final URL url, final String filename) throws MalformedURLException {
		String f = filename;
		if (!url.toString().endsWith("/")) {
			f = "/" + filename;
		}
		return new URL(url.toString() + f);
	}

	protected  String pullContentsFrom(final URL url) throws IOException {
		URLConnection connection = url.openConnection();
		InputStream content = connection.getInputStream();
		return new Scanner(content).useDelimiter("\\A").next();
	}
}
