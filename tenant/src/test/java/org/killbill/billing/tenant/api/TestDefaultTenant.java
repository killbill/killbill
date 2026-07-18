/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.tenant.api;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

import org.killbill.billing.tenant.TenantTestSuiteNoDB;
import org.redisson.client.codec.Codec;
import org.redisson.codec.SerializationCodec;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultTenant extends TenantTestSuiteNoDB {

    @Test(groups = "fast")
    public void testExternalizable() throws Throwable {
        final Codec codec = new SerializationCodec();

        final DefaultTenant tenantData = new DefaultTenant(UUID.randomUUID(), clock.getUTCNow(), clock.getUTCNow(), "er44TT-yy4r", "TTR445ee2", null);
        final DefaultTenant tenantData2 = externalize(codec, tenantData);
        Assert.assertEquals(tenantData2, tenantData);

        final DefaultTenant withSecret = new DefaultTenant(UUID.randomUUID(), clock.getUTCNow(), clock.getUTCNow(), "external", "apiKey", "apiSecret");
        final DefaultTenant withSecret2 = externalize(codec, withSecret);
        Assert.assertNotEquals(withSecret2, withSecret);
        Assert.assertNotNull(withSecret.getApiSecret());
        Assert.assertNull(withSecret2.getApiSecret());
    }

    private DefaultTenant externalize(final Codec codec, final DefaultTenant tenant) throws Throwable {
        // Keep the Redisson codec path, but avoid a direct test dependency on Netty's ByteBuf.
        final Object encoded = invokeRedissonCodecMethod(codec.getValueEncoder(), "encode", tenant);
        return (DefaultTenant) invokeRedissonCodecMethod(codec.getValueDecoder(), "decode", encoded, null);
    }

    private Object invokeRedissonCodecMethod(final Object target, final String methodName, final Object... args) throws Throwable {
        final Method method = findMethod(target.getClass(), methodName, args.length);

        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Method findMethod(final Class<?> clazz, final String methodName, final int argumentCount) throws IOException {
        for (final Method method : clazz.getDeclaredMethods()) {
            if (methodName.equals(method.getName()) && method.getParameterCount() == argumentCount) {
                return method;
            }
        }
        throw new IOException("Unable to find Redisson codec method " + methodName + " on " + clazz.getName());
    }
}
