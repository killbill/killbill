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

package com.ning.billing.jaxrs.resources;

import java.util.List;
import java.util.Set;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.clock.Clock;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.security.Permission;
import com.ning.billing.security.api.SecurityApi;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.SECURITY_PATH)
public class SecurityResource extends JaxRsResourceBase {

    private final SecurityApi securityApi;

    @Inject
    public SecurityResource(final SecurityApi securityApi,
                            final JaxrsUriBuilder uriBuilder,
                            final TagUserApi tagUserApi,
                            final CustomFieldUserApi customFieldUserApi,
                            final AuditUserApi auditUserApi,
                            final AccountUserApi accountUserApi,
                            final Clock clock,
                            final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.securityApi = securityApi;
    }

    @GET
    @Path("/permissions")
    @Produces(APPLICATION_JSON)
    public Response getCurrentUserPermissions(@javax.ws.rs.core.Context final HttpServletRequest request)  {
        final Set<Permission> permissions = securityApi.getCurrentUserPermissions(context.createContext(request));
        final List<String> json = ImmutableList.<String>copyOf(Iterables.<Permission, String>transform(permissions, Functions.toStringFunction()));
        return Response.status(Status.OK).entity(json).build();
    }

}
