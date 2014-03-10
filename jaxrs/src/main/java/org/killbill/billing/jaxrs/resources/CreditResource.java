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

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.joda.time.LocalDate;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.clock.Clock;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.json.CreditJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.CREDITS_PATH)
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
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.invoiceUserApi = invoiceUserApi;
        this.accountUserApi = accountUserApi;
    }

    @GET
    @Path("/{creditId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getCredit(@PathParam("creditId") final String creditId,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, AccountApiException {
        final TenantContext tenantContext = context.createContext(request);
        final InvoiceItem credit = invoiceUserApi.getCreditById(UUID.fromString(creditId), tenantContext);
        final Invoice invoice = invoiceUserApi.getInvoice(credit.getInvoiceId(), tenantContext);
        final CreditJson creditJson = new CreditJson(invoice, credit);
        return Response.status(Response.Status.OK).entity(creditJson).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCredit(final CreditJson json,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(json.getAccountId()), callContext);
        final LocalDate effectiveDate = new LocalDate(clock.getUTCNow(), account.getTimeZone());

        final InvoiceItem credit;
        if (json.getInvoiceId() != null) {
            // Apply an invoice level credit
            credit = invoiceUserApi.insertCreditForInvoice(account.getId(), UUID.fromString(json.getInvoiceId()), json.getCreditAmount(),
                                                           effectiveDate, account.getCurrency(), callContext);
        } else {
            // Apply a account level credit
            credit = invoiceUserApi.insertCredit(account.getId(), json.getCreditAmount(), effectiveDate,
                                                 account.getCurrency(), callContext);
        }

        return uriBuilder.buildResponse(uriInfo, CreditResource.class, "getCredit", credit.getId());
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE_ITEM;
    }
}
