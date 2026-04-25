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

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.json.InvoiceItemJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.CREDITS_PATH)
@Tag(name = "Credit", description = "Operations on credits")
public class CreditResource extends JaxRsResourceBase {

    private final InvoiceUserApi invoiceUserApi;
    private final AccountUserApi accountUserApi;

    @Inject
    public CreditResource(final InvoiceUserApi invoiceUserApi,
                          final AccountUserApi accountUserApi,
                          final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final PaymentApi paymentApi,
                          final InvoicePaymentApi invoicePaymentApi,
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.invoiceUserApi = invoiceUserApi;
        this.accountUserApi = accountUserApi;
    }

    @GET
    @Path("/{creditId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve a credit by id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoiceItemJson.class))),
                           @ApiResponse(responseCode = "400", description = "Invalid credit id supplied"),
                           @ApiResponse(responseCode = "404", description = "Credit not found")})
    public Response getCredit(@PathParam("creditId") final UUID creditId,
                              @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final InvoiceItem credit = invoiceUserApi.getCreditById(creditId, tenantContext);
        final InvoiceItemJson creditJson = new InvoiceItemJson(credit, Collections.emptyList(), null);
        return Response.status(Response.Status.OK).entity(creditJson).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create a credit")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InvoiceItemJson.class)))),
                           @ApiResponse(responseCode = "201", description = "Created credit successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id supplied"),
                           @ApiResponse(responseCode = "404", description = "Account not found")})
    public Response createCredits(final List<InvoiceItemJson> json,
                                 @QueryParam(QUERY_AUTO_COMMIT) @DefaultValue("false") final Boolean autoCommit,
                                 @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                 @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        verifyNonNullOrEmpty(json, "CreditJson body should be specified");
        verifyNonNullOrEmpty(json.get(0).getAccountId(), "CreditJson accountId needs to be set",
                             json.get(0).getAmount(), "CreditJson creditAmount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(json.get(0).getAccountId(), createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(json.get(0).getAccountId(), callContext);
        final LocalDate effectiveDate = new LocalDate(callContext.getCreatedDate(), account.getTimeZone());

        final Iterable<InvoiceItem> inputItems = validateSanitizeAndTranformInputItems(account.getCurrency(), json);

        final List<InvoiceItem> createdCredits = invoiceUserApi.insertCredits(account.getId(), effectiveDate, inputItems, autoCommit, pluginProperties, callContext);
        final List<InvoiceItemJson> createdCreditsJson = createdCredits.stream()
                .map(InvoiceItemJson::new)
                .collect(Collectors.toUnmodifiableList());

        return Response.status(Status.OK).entity(createdCreditsJson).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE_ITEM;
    }
}
