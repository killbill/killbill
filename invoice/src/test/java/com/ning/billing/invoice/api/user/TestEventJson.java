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
package com.ning.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.EmptyInvoiceEvent;
import com.ning.billing.invoice.api.InvoiceCreationEvent;

public class TestEventJson {

    private ObjectMapper mapper = new ObjectMapper();

    @BeforeTest(groups= {"fast"})
    public void setup() {
        mapper = new ObjectMapper();
        mapper.disable(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test(groups= {"fast"})
    public void testInvoiceCreationEvent() throws Exception {

        InvoiceCreationEvent e = new DefaultInvoiceCreationEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(12.0), Currency.USD, new DateTime(), UUID.randomUUID());

        String json = mapper.writeValueAsString(e);

        Class<?> claz = Class.forName(DefaultInvoiceCreationEvent.class.getName());
        Object obj =  mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }

    @Test(groups= {"fast"})
    public void testEmptyInvoiceEvent() throws Exception {

        EmptyInvoiceEvent e = new DefaultEmptyInvoiceEvent(UUID.randomUUID(), new DateTime(), UUID.randomUUID());

        String json = mapper.writeValueAsString(e);

        Class<?> claz = Class.forName(DefaultEmptyInvoiceEvent.class.getName());
        Object obj =  mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }
}
