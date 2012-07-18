/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.jaxrs.resources;

import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.jaxrs.json.CreditJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.CREDITS_PATH)
public class CreditResource extends JaxRsResourceBase {

    private final InvoiceUserApi invoiceUserApi;
    private final AccountUserApi accountUserApi;
    private final Context context;

    @Inject
    public CreditResource(final JaxrsUriBuilder uriBuilder,
                          final InvoiceUserApi invoiceUserApi,
                          final AccountUserApi accountUserApi,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi);
        this.invoiceUserApi = invoiceUserApi;
        this.accountUserApi = accountUserApi;
        this.context = context;
    }

    @GET
    @Path("/{creditId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getCredit(@PathParam("creditId") final String creditId) throws InvoiceApiException, AccountApiException {
        final InvoiceItem credit = invoiceUserApi.getCreditById(UUID.fromString(creditId));
        final Account account = accountUserApi.getAccountById(credit.getAccountId());
        final CreditJson creditJson = new CreditJson(credit, account.getTimeZone());
        return Response.status(Response.Status.OK).entity(creditJson).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCredit(final CreditJson json,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment) throws AccountApiException, InvoiceApiException {
        final Account account = accountUserApi.getAccountById(json.getAccountId());
        final LocalDate effectiveDate = json.getEffectiveDate().toDateTime(account.getTimeZone()).toLocalDate();

        final InvoiceItem credit;
        if (json.getInvoiceId() != null) {
            // Apply an invoice level credit
            credit = invoiceUserApi.insertCreditForInvoice(account.getId(), json.getInvoiceId(), json.getCreditAmount(),
                                                           effectiveDate, account.getCurrency(), context.createContext(createdBy, reason, comment));
        } else {
            // Apply a account level credit
            credit = invoiceUserApi.insertCredit(account.getId(), json.getCreditAmount(), effectiveDate,
                                                 account.getCurrency(), context.createContext(createdBy, reason, comment));
        }

        return uriBuilder.buildResponse(CreditResource.class, "getCredit", credit.getId());
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE_ITEM;
    }
}
