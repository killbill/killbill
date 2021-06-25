/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.payment.dao;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;

import org.killbill.billing.payment.api.PluginProperty;

import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFEncoder;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

public class PluginPropertySerializer {

    private static final int MAX_SIZE_PROPERTIES_BYTES = (8 * 1024); // As defined in payment_attempt ddl

    private static final JsonFactory jsonFactory = new JsonFactory();
    private static ObjectMapper mapper = new ObjectMapper(jsonFactory);

    static {
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.registerModule(new JodaModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static byte[] serialize(final Iterable<PluginProperty> input) throws PluginPropertySerializerException {

        final ByteArrayOutputStream out = new ByteArrayOutputStream(MAX_SIZE_PROPERTIES_BYTES);
        try {
            final JsonGenerator jsonGenerator = jsonFactory.createGenerator(out, JsonEncoding.UTF8);
            jsonGenerator.writeStartArray();
            for (final PluginProperty cur : input) {
                final String key = cur.getKey();
                final Object value = cur.getValue();
                jsonGenerator.writeStartObject();
                jsonGenerator.writeFieldName(key);
                mapper.writeValue(jsonGenerator, value);
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.close();
            final byte[] data = out.toByteArray();
            return LZFEncoder.encode(data);
        } catch (final IOException e) {
            throw new PluginPropertySerializerException(e);
        }
    }

    public static Iterable<PluginProperty> deserialize(final byte[] input) throws PluginPropertySerializerException {
        final Collection<PluginProperty> result = new ArrayList<PluginProperty>();
        if (input == null) {
            return result;
        }

        try {
            final byte[] uncompressed = LZFDecoder.decode(input);
            final InputStream in = new ByteArrayInputStream(uncompressed);
            final JsonParser jsonParser = jsonFactory.createParser(in);

            PluginProperty prop = null;
            String key = null;
            JsonToken nextToken = jsonParser.nextToken();
            while (nextToken != null && nextToken != JsonToken.END_ARRAY) {
                if (nextToken != JsonToken.START_ARRAY) {
                    if (nextToken == JsonToken.FIELD_NAME && key == null) {
                        key = jsonParser.getText();
                    } else if (key != null) {
                        final Object value = mapper.readValue(jsonParser, Object.class);
                        prop = new PluginProperty(key, value, false);
                        key = null;
                    } else if (nextToken == JsonToken.END_OBJECT) {
                        result.add(prop);
                        prop = null;
                    }
                }
                nextToken = jsonParser.nextToken();
            }
            jsonParser.close();
            return result;
        } catch (final UnsupportedEncodingException e) {
            throw new PluginPropertySerializerException(e);
        } catch (final JsonParseException e) {
            throw new PluginPropertySerializerException(e);
        } catch (final IOException e) {
            throw new PluginPropertySerializerException(e);
        }
    }

    public static class PluginPropertySerializerException extends Exception {

        public PluginPropertySerializerException() {
        }

        public PluginPropertySerializerException(final String message) {
            super(message);
        }

        public PluginPropertySerializerException(final Throwable cause) {
            super(cause);
        }

        public PluginPropertySerializerException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
