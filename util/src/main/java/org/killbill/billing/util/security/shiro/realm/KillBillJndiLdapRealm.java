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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.util.config.definition.SecurityConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

public class KillBillJndiLdapRealm extends JndiLdapRealm {

    private static final Logger log = LoggerFactory.getLogger(KillBillJndiLdapRealm.class);

    private static final String USERDN_SUBSTITUTION_TOKEN = "{0}";

    private static final SearchControls SUBTREE_SCOPE = new SearchControls();

    static {
        SUBTREE_SCOPE.setSearchScope(SearchControls.SUBTREE_SCOPE);
    }

    private static final Splitter SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

    private final String searchBase;
    private final String groupSearchFilter;
    private final String groupNameId;
    private final Map<String, Collection<String>> permissionsByGroup = Maps.newLinkedHashMap();

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

        searchBase = securityConfig.getShiroLDAPSearchBase();
        groupSearchFilter = securityConfig.getShiroLDAPGroupSearchFilter();
        groupNameId = securityConfig.getShiroLDAPGroupNameID();

        if (securityConfig.getShiroLDAPPermissionsByGroup() != null) {
            final Ini ini = new Ini();
            // When passing properties on the command line, \n can be escaped
            ini.load(securityConfig.getShiroLDAPPermissionsByGroup().replace("\\n", "\n"));
            for (final Section section : ini.getSections()) {
                for (final String role : section.keySet()) {
                    final Collection<String> permissions = ImmutableList.<String>copyOf(SPLITTER.split(section.get(role)));
                    permissionsByGroup.put(role, permissions);
                }
            }
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
        } catch (AuthenticationException ex) {
            log.info("LDAP authentication exception='{}'", ex.getLocalizedMessage());
            return ImmutableSet.<String>of();
        } finally {
            LdapUtils.closeContext(systemLdapCtx);
        }
    }

    private Set<String> findLDAPGroupsForUser(final String userName, final LdapContext ldapCtx) throws NamingException {
        final NamingEnumeration<SearchResult> foundGroups = ldapCtx.search(searchBase,
                                                                           groupSearchFilter.replace(USERDN_SUBSTITUTION_TOKEN, userName),
                                                                           SUBTREE_SCOPE);

        // Extract the name of all the groups
        final Iterator<SearchResult> groupsIterator = Iterators.<SearchResult>forEnumeration(foundGroups);
        final Iterator<String> groupsNameIterator = Iterators.<SearchResult, String>transform(groupsIterator,
                                                                                              new Function<SearchResult, String>() {
                                                                                                  @Override
                                                                                                  public String apply(final SearchResult groupEntry) {
                                                                                                      return extractGroupNameFromSearchResult(groupEntry);
                                                                                                  }
                                                                                              });
        final Iterator<String> finalGroupsNameIterator = Iterators.<String>filter(groupsNameIterator, Predicates.notNull());

        return Sets.newHashSet(finalGroupsNameIterator);
    }

    private String extractGroupNameFromSearchResult(final SearchResult searchResult) {
        // Get all attributes for that group
        final Iterator<? extends Attribute> attributesIterator = Iterators.forEnumeration(searchResult.getAttributes().getAll());

        // Find the attribute representing the group name
        final Iterator<? extends Attribute> groupNameAttributesIterator = Iterators.filter(attributesIterator,
                                                                                           new Predicate<Attribute>() {
                                                                                               @Override
                                                                                               public boolean apply(final Attribute attribute) {
                                                                                                   return groupNameId.equalsIgnoreCase(attribute.getID());
                                                                                               }
                                                                                           });

        // Extract the group name from the attribute
        // Note: at this point, groupNameAttributesIterator should really contain a single element
        final Iterator<String> groupNamesIterator = Iterators.transform(groupNameAttributesIterator,
                                                                        new Function<Attribute, String>() {
                                                                            @Override
                                                                            public String apply(final Attribute groupNameAttribute) {
                                                                                try {
                                                                                    final NamingEnumeration<?> enumeration = groupNameAttribute.getAll();
                                                                                    if (enumeration.hasMore()) {
                                                                                        return enumeration.next().toString();
                                                                                    } else {
                                                                                        return null;
                                                                                    }
                                                                                } catch (NamingException namingException) {
                                                                                    log.warn("Unable to read group name", namingException);
                                                                                    return null;
                                                                                }
                                                                            }
                                                                        });
        final Iterator<String> finalGroupNamesIterator = Iterators.<String>filter(groupNamesIterator, Predicates.notNull());

        if (finalGroupNamesIterator.hasNext()) {
            return finalGroupNamesIterator.next();
        } else {
            log.warn("Unable to find an attribute matching {}", groupNameId);
            return null;
        }
    }

    private Set<String> groupsPermissions(final Set<String> groups) {
        final Set<String> permissions = new HashSet<String>();
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
