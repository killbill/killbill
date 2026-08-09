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

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.ExportUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.api.annotation.TimedResource;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Singleton
@Path(JaxrsResource.EXPORT_PATH)
@Tag(name = "Export", description = "Export endpoints")
public class ExportResource extends JaxRsResourceBase {

    private final ExportUserApi exportUserApi;

    @Inject
    public ExportResource(final ExportUserApi exportUserApi,
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
        this.exportUserApi = exportUserApi;
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_OCTET_STREAM)
    @Operation(summary = "Export account data")
    //
    // Why schema-less @ApiResponse?
    //   Swagger Core 1.x had built-in filtering for javax.ws.rs.core.Response — it never
    //   introspected it. Swagger Core 2.x does NOT filter: an explicit
    //   @Schema(implementation = Response.class) recursively introspects the entire JAX-RS
    //   Response hierarchy (EntityTag, Link, MediaType, NewCookie, StatusType, UriBuilder),
    //   polluting the spec with framework internals. The 0.24.x spec had no response schema
    //   here, so we keep it schema-less to preserve generated-client backward compatibility.
    //
    // Why explicit @Content(mediaType)?
    //   Swagger Core 2.x only records media types inside OAS3 "content" maps, which require
    //   @Content. Without it, @Produces(APPLICATION_OCTET_STREAM) is invisible to the scanner
    //   and the Swagger 2.0 converter defaults to produces:["application/json"] — breaking
    //   codegen (loses OutputStream parameter, wrong Accept header). The @Content here (no
    //   schema) makes OAS3 truthful AND gives the converter the correct media type.
    //
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation",
                                        content = @Content(mediaType = APPLICATION_OCTET_STREAM)),
                           @ApiResponse(responseCode = "400", description = "Invalid account id supplied"),
                           @ApiResponse(responseCode = "404", description = "Account not found")})
    public StreamingOutput exportDataForAccount(@PathParam("accountId") final UUID accountId,
                                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                @HeaderParam(HDR_REASON) final String reason,
                                                @HeaderParam(HDR_COMMENT) final String comment,
                                                @jakarta.ws.rs.core.Context final HttpServletRequest request) {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        return new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException, WebApplicationException {
                // CSV by default for now
                exportUserApi.exportDataAsCSVForAccount(accountId, output, callContext);
            }
        };
    }
}
