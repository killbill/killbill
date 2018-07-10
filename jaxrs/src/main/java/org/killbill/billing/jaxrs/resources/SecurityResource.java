/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs.resources;

import java.util.List;
import java.util.Set;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.RoleDefinitionJson;
import org.killbill.billing.jaxrs.json.SubjectJson;
import org.killbill.billing.jaxrs.json.UserRolesJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.SECURITY_PATH)
@Api(value = JaxrsResource.SECURITY_PATH, description = "Information about RBAC", tags="Security")
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
                            final InvoicePaymentApi invoicePaymentApi,
                            final Clock clock,
                            final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.securityApi = securityApi;
    }

    @TimedResource
    @GET
    @Path("/permissions")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "List user permissions", response = String.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getCurrentUserPermissions(@javax.ws.rs.core.Context final HttpServletRequest request) {
        // The getCurrentUserPermissions takes a TenantContext which is not used because permissions are cross tenants (at this point)
        final TenantContext nullTenantContext = null;
        final Set<Permission> permissions = securityApi.getCurrentUserPermissions(nullTenantContext);
        final List<String> json = ImmutableList.<String>copyOf(Iterables.<Permission, String>transform(permissions, Functions.toStringFunction()));
        return Response.status(Status.OK).entity(json).build();
    }

    @TimedResource
    @GET
    @Path("/subject")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get user information", response = SubjectJson.class)
    @ApiResponses(value = {})
    public Response getCurrentUserSubject(@javax.ws.rs.core.Context final HttpServletRequest request) {
        final Subject subject = SecurityUtils.getSubject();
        final SubjectJson subjectJson = new SubjectJson(subject);
        return Response.status(Status.OK).entity(subjectJson).build();
    }

    @TimedResource
    @POST
    @Path("/users")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add a new user with roles (to make api requests)", response = UserRolesJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "User role created successfully")})
    public Response addUserRoles(final UserRolesJson json,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.addUserRoles(json.getUsername(), json.getPassword(), json.getRoles(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(uriInfo, SecurityResource.class, "getUserRoles", json.getUsername(), request);
    }

    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/users/{username:" + ANYTHING_PATTERN + "}/password")
    @ApiOperation(value = "Update a user password")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation")})
    public Response updateUserPassword(@PathParam("username") final String username,
                                       final UserRolesJson json,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.updateUserPassword(username, json.getPassword(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @Path("/users/{username:" + ANYTHING_PATTERN + "}/roles")
    @ApiOperation(value = "Get roles associated to a user", response = UserRolesJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "The user does not exist or has been inactivated")})
    public Response getUserRoles(@PathParam("username") final String username,
                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        final List<String> roles = securityApi.getUserRoles(username, context.createTenantContextNoAccountId(request));
        final UserRolesJson userRolesJson = new UserRolesJson(username, null, roles);
        return Response.status(Status.OK).entity(userRolesJson).build();
    }

    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/users/{username:" + ANYTHING_PATTERN + "}/roles")
    @ApiOperation(value = "Update roles associated to a user")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation")})
     public Response updateUserRoles(@PathParam("username") final String username,
                                    final UserRolesJson json,
                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                    @HeaderParam(HDR_REASON) final String reason,
                                    @HeaderParam(HDR_COMMENT) final String comment,
                                    @javax.ws.rs.core.Context final HttpServletRequest request,
                                    @javax.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.updateUserRoles(username, json.getRoles(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @DELETE
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/users/{username:" + ANYTHING_PATTERN + "}")
    @ApiOperation(value = "Invalidate an existing user")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation")})
    public Response invalidateUser(@PathParam("username") final String username,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final HttpServletRequest request,
                                   @javax.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.invalidateUser(username, context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }


    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @Path("/roles/{role:" + ANYTHING_PATTERN + "}")
    @ApiOperation(value = "Get role definition", response = RoleDefinitionJson.class)
    public Response getRoleDefinition(@PathParam("role") final String role,
                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        final List<String> roleDefinitions =  securityApi.getRoleDefinition(role, context.createTenantContextNoAccountId(request));
        final RoleDefinitionJson result =  new RoleDefinitionJson(role, roleDefinitions);
        return Response.status(Status.OK).entity(result).build();
    }


    @TimedResource
    @POST
    @Path("/roles")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add a new role definition)", response = RoleDefinitionJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Role definition created successfully")})
    public Response addRoleDefinition(final RoleDefinitionJson json,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final HttpServletRequest request,
                                      @javax.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.addRoleDefinition(json.getRole(), json.getPermissions(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(uriInfo, SecurityResource.class, "getRoleDefinition", json.getRole(), request);
    }


    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/roles")
    @ApiOperation(value = "Update a new role definition)")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation")})
    public Response updateRoleDefinition(final RoleDefinitionJson json,
                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                    @HeaderParam(HDR_REASON) final String reason,
                                    @HeaderParam(HDR_COMMENT) final String comment,
                                    @javax.ws.rs.core.Context final HttpServletRequest request,
                                    @javax.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.updateRoleDefinition(json.getRole(), json.getPermissions(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

}
