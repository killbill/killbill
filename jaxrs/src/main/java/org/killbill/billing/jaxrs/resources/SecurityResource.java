/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

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
import org.killbill.commons.metrics.api.annotation.TimedResource;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.SECURITY_PATH)
@Tag(name = "Security", description = "Information about RBAC")
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
    @Operation(summary = "List user permissions")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = String.class))))})
    public Response getCurrentUserPermissions(@jakarta.ws.rs.core.Context final HttpServletRequest request) {
        // The getCurrentUserPermissions takes a TenantContext which is not used because permissions are cross tenants (at this point)
        final TenantContext nullTenantContext = null;
        final Set<String> permissions = securityApi.getCurrentUserPermissions(nullTenantContext);
        return Response.status(Status.OK).entity(permissions).build();
    }

    @TimedResource
    @GET
    @Path("/subject")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Get user information")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubjectJson.class)))})
    public Response getCurrentUserSubject(@jakarta.ws.rs.core.Context final HttpServletRequest request) {
        final Subject subject = SecurityUtils.getSubject();
        final SubjectJson subjectJson = new SubjectJson(subject);
        return Response.status(Status.OK).entity(subjectJson).build();
    }

    @TimedResource
    @POST
    @Path("/users")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add a new user with roles (to make api requests)")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "User role created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserRolesJson.class)))})
    public Response addUserRoles(final UserRolesJson json,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                 @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.addUserRoles(json.getUsername(), json.getPassword(), json.getRoles(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(uriInfo, SecurityResource.class, "getUserRoles", json.getUsername(), request);
    }

    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/users/{username:" + ANYTHING_PATTERN + "}/password")
    @Operation(summary = "Update a user password")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation")})
    public Response updateUserPassword(@PathParam("username") final String username,
                                       final UserRolesJson json,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                       @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.updateUserPassword(username, json.getPassword(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @Path("/users/{username:" + ANYTHING_PATTERN + "}/roles")
    @Operation(summary = "Get roles associated to a user")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserRolesJson.class))),
                           @ApiResponse(responseCode = "404", description = "The user does not exist or has been inactivated")})
    public Response getUserRoles(@PathParam("username") final String username,
                                 @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                 @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        final List<String> roles = securityApi.getUserRoles(username, context.createTenantContextNoAccountId(request));
        final UserRolesJson userRolesJson = new UserRolesJson(username, null, roles);
        return Response.status(Status.OK).entity(userRolesJson).build();
    }

    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/users/{username:" + ANYTHING_PATTERN + "}/roles")
    @Operation(summary = "Update roles associated to a user")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation")})
     public Response updateUserRoles(@PathParam("username") final String username,
                                    final UserRolesJson json,
                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                    @HeaderParam(HDR_REASON) final String reason,
                                    @HeaderParam(HDR_COMMENT) final String comment,
                                    @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                    @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.updateUserRoles(username, json.getRoles(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @DELETE
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/users/{username:" + ANYTHING_PATTERN + "}")
    @Operation(summary = "Invalidate an existing user")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation")})
    public Response invalidateUser(@PathParam("username") final String username,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                   @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.invalidateUser(username, context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }


    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @Path("/roles")
    @Operation(summary = "List all available role definitions")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoleDefinitionJson.class))))})
    public Response getAvailableRoles(@jakarta.ws.rs.core.Context final HttpServletRequest request,
                                      @jakarta.ws.rs.core.Context final UriInfo uriInfo) {
        final Map<String, List<String>> availableRoles = securityApi.getAvailableRoles(context.createTenantContextNoAccountId(request));
        final List<RoleDefinitionJson> result = new ArrayList<>(availableRoles.size());
        for (final Map.Entry<String, List<String>> entry : availableRoles.entrySet()) {
            result.add(new RoleDefinitionJson(entry.getKey(), entry.getValue()));
        }
        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @Path("/roles/{role:" + ANYTHING_PATTERN + "}")
    @Operation(summary = "Get role definition")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleDefinitionJson.class)))})
    public Response getRoleDefinition(@PathParam("role") final String role,
                                 @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                 @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        final List<String> roleDefinitions =  securityApi.getRoleDefinition(role, context.createTenantContextNoAccountId(request));
        final RoleDefinitionJson result =  new RoleDefinitionJson(role, roleDefinitions);
        return Response.status(Status.OK).entity(result).build();
    }


    @TimedResource
    @POST
    @Path("/roles")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add a new role definition)")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Role definition created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleDefinitionJson.class)))})
    public Response addRoleDefinition(final RoleDefinitionJson json,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                      @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.addRoleDefinition(json.getRole(), json.getPermissions(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(uriInfo, SecurityResource.class, "getRoleDefinition", json.getRole(), request);
    }


    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/roles")
    @Operation(summary = "Update a new role definition)")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation")})
    public Response updateRoleDefinition(final RoleDefinitionJson json,
                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                    @HeaderParam(HDR_REASON) final String reason,
                                    @HeaderParam(HDR_COMMENT) final String comment,
                                    @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                    @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws SecurityApiException {
        securityApi.updateRoleDefinition(json.getRole(), json.getPermissions(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

}
