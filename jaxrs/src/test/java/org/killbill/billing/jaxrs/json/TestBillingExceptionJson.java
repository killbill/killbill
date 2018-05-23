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

package org.killbill.billing.jaxrs.json;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.json.BillingExceptionJson.StackTraceElementJson;

import com.google.common.collect.ImmutableList;

public class TestBillingExceptionJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String className = UUID.randomUUID().toString();
        final int code = Integer.MIN_VALUE;
        final String message = UUID.randomUUID().toString();
        final String causeClassName = UUID.randomUUID().toString();
        final String causeMessage = UUID.randomUUID().toString();

        final BillingExceptionJson exceptionJson = new BillingExceptionJson(className, code, message, causeClassName, causeMessage, ImmutableList.<StackTraceElementJson>of());
        Assert.assertEquals(exceptionJson.getClassName(), className);
        Assert.assertEquals(exceptionJson.getCode(), (Integer) code);
        Assert.assertEquals(exceptionJson.getMessage(), message);
        Assert.assertEquals(exceptionJson.getCauseClassName(), causeClassName);
        Assert.assertEquals(exceptionJson.getCauseMessage(), causeMessage);
        Assert.assertEquals(exceptionJson.getStackTrace().size(), 0);

        final String asJson = mapper.writeValueAsString(exceptionJson);
        final BillingExceptionJson fromJson = mapper.readValue(asJson, BillingExceptionJson.class);
        Assert.assertEquals(fromJson, exceptionJson);
    }

    @Test(groups = "fast")
    public void testFromException() throws Exception {
        final String nil = null;
        try {
            nil.toString();
            Assert.fail();
        } catch (final NullPointerException e) {
            final BillingExceptionJson exceptionJson = new BillingExceptionJson(e, true);
            Assert.assertEquals(exceptionJson.getClassName(), e.getClass().getName());
            Assert.assertNull(exceptionJson.getCode());
            Assert.assertNull(exceptionJson.getMessage());
            Assert.assertNull(exceptionJson.getCauseClassName());
            Assert.assertNull(exceptionJson.getCauseMessage());
            Assert.assertFalse(exceptionJson.getStackTrace().isEmpty());
            Assert.assertEquals(exceptionJson.getStackTrace().get(0).getClassName(), TestBillingExceptionJson.class.getName());
            Assert.assertEquals(exceptionJson.getStackTrace().get(0).getMethodName(), "testFromException");
            Assert.assertFalse(exceptionJson.getStackTrace().get(0).isNativeMethod());

            final BillingExceptionJson exceptionJsonNoStackTrace = new BillingExceptionJson(e, false);
            Assert.assertEquals(exceptionJsonNoStackTrace.getClassName(), e.getClass().getName());
            Assert.assertNull(exceptionJsonNoStackTrace.getCode());
            Assert.assertNull(exceptionJsonNoStackTrace.getMessage());
            Assert.assertNull(exceptionJsonNoStackTrace.getCauseClassName());
            Assert.assertNull(exceptionJsonNoStackTrace.getCauseMessage());
            Assert.assertTrue(exceptionJsonNoStackTrace.getStackTrace().isEmpty());
        }
    }
}
