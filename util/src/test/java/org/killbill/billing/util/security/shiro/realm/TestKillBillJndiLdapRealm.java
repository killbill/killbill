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

package org.killbill.billing.util.security.shiro.realm;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.config.definition.SecurityConfig;

import com.google.common.collect.Sets;

public class TestKillBillJndiLdapRealm extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCheckConfiguration() throws Exception {
        // Test default configuration (see SecurityConfig)
        final Map<String, Collection<String>> permission = killBillJndiLdapRealm.getPermissionsByGroup();

        Assert.assertEquals(permission.get("admin").size(), 1);
        Assert.assertEquals(permission.get("admin").iterator().next(), "*:*");

        Assert.assertEquals(permission.get("finance").size(), 2);
        Assert.assertEquals(Sets.newHashSet(permission.get("finance")), Sets.newHashSet("invoice:*", "payment:*"));

        Assert.assertEquals(permission.get("support").size(), 2);
        Assert.assertEquals(Sets.newHashSet(permission.get("support")), Sets.newHashSet("entitlement:*", "invoice:item_adjust"));
    }

    @Test(groups = "external", enabled = false)
    public void testCheckLDAPConnection() throws Exception {
        // Convenience method to verify your LDAP connectivity
        final Properties props = new Properties();
        props.setProperty("org.killbill.security.ldap.userDnTemplate", "uid={0},ou=users,dc=mycompany,dc=com");
        props.setProperty("org.killbill.security.ldap.searchBase", "ou=groups,dc=mycompany,dc=com");
        props.setProperty("org.killbill.security.ldap.groupSearchFilter", "memberOf=uid={0},ou=users,dc=mycompany,dc=com");
        props.setProperty("org.killbill.security.ldap.groupNameId", "cn");
        props.setProperty("org.killbill.security.ldap.url", "ldap://ldap:389");
        props.setProperty("org.killbill.security.ldap.disableSSLCheck", "true");
        props.setProperty("org.killbill.security.ldap.systemUsername", "cn=root");
        props.setProperty("org.killbill.security.ldap.systemPassword", "password");
        props.setProperty("org.killbill.security.ldap.authenticationMechanism", "simple");
        props.setProperty("org.killbill.security.ldap.permissionsByGroup", "support-group: entitlement:*\n" +
                                                                       "finance-group: invoice:*, payment:*\n" +
                                                                       "ops-group: *:*");
        final ConfigSource customConfigSource = new SimplePropertyConfigSource(props);
        final SecurityConfig securityConfig = new ConfigurationObjectFactory(customConfigSource).build(SecurityConfig.class);
        final KillBillJndiLdapRealm ldapRealm = new KillBillJndiLdapRealm(securityConfig);

        final String username = "pierre";
        final String password = "password";

        // Check authentication
        final UsernamePasswordToken token = new UsernamePasswordToken(username, password);
        final AuthenticationInfo authenticationInfo = ldapRealm.getAuthenticationInfo(token);
        System.out.println(authenticationInfo);

        // Check permissions
        final SimplePrincipalCollection principals = new SimplePrincipalCollection(username, username);
        final AuthorizationInfo authorizationInfo = ldapRealm.queryForAuthorizationInfo(principals, ldapRealm.getContextFactory());
        System.out.println("Roles: " + authorizationInfo.getRoles());
        System.out.println("Permissions: " + authorizationInfo.getStringPermissions());
    }
}
