/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.util;

import java.util.UUID;

import javax.servlet.ServletRequest;

import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallContextFactory;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.commons.request.Request;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

public class Context {

    private final CallOrigin origin;
    private final UserType userType;
    private final CallContextFactory contextFactory;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public Context(final CallContextFactory factory, final InternalCallContextFactory internalCallContextFactory) {
        this.origin = CallOrigin.EXTERNAL;
        this.userType = UserType.CUSTOMER;
        this.contextFactory = factory;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public CallContext createCallContextNoAccountId(final String createdBy, final String reason, final String comment, final ServletRequest request)
            throws IllegalArgumentException {
        return createCallContextWithAccountId(null, createdBy, reason, comment, request);
    }

    public CallContext createCallContextWithAccountId(final UUID accountId, final String createdBy, final String reason, final String comment, final ServletRequest request)
            throws IllegalArgumentException {
        try {
            Preconditions.checkNotNull(createdBy, String.format("Header %s needs to be set", JaxrsResource.HDR_CREATED_BY));
            final Tenant tenant = getTenantFromRequest(request);
            final UUID tenantId = tenant == null ? null : tenant.getId();
            final CallContext callContext = contextFactory.createCallContext(accountId, tenantId, createdBy, origin, userType, reason,
                                                                             comment, getOrCreateUserToken());

            populateMDCContext(callContext);

            return callContext;
        } catch (final NullPointerException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public TenantContext createTenantContextNoAccountId(final ServletRequest request) {
        return createTenantContextWithAccountId(null, request);
    }

    public TenantContext createTenantContextWithAccountId(final UUID accountId, final ServletRequest request) {
        final TenantContext tenantContext;

        final Tenant tenant = getTenantFromRequest(request);
        if (tenant == null) {
            // Multi-tenancy may not have been configured - default to "default" tenant (see InternalCallContextFactory)
            tenantContext = contextFactory.createTenantContext(accountId, null);
        } else {
            tenantContext = contextFactory.createTenantContext(accountId, tenant.getId());
        }

        populateMDCContext(tenantContext);

        return tenantContext;
    }

    // Use REQUEST_ID_HEADER if this is provided and looks like a UUID, if not allocate a random one.
    public static  UUID getOrCreateUserToken() {
        UUID userToken;
        if (Request.getPerThreadRequestData().getRequestId() != null) {
            try {
                userToken = UUID.fromString(Request.getPerThreadRequestData().getRequestId());
            } catch (final IllegalArgumentException ignored) {
                userToken = UUIDs.randomUUID();
            }
        } else {
            userToken = UUIDs.randomUUID();
        }
        return userToken;
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

    private void populateMDCContext(final CallContext callContext) {
        // InternalCallContextFactory will do it for us
        internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(callContext);
    }

    private void populateMDCContext(final TenantContext tenantContext) {
        // InternalCallContextFactory will do it for us
        internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(tenantContext);
    }
}
