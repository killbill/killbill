/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.util.config.definition;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.DefaultNull;
import org.skife.config.Description;

public interface SecurityConfig extends KillbillConfig {

    @Config("org.killbill.security.shiroResourcePath")
    @Default("classpath:shiro.ini")
    @Description("Path to the shiro.ini file (classpath, url or file resource)")
    public String getShiroResourcePath();

    @Config("org.killbill.security.shiroNbHashIterations")
    @Default("200000")
    @Description("Sets the number of times submitted credentials will be hashed before comparing to the credentials stored in the system")
    public Integer getShiroNbHashIterations();

    // LDAP Realm

    @Config("org.killbill.security.ldap.userDnTemplate")
    @DefaultNull
    @Description("LDAP server's User DN format (e.g. uid={0},ou=users,dc=mycompany,dc=com)")
    public String getShiroLDAPUserDnTemplate();

    @Config("org.killbill.security.ldap.searchBase")
    @DefaultNull
    @Description("LDAP search base to use")
    public String getShiroLDAPSearchBase();

    @Config("org.killbill.security.ldap.groupSearchFilter")
    @Default("memberOf=uid={0}")
    @Description("LDAP search filter to use to find groups (e.g. memberOf=uid={0},ou=users,dc=mycompany,dc=com)")
    public String getShiroLDAPGroupSearchFilter();

    @Config("org.killbill.security.ldap.groupNameId")
    @Default("memberOf")
    @Description("Group name attribute ID in LDAP")
    public String getShiroLDAPGroupNameID();

    @Config("org.killbill.security.ldap.permissionsByGroup")
    @Default("admin = *:*\n" +
             "finance = invoice:*, payment:*\n" +
             "support = entitlement:*, invoice:item_adjust")
    @Description("LDAP permissions by LDAP group")
    public String getShiroLDAPPermissionsByGroup();

    @Config("org.killbill.security.ldap.url")
    @Default("ldap://127.0.0.1:389")
    @Description("LDAP server url")
    public String getShiroLDAPUrl();

    @Config("org.killbill.security.ldap.systemUsername")
    @DefaultNull
    @Description("LDAP username")
    public String getShiroLDAPSystemUsername();

    @Config("org.killbill.security.ldap.systemPassword")
    @DefaultNull
    @Description("LDAP password")
    public String getShiroLDAPSystemPassword();

    @Config("org.killbill.security.ldap.authenticationMechanism")
    @Default("simple")
    @Description("LDAP authentication mechanism (e.g. DIGEST-MD5)")
    public String getShiroLDAPAuthenticationMechanism();

    @Config("org.killbill.security.ldap.disableSSLCheck")
    @Default("false")
    @Description("Whether to ignore SSL certificates checks")
    public boolean disableShiroLDAPSSLCheck();
}
