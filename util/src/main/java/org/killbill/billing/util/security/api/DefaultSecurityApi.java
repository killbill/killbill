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

package org.killbill.billing.util.security.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.security.Logical;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.security.shiro.dao.RolesPermissionsModelDao;
import org.killbill.billing.util.security.shiro.dao.UserDao;
import org.killbill.billing.util.security.shiro.dao.UserRolesModelDao;
import org.killbill.billing.util.security.shiro.realm.KillBillJdbcRealm;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DefaultSecurityApi implements SecurityApi {

    private static final String[] allPermissions = new String[Permission.values().length];

    private final UserDao userDao;

    @Inject
    public DefaultSecurityApi(final UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public synchronized void login(final Object principal, final Object credentials) {
        final Subject currentUser = SecurityUtils.getSubject();

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
        currentUser.logout();
    }

    @Override
    public Set<Permission> getCurrentUserPermissions(final TenantContext context) {
        final Permission[] killbillPermissions = Permission.values();
        final String[] killbillPermissionsString = getAllPermissionsAsStrings();

        final Subject subject = SecurityUtils.getSubject();
        // Bulk (optimized) call
        final boolean[] permissions = subject.isPermitted(killbillPermissionsString);

        final Set<Permission> userPermissions = new HashSet<Permission>();
        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i]) {
                userPermissions.add(killbillPermissions[i]);
            }
        }

        return userPermissions;
    }

    @Override
    public void checkCurrentUserPermissions(final List<Permission> permissions, final Logical logical, final TenantContext context) throws SecurityApiException {
        final String[] permissionsString = Lists.<Permission, String>transform(permissions, Functions.toStringFunction()).toArray(new String[permissions.size()]);

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
        } catch (AuthorizationException e) {
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
    }

    @Override
    public List<String> getUserRoles(final String username, final TenantContext tenantContext) {
        final List<UserRolesModelDao> permissionsModelDao = userDao.getUserRoles(username);
        return ImmutableList.copyOf(Iterables.transform(permissionsModelDao, new Function<UserRolesModelDao, String>() {
            @Nullable
            @Override
            public String apply(final UserRolesModelDao input) {
                return input.getRoleName();
            }
        }));
    }

    @Override
    public void addRoleDefinition(final String role, final List<String> permissions, final CallContext callContext) throws SecurityApiException {
        final List<String> sanitizedPermissions = sanitizeAndValidatePermissions(permissions);
        userDao.addRoleDefinition(role, sanitizedPermissions, callContext.getUserName());
    }

    @Override
    public List<String> getRoleDefinition(final String role, final TenantContext tenantContext) {
        final List<RolesPermissionsModelDao> permissionsModelDao = userDao.getRoleDefinition(role);
        return ImmutableList.copyOf(Iterables.transform(permissionsModelDao, new Function<RolesPermissionsModelDao, String>() {
            @Nullable
            @Override
            public String apply(final RolesPermissionsModelDao input) {
                return input.getPermission();
            }
        }));
    }

    private List<String> sanitizeAndValidatePermissions(final List<String> permissions) throws SecurityApiException {

        if (permissions == null || permissions.isEmpty()) {
            throw new SecurityApiException(ErrorCode.SECURITY_INVALID_PERMISSIONS, "null");
        }

        final HashMap<String, Set<String>> groupToValues = new HashMap<String, Set<String>>();
        for (final String curPerm : permissions) {

            if (curPerm.equals("*")) {
                return ImmutableList.of("*");
            }

            final String[] permissionParts = curPerm.split(":");
            if (permissionParts.length != 1 && permissionParts.length != 2) {
                throw new SecurityApiException(ErrorCode.SECURITY_INVALID_PERMISSIONS, curPerm);
            }

            boolean resolved = false;
            for (final Permission cur : Permission.values()) {
                if (resolved) {
                    break;
                }
                if (!cur.getGroup().equals(permissionParts[0])) {
                    continue;
                }

                Set<String> groupPermissions = groupToValues.get(permissionParts[0]);
                if (groupPermissions == null) {
                    groupPermissions = new HashSet<String>();
                    groupToValues.put(permissionParts[0], groupPermissions);
                }
                if (permissionParts.length == 1 || permissionParts[1].equals("*")) {
                    groupPermissions.clear();
                    groupPermissions.add("*");
                    resolved = true;
                    break;
                }

                if (cur.getValue().equals(permissionParts[1])) {
                    groupPermissions.add(permissionParts[1]);
                    resolved = true;
                    break;
                }
            }
            if (!resolved) {
                throw new SecurityApiException(ErrorCode.SECURITY_INVALID_PERMISSIONS, curPerm);
            }
        }

        final List<String> sanitizedPermissions = new ArrayList<String>();
        for (String group : groupToValues.keySet()) {
            final Set<String> groupPermissions = groupToValues.get(group);
            for (String value : groupPermissions) {
                sanitizedPermissions.add(String.format("%s:%s", group, value));
            }
        }
        return sanitizedPermissions;
    }

    private String[] getAllPermissionsAsStrings() {
        if (allPermissions[0] == null) {
            synchronized (allPermissions) {
                if (allPermissions[0] == null) {
                    final Permission[] killbillPermissions = Permission.values();
                    for (int i = 0; i < killbillPermissions.length; i++) {
                        allPermissions[i] = killbillPermissions[i].toString();
                    }
                }
            }
        }
        return allPermissions;
    }

    private void invalidateJDBCAuthorizationCache(final String username) {
        final Collection<Realm> realms = ((DefaultSecurityManager) SecurityUtils.getSecurityManager()).getRealms();
        final KillBillJdbcRealm killBillJdbcRealm = (KillBillJdbcRealm) Iterables.tryFind(realms, new Predicate<Realm>() {
            @Override
            public boolean apply(@Nullable final Realm input) {
                return (input instanceof KillBillJdbcRealm);
            }
        }).orNull();

        if (killBillJdbcRealm != null) {
            SimplePrincipalCollection principals = new SimplePrincipalCollection();
            principals.add(username, killBillJdbcRealm.getName());
            killBillJdbcRealm.clearCachedAuthorizationInfo(principals);
        }
    }
}
