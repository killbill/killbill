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

package com.ning.billing.osgi.api;

/**
 * Those represents the properties that plugin can use when registering services
 * and that Killbill knows how to interpret. At a minimum, the plugin should use PLUGIN_NAME_PROP
 */
public interface OSGIPluginProperties {

    /** Name of the plugin when it registers itself */
    // TODO We should make sure that this mataches the 'symbolic name' of the plugin, or if not how those two play together
    public static final String PLUGIN_NAME_PROP = "killbill.pluginName";

    /** Name of the instnace of the plugin; if 2 instances of the same plugin register */
    public static final String PLUGIN_INSTANCE_PROP = "killbill.pluginInstance";

    /** Used to export an additional configuration string for that service
     *  For instance for Servlet services this is used to specify the path of the servlet.
     */
    public static final String PLUGIN_SERVICE_INFO = "killbill.pluginServiceInfo";

}
