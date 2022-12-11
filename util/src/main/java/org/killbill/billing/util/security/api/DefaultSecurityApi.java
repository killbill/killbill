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

package org.killbill.billing.util.security.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.session.UnknownSessionException;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.security.Logical;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.commons.utils.Strings;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.security.shiro.dao.UserDao;
import org.killbill.billing.util.security.shiro.realm.KillBillJdbcRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSecurityApi implements SecurityApi {

    // Custom Realm implementors are encouraged to enable DEBUG level logging for development
    private static final Logger logger = LoggerFactory.getLogger(DefaultSecurityApi.class);

    private final UserDao userDao;
    private final Set<Realm> realms;
    private final Map<Realm, Method> getAuthorizationInfoMethods = new HashMap<Realm, Method>();

    @Inject
    public DefaultSecurityApi(final UserDao userDao, final Set<Realm> realms) {
        this.userDao = userDao;
        this.realms = realms;
        buildGetAuthorizationInfoMethods();
    }

    @Override
    public synchronized void login(final Object principal, final Object credentials) {

        final Subject currentUser = SecurityUtils.getSubject();
        if (currentUser.isAuthenticated()) {
            try {
                logout();
            } catch (final UnknownSessionException swallowme) {
            }
        }

        // Workaround for https://issues.apache.org/jira/browse/SHIRO-510
        // TODO Not sure if it's a good fix?
        if (principal.equals(currentUser.getPrincipal()) &&
            currentUser.isAuthenticated()) {
            return;
        }

        // UsernamePasswordToken is hardcoded in AuthenticatingRealm
        if (principal instanceof String && credentials instanceof String) {
            currentUser.login(new UsernamePasswordToken((String) principal, (String) credentials));
        } else if (principal instanceof String && credentials instanceof char[]) {
            currentUser.login(new UsernamePasswordToken((String) principal, (char[]) credentials));
        } else {
            currentUser.login(new AuthenticationToken() {
                @Override
                public Object getPrincipal() {
                    return principal;
                }

                @Override
                public Object getCredentials() {
                    return credentials;
                }
            });
        }
    }

    @Override
    public void logout() {
        final Subject currentUser = SecurityUtils.getSubject();
        if (currentUser != null && currentUser.isAuthenticated()) {
            currentUser.logout();
        }
    }

    @Override
    public boolean isSubjectAuthenticated() {
        return SecurityUtils.getSubject().isAuthenticated();
    }

    @Override
    public Set<String> getCurrentUserPermissions(final TenantContext context) {
        final Subject subject = SecurityUtils.getSubject();

        final Set<String> allPermissions = new HashSet<>();
        for (final Entry<Realm, Method> realmAndMethod : getAuthorizationInfoMethods.entrySet()) {
            try {
                final AuthorizationInfo authorizationInfo = (AuthorizationInfo) realmAndMethod.getValue().invoke(realmAndMethod.getKey(), subject.getPrincipals());
                if (authorizationInfo == null) {
                    logger.debug("No AuthorizationInfo returned from Realm {}", realmAndMethod.getKey());
                } else {
                    final Collection<org.apache.shiro.authz.Permission> realmObjectPermissions = authorizationInfo.getObjectPermissions();
                    if (realmObjectPermissions == null) {
                        logger.debug("No ObjectPermissions returned from Realm {}", realmAndMethod.getKey());
                    } else {
                        for (final org.apache.shiro.authz.Permission realmPermission : realmObjectPermissions) {
                            // Note: this assumes custom realms return something sensible here
                            final String realmPermissionAsString = realmPermission.toString();
                            if (realmPermissionAsString == null) {
                                logger.debug("Null ObjectPermission#toString returned from Realm {}", realmAndMethod.getKey());
                            } else {
                                allPermissions.add(realmPermissionAsString);
                            }
                        }
                    }
                    // The Javadoc says that getObjectPermissions should contain the results from getStringPermissions,
                    // but this is incorrect in practice (JdbcRealm for instance)
                    final Collection<String> realmStringPermissions = authorizationInfo.getStringPermissions();
                    if (realmStringPermissions == null) {
                        logger.debug("No StringPermissions returned from Realm {}", realmAndMethod.getKey());
                    } else {
                        allPermissions.addAll(authorizationInfo.getStringPermissions());
                    }
                }
            } catch (final IllegalAccessException e) {
                // Ignore
                logger.debug("Unable to retrieve permissions for Realm {}", realmAndMethod.getKey(), e);
            } catch (final InvocationTargetException e) {
                // Ignore
                logger.debug("Unable to retrieve permissions for Realm {}", realmAndMethod.getKey(), e);
            } catch (final RuntimeException e) {
                // Ignore
                logger.debug("Unable to retrieve permissions for Realm {}", realmAndMethod.getKey(), e);
            }
        }

        return allPermissions;
    }

    @Override
    public void checkCurrentUserPermissions(final List<Permission> permissions, final Logical logical, final TenantContext context) throws SecurityApiException {
        final String[] permissionsString = permissions.stream().map(Permission::toString).toArray(String[]::new);

        try {
            final Subject subject = SecurityUtils.getSubject();
            if (permissionsString.length == 1) {
                subject.checkPermission(permissionsString[0]);
            } else if (Logical.AND.equals(logical)) {
                subject.checkPermissions(permissionsString);
            } else if (Logical.OR.equals(logical)) {
                boolean hasAtLeastOnePermission = false;
                for (final String permission : permissionsString) {
                    if (subject.isPermitted(permission)) {
                        hasAtLeastOnePermission = true;
                        break;
                    }
                }

                // Cause the exception if none match
                if (!hasAtLeastOnePermission) {
                    subject.checkPermission(permissionsString[0]);
                }
            }
        } catch (final AuthorizationException e) {
            throw new SecurityApiException(e, ErrorCode.SECURITY_NOT_ENOUGH_PERMISSIONS);
        }
    }

    @Override
    public void addUserRoles(final String username, final String password, final List<String> roles, final CallContext callContext) throws SecurityApiException {
        userDao.insertUser(username, password, roles, callContext.getUserName());
    }

    @Override
    public void updateUserPassword(final String username, final String password, final CallContext callContext) throws SecurityApiException {
        userDao.updateUserPassword(username, password, callContext.getUserName());
    }

    @Override
    public void updateUserRoles(final String username, final List<String> roles, final CallContext callContext) throws SecurityApiException {
        userDao.updateUserRoles(username, roles, callContext.getUserName());
        invalidateJDBCAuthorizationCache(username);
    }

    @Override
    public void invalidateUser(final String username, final CallContext callContext) throws SecurityApiException {
        userDao.invalidateUser(username, callContext.getUserName());
        // Invalidate the JSESSIONID
        logout();
    }

    @Override
    public List<String> getUserRoles(final String username, final TenantContext tenantContext) throws SecurityApiException {
        return userDao.getUserRoles(username).stream()
                .map(input -> input == null ? null : input.getRoleName())
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void addRoleDefinition(final String role, final List<String> permissions, final CallContext callContext) throws SecurityApiException {
        final List<String> sanitizedPermissions = sanitizePermissions(permissions);
        userDao.addRoleDefinition(role, sanitizedPermissions, callContext.getUserName());
    }

    @Override
    public void updateRoleDefinition(final String role, final List<String> permissions, final CallContext callContext) throws SecurityApiException {
        final List<String> sanitizedPermissions = sanitizePermissions(permissions);
        userDao.updateRoleDefinition(role, sanitizedPermissions, callContext.getUserName());
    }

    @Override
    public List<String> getRoleDefinition(final String role, final TenantContext tenantContext) {
        return userDao.getRoleDefinition(role).stream()
                .map(input -> input == null ? null : input.getPermission())
                .collect(Collectors.toUnmodifiableList());
    }

    private List<String> sanitizePermissions(final List<String> permissionsRaw) throws SecurityApiException {
        if (permissionsRaw == null) {
            return Collections.emptyList();
        }
        final Collection<String> permissions = permissionsRaw.stream()
                .filter(permission -> permission != null && !permission.isEmpty())
                .collect(Collectors.toUnmodifiableList());

        final Map<String, Set<String>> groupToValues = new HashMap<>();
        for (final String curPerm : permissions) {
            if ("*".equals(curPerm)) {
                return List.of("*");
            }

            final String[] permissionParts = curPerm.split(":");
            if (permissionParts.length != 1 && permissionParts.length != 2) {
                throw new SecurityApiException(ErrorCode.SECURITY_INVALID_PERMISSIONS, curPerm);
            }

            Set<String> groupPermissions = groupToValues.get(permissionParts[0]);
            if (groupPermissions == null) {
                groupPermissions = new HashSet<String>();
                groupToValues.put(permissionParts[0], groupPermissions);
            }
            if (permissionParts.length == 1 || "*".equals(permissionParts[1]) || Strings.emptyToNull(permissionParts[1]) == null) {
                groupPermissions.clear();
                groupPermissions.add("*");
            } else {
                groupPermissions.add(permissionParts[1]);
            }
        }

        final List<String> expandedPermissions = new ArrayList<>();
        for (final String group : groupToValues.keySet()) {
            final Set<String> groupPermissions = groupToValues.get(group);
            for (final String value : groupPermissions) {
                expandedPermissions.add(String.format("%s:%s", group, value));
            }
        }
        return expandedPermissions;
    }

    private void invalidateJDBCAuthorizationCache(final String username) {
        final Collection<Realm> realms = ((DefaultSecurityManager) SecurityUtils.getSecurityManager()).getRealms();
        final KillBillJdbcRealm killBillJdbcRealm = (KillBillJdbcRealm) realms.stream()
                .filter(realm -> (realm instanceof KillBillJdbcRealm))
                .findFirst()
                .orElse(null);

        if (killBillJdbcRealm != null) {
            final SimplePrincipalCollection principals = new SimplePrincipalCollection();
            principals.add(username, killBillJdbcRealm.getName());
            killBillJdbcRealm.clearCachedAuthorizationInfo(principals);
        }
    }

    private void buildGetAuthorizationInfoMethods() {
        for (final Realm realm : realms) {
            if (!(realm instanceof AuthorizingRealm)) {
                logger.debug("Unable to retrieve getAuthorizationInfo method from Realm {}: not an AuthorizingRealm", realm);
                continue;
            }

            Method getAuthorizationInfoMethod = null;
            Class<?> clazz = realm.getClass();
            while (clazz != null) {
                final Method[] methods = clazz.getDeclaredMethods();
                for (final Method method : methods) {
                    if ("getAuthorizationInfo".equals(method.getName())) {
                        getAuthorizationInfoMethod = method;
                        getAuthorizationInfoMethod.setAccessible(true);
                        break;
                    }
                }
                clazz = clazz.getSuperclass();
            }
            if (getAuthorizationInfoMethod == null) {
                logger.debug("Unable to retrieve getAuthorizationInfo method from Realm {}", realm);
                continue;
            }

            getAuthorizationInfoMethods.put(realm, getAuthorizationInfoMethod);
        }
    }
}
