/*
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.OverdueJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.overdue.api.OverdueApi;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.api.annotation.TimedResource;
import org.killbill.xmlloader.XMLLoader;
import org.killbill.xmlloader.XMLWriter;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_XML;

@Singleton
@Path(JaxrsResource.OVERDUE_PATH)
@Tag(name = "Overdue", description = "Overdue information")
public class OverdueResource extends JaxRsResourceBase {

    private final OverdueApi overdueApi;

    @Inject
    public OverdueResource(final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final AccountUserApi accountUserApi,
                           final PaymentApi paymentApi,
                           final InvoicePaymentApi invoicePaymentApi,
                           final OverdueApi overdueApi,
                           final Clock clock,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.overdueApi = overdueApi;
    }

    //
    // We mark this resource as hidden from a swagger point of view and create another one with a different Path below
    // to hack around the restrictions of having only one type of HTTP verb per Path
    // see https://github.com/killbill/killbill/issues/913
    //
    @TimedResource
    @GET
    @Produces(TEXT_XML)
    @Operation(summary = "Retrieve the overdue config as XML", hidden = true)
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_XML, schema = @Schema(implementation = String.class)))})
    public Response getOverdueConfigXmlOriginal(@jakarta.ws.rs.core.Context final HttpServletRequest request) throws Exception {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        return Response.status(Status.OK).entity(XMLWriter.writeXML((DefaultOverdueConfig) overdueApi.getOverdueConfig(tenantContext), DefaultOverdueConfig.class)).build();
    }

    @TimedResource
    @GET
    @Path("/xml")
    @Produces(TEXT_XML)
    @Operation(summary = "Retrieve the overdue config as XML")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_XML, schema = @Schema(implementation = String.class)))})
    public Response getOverdueConfigXml(@jakarta.ws.rs.core.Context final HttpServletRequest request) throws Exception {
        return getOverdueConfigXmlOriginal(request);
    }



    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the overdue config as JSON")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OverdueJson.class)))})
    public Response getOverdueConfigJson(@jakarta.ws.rs.core.Context final HttpServletRequest request) throws Exception {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final OverdueConfig overdueConfig = overdueApi.getOverdueConfig(tenantContext);
        final OverdueJson result = new OverdueJson(overdueConfig);
        return Response.status(Status.OK).entity(result).build();
    }



    //
    // We mark this resource as hidden from a swagger point of view and create another one with a different Path below
    // to hack around the restrictions of having only one type of HTTP verb per Path
    // see https://github.com/killbill/killbill/issues/913
    //
    @TimedResource
    @POST
    @Consumes(TEXT_XML)
    @Operation(summary = "Upload the full overdue config as XML", hidden = true)
    @ApiResponses(value = {})
    public Response uploadOverdueConfigXmlOriginal(final String overdueXML,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                                   @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        // Validation purpose:  Will throw if bad XML or catalog validation fails
        final InputStream stream = new ByteArrayInputStream(overdueXML.getBytes(StandardCharsets.UTF_8));
        XMLLoader.getObjectFromStream(stream, DefaultOverdueConfig.class);

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        overdueApi.uploadOverdueConfig(overdueXML, callContext);
        return uriBuilder.buildResponse(uriInfo, OverdueResource.class, null, null, request);
    }

    @TimedResource
    @POST
    @Path("/xml")
    @Consumes(TEXT_XML)
    @Operation(summary = "Upload the full overdue config as XML")
    // Server returns 201 with no body (only a Location header), but the 0.24.x swagger spec
    // declared the response as {type: "string"}. Generated clients from that spec return a String,
    // and existing user code may call .isEmpty() on it. Dropping the schema would change the
    // generated return type to void/null, breaking backward compatibility with a NPE.
    // We keep the string schema to preserve the generated client contract.
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Successfully uploaded overdue config", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = "string"))),
                           @ApiResponse(responseCode = "400", description = "Invalid node command supplied")})
    public Response uploadOverdueConfigXml(final String overdueXML,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                                   @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        return uploadOverdueConfigXmlOriginal(overdueXML, createdBy, reason, comment, request, uriInfo);
    }


    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Upload the full overdue config as JSON")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OverdueJson.class))),
                           @ApiResponse(responseCode = "201", description = "Successfully uploaded overdue config"),
                           @ApiResponse(responseCode = "400", description = "Invalid node command supplied")})
    public Response uploadOverdueConfigJson(final OverdueJson overdueJson,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                            @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final OverdueConfig overdueConfig = OverdueJson.toOverdueConfigWithValidation(overdueJson);
        overdueApi.uploadOverdueConfig(overdueConfig, callContext);
        return uriBuilder.buildResponse(uriInfo, OverdueResource.class, null, null, request);
    }

}
