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

package com.ning.billing.osgi;

import com.ning.billing.osgi.api.OSGIServiceDescriptor;

public class DefaultOSGIServiceDescriptor implements OSGIServiceDescriptor {

    private final String pluginSymbolicName;
    private final String serviceName;
    private final String serviceInfo;
    private final String serviceType;

    public DefaultOSGIServiceDescriptor(final String pluginSymbolicName, final String serviceName, final String serviceInfo, final String serviceType) {
        this.pluginSymbolicName = pluginSymbolicName;
        this.serviceName = serviceName;
        this.serviceInfo = serviceInfo;
        this.serviceType = serviceType;
    }

    @Override
    public String getPluginSymbolicName() {
        return pluginSymbolicName;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getServiceInfo() {
        return serviceInfo;
    }

    @Override
    public String getServiceType() {
        return serviceType;
    }
}
