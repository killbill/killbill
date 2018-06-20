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

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.INVOICES_ITEMS_PATH)
@Api(value = JaxrsResource.INVOICES_ITEMS_PATH, description = "Operations on invoice items", tags="InvoiceItem")
public class InvoiceItemResource extends JaxRsResourceBase {
    private static final String ID_PARAM_NAME = "invoiceItemId";

    @Inject
    public InvoiceItemResource(final JaxrsUriBuilder uriBuilder, final TagUserApi tagUserApi, final CustomFieldUserApi customFieldUserApi,
                               final AuditUserApi auditUserApi, final AccountUserApi accountUserApi, final PaymentApi paymentApi,
                               final InvoicePaymentApi invoicePaymentApi, final SubscriptionApi subscriptionApi, final Clock clock, final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, subscriptionApi, clock, context);
    }

    @TimedResource
    @GET
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve invoice item custom fields", response = CustomFieldJson.class, responseContainer = "List", nickname = "getInvoiceItemCustomFields")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice item id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(id, auditMode, context.createTenantContextNoAccountId(request));
    }

    @TimedResource
    @POST
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to invoice item", response = CustomField.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Custom field created successfully"),
                           @ApiResponse(code = 400, message = "Invalid invoice item id supplied")})
    public Response createInvoiceItemCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                  final List<CustomFieldJson> customFields,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request,
                                                  @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request), uriInfo, request);
    }


    @TimedResource
    @PUT
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Modify custom fields to invoice item")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid invoice item id supplied")})
    public Response modifyInvoiceItemCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                  final List<CustomFieldJson> customFields,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.modifyCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }


    @TimedResource
    @DELETE
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from invoice item")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid invoice item id supplied")})
    public Response deleteInvoiceItemCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                  @QueryParam(QUERY_CUSTOM_FIELD) final List<UUID> customFieldList,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(id, customFieldList,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve invoice item tags", response = TagJson.class, responseContainer = "List", nickname = "getInvoiceItemTags")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice item id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID id,
                            @ApiParam(required=true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, AccountApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final Account account = accountUserApi.getAccountById(accountId, tenantContext);

        return super.getTags(account.getId(), id, auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to invoice item", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Tag created successfully"),
                           @ApiResponse(code = 400, message = "Invalid invoice item id supplied")})
    public Response createInvoiceItemTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                          final List<UUID> tagList,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(id, tagList, uriInfo,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request), request);
    }

    @TimedResource
    @DELETE
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from invoice item")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid invoice item id supplied")})
    public Response deleteInvoiceItemTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                          @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(id, tagList,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE_ITEM;
    }
}
