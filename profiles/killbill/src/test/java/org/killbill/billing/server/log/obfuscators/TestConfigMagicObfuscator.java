/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.server.log.obfuscators;

import org.killbill.billing.server.log.ServerTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class TestConfigMagicObfuscator extends ServerTestSuiteNoDB {

    private final ConfigMagicObfuscator obfuscator = new ConfigMagicObfuscator();

    @Test(groups = "fast")
    public void testKey() throws Exception {
        verify("Assigning value [pass2b78b7cef] for [org.killbill.billing.plugin.avatax.licenseKey] on [org.killbill.billing.plugins.avatax#getLicenseKey()]",
               "Assigning value [*************] for [org.killbill.billing.plugin.avatax.licenseKey] on [org.killbill.billing.plugins.avatax#getLicenseKey()]");

        verify("Assigning value [pass2b78b7cef] for [org.killbill.billing.plugin.avatax.apiKey] on [org.killbill.billing.plugins.avatax#getApiKey()]",
               "Assigning value [*************] for [org.killbill.billing.plugin.avatax.apiKey] on [org.killbill.billing.plugins.avatax#getApiKey()]");
    }

    @Test(groups = "fast")
    public void testPassword() throws Exception {
        verify("Assigning value [pass2b78b7ce] for [org.killbill.dao.pass] on [org.killbill.commons.jdbi.guice.DaoConfig#getPass()]",
               "Assigning value [************] for [org.killbill.dao.pass] on [org.killbill.commons.jdbi.guice.DaoConfig#getPass()]");

        verify("Assigning value [pass2b78b7ce] for [org.killbill.dao.password] on [org.killbill.commons.jdbi.guice.DaoConfig#getPassword()]",
               "Assigning value [************] for [org.killbill.dao.password] on [org.killbill.commons.jdbi.guice.DaoConfig#getPassword()]");
    }

    private void verify(final String input, final String output) {
        final String obfuscated = obfuscator.obfuscate(input, Mockito.mock(ILoggingEvent.class));
        Assert.assertEquals(obfuscated, output, obfuscated);
    }
}
