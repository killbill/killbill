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

package org.killbill.billing.util.security.shiro.realm;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.killbill.billing.platform.glue.KillBillPlatformModuleBase;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.security.shiro.KillbillCredentialsMatcher;

public class KillBillJdbcRealm extends JdbcRealm {

    protected static final String KILLBILL_SALTED_AUTHENTICATION_QUERY = "select password, password_salt from users where username = ? and is_active";
    protected static final String KILLBILL_USER_ROLES_QUERY = "select role_name from user_roles where username = ? and is_active";
    protected static final String KILLBILL_PERMISSIONS_QUERY = "select permission from roles_permissions where role_name = ? and is_active";

    private final DataSource dataSource;
    private final SecurityConfig securityConfig;

    @Inject
    public KillBillJdbcRealm(@Named(KillBillPlatformModuleBase.SHIRO_DATA_SOURCE_ID) final DataSource dataSource, final SecurityConfig securityConfig) {
        super();
        this.dataSource = dataSource;
        this.securityConfig = securityConfig;

        // TODO Enable when we add support for cache invalidation
        // See JavaDoc warning: https://shiro.apache.org/static/1.2.3/apidocs/org/apache/shiro/realm/AuthenticatingRealm.html
        //setAuthenticationCachingEnabled(true);

        // Tweak JdbcRealm defaults
        setPermissionsLookupEnabled(true);
        setAuthenticationQuery(KILLBILL_SALTED_AUTHENTICATION_QUERY);
        setUserRolesQuery(KILLBILL_USER_ROLES_QUERY);
        setPermissionsQuery(KILLBILL_PERMISSIONS_QUERY);

        configureSecurity();
        configureDataSource();
    }

    @Override
    public void clearCachedAuthorizationInfo(PrincipalCollection principals) {
        super.clearCachedAuthorizationInfo(principals);
    }

    private void configureSecurity() {
        setSaltStyle(SaltStyle.COLUMN);
        setCredentialsMatcher(KillbillCredentialsMatcher.getCredentialsMatcher(securityConfig));
    }

    private void configureDataSource() {
        setDataSource(dataSource);
    }
}
