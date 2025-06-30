/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.skife.config.ConfigSource;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;
import org.testng.annotations.Test;

public class TestKillBillOktaRealm extends UtilTestSuiteNoDB {

    @Test(groups = "external", enabled = false)
    public void testCheckOktaConnection() throws Exception {
        // Convenience method to verify your Okta connectivity
        final Properties props = new Properties();
        props.setProperty("org.killbill.security.okta.url", "https://dev-XXXXXX.oktapreview.com");
        props.setProperty("org.killbill.security.okta.apiToken", "YYYYYY");
        props.setProperty("org.killbill.security.okta.permissionsByGroup", "support-group: entitlement:*\n" +
                                                                           "finance-group: invoice:*, payment:*\n" +
                                                                           "ops-group: *:*");
        final ConfigSource customConfigSource = new SimplePropertyConfigSource(props);
        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(customConfigSource).build(SecurityConfig.class);
        final KillBillOktaRealm oktaRealm = new KillBillOktaRealm(securityConfig);

        final String username = "pierre";
        final String password = "password";

        // Check authentication
        final UsernamePasswordToken token = new UsernamePasswordToken(username, password);
        final AuthenticationInfo authenticationInfo = oktaRealm.getAuthenticationInfo(token);
        System.out.println(authenticationInfo);

        // Check permissions
        final SimplePrincipalCollection principals = new SimplePrincipalCollection(username, username);
        final AuthorizationInfo authorizationInfo = oktaRealm.doGetAuthorizationInfo(principals);
        System.out.println("Roles: " + authorizationInfo.getRoles());
        System.out.println("Permissions: " + authorizationInfo.getStringPermissions());
    }
}
