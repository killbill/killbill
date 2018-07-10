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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

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
import org.killbill.commons.metrics.TimedResource;

import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Singleton
@Path(JaxrsResource.EXPORT_PATH)
@Api(value = JaxrsResource.EXPORT_PATH, description = "Export endpoints", tags="Export")
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
    @ApiOperation(value = "Export account data", response = Response.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Success"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public StreamingOutput exportDataForAccount(@PathParam("accountId") final UUID accountId,
                                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                @HeaderParam(HDR_REASON) final String reason,
                                                @HeaderParam(HDR_COMMENT) final String comment,
                                                @javax.ws.rs.core.Context final HttpServletRequest request) {
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
