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

import java.net.URI;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.clock.Clock;
import org.killbill.billing.jaxrs.json.TenantJson;
import org.killbill.billing.jaxrs.json.TenantKeyJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantData;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.TENANTS_PATH)
@Api(value = JaxrsResource.TENANTS_PATH, description = "Operations on tenants")
public class TenantResource extends JaxRsResourceBase {

    private final TenantUserApi tenantApi;

    @Inject
    public TenantResource(final TenantUserApi tenantApi,
                          final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final AccountUserApi accountUserApi,
                          final PaymentApi paymentApi,
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
        this.tenantApi = tenantApi;
    }

    @Timed
    @GET
    @Path("/{tenantId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a tenant by id", response = TenantJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied"),
                           @ApiResponse(code = 404, message = "Tenant not found")})
    public Response getTenant(@PathParam("tenantId") final String tenantId) throws TenantApiException {
        final Tenant tenant = tenantApi.getTenantById(UUID.fromString(tenantId));
        return Response.status(Status.OK).entity(new TenantJson(tenant)).build();
    }

    @Timed
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a tenant by its API key", response = TenantJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Tenant not found")})
    public Response getTenantByApiKey(@QueryParam(QUERY_API_KEY) final String externalKey) throws TenantApiException {
        final Tenant tenant = tenantApi.getTenantByApiKey(externalKey);
        return Response.status(Status.OK).entity(new TenantJson(tenant)).build();
    }

    @Timed
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a tenant")
    @ApiResponses(value = {@ApiResponse(code = 500, message = "Tenant already exists")})
    public Response createTenant(final TenantJson json,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws TenantApiException {
        verifyNonNullOrEmpty(json, "TenantJson body should be specified");
        verifyNonNullOrEmpty(json.getApiKey(), "TenantJson apiKey needs to be set",
                             json.getApiSecret(), "TenantJson apiSecret needs to be set");

        final TenantData data = json.toTenantData();
        final Tenant tenant = tenantApi.createTenant(data, context.createContext(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(uriInfo, TenantResource.class, "getTenant", tenant.getId());
    }

    @Timed
    @POST
    @Path("/" + REGISTER_NOTIFICATION_CALLBACK)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a push notification")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response registerPushNotificationCallback(@PathParam("tenantId") final String tenantId,
                                                     @QueryParam(QUERY_NOTIFICATION_CALLBACK) final String notificationCallback,
                                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                     @HeaderParam(HDR_REASON) final String reason,
                                                     @HeaderParam(HDR_COMMENT) final String comment,
                                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        tenantApi.addTenantKeyValue(TenantKey.PUSH_NOTIFICATION_CB.toString(), notificationCallback, callContext);
        final URI uri = UriBuilder.fromResource(TenantResource.class).path(TenantResource.class, "getPushNotificationCallbacks").build();
        return Response.created(uri).build();
    }

    @Timed
    @GET
    @Path("/" + REGISTER_NOTIFICATION_CALLBACK)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a push notification", response = TenantKeyJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response getPushNotificationCallbacks(@javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {

        final TenantContext tenatContext = context.createContext(request);
        final List<String> values = tenantApi.getTenantValueForKey(TenantKey.PUSH_NOTIFICATION_CB.toString(), tenatContext);
        final TenantKeyJson result = new TenantKeyJson(TenantKey.PUSH_NOTIFICATION_CB.toString(), values);
        return Response.status(Status.OK).entity(result).build();
    }

    @Timed
    @DELETE
    @Path("/REGISTER_NOTIFICATION_CALLBACK")
    @ApiOperation(value = "Delete a push notification")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response deletePushNotificationCallbacks(@PathParam("tenantId") final String tenantId,
                                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                    @HeaderParam(HDR_REASON) final String reason,
                                                    @HeaderParam(HDR_COMMENT) final String comment,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        tenantApi.deleteTenantKey(TenantKey.PUSH_NOTIFICATION_CB.toString(), callContext);
        return Response.status(Status.OK).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.TENANT;
    }
}
