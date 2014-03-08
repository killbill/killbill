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

package org.killbill.billing.util.config.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.killbill.billing.catalog.api.InvalidConfigException;

public class XMLLoader {
    public static Logger log = LoggerFactory.getLogger(XMLLoader.class);

    public static <T extends ValidatingConfig<T>> T getObjectFromString(final String uri, final Class<T> objectType) throws Exception {
        if (uri == null) {
            return null;
        }
        log.info("Initializing an object of class " + objectType.getName() + " from xml file at: " + uri);

        return getObjectFromStream(new URI(uri), UriAccessor.accessUri(uri), objectType);
    }

    public static <T extends ValidatingConfig<T>> T getObjectFromUri(final URI uri, final Class<T> objectType) throws Exception {
        if (uri == null) {
            return null;
        }
        log.info("Initializing an object of class " + objectType.getName() + " from xml file at: " + uri);

        return getObjectFromStream(uri, UriAccessor.accessUri(uri), objectType);
    }

    public static <T extends ValidatingConfig<T>> T getObjectFromStream(final URI uri, final InputStream stream, final Class<T> clazz) throws SAXException, InvalidConfigException, JAXBException, IOException, TransformerException, ValidationException {
        if (stream == null) {
            return null;
        }

        final Object o = unmarshaller(clazz).unmarshal(stream);
        if (clazz.isInstance(o)) {
            @SuppressWarnings("unchecked") final
            T castObject = (T) o;
            try {
                validate(uri, castObject);
            } catch (ValidationException e) {
                e.getErrors().log(log);
                System.err.println(e.getErrors().toString());
                throw e;
            }
            return castObject;
        } else {
            return null;
        }
    }

    public static <T> T getObjectFromStreamNoValidation(final InputStream stream, final Class<T> clazz) throws SAXException, InvalidConfigException, JAXBException, IOException, TransformerException {
        final Object o = unmarshaller(clazz).unmarshal(stream);
        if (clazz.isInstance(o)) {
            @SuppressWarnings("unchecked") final
            T castObject = (T) o;
            return castObject;
        } else {
            return null;
        }
    }


    public static <T extends ValidatingConfig<T>> void validate(final URI uri, final T c) throws ValidationException {
        c.initialize(c, uri);
        final ValidationErrors errs = c.validate(c, new ValidationErrors());
        log.info("Errors: " + errs.size() + " for " + uri);
        if (errs.size() > 0) {
            throw new ValidationException(errs);
        }
    }

    public static Unmarshaller unmarshaller(final Class<?> clazz) throws JAXBException, SAXException, IOException, TransformerException {
        final JAXBContext context = JAXBContext.newInstance(clazz);

        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final Unmarshaller um = context.createUnmarshaller();

        final Schema schema = factory.newSchema(new StreamSource(XMLSchemaGenerator.xmlSchema(clazz)));
        um.setSchema(schema);

        return um;
    }

}
