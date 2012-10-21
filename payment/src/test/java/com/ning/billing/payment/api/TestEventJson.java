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

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.payment.PaymentTestSuite;
import com.ning.billing.util.events.PaymentErrorInternalEvent;
import com.ning.billing.util.events.PaymentInfoInternalEvent;
import com.ning.billing.util.jackson.ObjectMapper;

public class TestEventJson extends PaymentTestSuite {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testPaymentErrorEvent() throws Exception {
        final PaymentErrorInternalEvent e = new DefaultPaymentErrorEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "no message", UUID.randomUUID());
        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(DefaultPaymentErrorEvent.class.getName());
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }

    @Test(groups = "fast")
    public void testPaymentInfoEvent() throws Exception {
        final PaymentInfoInternalEvent e = new DefaultPaymentInfoEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(12.9), new Integer(13), PaymentStatus.SUCCESS, "ext-ref1-12345", "ext-ref2-12345", UUID.randomUUID(), new DateTime());
        final String json = mapper.writeValueAsString(e);

        final Class<?> clazz = Class.forName(DefaultPaymentInfoEvent.class.getName());
        final Object obj = mapper.readValue(json, clazz);
        Assert.assertTrue(obj.equals(e));
    }
}
