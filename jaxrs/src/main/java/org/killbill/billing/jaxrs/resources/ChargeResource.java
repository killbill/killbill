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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.CHARGE_TRANSACTIONS_PATH)
@Api(value = JaxrsResource.CHARGE_TRANSACTIONS_PATH, description = "Operations on charges", tags = "Charge")
public class ChargeResource extends JaxRsResourceBase {

    private final InvoiceUserApi invoiceUserApi;
    private final AccountUserApi accountUserApi;

    @Inject
    public ChargeResource(final InvoiceUserApi invoiceUserApi,
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
    @Path("/{chargeId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a charge by id", response = InvoiceItemJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid charge id supplied"),
                           @ApiResponse(code = 404, message = "Charge not found")})
    public Response getCharge(@PathParam("chargeId") final UUID chargeId,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final InvoiceItem charge = invoiceUserApi.getExternalChargeById(chargeId, tenantContext);
        final InvoiceItemJson chargeJson = new InvoiceItemJson(charge, Collections.emptyList(), null);
        return Response.status(Response.Status.OK).entity(chargeJson).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a charge", response = InvoiceItemJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created charge successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createCharges(final List<InvoiceItemJson> json,
                                 @QueryParam(QUERY_AUTO_COMMIT) @DefaultValue("false") final Boolean autoCommit,
                                 @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        verifyNonNullOrEmpty(json, "ChargeJson body should be specified");
        verifyNonNullOrEmpty(json.get(0).getAccountId(), "ChargeJson accountId needs to be set",
                             json.get(0).getAmount(), "ChargeJson chargeAmount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(json.get(0).getAccountId(), createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(json.get(0).getAccountId(), callContext);
        final LocalDate effectiveDate = new LocalDate(callContext.getCreatedDate(), account.getTimeZone());

        final Iterable<InvoiceItem> inputItems = validateSanitizeAndTranformInputItems(account.getCurrency(), json);
        final List<InvoiceItem> createdCharges = invoiceUserApi.insertExternalCharges(account.getId(), effectiveDate, inputItems, autoCommit, pluginProperties, callContext);
        final List<InvoiceItemJson> createdChargesJson = createdCharges.stream()
                .map(InvoiceItemJson::new)
                .collect(Collectors.toUnmodifiableList());

        return Response.status(Status.OK).entity(createdChargesJson).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE_ITEM;
    }
}
