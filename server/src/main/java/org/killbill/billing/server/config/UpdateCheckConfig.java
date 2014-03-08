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

package org.killbill.billing.server.config;

import java.net.URI;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;

import org.killbill.billing.util.config.KillbillConfig;

public interface UpdateCheckConfig extends KillbillConfig {

    @Config("killbill.server.updateCheck.skip")
    @Default("false")
    @Description("Whether to skip update checks")
    public boolean shouldSkipUpdateCheck();

    @Config("killbill.server.updateCheck.url")
    @Default("https://raw.github.com/killbill/killbill/master/server/src/main/resources/update-checker/killbill-server-update-list.properties")
    @Description("URL to retrieve the latest version of Kill Bill")
    public URI updateCheckURL();

    @Config("killbill.server.updateCheck.connectTimeout")
    @Default("3000")
    @Description("Update check connection timeout")
    public int updateCheckConnectionTimeout();
}
