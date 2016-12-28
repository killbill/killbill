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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
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

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.jaxrs.json.TenantJson;
import org.killbill.billing.jaxrs.json.TenantKeyJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantData;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Singleton
@Path(JaxrsResource.TENANTS_PATH)
@Api(value = JaxrsResource.TENANTS_PATH, description = "Operations on tenants")
public class TenantResource extends JaxRsResourceBase {

    private final TenantUserApi tenantApi;
    private final CatalogUserApi catalogUserApi;

    @Inject
    public TenantResource(final TenantUserApi tenantApi,
                          final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final AccountUserApi accountUserApi,
                          final PaymentApi paymentApi,
                          final CatalogUserApi catalogUserApi,
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, null, clock, context);
        this.tenantApi = tenantApi;
        this.catalogUserApi = catalogUserApi;
    }

    @TimedResource
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

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a tenant by its API key", response = TenantJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Tenant not found")})
    public Response getTenantByApiKey(@QueryParam(QUERY_API_KEY) final String externalKey) throws TenantApiException {
        final Tenant tenant = tenantApi.getTenantByApiKey(externalKey);
        return Response.status(Status.OK).entity(new TenantJson(tenant)).build();
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a tenant")
    @ApiResponses(value = {@ApiResponse(code = 500, message = "Tenant already exists")})
    public Response createTenant(final TenantJson json,
                                 @QueryParam(QUERY_TENANT_USE_GLOBAL_DEFAULT) @DefaultValue("true") final Boolean useGlobalDefault,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws TenantApiException, CatalogApiException {
        verifyNonNullOrEmpty(json, "TenantJson body should be specified");
        verifyNonNullOrEmpty(json.getApiKey(), "TenantJson apiKey needs to be set",
                             json.getApiSecret(), "TenantJson apiSecret needs to be set");

        final TenantData data = json.toTenantData();
        final Tenant tenant = tenantApi.createTenant(data, context.createContext(createdBy, reason, comment, request));
        if (!useGlobalDefault) {
            final CallContext callContext = new DefaultCallContext(tenant.getId(), createdBy, CallOrigin.EXTERNAL,
                                                                   UserType.CUSTOMER, Context.getOrCreateUserToken(), clock);
            catalogUserApi.createDefaultEmptyCatalog(clock.getUTCNow(),callContext);
        }
        return uriBuilder.buildResponse(uriInfo, TenantResource.class, "getTenant", tenant.getId(), request);
    }

    @TimedResource
    @POST
    @Path("/" + REGISTER_NOTIFICATION_CALLBACK)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a push notification")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response registerPushNotificationCallback(@QueryParam(QUERY_NOTIFICATION_CALLBACK) final String notificationCallback,
                                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                     @HeaderParam(HDR_REASON) final String reason,
                                                     @HeaderParam(HDR_COMMENT) final String comment,
                                                     @javax.ws.rs.core.Context final HttpServletRequest request,
                                                     @javax.ws.rs.core.Context final UriInfo uriInfo) throws TenantApiException {
        return insertTenantKey(TenantKey.PUSH_NOTIFICATION_CB,  null,  notificationCallback, uriInfo,"getPushNotificationCallbacks", createdBy, reason, comment, request);
    }

    @TimedResource
    @GET
    @Path("/" + REGISTER_NOTIFICATION_CALLBACK)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a push notification", response = TenantKeyJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response getPushNotificationCallbacks(@javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        return getTenantKey(TenantKey.PUSH_NOTIFICATION_CB, null, request);
    }

    @TimedResource
    @DELETE
    @Path("/" + REGISTER_NOTIFICATION_CALLBACK)
    @ApiOperation(value = "Delete a push notification")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response deletePushNotificationCallbacks(@HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                    @HeaderParam(HDR_REASON) final String reason,
                                                    @HeaderParam(HDR_COMMENT) final String comment,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        return deleteTenantKey(TenantKey.PUSH_NOTIFICATION_CB, null, createdBy, reason, comment, request);
    }

    @TimedResource
    @POST
    @Path("/" + UPLOAD_PLUGIN_CONFIG + "/{pluginName:" + ANYTHING_PATTERN + "}")
    @Consumes(TEXT_PLAIN)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add a per tenant configuration for a plugin")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response uploadPluginConfiguration(final String pluginConfig,
                                              @PathParam("pluginName") final String pluginName,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @javax.ws.rs.core.Context final HttpServletRequest request,
                                              @javax.ws.rs.core.Context final UriInfo uriInfo) throws TenantApiException {
        return insertTenantKey(TenantKey.PLUGIN_CONFIG_, pluginName, pluginConfig, uriInfo, "getPluginConfiguration", createdBy, reason, comment, request);
    }



    @TimedResource
    @GET
    @Path("/" + UPLOAD_PLUGIN_CONFIG + "/{pluginName:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a per tenant configuration for a plugin", response = TenantKeyJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response getPluginConfiguration(@PathParam("pluginName") final String pluginName,
                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        return getTenantKey(TenantKey.PLUGIN_CONFIG_, pluginName, request);
    }

    @TimedResource
    @DELETE
    @Path("/" + UPLOAD_PLUGIN_CONFIG + "/{pluginName:" + ANYTHING_PATTERN + "}")
    @ApiOperation(value = "Delete a per tenant configuration for a plugin")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response deletePluginConfiguration(@PathParam("pluginName") final String pluginName,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        return deleteTenantKey(TenantKey.PLUGIN_CONFIG_, pluginName, createdBy, reason, comment, request);
    }


    @TimedResource
    @GET
    @Path("/" + UPLOAD_PER_TENANT_CONFIG + "/{keyPrefix:" + ANYTHING_PATTERN + "}" + "/" + SEARCH)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a per tenant key value based on key prefix", response = TenantKeyJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response getAllPluginConfiguration(@PathParam("keyPrefix") final String keyPrefix,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {

        final TenantContext tenantContext = context.createContext(request);
        final Map<String, List<String>> apiResult = tenantApi.searchTenantKeyValues(keyPrefix, tenantContext);
        final List<TenantKeyJson> result = new ArrayList<TenantKeyJson>();
        for (final String cur : apiResult.keySet()) {
            result.add(new TenantKeyJson(cur, apiResult.get(cur)));
        }
        return Response.status(Status.OK).entity(result).build();
    }


    @TimedResource
    @POST
    @Path("/" + UPLOAD_PER_TENANT_CONFIG)
    @Consumes(TEXT_PLAIN)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add a per tenant configuration (system properties)")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response uploadPerTenantConfiguration(final String perTenantConfig,
                                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                 @HeaderParam(HDR_REASON) final String reason,
                                                 @HeaderParam(HDR_COMMENT) final String comment,
                                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws TenantApiException {
        return insertTenantKey(TenantKey.PER_TENANT_CONFIG, null, perTenantConfig, uriInfo, "getPerTenantConfiguration", createdBy, reason, comment, request);
    }

    @TimedResource
    @GET
    @Path("/" + UPLOAD_PER_TENANT_CONFIG)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a per tenant configuration (system properties)", response = TenantKeyJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response getPerTenantConfiguration(@javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        return getTenantKey(TenantKey.PER_TENANT_CONFIG, null, request);
    }

    @TimedResource
    @DELETE
    @Path("/" + UPLOAD_PER_TENANT_CONFIG)
    @ApiOperation(value = "Delete a per tenant configuration (system properties)")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response deletePerTenantConfiguration(@HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        return deleteTenantKey(TenantKey.PER_TENANT_CONFIG, null, createdBy, reason, comment, request);
    }

    @TimedResource
    @POST
    @Path("/" + UPLOAD_PLUGIN_PAYMENT_STATE_MACHINE_CONFIG + "/{pluginName:" + ANYTHING_PATTERN + "}")
    @Consumes(TEXT_PLAIN)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add a per tenant payment state machine for a plugin")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response uploadPluginPaymentStateMachineConfig(final String paymentStateMachineConfig,
                                                          @PathParam("pluginName") final String pluginName,
                                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                          @HeaderParam(HDR_REASON) final String reason,
                                                          @HeaderParam(HDR_COMMENT) final String comment,
                                                          @javax.ws.rs.core.Context final HttpServletRequest request,
                                                          @javax.ws.rs.core.Context final UriInfo uriInfo) throws TenantApiException {
        return insertTenantKey(TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_, pluginName, paymentStateMachineConfig, uriInfo, "getPluginPaymentStateMachineConfig", createdBy, reason, comment, request);
    }

    @TimedResource
    @GET
    @Path("/" + UPLOAD_PLUGIN_PAYMENT_STATE_MACHINE_CONFIG + "/{pluginName:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a per tenant payment state machine for a plugin", response = TenantKeyJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response getPluginPaymentStateMachineConfig(@PathParam("pluginName") final String pluginName,
                                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        return getTenantKey(TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_, pluginName, request);
    }

    @TimedResource
    @DELETE
    @Path("/" + UPLOAD_PLUGIN_PAYMENT_STATE_MACHINE_CONFIG + "/{pluginName:" + ANYTHING_PATTERN + "}")
    @ApiOperation(value = "Delete a per tenant payment state machine for a plugin")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response deletePluginPaymentStateMachineConfig(@PathParam("pluginName") final String pluginName,
                                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                          @HeaderParam(HDR_REASON) final String reason,
                                                          @HeaderParam(HDR_COMMENT) final String comment,
                                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        return deleteTenantKey(TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_, pluginName, createdBy, reason, comment, request);
    }

    @TimedResource
    @POST
    @Path("/" + USER_KEY_VALUE + "/{keyName:" + ANYTHING_PATTERN + "}")
    @Consumes(TEXT_PLAIN)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add a per tenant user key/value")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response insertUserKeyValue(@PathParam("keyName") final String key,
                               final String value,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final HttpServletRequest request,
                               @javax.ws.rs.core.Context  final UriInfo uriInfo) throws TenantApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        tenantApi.addTenantKeyValue(key, value, callContext);
        return uriBuilder.buildResponse(uriInfo, TenantResource.class, "getUserKeyValue", key, request);
    }

    @TimedResource
    @GET
    @Path("/" + USER_KEY_VALUE + "/{keyName:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a per tenant user key/value", response = TenantKeyJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response getUserKeyValue(@PathParam("keyName") final String key,
                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        final TenantContext tenantContext = context.createContext(request);
        final List<String> values = tenantApi.getTenantValuesForKey(key, tenantContext);
        final TenantKeyJson result = new TenantKeyJson(key, values);
        return Response.status(Status.OK).entity(result).build();
    }


    @TimedResource
    @DELETE
    @Path("/" + USER_KEY_VALUE + "/{keyName:" + ANYTHING_PATTERN + "}")
    @ApiOperation(value = "Delete  a per tenant user key/value")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid tenantId supplied")})
    public Response deleteUserKeyValue(@PathParam("keyName") final String key,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        tenantApi.deleteTenantKey(key, callContext);
        return Response.status(Status.OK).build();
    }



    private Response insertTenantKey(final TenantKey key,
                                     @Nullable final String keyPostfix,
                                     final String value,
                                     final UriInfo uriInfo,
                                     final String getMethodStr,
                                     final String createdBy,
                                     final String reason,
                                     final String comment,
                                     final HttpServletRequest request) throws TenantApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final String tenantKey = keyPostfix != null ? key.toString() + keyPostfix : key.toString();
        tenantApi.addTenantKeyValue(tenantKey, value, callContext);

        return uriBuilder.buildResponse(uriInfo, TenantResource.class, getMethodStr, keyPostfix, request);
    }



    private Response getTenantKey(final TenantKey key,
                                  @Nullable final String keyPostfix,
                                  final HttpServletRequest request) throws TenantApiException {
        final TenantContext tenantContext = context.createContext(request);
        final String tenantKey = keyPostfix != null ? key.toString() + keyPostfix : key.toString();
        final List<String> values = tenantApi.getTenantValuesForKey(tenantKey, tenantContext);
        final TenantKeyJson result = new TenantKeyJson(tenantKey, values);
        return Response.status(Status.OK).entity(result).build();
    }

    private Response deleteTenantKey(final TenantKey key,
                                     @Nullable final String keyPostfix,
                                     final String createdBy,
                                     final String reason,
                                     final String comment,
                                     final HttpServletRequest request) throws TenantApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final String tenantKey = keyPostfix != null ? key.toString() + keyPostfix : key.toString();
        tenantApi.deleteTenantKey(tenantKey, callContext);
        return Response.status(Status.OK).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.TENANT;
    }
}
