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

package com.ning.billing.util.security.api;

import java.util.List;
import java.util.Set;

import com.ning.billing.security.Logical;
import com.ning.billing.security.Permission;
import com.ning.billing.security.api.SecurityApi;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.collect.ImmutableSet;

public class NoOpSecurityApi implements SecurityApi {

    @Override
    public Set<Permission> getCurrentUserPermissions(final TenantContext context) {
        return ImmutableSet.<Permission>copyOf(Permission.values());
    }

    @Override
    public void checkCurrentUserPermissions(final List<Permission> permissions, final Logical logical, final TenantContext context) throws SecurityException {
        // No-Op
    }
}
