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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
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
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.InvoiceDryRunJson;
import org.killbill.billing.jaxrs.json.InvoiceItemJson;
import org.killbill.billing.jaxrs.json.InvoiceJson;
import org.killbill.billing.jaxrs.json.InvoicePaymentJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.LocaleUtils;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.killbill.billing.jaxrs.resources.SubscriptionResourceHelpers.buildPlanPhasePriceOverrides;

@Path(JaxrsResource.INVOICES_PATH)
@Api(value = JaxrsResource.INVOICES_PATH, description = "Operations on invoices", tags="Invoice")
public class InvoiceResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(InvoiceResource.class);
    private static final String ID_PARAM_NAME = "invoiceId";
    private static final String LOCALE_PARAM_NAME = "locale";

    private final InvoiceUserApi invoiceApi;
    private final TenantUserApi tenantApi;
    private final Locale defaultLocale;

    private static final Ordering<InvoicePaymentJson> INVOICE_PAYMENT_ORDERING = Ordering.from(new Comparator<InvoicePaymentJson>() {
        @Override
        public int compare(final InvoicePaymentJson o1, final InvoicePaymentJson o2) {
            return o1.getTransactions().get(0).getEffectiveDate().compareTo(o2.getTransactions().get(0).getEffectiveDate());
        }
    });

    @Inject
    public InvoiceResource(final AccountUserApi accountUserApi,
                           final InvoiceUserApi invoiceApi,
                           final PaymentApi paymentApi,
                           final InvoicePaymentApi invoicePaymentApi,
                           final Clock clock,
                           final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final TenantUserApi tenantApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.invoiceApi = invoiceApi;
        this.tenantApi = tenantApi;
        this.defaultLocale = Locale.getDefault();
    }

    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an invoice by id", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response getInvoice(@PathParam("invoiceId") final UUID invoiceId,
                               @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                               @QueryParam(QUERY_INVOICE_WITH_CHILDREN_ITEMS) @DefaultValue("false") final boolean withChildrenItems,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoice(invoiceId, tenantContext);
        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
        }

        final List<InvoiceItem> childInvoiceItems = withChildrenItems ? invoiceApi.getInvoiceItemsByParentInvoice(invoice.getId(), tenantContext) : null;
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        final InvoiceJson json = new InvoiceJson(invoice, withItems, childInvoiceItems, accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }


    @TimedResource
    @GET
    @Path("/byNumber/{invoiceNumber:" + NUMBER_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an invoice by number", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Invoice not found")})
    public Response getInvoiceByNumber(@PathParam("invoiceNumber") final Integer invoiceNumber,
                                       @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                                       @QueryParam(QUERY_INVOICE_WITH_CHILDREN_ITEMS) @DefaultValue("false") final boolean withChildrenItems,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoiceByNumber(invoiceNumber, tenantContext);
        final List<InvoiceItem> childInvoiceItems = withChildrenItems ? invoiceApi.getInvoiceItemsByParentInvoice(invoice.getId(), tenantContext) : null;
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        final InvoiceJson json = new InvoiceJson(invoice, withItems, childInvoiceItems, accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }



    @TimedResource
    @GET
    @Path("/byItemId/{itemId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an invoice by invoice item id", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Invoice not found")})
    public Response getInvoiceByItemId(@PathParam("itemId") final UUID invoiceItemId,
                                       @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                                       @QueryParam(QUERY_INVOICE_WITH_CHILDREN_ITEMS) @DefaultValue("false") final boolean withChildrenItems,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoiceByInvoiceItem(invoiceItemId, tenantContext);
        final List<InvoiceItem> childInvoiceItems = withChildrenItems ? invoiceApi.getInvoiceItemsByParentInvoice(invoice.getId(), tenantContext) : null;
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        final InvoiceJson json = new InvoiceJson(invoice, withItems, childInvoiceItems, accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }


    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/html")
    @Produces(TEXT_HTML)
    @ApiOperation(value = "Render an invoice as HTML", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Invoice not found")})
    public Response getInvoiceAsHTML(@PathParam("invoiceId") final UUID invoiceId,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, IOException, AccountApiException {
        return Response.status(Status.OK).entity(invoiceApi.getInvoiceAsHTML(invoiceId, context.createTenantContextNoAccountId(request))).build();
    }

    @TimedResource
    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "List invoices", response = InvoiceJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getInvoices(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final Boolean withItems,
                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<Invoice> invoices = invoiceApi.getInvoices(offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(InvoiceResource.class, "getInvoices", invoices.getNextOffset(), limit, ImmutableMap.<String, String>of(QUERY_INVOICE_WITH_ITEMS, withItems.toString(),
                                                                                                                                                           QUERY_AUDIT, auditMode.getLevel().toString()));

        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());
        return buildStreamingPaginationResponse(invoices,
                                                new Function<Invoice, InvoiceJson>() {
                                                    @Override
                                                    public InvoiceJson apply(final Invoice invoice) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(invoice.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(invoice.getAccountId(), auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }
                                                        return new InvoiceJson(invoice, withItems, null, accountsAuditLogs.get().get(invoice.getAccountId()));
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource
    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Search invoices", response = InvoiceJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response searchInvoices(@PathParam("searchKey") final String searchKey,
                                   @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                   @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                   @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final Boolean withItems,
                                   @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<Invoice> invoices = invoiceApi.searchInvoices(searchKey, offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(InvoiceResource.class, "searchInvoices", invoices.getNextOffset(), limit, ImmutableMap.<String, String>of("searchKey", searchKey,
                                                                                                                                                              QUERY_INVOICE_WITH_ITEMS, withItems.toString(),
                                                                                                                                                              QUERY_AUDIT, auditMode.getLevel().toString()));
        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());
        return buildStreamingPaginationResponse(invoices,
                                                new Function<Invoice, InvoiceJson>() {
                                                    @Override
                                                    public InvoiceJson apply(final Invoice invoice) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(invoice.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(invoice.getAccountId(), auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }
                                                        return new InvoiceJson(invoice, withItems, null, accountsAuditLogs.get().get(invoice.getAccountId()));
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Trigger an invoice generation", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created invoice successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id or target datetime supplied")})
    public Response createFutureInvoice(@ApiParam(required=true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                                        @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final HttpServletRequest request,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {

        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        final LocalDate inputDate = toLocalDate(targetDate);

        try {
            final Invoice generatedInvoice = invoiceApi.triggerInvoiceGeneration(accountId, inputDate, callContext);
            return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, "getInvoice", generatedInvoice.getId(), request);
        } catch (InvoiceApiException e) {
            if (e.getCode() == ErrorCode.INVOICE_NOTHING_TO_DO.getCode()) {
                return Response.status(Status.NOT_FOUND).build();
            }
            throw e;
        }
    }

    @TimedResource
    @POST
    @Path("/" + MIGRATION + "/{accountId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a migration invoice", response = InvoiceJson.class, tags="Invoice")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created migration invoice successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id or target datetime supplied")})
    public Response createMigrationInvoice(@PathParam("accountId") final UUID accountId,
                                           final List<InvoiceItemJson> items,
                                           @Nullable @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final HttpServletRequest request,
                                           @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);
        final Iterable<InvoiceItem> sanitizedInvoiceItems = validateSanitizeAndTranformInputItems(account.getCurrency(), items);
        final LocalDate resolvedTargetDate = toLocalDateDefaultToday(account, targetDate, callContext);
        final UUID invoiceId = invoiceApi.createMigrationInvoice(accountId, resolvedTargetDate, sanitizedInvoiceItems, callContext);
        return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, "getInvoice", invoiceId, request);
    }

    @TimedResource
    @POST
    @Path("/" + DRY_RUN)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Generate a dryRun invoice", response = InvoiceJson.class)
    @ApiResponses(value = {/* @ApiResponse(code = 200, message = "Successful"),  */ /* Already added by default */
                           @ApiResponse(code = 204, message = "Nothing to generate"),
                           @ApiResponse(code = 400, message = "Invalid account id or target datetime supplied")})
    public Response generateDryRunInvoice(@Nullable final InvoiceDryRunJson dryRunSubscriptionSpec,
                                          @ApiParam(required=true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                                          @Nullable @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final HttpServletRequest request,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        final LocalDate inputDate;
        if (dryRunSubscriptionSpec != null) {
            if (DryRunType.UPCOMING_INVOICE.equals(dryRunSubscriptionSpec.getDryRunType())) {
                inputDate = null;
            } else if (DryRunType.SUBSCRIPTION_ACTION.equals(dryRunSubscriptionSpec.getDryRunType()) && dryRunSubscriptionSpec.getEffectiveDate() != null) {
                inputDate = dryRunSubscriptionSpec.getEffectiveDate();
            } else {
                inputDate = toLocalDate(targetDate);
            }
        } else {
            inputDate = toLocalDate(targetDate);
        }

        // Passing a null or empty body means we are trying to generate an invoice with a (future) targetDate
        // On the other hand if body is not null, we are attempting a dryRun subscription operation
        if (dryRunSubscriptionSpec != null && dryRunSubscriptionSpec.getDryRunAction() != null) {
            if (SubscriptionEventType.START_BILLING.equals(dryRunSubscriptionSpec.getDryRunAction())) {
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getProductName(), "DryRun subscription product category should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBillingPeriod(), "DryRun subscription billingPeriod should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getProductCategory(), "DryRun subscription product category should be specified");
                if (dryRunSubscriptionSpec.getProductCategory().equals(ProductCategory.ADD_ON)) {
                    verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBundleId(), "DryRun bundle ID should be specified");
                }
            } else if (SubscriptionEventType.CHANGE.equals(dryRunSubscriptionSpec.getDryRunAction())) {
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getProductName(), "DryRun subscription product category should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBillingPeriod(), "DryRun subscription billingPeriod should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getSubscriptionId(), "DryRun subscriptionID should be specified");
            } else if (SubscriptionEventType.STOP_BILLING.equals(dryRunSubscriptionSpec.getDryRunAction())) {
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getSubscriptionId(), "DryRun subscriptionID should be specified");
            }
        }

        final Account account = accountUserApi.getAccountById(accountId, callContext);

        final DryRunArguments dryRunArguments = new DefaultDryRunArguments(dryRunSubscriptionSpec, account);
        try {
            final Invoice generatedInvoice = invoiceApi.triggerDryRunInvoiceGeneration(accountId, inputDate, dryRunArguments, callContext);
            return Response.status(Status.OK).entity(new InvoiceJson(generatedInvoice, true, null, null)).build();
        } catch (InvoiceApiException e) {
            if (e.getCode() == ErrorCode.INVOICE_NOTHING_TO_DO.getCode()) {
                return Response.status(Status.NO_CONTENT).build();
            }
            throw e;
        }
    }

    @TimedResource
    @DELETE
    @Path("/{invoiceId:" + UUID_PATTERN + "}" + "/{invoiceItemId:" + UUID_PATTERN + "}/cba")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Delete a CBA item")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id, invoice id or invoice item id supplied"),
                           @ApiResponse(code = 404, message = "Account or invoice not found")})
    public Response deleteCBA(@PathParam("invoiceId") final UUID invoiceId,
                              @PathParam("invoiceItemId") final UUID invoiceItemId,
                              @ApiParam(required=true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                              @HeaderParam(HDR_REASON) final String reason,
                              @HeaderParam(HDR_COMMENT) final String comment,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);

        invoiceApi.deleteCBA(account.getId(), invoiceId, invoiceItemId, callContext);

        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Adjust an invoice item", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created adjustment Successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id, invoice id or invoice item id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response adjustInvoiceItem(@PathParam("invoiceId") final UUID invoiceId,
                                      final InvoiceItemJson json,
                                      @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final HttpServletRequest request,
                                      @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        verifyNonNullOrEmpty(json, "InvoiceItemJson body should be specified");
        verifyNonNullOrEmpty(json.getAccountId(), "InvoiceItemJson accountId needs to be set",
                             json.getInvoiceItemId(), "InvoiceItemJson invoiceItemId needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID accountId = json.getAccountId();
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final LocalDate requestedDate = toLocalDateDefaultToday(accountId, requestedDateTimeString, callContext);
        final InvoiceItem adjustmentItem;
        if (json.getAmount() == null) {
            adjustmentItem = invoiceApi.insertInvoiceItemAdjustment(accountId,
                                                                    invoiceId,
                                                                    json.getInvoiceItemId(),
                                                                    requestedDate,
                                                                    json.getDescription(),
                                                                    json.getItemDetails(),
                                                                    pluginProperties,
                                                                    callContext);
        } else {
            adjustmentItem = invoiceApi.insertInvoiceItemAdjustment(accountId,
                                                                    invoiceId,
                                                                    json.getInvoiceItemId(),
                                                                    requestedDate,
                                                                    json.getAmount(),
                                                                    json.getCurrency(),
                                                                    json.getDescription(),
                                                                    json.getItemDetails(),
                                                                    pluginProperties,
                                                                    callContext);
        }

        if (adjustmentItem == null) {
            return Response.status(Status.NOT_FOUND).build();
        } else {
            return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, "getInvoice", adjustmentItem.getInvoiceId(), request);
        }
    }

    @TimedResource
    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/" + CHARGES + "/{accountId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Create external charge(s)", response = InvoiceItemJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created external charge Successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createExternalCharges(@PathParam("accountId") final UUID accountId,
                                          final List<InvoiceItemJson> externalChargesJson,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                          @QueryParam(QUERY_AUTO_COMMIT) @DefaultValue("false") final Boolean autoCommit,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);
        final Iterable<InvoiceItem> sanitizedExternalChargesJson = validateSanitizeAndTranformInputItems(account.getCurrency(), externalChargesJson);

        // Get the effective date of the external charge, in the account timezone
        final LocalDate requestedDate = toLocalDateDefaultToday(account, requestedDateTimeString, callContext);
        final List<InvoiceItem> createdExternalCharges = invoiceApi.insertExternalCharges(account.getId(), requestedDate, sanitizedExternalChargesJson, autoCommit, pluginProperties, callContext);

        final List<InvoiceItemJson> createdExternalChargesJson = Lists.<InvoiceItem, InvoiceItemJson>transform(createdExternalCharges,
                                                                                                               new Function<InvoiceItem, InvoiceItemJson>() {
                                                                                                                   @Override
                                                                                                                   public InvoiceItemJson apply(final InvoiceItem input) {
                                                                                                                       return new InvoiceItemJson(input);
                                                                                                                   }
                                                                                                               }
                                                                                                              );
        return Response.status(Status.OK).entity(createdExternalChargesJson).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/" + TAXES + "/{accountId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Create tax items", response = InvoiceItemJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Create tax items successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createTaxItems(@PathParam("accountId") final UUID accountId,
                                   final List<InvoiceItemJson> taxItemJson,
                                   @QueryParam(QUERY_AUTO_COMMIT) @DefaultValue("false") final Boolean autoCommit,
                                   @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final HttpServletRequest request,
                                   @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        verifyNonNullOrEmpty(taxItemJson, "Body should be specified");
        verifyNonNullOrEmpty(accountId, "AccountId needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);
        final Iterable<InvoiceItem> sanitizedTaxItemsJson = validateSanitizeAndTranformInputItems(account.getCurrency(), taxItemJson);

        final LocalDate requestedDate = toLocalDateDefaultToday(account, requestedDateTimeString, callContext);
        final List<InvoiceItem> createdTaxItems = invoiceApi.insertTaxItems(account.getId(), requestedDate, sanitizedTaxItemsJson, autoCommit, pluginProperties, callContext);

        final List<InvoiceItemJson> createdTaxItemJson = Lists.<InvoiceItem, InvoiceItemJson>transform(createdTaxItems,
                                                                                                       new Function<InvoiceItem, InvoiceItemJson>() {
                                                                                                           @Override
                                                                                                           public InvoiceItemJson apply(final InvoiceItem input) {
                                                                                                               return new InvoiceItemJson(input);
                                                                                                           }
                                                                                                       }
                                                                                                      );
        return Response.status(Status.OK).entity(createdTaxItemJson).build();
    }



    private Iterable<InvoiceItem> validateSanitizeAndTranformInputItems(final Currency accountCurrency, final Iterable<InvoiceItemJson> inputItems) throws InvoiceApiException {
        try {
            final Iterable<InvoiceItemJson> sanitized = Iterables.transform(inputItems, new Function<InvoiceItemJson, InvoiceItemJson>() {
                @Override
                public InvoiceItemJson apply(final InvoiceItemJson input) {
                    if (input.getCurrency() != null) {
                        if (!input.getCurrency().equals(accountCurrency)) {
                            throw new IllegalArgumentException(input.getCurrency().toString());
                        }
                        return input;
                    } else {
                        return new InvoiceItemJson(null,
                                                   input.getInvoiceId(),
                                                   input.getLinkedInvoiceItemId(),
                                                   input.getAccountId(),
                                                   input.getChildAccountId(),
                                                   input.getBundleId(),
                                                   input.getSubscriptionId(),
                                                   input.getProductName(),
                                                   input.getPlanName(),
                                                   input.getPhaseName(),
                                                   input.getUsageName(),
                                                   input.getPrettyProductName(),
                                                   input.getPrettyPlanName(),
                                                   input.getPrettyPhaseName(),
                                                   input.getPrettyUsageName(),
                                                   input.getItemType(),
                                                   input.getDescription(),
                                                   input.getStartDate(),
                                                   input.getEndDate(),
                                                   input.getAmount(),
                                                   input.getRate(),
                                                   accountCurrency,
                                                   input.getQuantity(),
                                                   input.getItemDetails(),
                                                   null,
                                                   null);
                    }
                }
            });

            return Iterables.transform(sanitized, new Function<InvoiceItemJson, InvoiceItem>() {
                @Override
                public InvoiceItem apply(final InvoiceItemJson input) {
                    return input.toInvoiceItem();
                }
            });
        } catch (IllegalArgumentException e) {
            throw new InvoiceApiException(ErrorCode.CURRENCY_INVALID, accountCurrency, e.getMessage());
        }
    }

    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payments associated with an invoice", response = InvoicePaymentJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response getPaymentsForInvoice(@PathParam("invoiceId") final UUID invoiceId,
                                          @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                          @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                          @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, InvoiceApiException {

        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoice(invoiceId, tenantContext);

        // Extract unique set of paymentId for this invoice
        final Set<UUID> invoicePaymentIds = ImmutableSet.copyOf(Iterables.transform(invoice.getPayments(), new Function<InvoicePayment, UUID>() {
            @Override
            public UUID apply(final InvoicePayment input) {
                return input.getPaymentId();
            }
        }));
        if (invoicePaymentIds.isEmpty()) {
            return Response.status(Status.OK).entity(ImmutableList.<InvoicePaymentJson>of()).build();
        }

        final List<Payment> payments = new ArrayList<Payment>();
        for (final UUID paymentId : invoicePaymentIds) {
            final Payment payment = paymentApi.getPayment(paymentId, withPluginInfo, withAttempts, ImmutableList.<PluginProperty>of(), tenantContext);
            payments.add(payment);
        }

        final Iterable<InvoicePaymentJson> result = INVOICE_PAYMENT_ORDERING.sortedCopy(Iterables.transform(payments, new Function<Payment, InvoicePaymentJson>() {
            @Override
            public InvoicePaymentJson apply(final Payment input) {
                return new InvoicePaymentJson(input, invoice.getId(), null);
            }
        }));
        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @ApiOperation(value = "Trigger a payment for invoice", response = InvoicePaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created payment Successfully"),
                           @ApiResponse(code = 204, message = "Nothing to pay for"),
                           @ApiResponse(code = 400, message = "Invalid account id or invoice id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createInstantPayment(@PathParam("invoiceId") final UUID invoiceId,
                                         final InvoicePaymentJson payment,
                                         @QueryParam(QUERY_PAYMENT_EXTERNAL) @DefaultValue("false") final Boolean externalPayment,
                                         @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @javax.ws.rs.core.Context final HttpServletRequest request,
                                         @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, PaymentApiException {
        verifyNonNullOrEmpty(payment, "InvoicePaymentJson body should be specified");
        verifyNonNullOrEmpty(payment.getAccountId(), "InvoicePaymentJson accountId needs to be set",
                             payment.getTargetInvoiceId(), "InvoicePaymentJson targetInvoiceId needs to be set",
                             payment.getPurchasedAmount(), "InvoicePaymentJson purchasedAmount needs to be set");
        Preconditions.checkArgument(!externalPayment || payment.getPaymentMethodId() == null, "InvoicePaymentJson should not contain a paymentMethodId when this is an external payment");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContext);
        final UUID paymentMethodId = externalPayment ? null :
                                     (payment.getPaymentMethodId() != null ? payment.getPaymentMethodId() : account.getPaymentMethodId());

        final InvoicePayment result = createPurchaseForInvoice(account, invoiceId, payment.getPurchasedAmount(), paymentMethodId, externalPayment,
                                                               payment.getPaymentExternalKey(), null, pluginProperties, callContext);
        return result != null ?
               uriBuilder.buildResponse(uriInfo, InvoicePaymentResource.class, "getInvoicePayment", result.getPaymentId(), request) :
               Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Path("/" + INVOICE_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @Produces(TEXT_PLAIN)
    @ApiOperation(value = "Retrieves the invoice translation for the tenant", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid locale supplied"),
                           @ApiResponse(code = 404, message = "Translation not found")})
    public Response getInvoiceTranslation(@PathParam("locale") final String localeStr,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(localeStr, TenantKey.INVOICE_TRANSLATION_, request);
    }

    @TimedResource
    @POST
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Path("/" + INVOICE_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @ApiOperation(value = "Upload the invoice translation for the tenant", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Uploaded invoice translation Successfully")})
    public Response uploadInvoiceTranslation(@PathParam("locale") final String localeStr,
                                             final String invoiceTranslation,
                                             @QueryParam(QUERY_DELETE_IF_EXISTS) @DefaultValue("false") final boolean deleteIfExists,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final HttpServletRequest request,
                                             @javax.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        return uploadTemplateResource(invoiceTranslation,
                                      localeStr,
                                      deleteIfExists,
                                      TenantKey.INVOICE_TRANSLATION_,
                                      "getInvoiceTranslation",
                                      createdBy,
                                      reason,
                                      comment,
                                      request,
                                      uriInfo);
    }

    @TimedResource
    @GET
    @Path("/" + INVOICE_CATALOG_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @Produces(TEXT_PLAIN)
    @ApiOperation(value = "Retrieves the catalog translation for the tenant", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid locale supplied"),
                           @ApiResponse(code = 404, message = "Template not found")})
    public Response getCatalogTranslation(@PathParam("locale") final String localeStr,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(localeStr, TenantKey.CATALOG_TRANSLATION_, request);
    }

    @TimedResource
    @POST
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Path("/" + INVOICE_CATALOG_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @ApiOperation(value = "Upload the catalog translation for the tenant", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Uploaded catalog translation Successfully")})
    public Response uploadCatalogTranslation(@PathParam("locale") final String localeStr,
                                             final String catalogTranslation,
                                             @QueryParam(QUERY_DELETE_IF_EXISTS) @DefaultValue("false") final boolean deleteIfExists,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final HttpServletRequest request,
                                             @javax.ws.rs.core.Context final UriInfo uriInfo) throws Exception {

        return uploadTemplateResource(catalogTranslation,
                                      localeStr,
                                      deleteIfExists,
                                      TenantKey.CATALOG_TRANSLATION_,
                                      "getCatalogTranslation",
                                      createdBy,
                                      reason,
                                      comment,
                                      request,
                                      uriInfo);
    }

    @TimedResource
    @GET
    @Path("/" + INVOICE_TEMPLATE)
    @Produces(TEXT_HTML)
    @ApiOperation(value = "Retrieves the invoice template for the tenant", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Template not found")})
    public Response getInvoiceTemplate(@javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(null, TenantKey.INVOICE_TEMPLATE, request);
    }

    @TimedResource
    @POST
    @Produces(TEXT_HTML)
    @Consumes(TEXT_HTML)
    @Path("/" + INVOICE_TEMPLATE)
    @ApiOperation(value = "Upload the invoice template for the tenant", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Uploaded invoice template Successfully")})
    public Response uploadInvoiceTemplate(final String catalogTranslation,
                                          @QueryParam(QUERY_DELETE_IF_EXISTS) @DefaultValue("false") final boolean deleteIfExists,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final HttpServletRequest request,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        return uploadTemplateResource(catalogTranslation,
                                      null,
                                      deleteIfExists,
                                      TenantKey.INVOICE_TEMPLATE,
                                      "getInvoiceTemplate",
                                      createdBy,
                                      reason,
                                      comment,
                                      request,
                                      uriInfo);
    }


    @TimedResource
    @GET
    @Path("/" + INVOICE_MP_TEMPLATE + "/{locale:" + ANYTHING_PATTERN + "}/")
    @Produces(TEXT_HTML)
    @ApiOperation(value = "Retrieves the manualPay invoice template for the tenant", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Template not found")})
    public Response getInvoiceMPTemplate(@PathParam("locale") final String localeStr,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(null, TenantKey.INVOICE_MP_TEMPLATE, request);
    }

    @TimedResource
    @POST
    @Produces(TEXT_HTML)
    @Consumes(TEXT_HTML)
    @Path("/" + INVOICE_MP_TEMPLATE)
    @ApiOperation(value = "Upload the manualPay invoice template for the tenant", response = String.class)
    public Response uploadInvoiceMPTemplate(final String catalogTranslation,
                                            @QueryParam(QUERY_DELETE_IF_EXISTS) @DefaultValue("false") final boolean deleteIfExists,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @javax.ws.rs.core.Context final HttpServletRequest request,
                                            @javax.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        return uploadTemplateResource(catalogTranslation,
                                      null,
                                      deleteIfExists,
                                      TenantKey.INVOICE_MP_TEMPLATE,
                                      "getInvoiceMPTemplate",
                                      createdBy,
                                      reason,
                                      comment,
                                      request,
                                      uriInfo);
    }

    private Response uploadTemplateResource(final String templateResource,
                                            @Nullable final String localeStr,
                                            final boolean deleteIfExists,
                                            final TenantKey tenantKey,
                                            final String getMethodStr,
                                            final String createdBy,
                                            final String reason,
                                            final String comment,
                                            final HttpServletRequest request,
                                            final UriInfo uriInfo) throws Exception {
        final String tenantKeyStr;
        if (localeStr != null) {
            // Validation purpose:  Will throw bad stream
            final InputStream stream = new ByteArrayInputStream(templateResource.getBytes());
            new PropertyResourceBundle(stream);
            final Locale locale = localeStr != null ? LocaleUtils.toLocale(localeStr) : defaultLocale;
            tenantKeyStr = LocaleUtils.localeString(locale, tenantKey.toString());
        } else {
            tenantKeyStr = tenantKey.toString();
        }

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        if (!tenantApi.getTenantValuesForKey(tenantKeyStr, callContext).isEmpty()) {
            if (deleteIfExists) {
                tenantApi.deleteTenantKey(tenantKeyStr, callContext);
            } else {
                return Response.status(Status.BAD_REQUEST).build();
            }
        }
        tenantApi.addTenantKeyValue(tenantKeyStr, templateResource, callContext);
        return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, getMethodStr, localeStr, request);
    }

    private Response getTemplateResource(@Nullable final String localeStr,
                                         final TenantKey tenantKey,
                                         final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final String tenantKeyStr = localeStr != null ?
                                    LocaleUtils.localeString(LocaleUtils.toLocale(localeStr), tenantKey.toString()) :
                                    tenantKey.toString();
        final List<String> result = tenantApi.getTenantValuesForKey(tenantKeyStr, tenantContext);
        return result.isEmpty() ? Response.status(Status.NOT_FOUND).build() : Response.status(Status.OK).entity(result.get(0)).build();
    }

    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve invoice custom fields", response = CustomFieldJson.class, responseContainer = "List", nickname = "getInvoiceCustomFields")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(id, auditMode, context.createTenantContextNoAccountId(request));
    }

    @TimedResource
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to invoice", response = CustomField.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Custom field created successfully"),
                           @ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response createInvoiceCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
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
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Modify custom fields to invoice")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response modifyInvoiceCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
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
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from invoice")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response deleteInvoiceCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
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
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve invoice tags", response = TagJson.class, responseContainer = "List", nickname = "getInvoiceTags")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID invoiceId,
                            @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoice(invoiceId, tenantContext);
        return super.getTags(invoice.getAccountId(), invoiceId, auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to invoice", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Tag created successfully"),
                           @ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response createInvoiceTags(@PathParam(ID_PARAM_NAME) final UUID id,
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
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from invoice")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response deleteInvoiceTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                      @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(id, tagList,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @PUT
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + COMMIT_INVOICE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Perform the invoice status transition from DRAFT to COMMITTED")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response commitInvoice(@PathParam("invoiceId") final UUID invoiceId,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo) throws InvoiceApiException {

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        invoiceApi.commitInvoice(invoiceId, callContext);
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @PUT
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + VOID_INVOICE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Perform the action of voiding an invoice")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid invoice id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response voidInvoice(@PathParam("invoiceId") final UUID invoiceId,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo) throws InvoiceApiException {

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        invoiceApi.voidInvoice(invoiceId, callContext);
        return Response.status(Status.NO_CONTENT).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE;
    }

    private static class DefaultDryRunArguments implements DryRunArguments {

        private final DryRunType dryRunType;
        private final SubscriptionEventType action;
        private final UUID subscriptionId;
        private final LocalDate effectiveDate;
        private final EntitlementSpecifier specifier;
        private final UUID bundleId;
        private final BillingActionPolicy billingPolicy;

        public DefaultDryRunArguments(final InvoiceDryRunJson input, final Account account) {
            if (input == null) {
                this.dryRunType = DryRunType.TARGET_DATE;
                this.action = null;
                this.subscriptionId = null;
                this.effectiveDate = null;
                this.specifier = null;
                this.bundleId = null;
                this.billingPolicy = null;
            } else {
                this.dryRunType = input.getDryRunType() != null ? input.getDryRunType() : DryRunType.TARGET_DATE;
                this.action = input.getDryRunAction() != null ? input.getDryRunAction() : null;
                this.subscriptionId = input.getSubscriptionId();
                this.bundleId = input.getBundleId();
                this.effectiveDate = input.getEffectiveDate();
                this.billingPolicy = input.getBillingPolicy() != null ? input.getBillingPolicy() : null;
                final PlanPhaseSpecifier planPhaseSpecifier = (input.getProductName() != null &&
                                                               input.getProductCategory() != null &&
                                                               input.getBillingPeriod() != null) ?
                                                              new PlanPhaseSpecifier(input.getProductName(),
                                                                                     input.getBillingPeriod(),
                                                                                     input.getPriceListName(),
                                                                                     input.getPhaseType() != null ? input.getPhaseType() : null) :
                                                              null;
                final List<PlanPhasePriceOverride> overrides = buildPlanPhasePriceOverrides(input.getPriceOverrides(),
                                                                                            account.getCurrency(),
                                                                                            planPhaseSpecifier);

                this.specifier = new EntitlementSpecifier() {
                    @Override
                    public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                        return planPhaseSpecifier;
                    }
                    @Override
                    public Integer getBillCycleDay() {
                        return null;
                    }
                    @Override
                    public List<PlanPhasePriceOverride> getOverrides() {
                        return overrides;
                    }
                };
            }
        }

        @Override
        public DryRunType getDryRunType() {
            return dryRunType;
        }

        @Override
        public EntitlementSpecifier getEntitlementSpecifier() {
            return specifier;
        }

        @Override
        public SubscriptionEventType getAction() {
            return action;
        }

        @Override
        public UUID getSubscriptionId() {
            return subscriptionId;
        }

        @Override
        public LocalDate getEffectiveDate() {
            return effectiveDate;
        }

        @Override
        public UUID getBundleId() {
            return bundleId;
        }

        @Override
        public BillingActionPolicy getBillingActionPolicy() {
            return billingPolicy;
        }


        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("DefaultDryRunArguments{");
            sb.append("dryRunType=").append(dryRunType);
            sb.append(", action=").append(action);
            sb.append(", subscriptionId=").append(subscriptionId);
            sb.append(", effectiveDate=").append(effectiveDate);
            sb.append(", specifier=").append(specifier);
            sb.append(", bundleId=").append(bundleId);
            sb.append(", billingPolicy=").append(billingPolicy);
            sb.append('}');
            return sb.toString();
        }
    }
}
