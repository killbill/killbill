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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.subject.Subject;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.security.Logical;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;

public class DefaultSecurityApi implements SecurityApi {

    private static final String[] allPermissions = new String[Permission.values().length];

    @Override
    public void login(final Object principal, final Object credentials) {
        final Subject currentUser = SecurityUtils.getSubject();

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
}
