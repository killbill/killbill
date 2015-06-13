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

package org.killbill.billing.jaxrs.util;

import javax.servlet.ServletRequest;

import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallContextFactory;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

public class Context {

    private final CallOrigin origin;
    private final UserType userType;
    final CallContextFactory contextFactory;

    @Inject
    public Context(final CallContextFactory factory) {
        this.origin = CallOrigin.EXTERNAL;
        this.userType = UserType.CUSTOMER;
        this.contextFactory = factory;
    }

    public CallContext createContext(final String createdBy, final String reason, final String comment, final ServletRequest request)
            throws IllegalArgumentException {
        try {
            Preconditions.checkNotNull(createdBy, String.format("Header %s needs to be set", JaxrsResource.HDR_CREATED_BY));
            final Tenant tenant = getTenantFromRequest(request);
            return contextFactory.createCallContext(tenant == null ? null : tenant.getId(), createdBy, origin, userType, reason,
                                                    comment, UUIDs.randomUUID());
        } catch (final NullPointerException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public TenantContext createContext(final ServletRequest request) {
        final Tenant tenant = getTenantFromRequest(request);
        if (tenant == null) {
            // Multi-tenancy may not have been configured - default to "default" tenant (see InternalCallContextFactory)
            return contextFactory.createTenantContext(null);
        } else {
            return contextFactory.createTenantContext(tenant.getId());
        }
    }

    private Tenant getTenantFromRequest(final ServletRequest request) {
        // See org.killbill.billing.server.security.TenantFilter
        final Object tenantObject = request.getAttribute("killbill_tenant");
        if (tenantObject == null) {
            return null;
        } else {
            return (Tenant) tenantObject;
        }
    }
}
