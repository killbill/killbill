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

package org.killbill.billing.util.discovery;

import com.google.common.base.Strings;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.PropertiesInstanceConfig;

import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Singleton
public class KillbillInstanceConfig extends PropertiesInstanceConfig implements EurekaInstanceConfig {

    private String hostName;

    public KillbillInstanceConfig() { }

    public KillbillInstanceConfig(String namespace) {
        super(namespace);
    }

    public KillbillInstanceConfig(String namespace, DataCenterInfo dataCenterInfo) {
        super(namespace, dataCenterInfo);
    }

    @Override
    public String getInstanceId() {
        final String appName = getAppname();
        final UUID uuid = UUID.randomUUID();
        return appName + ":" + uuid.toString();
    }

    @Override
    public String getHostName(boolean refresh) {
        if(Strings.isNullOrEmpty(hostName)) {
            determineHostName();
        }
        return hostName;
    }

    protected void determineHostName() {
        try {
            hostName = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Failed to get canonical hostname", e);
        }
    }
}