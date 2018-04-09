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

package org.killbill.billing.util.discovery.provider;

import javax.inject.Provider;

import org.killbill.billing.util.discovery.KillbillInstanceConfig;

import com.google.inject.Inject;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaNamespace;

public class KillbillInstanceConfigProvider implements Provider<EurekaInstanceConfig> {
    @Inject(optional = true)
    @EurekaNamespace
    private String namespace;

    private KillbillInstanceConfig config;

    @Override
    public synchronized KillbillInstanceConfig get() {
        if (config == null) {
            if (namespace == null) {
                config = new KillbillInstanceConfig();
            } else {
                config = new KillbillInstanceConfig(namespace);
            }

            // TODO: Remove this when DiscoveryManager is finally no longer used
            DiscoveryManager.getInstance().setEurekaInstanceConfig(config);
        }
        return config;
    }
}