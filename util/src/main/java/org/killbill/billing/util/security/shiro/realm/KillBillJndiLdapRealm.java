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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.Ini.Section;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;
import org.apache.shiro.realm.ldap.JndiLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.PrincipalCollection;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.commons.utils.Strings;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.commons.utils.collect.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KillBillJndiLdapRealm extends JndiLdapRealm {

    private static final Logger log = LoggerFactory.getLogger(KillBillJndiLdapRealm.class);

    private static final String USERDN_SUBSTITUTION_TOKEN = "{0}";

    private static final SearchControls SUBTREE_SCOPE = new SearchControls();

    static {
        SUBTREE_SCOPE.setSearchScope(SearchControls.SUBTREE_SCOPE);
    }

    private final String searchBase;
    private final String groupSearchFilter;
    private final String groupNameId;
    private final Map<String, Collection<String>> permissionsByGroup = new LinkedHashMap<>();
    private final String dnSearchFilter;

    @Inject
    public KillBillJndiLdapRealm(final SecurityConfig securityConfig) {
        super();

        if (securityConfig.getShiroLDAPUserDnTemplate() != null) {
            setUserDnTemplate(securityConfig.getShiroLDAPUserDnTemplate());
        }

        final JndiLdapContextFactory contextFactory = (JndiLdapContextFactory) getContextFactory();
        if (securityConfig.disableShiroLDAPSSLCheck()) {
            contextFactory.getEnvironment().put("java.naming.ldap.factory.socket", SkipSSLCheckSocketFactory.class.getName());
        }
        contextFactory.getEnvironment().put("java.naming.referral", securityConfig.followShiroLDAPReferrals() ? "follow" : "ignore");
        if (securityConfig.getShiroLDAPUrl() != null) {
            contextFactory.setUrl(securityConfig.getShiroLDAPUrl());
        }
        if (securityConfig.getShiroLDAPSystemUsername() != null) {
            contextFactory.setSystemUsername(securityConfig.getShiroLDAPSystemUsername());
        }
        if (securityConfig.getShiroLDAPSystemPassword() != null) {
            contextFactory.setSystemPassword(securityConfig.getShiroLDAPSystemPassword());
        }
        if (securityConfig.getShiroLDAPAuthenticationMechanism() != null) {
            contextFactory.setAuthenticationMechanism(securityConfig.getShiroLDAPAuthenticationMechanism());
        }
        setContextFactory(contextFactory);

        dnSearchFilter = securityConfig.getShiroLDAPDnSearchTemplate();

        searchBase = securityConfig.getShiroLDAPSearchBase();
        groupSearchFilter = securityConfig.getShiroLDAPGroupSearchFilter();
        groupNameId = securityConfig.getShiroLDAPGroupNameID();

        if (securityConfig.getShiroLDAPPermissionsByGroup() != null) {
            final Ini ini = new Ini();
            // When passing properties on the command line, \n can be escaped
            ini.load(securityConfig.getShiroLDAPPermissionsByGroup().replace("\\n", "\n"));
            for (final Section section : ini.getSections()) {
                for (final String rawRole : section.keySet()) {
                    // Un-escape manually = (required if the role name is a DN)
                    final Collection<String> permissions = Strings.split(section.get(rawRole), ",");
                    final String role = rawRole.replace("\\=", "=");
                    permissionsByGroup.put(role, permissions);
                }
            }
        }
    }

    @Override
    protected String getUserDn(final String principal) throws IllegalArgumentException, IllegalStateException {
        if (dnSearchFilter != null) {
            return findUserDN(principal, getContextFactory());
        } else {
            // Use template
            return super.getUserDn(principal);
        }
    }

    private String findUserDN(final String userName, final LdapContextFactory ldapContextFactory) {
        LdapContext systemLdapCtx = null;
        try {
            systemLdapCtx = ldapContextFactory.getSystemLdapContext();
            final NamingEnumeration<SearchResult> usersFound = systemLdapCtx.search(searchBase,
                                                                                    dnSearchFilter.replace(USERDN_SUBSTITUTION_TOKEN, userName),
                                                                                    SUBTREE_SCOPE);
            return usersFound.hasMore() ? usersFound.next().getNameInNamespace() : null;
        } catch (final AuthenticationException ex) {
            log.info("LDAP authentication exception='{}'", ex.getLocalizedMessage());
            throw new IllegalArgumentException(ex);
        } catch (final NamingException e) {
            log.info("LDAP exception='{}'", e.getLocalizedMessage());
            throw new IllegalArgumentException(e);
        } finally {
            LdapUtils.closeContext(systemLdapCtx);
        }
    }

    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(final PrincipalCollection principals, final LdapContextFactory ldapContextFactory) throws NamingException {
        final Set<String> userGroups = findLDAPGroupsForUser(principals, ldapContextFactory);

        final SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo(userGroups);
        final Set<String> stringPermissions = groupsPermissions(userGroups);
        simpleAuthorizationInfo.setStringPermissions(stringPermissions);

        return simpleAuthorizationInfo;
    }

    private Set<String> findLDAPGroupsForUser(final PrincipalCollection principals, final LdapContextFactory ldapContextFactory) throws NamingException {
        final String username = (String) getAvailablePrincipal(principals);

        LdapContext systemLdapCtx = null;
        try {
            systemLdapCtx = ldapContextFactory.getSystemLdapContext();
            return findLDAPGroupsForUser(username, systemLdapCtx);
        } catch (final AuthenticationException ex) {
            log.info("LDAP authentication exception='{}'", ex.getLocalizedMessage());
            return Collections.emptySet();
        } finally {
            LdapUtils.closeContext(systemLdapCtx);
        }
    }

    private Set<String> findLDAPGroupsForUser(final String userName, final LdapContext ldapCtx) throws NamingException {
        final NamingEnumeration<SearchResult> foundGroups = ldapCtx.search(searchBase,
                                                                           groupSearchFilter.replace(USERDN_SUBSTITUTION_TOKEN, userName),
                                                                           SUBTREE_SCOPE);

        if (!foundGroups.hasMoreElements()) {
            return Collections.emptySet();
        }

        // There should really only be one entry
        final SearchResult result = foundGroups.next();

        // Extract the name of all the groups
        final Collection<String> finalGroupsNames = extractGroupNamesFromSearchResult(result);

        return new HashSet<>(finalGroupsNames);
    }

    @VisibleForTesting
    Collection<String> extractGroupNamesFromSearchResult(final SearchResult searchResult) {
        // Extract the group name from the attribute
        final Iterator<? extends Attribute> attributesIterator = searchResult.getAttributes().getAll().asIterator();
        final List<String> attributes = new ArrayList<>();
        Iterators.toStream(attributesIterator).filter(attribute -> groupNameId.equalsIgnoreCase(attribute.getID())).forEach(attr -> {
            try {
                if(attr.get() instanceof Collection) {
                    attributes.addAll((Collection) attr.get());
                } else {
                    attributes.add(attr.get().toString());
                }
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        });
        return attributes;
    }

    private Set<String> groupsPermissions(final Set<String> groups) {
        final Set<String> permissions = new HashSet<>();
        for (final String group : groups) {
            final Collection<String> permissionsForGroup = permissionsByGroup.get(group);
            if (permissionsForGroup != null) {
                permissions.addAll(permissionsForGroup);
            }
        }
        return permissions;
    }

    @VisibleForTesting
    public Map<String, Collection<String>> getPermissionsByGroup() {
        return permissionsByGroup;
    }
}
