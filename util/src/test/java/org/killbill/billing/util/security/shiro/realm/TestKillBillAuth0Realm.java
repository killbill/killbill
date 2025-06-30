/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import java.util.Properties;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.skife.config.ConfigSource;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;
import org.testng.annotations.Test;

import io.jsonwebtoken.Claims;

public class TestKillBillAuth0Realm extends UtilTestSuiteNoDB {

    @Test(groups = "external", enabled = false)
    public void testCheckAuth0Connection() throws Exception {
        // Convenience method to verify your Auth0 connectivity
        final Properties props = new Properties();
        props.setProperty("org.killbill.security.auth0.url", "https://XXX.us.auth0.com");
        props.setProperty("org.killbill.security.auth0.clientId", "YYY");
        props.setProperty("org.killbill.security.auth0.clientSecret", "ZZZ");
        props.setProperty("org.killbill.security.auth0.apiIdentifier", "WWW");
        props.setProperty("org.killbill.security.auth0.databaseConnectionName", "Username-Password-Authentication");
        props.setProperty("org.killbill.security.auth0.allowedClockSkew", "2000s");
        final ConfigSource customConfigSource = new SimplePropertyConfigSource(props);
        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(customConfigSource).build(SecurityConfig.class);
        final KillBillAuth0Realm auth0Realm = new KillBillAuth0Realm(securityConfig, clock);

        final String username = "test@example.com";
        final String password = "password";

        // Check authentication
        final AuthenticationToken token = new UsernamePasswordToken(username, password);
        final AuthenticationInfo authenticationInfo = auth0Realm.getAuthenticationInfo(token);
        System.out.println(authenticationInfo);

        // Check permissions
        final PrincipalCollection principals = new SimplePrincipalCollection(username, username);
        final AuthorizationInfo authorizationInfo = auth0Realm.doGetAuthorizationInfo(principals);
        System.out.println("Roles: " + authorizationInfo.getRoles());
        System.out.println("Permissions: " + authorizationInfo.getStringPermissions());

        // Check JWT
        final Claims claims = auth0Realm.verifyJWT("JWT");
        System.out.println("Token claims: " + claims);
    }
}
