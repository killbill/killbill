/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.Ini.Section;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.killbill.commons.utils.Strings;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class KillBillOktaRealm extends AuthorizingRealm {

    private static final Logger log = LoggerFactory.getLogger(KillBillOktaRealm.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String USER_AGENT = "KillBill/1.0";
    private static final int DEFAULT_TIMEOUT_SECS = 15;

    private final Map<String, Collection<String>> permissionsByGroup = new LinkedHashMap<>();

    private final SecurityConfig securityConfig;
    private final HttpClient httpClient;

    @Inject
    public KillBillOktaRealm(final SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
        this.httpClient = HttpClient.newBuilder()
                                    .connectTimeout(Duration.of(DEFAULT_TIMEOUT_SECS, ChronoUnit.SECONDS)).build();

        if (securityConfig.getShiroOktaPermissionsByGroup() != null) {
            final Ini ini = new Ini();
            // When passing properties on the command line, \n can be escaped
            ini.load(securityConfig.getShiroOktaPermissionsByGroup().replace("\\n", "\n"));
            for (final Section section : ini.getSections()) {
                for (final String role : section.keySet()) {
                    final Collection<String> permissions = Strings.split(section.get(role), ",");
                    permissionsByGroup.put(role, permissions);
                }
            }
        }
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(final PrincipalCollection principals) {
        final String username = (String) getAvailablePrincipal(principals);
        final String userId = findOktaUserId(username);
        final Set<String> userGroups = findOktaGroupsForUser(userId);

        final SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo(userGroups);
        final Set<String> stringPermissions = groupsPermissions(userGroups);
        simpleAuthorizationInfo.setStringPermissions(stringPermissions);

        return simpleAuthorizationInfo;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token) throws AuthenticationException {
        final UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        if (doAuthenticate(upToken)) {
            // Credentials are valid
            return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
        } else {
            throw new AuthenticationException("Okta authentication failed");
        }
    }

    private boolean doAuthenticate(final UsernamePasswordToken upToken) {
        final Map<String, String> body = Map.of("username", upToken.getUsername(),
                                                "password", String.valueOf(upToken.getPassword()));

        final String bodyString;
        try {
            bodyString = mapper.writeValueAsString(body);
        } catch (final JsonProcessingException e) {
            log.warn("Error while generating Okta payload");
            throw new AuthenticationException(e);
        }

        final HttpRequest request = HttpRequest.newBuilder()
                                               .uri(URI.create(securityConfig.getShiroOktaUrl() + "/api/v1/authn"))
                                               .header("User-Agent", USER_AGENT)
                                               .header("Content-Type", "application/json; charset=UTF-8")
                                               .header("Authorization", "SSWS " + securityConfig.getShiroOktaAPIToken())
                                               .timeout(Duration.of(DEFAULT_TIMEOUT_SECS, ChronoUnit.SECONDS))
                                               .POST(HttpRequest.BodyPublishers.ofString(bodyString))
                                               .build();

        final HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (final Exception e) {
            log.warn("Error while connecting to Auth0", e);
            throw new AuthenticationException(e);
        }

        return isAuthenticated(response);
    }

    private boolean isAuthenticated(final HttpResponse<InputStream> oktaRawResponse) {
        try {
            final Map oktaResponse = mapper.readValue(oktaRawResponse.body(), Map.class);
            if ("SUCCESS".equals(oktaResponse.get("status"))) {
                return true;
            } else {
                log.warn("Okta authentication failed: " + oktaResponse);
                return false;
            }
        } catch (final IOException e) {
            log.warn("Unable to read response from Okta");
            throw new AuthenticationException(e);
        }
    }

    private String findOktaUserId(final String login) {
        final String path;
        try {
            path = "/api/v1/users/" + URLEncoder.encode(login, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            // Should never happen
            throw new IllegalStateException(e);
        }

        final HttpResponse<InputStream> oktaRawResponse = doGetRequest(path);
        try {
            final Map oktaResponse = mapper.readValue(oktaRawResponse.body(), Map.class);
            return (String) oktaResponse.get("id");
        } catch (final IOException e) {
            log.warn("Unable to read response from Okta");
            throw new AuthorizationException(e);
        }
    }

    private Set<String> findOktaGroupsForUser(final String userId) {
        final String path = "/api/v1/users/" + userId + "/groups";
        final HttpResponse<InputStream> response = doGetRequest(path);
        return getGroups(response);
    }

    private HttpResponse<InputStream> doGetRequest(final String path) {
        final HttpRequest request = HttpRequest.newBuilder()
                                               .uri(URI.create(securityConfig.getShiroOktaUrl() + path))
                                               .header("User-Agent", USER_AGENT)
                                               .header("Authorization", "SSWS " + securityConfig.getShiroOktaAPIToken())
                                               .timeout(Duration.of(DEFAULT_TIMEOUT_SECS, ChronoUnit.SECONDS))
                                               .build();

        final HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (final Exception e) {
            log.warn("Error while connecting to Auth0", e);
            throw new AuthenticationException(e);
        }

        return response;
    }

    private Set<String> getGroups(final HttpResponse<InputStream> oktaRawResponse) {
        try {
            final List<Map> oktaResponse = mapper.readValue(oktaRawResponse.body(), new TypeReference<List<Map>>() {});
            final Set<String> groups = new HashSet<String>();
            for (final Map group : oktaResponse) {
                final Object groupProfile = group.get("profile");
                if (groupProfile != null && groupProfile instanceof Map) {
                    groups.add((String) ((Map) groupProfile).get("name"));
                }
            }
            return groups;
        } catch (final IOException e) {
            log.warn("Unable to read response from Okta");
            throw new AuthorizationException(e);
        }
    }

    private Set<String> groupsPermissions(final Iterable<String> groups) {
        final Set<String> permissions = new HashSet<String>();
        for (final String group : groups) {
            final Collection<String> permissionsForGroup = permissionsByGroup.get(group);
            if (permissionsForGroup != null) {
                permissions.addAll(permissionsForGroup);
            }
        }
        return permissions;
    }
}
