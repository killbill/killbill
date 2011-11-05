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

package com.ning.billing.util.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.TransformerException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.ning.billing.catalog.api.InvalidConfigException;

public class XMLLoader {
	private static final String URI_SCHEME_FOR_CLASSPATH = "jar";
	public static Logger log = LoggerFactory.getLogger(XMLLoader.class);

	public static <T extends ValidatingConfig<T>> T getObjectFromURI(URI uri, Class<T> objectType) throws SAXException, InvalidConfigException, JAXBException, IOException, TransformerException, URISyntaxException {
        String scheme = uri.getScheme();
        if (scheme.equals(URI_SCHEME_FOR_CLASSPATH)) {
        	InputStream resourceStream = XMLLoader.class.getResourceAsStream(uri.getPath());
        	return getObjectFromStream(uri, resourceStream, objectType);
        }
        return getObjectFromURL(uri.toURL(), objectType);
    }

	public static <T extends ValidatingConfig<T>> T getObjectFromURL(URL url, Class<T> clazz) throws SAXException, InvalidConfigException, JAXBException, IOException, TransformerException, URISyntaxException {
        Object o = unmarshaller(clazz).unmarshal(url);
    
        if (clazz.isInstance(o)) {
            @SuppressWarnings("unchecked")
			T castObject = (T)o;
            validate(url.toURI(),castObject);
            return castObject;
        } else {
            return null;
        }
    }

	private static <T extends ValidatingConfig<T>> T getObjectFromStream(URI uri,InputStream stream, Class<T> clazz) throws SAXException, InvalidConfigException, JAXBException, IOException, TransformerException {
        Object o = unmarshaller(clazz).unmarshal(stream);
        if (clazz.isInstance(o)) {
        	@SuppressWarnings("unchecked")
			T castObject = (T)o;
            validate(uri,castObject);
            return castObject;
        } else {
            return null;
        }
    }

    

	public static <T extends ValidatingConfig<T>> void validate(URI uri, T c) {
            c.initialize(c);
            ValidationErrors errs = c.validate(c, new ValidationErrors());
            System.out.println("Errors: " + errs.size() + " for " + uri);       
    }
    
    public static Unmarshaller unmarshaller(Class<?> clazz) throws JAXBException, SAXException, IOException, TransformerException {
    	 JAXBContext context =JAXBContext.newInstance(clazz);

         SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI );
         Unmarshaller um = context.createUnmarshaller();

         Schema schema = factory.newSchema(XMLSchemaGenerator.xmlSchema(clazz));
         um.setSchema(schema);
         
         return um;
    }
	
}
