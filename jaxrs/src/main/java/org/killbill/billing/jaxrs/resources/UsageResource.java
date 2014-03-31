/*
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.resources;

import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.UsageJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;

import com.google.inject.Singleton;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.USAGES_PATH)
public class UsageResource extends JaxRsResourceBase {

    private final UsageUserApi usageUserApi;

    @Inject
    public UsageResource(final JaxrsUriBuilder uriBuilder,
                         final TagUserApi tagUserApi,
                         final CustomFieldUserApi customFieldUserApi,
                         final AuditUserApi auditUserApi,
                         final AccountUserApi accountUserApi,
                         final UsageUserApi usageUserApi,
                         final Clock clock,
                         final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.usageUserApi = usageUserApi;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response recordUsage(final UsageJson json,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo)  {

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        usageUserApi.recordRolledUpUsage(UUID.fromString(json.getSubscriptionId()), json.getUnitType(), json.getStartTime(), json.getEndTime(), json.getAmount(), callContext);
        return Response.status(Status.CREATED).build();
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/{unitType}")
    @Produces(APPLICATION_JSON)
    public Response getUsage(@PathParam("subscriptionId") final String subscriptionId,
                               @PathParam("unitType") final String unitType,
                               @QueryParam(QUERY_START_TIME) final String startTime,
                               @QueryParam(QUERY_END_TIME) final String endTime,
                               @javax.ws.rs.core.Context final HttpServletRequest request)  {

        final TenantContext tenantContext = context.createContext(request);

        final DateTime usageStartTime = DATE_TIME_FORMATTER.parseDateTime(startTime);
        final DateTime usageEndTime = DATE_TIME_FORMATTER.parseDateTime(endTime);

        final RolledUpUsage usage = usageUserApi.getUsageForSubscription(UUID.fromString(subscriptionId), unitType, usageStartTime, usageEndTime, tenantContext);
        final UsageJson result = new UsageJson(usage);
        return Response.status(Status.OK).entity(result).build();
    }

}

