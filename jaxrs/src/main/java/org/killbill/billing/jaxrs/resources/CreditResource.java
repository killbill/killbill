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

import java.util.UUID;

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
import javax.ws.rs.core.UriInfo;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.json.CreditJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.CREDITS_PATH)
@Api(value = JaxrsResource.CREDITS_PATH, description = "Operations on credits", tags="Credit")
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
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, null, clock, context);
        this.invoiceUserApi = invoiceUserApi;
        this.accountUserApi = accountUserApi;
    }

    @GET
    @Path("/{creditId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a credit by id", response = CreditJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid credit id supplied"),
                           @ApiResponse(code = 404, message = "Credit not found")})
    public Response getCredit(@PathParam("creditId") final UUID creditId,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final InvoiceItem credit = invoiceUserApi.getCreditById(creditId, tenantContext);
        final Invoice invoice = invoiceUserApi.getInvoice(credit.getInvoiceId(), tenantContext);
        final CreditJson creditJson = new CreditJson(invoice, credit);
        return Response.status(Response.Status.OK).entity(creditJson).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a credit", response = CreditJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created credit successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createCredit(final CreditJson json,
                                 @QueryParam(QUERY_AUTO_COMMIT) @DefaultValue("false") final Boolean autoCommit,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        verifyNonNullOrEmpty(json, "CreditJson body should be specified");
        verifyNonNullOrEmpty(json.getAccountId(), "CreditJson accountId needs to be set",
                             json.getCreditAmount(), "CreditJson creditAmount needs to be set");

        final CallContext callContext = context.createCallContextWithAccountId(json.getAccountId(), createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(json.getAccountId(), callContext);
        final LocalDate effectiveDate = new LocalDate(callContext.getCreatedDate(), account.getTimeZone());

        final InvoiceItem credit;
        if (json.getInvoiceId() != null) {
            // Apply an invoice level credit
            credit = invoiceUserApi.insertCreditForInvoice(account.getId(), json.getInvoiceId(), json.getCreditAmount(),
                                                           effectiveDate, account.getCurrency(), json.getDescription(), json.getItemDetails(), callContext);
        } else {
            // Apply a account level credit
            credit = invoiceUserApi.insertCredit(account.getId(), json.getCreditAmount(), effectiveDate,
                                                 account.getCurrency(), autoCommit, json.getDescription(), json.getItemDetails(), callContext);
        }

        return uriBuilder.buildResponse(uriInfo, CreditResource.class, "getCredit", credit.getId(), request);
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE_ITEM;
    }
}
