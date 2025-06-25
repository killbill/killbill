/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.util.glue;

import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.Factory;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.skife.config.ConfigSource;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealmsFromShiroIniProvider {

    private static final Logger log = LoggerFactory.getLogger(RealmsFromShiroIniProvider.class);

    public static DefaultSecurityManager get(final ConfigSource configSource) {
        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(configSource).build(SecurityConfig.class);

        try {
            final Factory<SecurityManager> factory = new IniSecurityManagerFactory(securityConfig.getShiroResourcePath());
            // TODO Pierre hack - lame cast here, but we need to have Shiro go through its reflection magic
            // to parse the [main] section of the ini file. Without duplicating code, this seems to be possible only
            // by going through IniSecurityManagerFactory.
            return (DefaultSecurityManager) factory.getInstance();
        } catch (final ConfigurationException e) {
            log.warn("Unable to configure RBAC", e);
        }

        return null;
    }
}
