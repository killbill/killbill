package com.ning.billing.util.security;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.lang.annotation.Annotation;

import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.aop.AuthorizingAnnotationHandler;
import org.apache.shiro.subject.Subject;

import com.ning.billing.security.Logical;
import com.ning.billing.security.RequiresPermissions;

public class PermissionAnnotationHandler extends AuthorizingAnnotationHandler {

    public PermissionAnnotationHandler() {
        super(RequiresPermissions.class);
    }

    public void assertAuthorized(final Annotation annotation) throws AuthorizationException {
        if (!(annotation instanceof RequiresPermissions)) {
            return;
        }

        final RequiresPermissions requiresPermissions = (RequiresPermissions) annotation;
        final String[] permissions = new String[requiresPermissions.value().length];
        for (int i = 0; i < permissions.length; i++) {
            permissions[i] = requiresPermissions.value()[i].toString();
        }

        final Subject subject = getSubject();
        if (permissions.length == 1) {
            subject.checkPermission(permissions[0]);
        } else if (Logical.AND.equals(requiresPermissions.logical())) {
            subject.checkPermissions(permissions);
        } else if (Logical.OR.equals(requiresPermissions.logical())) {
            boolean hasAtLeastOnePermission = false;
            for (final String permission : permissions) {
                if (subject.isPermitted(permission)) {
                    hasAtLeastOnePermission = true;
                    break;
                }
            }

            // Cause the exception if none match
            if (!hasAtLeastOnePermission) {
                getSubject().checkPermission(permissions[0]);
            }
        }
    }
}
