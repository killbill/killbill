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

package org.killbill.billing.jaxrs.resources;

import java.util.List;
import java.util.Set;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.clock.Clock;
import org.killbill.billing.jaxrs.json.SubjectJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;

import com.codahale.metrics.annotation.Timed;
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
                            final PaymentApi paymentApi,
                            final Clock clock,
                            final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
        this.securityApi = securityApi;
    }

    //@Timed
    @GET
    @Path("/permissions")
    @Produces(APPLICATION_JSON)
    public Response getCurrentUserPermissions(@javax.ws.rs.core.Context final HttpServletRequest request) {
        final Set<Permission> permissions = securityApi.getCurrentUserPermissions(context.createContext(request));
        final List<String> json = ImmutableList.<String>copyOf(Iterables.<Permission, String>transform(permissions, Functions.toStringFunction()));
        return Response.status(Status.OK).entity(json).build();
    }

    //@Timed
    @GET
    @Path("/subject")
    @Produces(APPLICATION_JSON)
    public Response getCurrentUserSubject(@javax.ws.rs.core.Context final HttpServletRequest request) {
        final Subject subject = SecurityUtils.getSubject();
        final SubjectJson subjectJson = new SubjectJson(subject);
        return Response.status(Status.OK).entity(subjectJson).build();
    }
}
