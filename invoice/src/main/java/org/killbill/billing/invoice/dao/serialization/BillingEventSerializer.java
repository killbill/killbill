/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.invoice.dao.serialization;

import java.io.IOException;

import org.killbill.billing.junction.BillingEventSet;
import org.xerial.snappy.Snappy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

public class BillingEventSerializer {

    private static final JsonFactory jsonFactory = new JsonFactory();
    private static ObjectMapper mapper = new ObjectMapper(jsonFactory);

    static {
        mapper.registerModule(new JodaModule());
        mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public static byte[] serialize(final BillingEventSet eventSet) throws IOException {

        final BillingEventSetJson json = new BillingEventSetJson(eventSet);
        final byte[] data = mapper.writeValueAsBytes(json);
        return Snappy.compress(data);
    }
}
