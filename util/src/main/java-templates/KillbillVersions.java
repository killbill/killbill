/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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


package org.killbill.billing.util.nodes;

public final class KillbillVersions {

    private static final String KB_VERSION = "${killbill.version}";
    private static final String API_VERSION = "${killbill-api.version}";
    private static final String PLUGIN_API_VERSION = "${killbill-plugin-api.version}";
    private static final String COMMON_VERSION = "${killbill-commons.version}";
    private static final String PLATFORM_VERSION = "${killbill-platform.version}";

    public static String getKillbillVersion() {
        return KB_VERSION;
    }

    public static String getApiVersion() {
        return API_VERSION;
    }

    public static String getPluginApiVersion() {
        return PLUGIN_API_VERSION;
    }

    public static String getCommonVersion() {
        return COMMON_VERSION;
    }

    public static String getPlatformVersion() {
        return PLATFORM_VERSION;
    }
}

