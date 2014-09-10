/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.server.listeners;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.servlet.ServletContext;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.platform.config.DefaultKillbillConfigSource;
import org.killbill.billing.server.modules.KillpayServerModule;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Module;

public class KillpayGuiceListener extends KillbillGuiceListener {

    @Override
    protected Module getModule(final ServletContext servletContext) {
        return new KillpayServerModule(servletContext, config, configSource);
    }

    @Override
    protected KillbillConfigSource getConfigSource() throws IOException, URISyntaxException {
        final ImmutableMap<String, String> defaultProperties = ImmutableMap.<String, String>of("org.killbill.server.updateCheck.url",
                                                                                               "https://raw.github.com/killbill/killbill/master/profiles/killpay/src/main/resources/update-checker/killbill-server-update-list.properties");
        return new DefaultKillbillConfigSource(defaultProperties);
    }
}
