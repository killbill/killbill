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
package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;


public class TestEventJson {


    private ObjectMapper mapper = new ObjectMapper();

    @BeforeTest(groups= {"fast"})
    public void setup() {
        mapper = new ObjectMapper();
        mapper.disable(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test(groups= {"fast"})
    public void testPaymentErrorEvent() throws Exception {
        PaymentErrorEvent e = new DefaultPaymentErrorEvent("credit card", "Failed payment", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        String json = mapper.writeValueAsString(e);

        Class<?> claz = Class.forName(DefaultPaymentErrorEvent.class.getName());
        Object obj =  mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }
    
    @Test(groups= {"fast"})
    public void testPaymentInfoEvent() throws Exception {
        PaymentInfoEvent e = new DefaultPaymentInfoEvent(UUID.randomUUID().toString(), new BigDecimal(12), new BigDecimal(12.9), "BNP", "eeert", "success",
                "credit", "ref", "paypal", "paypal", "", "", UUID.randomUUID(), new DateTime());
        
        String json = mapper.writeValueAsString(e);

        Class<?> claz = Class.forName(DefaultPaymentInfoEvent.class.getName());
        Object obj =  mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }
}
