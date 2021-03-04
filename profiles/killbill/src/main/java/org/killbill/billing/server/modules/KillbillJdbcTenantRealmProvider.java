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

package org.killbill.billing.server.modules;

import javax.inject.Named;
import javax.sql.DataSource;

import org.apache.shiro.cache.CacheManager;
import org.killbill.billing.server.security.KillbillJdbcTenantRealm;
import org.killbill.billing.util.config.definition.SecurityConfig;

import com.google.inject.Inject;
import com.google.inject.Provider;

import static org.killbill.billing.platform.glue.KillBillPlatformModuleBase.SHIRO_DATA_SOURCE_ID;

public class KillbillJdbcTenantRealmProvider implements Provider<KillbillJdbcTenantRealm> {

    private final SecurityConfig securityConfig;
    private final CacheManager cacheManager;
    private final DataSource dataSource;

    @Inject
    public KillbillJdbcTenantRealmProvider(final SecurityConfig securityConfig, final CacheManager cacheManager, @Named(SHIRO_DATA_SOURCE_ID) final DataSource dataSource) {
        this.securityConfig = securityConfig;
        this.cacheManager = cacheManager;
        this.dataSource = dataSource;
    }

    @Override
    public KillbillJdbcTenantRealm get() {
        final KillbillJdbcTenantRealm killbillJdbcTenantRealm = new KillbillJdbcTenantRealm(dataSource, securityConfig);

        // Set the cache manager
        // Note: the DefaultWebSecurityManager used for RBAC will have all of its realms (set in KillBillShiroWebModule)
        // automatically configured with the EhCache manager (see EhCacheManagerProvider)
        killbillJdbcTenantRealm.setCacheManager(cacheManager);

        return killbillJdbcTenantRealm;
    }
}
