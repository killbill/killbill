package org.killbill.billing.util.security;

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

import javax.inject.Inject;

import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.aop.AuthorizingAnnotationHandler;

import org.killbill.billing.security.Permission;
import org.killbill.billing.security.RequiresPermissions;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.callcontext.DefaultTenantContext;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.collect.ImmutableList;

public class PermissionAnnotationHandler extends AuthorizingAnnotationHandler {

    private final TenantContext context = new DefaultTenantContext(null, null);

    @Inject
    SecurityApi securityApi;

    public PermissionAnnotationHandler() {
        super(RequiresPermissions.class);
    }

    public void assertAuthorized(final Annotation annotation) throws AuthorizationException {
        if (!(annotation instanceof RequiresPermissions)) {
            return;
        }

        final RequiresPermissions requiresPermissions = (RequiresPermissions) annotation;
        try {
            securityApi.checkCurrentUserPermissions(ImmutableList.<Permission>copyOf(requiresPermissions.value()), requiresPermissions.logical(), context);
        } catch (SecurityApiException e) {
            if (e.getCause() != null && e.getCause() instanceof AuthorizationException) {
                throw (AuthorizationException) e.getCause();
            } else if (e.getCause() != null) {
                throw new AuthorizationException(e.getCause());
            } else {
                throw new AuthorizationException(e);
            }
        }
    }
}
