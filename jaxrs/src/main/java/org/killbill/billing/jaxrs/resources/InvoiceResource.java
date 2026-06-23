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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PropertyResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.BillingActionPolicy;
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
import org.killbill.billing.jaxrs.json.AuditLogJson;
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
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.LocaleUtils;
import org.killbill.commons.utils.Preconditions;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.api.annotation.TimedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.killbill.billing.jaxrs.resources.SubscriptionResourceHelpers.buildPlanPhasePriceOverrides;

@Singleton
@Path(JaxrsResource.INVOICES_PATH)
@Tag(name = "Invoice", description = "Operations on invoices")
public class InvoiceResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(InvoiceResource.class);
    private static final String ID_PARAM_NAME = "invoiceId";

    private final InvoiceUserApi invoiceApi;
    private final TenantUserApi tenantApi;
    private final Locale defaultLocale;

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

    /**
     * Replace the same logic that occurs in:
     * - {@link #getInvoice(UUID, boolean, AuditMode, HttpServletRequest)}
     * - {@link #getInvoiceByItemId(UUID, boolean, AuditMode, HttpServletRequest)}
     * - {@link #getInvoiceByNumber(Integer, boolean, AuditMode, HttpServletRequest)}
     */
    private Response buildGetInvoiceWithItemResponse(final Invoice invoice,
                                                     final boolean withChildrenItems,
                                                     final AuditMode auditMode,
                                                     final TenantContext tenantContext) throws InvoiceApiException {
        final List<InvoiceItem> childInvoiceItems = withChildrenItems ? invoiceApi.getInvoiceItemsByParentInvoice(invoice.getId(), tenantContext) : null;
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        final InvoiceJson json = new InvoiceJson(invoice, childInvoiceItems, accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }


    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an invoice by id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoiceJson.class))),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied"),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response getInvoice(@PathParam("invoiceId") final UUID invoiceId,
                               @QueryParam(QUERY_INVOICE_WITH_CHILDREN_ITEMS) @DefaultValue("false") final boolean withChildrenItems,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoice(invoiceId, tenantContext);
        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
        }

        return buildGetInvoiceWithItemResponse(invoice, withChildrenItems, auditMode, tenantContext);
    }

    @TimedResource
    @GET
    @Path("/{groupId:" + UUID_PATTERN + "}/" + GROUP)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve a set of invoices by group id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InvoiceJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid group id supplied")})
    public Response getInvoicesGroup(@PathParam("groupId") final UUID groupId,
                                     @Parameter(required = true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                                     @QueryParam(QUERY_INVOICE_WITH_CHILDREN_ITEMS) @DefaultValue("false") final boolean withChildrenItems,
                                     @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                     @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final Iterable<Invoice> invoices = invoiceApi.getInvoicesByGroup(accountId, groupId, tenantContext);

        final List<InvoiceJson> result = new ArrayList<>();
        final Iterator<Invoice> it = invoices.iterator();
        while (it.hasNext()) {
            final Invoice invoice  = it.next();
            final List<InvoiceItem> childInvoiceItems = withChildrenItems ? invoiceApi.getInvoiceItemsByParentInvoice(invoice.getId(), tenantContext) : null;
            final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);
            result.add(new InvoiceJson(invoice, childInvoiceItems, accountAuditLogs));
        }
        if (result.isEmpty()) {
            return Response.status(Status.NOT_FOUND).build();
        } else {
            return Response.status(Status.OK).entity(result).build();
        }
    }

    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + AUDIT_LOG_WITH_HISTORY)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve invoice audit logs with history by id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AuditLogJson.class)))),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response getInvoiceAuditLogsWithHistory(@PathParam("invoiceId") final UUID invoiceId,
                                                   @jakarta.ws.rs.core.Context final HttpServletRequest request) {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final List<AuditLogWithHistory> auditLogWithHistory = invoiceApi.getInvoiceAuditLogsWithHistoryForId(invoiceId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogsWithHistory(auditLogWithHistory)).build();
    }


    @TimedResource
    @GET
    @Path("/byNumber/{invoiceNumber:" + NUMBER_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an invoice by number")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoiceJson.class))),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response getInvoiceByNumber(@PathParam("invoiceNumber") final Integer invoiceNumber,
                                       @QueryParam(QUERY_INVOICE_WITH_CHILDREN_ITEMS) @DefaultValue("false") final boolean withChildrenItems,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoiceByNumber(invoiceNumber, tenantContext);

        return buildGetInvoiceWithItemResponse(invoice, withChildrenItems, auditMode, tenantContext);
    }


    @TimedResource
    @GET
    @Path("/byItemId/{itemId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an invoice by invoice item id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoiceJson.class))),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response getInvoiceByItemId(@PathParam("itemId") final UUID invoiceItemId,
                                       @QueryParam(QUERY_INVOICE_WITH_CHILDREN_ITEMS) @DefaultValue("false") final boolean withChildrenItems,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoiceByInvoiceItem(invoiceItemId, tenantContext);

        return buildGetInvoiceWithItemResponse(invoice, withChildrenItems, auditMode, tenantContext);
    }


    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/html")
    @Produces(TEXT_HTML)
    @Operation(summary = "Render an invoice as HTML")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_HTML, schema = @Schema(implementation = String.class))),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response getInvoiceAsHTML(@PathParam("invoiceId") final UUID invoiceId,
                                     @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, IOException, AccountApiException {
        return Response.status(Status.OK).entity(invoiceApi.getInvoiceAsHTML(invoiceId, context.createTenantContextNoAccountId(request))).build();
    }

    /**
     * Replace the same logic that occurs in:
     * - {@link #getInvoices(Long, Long, AuditMode, HttpServletRequest)}
     * - {@link #searchInvoices(String, Long, Long, AuditMode, HttpServletRequest)}
     */
    private Response buildInvoicesStreamingPaginationResponse(final Pagination<Invoice> invoices,
                                                              final URI nextPageUri,
                                                              final AuditMode auditMode,
                                                              final TenantContext tenantContext) {
        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<>(new HashMap<>());
        return buildStreamingPaginationResponse(invoices,
                                                invoice -> {
                                                    // Cache audit logs per account
                                                    if (accountsAuditLogs.get().get(invoice.getAccountId()) == null) {
                                                        accountsAuditLogs.get().put(invoice.getAccountId(), auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext));
                                                    }
                                                    return new InvoiceJson(invoice, null, accountsAuditLogs.get().get(invoice.getAccountId()));
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource
    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List invoices")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InvoiceJson.class))))})
    public Response getInvoices(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<Invoice> invoices = invoiceApi.getInvoices(offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(InvoiceResource.class, "getInvoices", invoices.getNextOffset(), limit, Map.of(QUERY_AUDIT, auditMode.getLevel().toString()), Collections.emptyMap());

        return buildInvoicesStreamingPaginationResponse(invoices, nextPageUri, auditMode, tenantContext);
    }

    @TimedResource
    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Search invoices")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InvoiceJson.class))))})
    public Response searchInvoices(@PathParam("searchKey") final String searchKey,
                                   @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                   @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                   @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                   @jakarta.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<Invoice> invoices = invoiceApi.searchInvoices(searchKey, offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(InvoiceResource.class, "searchInvoices", invoices.getNextOffset(), limit, Map.of(QUERY_AUDIT, auditMode.getLevel().toString()), Map.of("searchKey", searchKey));

        return buildInvoicesStreamingPaginationResponse(invoices, nextPageUri, auditMode, tenantContext);
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Trigger an invoice generation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoiceJson.class))),
                           @ApiResponse(responseCode = "201", description = "Created invoice successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id or target datetime supplied")})
    public Response createFutureInvoice(@Parameter(required = true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                                        @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                        @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {

        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final LocalDate inputDate = toLocalDate(targetDate);

        try {
            final Invoice generatedInvoice = invoiceApi.triggerInvoiceGeneration(accountId, inputDate, pluginProperties, callContext);
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
    @Path("/" + GROUP)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Trigger an invoice generation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InvoiceJson.class)))),
                           @ApiResponse(responseCode = "201", description = "Created invoice successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id or target datetime supplied")})
    public Response createFutureInvoiceGroup(@Parameter(required = true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                                             @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                             @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                             @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {

        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);

        final LocalDate inputDate = toLocalDate(targetDate);

        try {
            final Iterable<Invoice> generatedInvoices = invoiceApi.triggerInvoiceGroupGeneration(accountId, inputDate, pluginProperties, callContext);
            final UUID groupId = generatedInvoices.iterator().next().getGroupId();
            return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, "getInvoicesGroup", groupId, request);
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
    @Operation(summary = "Create a migration invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoiceJson.class))),
                           @ApiResponse(responseCode = "201", description = "Created migration invoice successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id or target datetime supplied")})
    public Response createMigrationInvoice(@PathParam("accountId") final UUID accountId,
                                           final List<InvoiceItemJson> items,
                                           @Nullable @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                           @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
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
    @Operation(summary = "Generate a dryRun invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoiceJson.class))),
                           /* @ApiResponse(responseCode = "200", description = "Successful"),  */ /* Already added by default */
            @ApiResponse(responseCode = "204", description = "Nothing to generate"),
            @ApiResponse(responseCode = "400", description = "Invalid account id or target datetime supplied")})
    public Response generateDryRunInvoice(@Nullable final InvoiceDryRunJson dryRunSubscriptionSpec,
                                          @Parameter(required = true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                                          @Nullable @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                          @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);

        final LocalDate inputDate = (dryRunSubscriptionSpec != null && DryRunType.UPCOMING_INVOICE.equals(dryRunSubscriptionSpec.getDryRunType())) ?
                                    null : toLocalDate(targetDate);

        // Passing a null or empty body means we are trying to generate an invoice with a (future) targetDate
        // On the other hand if body is not null, we are attempting a dryRun subscription operation
        if (dryRunSubscriptionSpec != null && dryRunSubscriptionSpec.getDryRunAction() != null) {
            if (SubscriptionEventType.START_BILLING.equals(dryRunSubscriptionSpec.getDryRunAction()) || SubscriptionEventType.CHANGE.equals(dryRunSubscriptionSpec.getDryRunAction())) {
                if (dryRunSubscriptionSpec.getPlanName() == null) {
                    verifyNonNullOrEmpty(dryRunSubscriptionSpec.getProductName(), "DryRun subscription product category should be specified when no planName is specified");
                    verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBillingPeriod(), "DryRun subscription billingPeriod should be specified when no planName is specified");
                    verifyNonNullOrEmpty(dryRunSubscriptionSpec.getProductCategory(), "DryRun subscription product category should be specified when no planName is specified");
                    if (dryRunSubscriptionSpec.getProductCategory().equals(ProductCategory.ADD_ON)) {
                        verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBundleId(), "DryRun bundleID should be specified when product category is ADD_ON");
                    }
                } else {
                    Preconditions.checkArgument(dryRunSubscriptionSpec.getProductName() == null, "DryRun subscription productName should not be set when planName is specified");
                    Preconditions.checkArgument(dryRunSubscriptionSpec.getBillingPeriod() == null, "DryRun subscription billing period should not be set when planName is specified");
                    Preconditions.checkArgument(dryRunSubscriptionSpec.getProductCategory() == null, "DryRun subscription product category should not be set when planName is specified");
                }
            }
            if (SubscriptionEventType.CHANGE.equals(dryRunSubscriptionSpec.getDryRunAction()) || SubscriptionEventType.STOP_BILLING.equals(dryRunSubscriptionSpec.getDryRunAction())) {
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getSubscriptionId(), "DryRun subscriptionID should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBundleId(), "DryRun bundleID should be specified");
            }
        }

        final Account account = accountUserApi.getAccountById(accountId, callContext);

        final DryRunArguments dryRunArguments = new DefaultDryRunArguments(dryRunSubscriptionSpec, account);
        try {
            final Invoice generatedInvoice = invoiceApi.triggerDryRunInvoiceGeneration(accountId, inputDate, dryRunArguments, pluginProperties, callContext);
            return Response.status(Status.OK).entity(new InvoiceJson(generatedInvoice, null, null)).build();
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
    @Operation(summary = "Delete a CBA item")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id, invoice id or invoice item id supplied"),
                           @ApiResponse(responseCode = "404", description = "Account or invoice not found")})
    public Response deleteCBA(@PathParam("invoiceId") final UUID invoiceId,
                              @PathParam("invoiceItemId") final UUID invoiceItemId,
                              @Parameter(required = true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                              @HeaderParam(HDR_REASON) final String reason,
                              @HeaderParam(HDR_COMMENT) final String comment,
                              @jakarta.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException {
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
    @Operation(summary = "Adjust an invoice item")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoiceJson.class))),
                           @ApiResponse(responseCode = "201", description = "Created adjustment Successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id, invoice id or invoice item id supplied"),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response adjustInvoiceItem(@PathParam("invoiceId") final UUID invoiceId,
                                      final InvoiceItemJson json,
                                      @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                      @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
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
    @Operation(summary = "Create external charge(s)")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InvoiceItemJson.class)))),
                           @ApiResponse(responseCode = "201", description = "Created external charge Successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id supplied"),
                           @ApiResponse(responseCode = "404", description = "Account not found")})
    public Response createExternalCharges(@PathParam("accountId") final UUID accountId,
                                          final List<InvoiceItemJson> externalChargesJson,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                          @QueryParam(QUERY_AUTO_COMMIT) @DefaultValue("false") final Boolean autoCommit,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @jakarta.ws.rs.core.Context final UriInfo uriInfo,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);
        final Iterable<InvoiceItem> sanitizedExternalChargesJson = validateSanitizeAndTranformInputItems(account.getCurrency(), externalChargesJson);

        // Get the effective date of the external charge, in the account timezone
        final LocalDate requestedDate = toLocalDateDefaultToday(account, requestedDateTimeString, callContext);
        final List<InvoiceItem> createdExternalCharges = invoiceApi.insertExternalCharges(account.getId(), requestedDate, sanitizedExternalChargesJson, autoCommit, pluginProperties, callContext);

        final List<InvoiceItemJson> createdExternalChargesJson = createdExternalCharges.stream()
                                                                                       .map(InvoiceItemJson::new)
                                                                                       .collect(Collectors.toUnmodifiableList());

        return Response.status(Status.OK).entity(createdExternalChargesJson).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/" + TAXES + "/{accountId:" + UUID_PATTERN + "}")
    @Operation(summary = "Create tax items")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InvoiceItemJson.class)))),
                           @ApiResponse(responseCode = "201", description = "Create tax items successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id supplied"),
                           @ApiResponse(responseCode = "404", description = "Account not found")})
    public Response createTaxItems(@PathParam("accountId") final UUID accountId,
                                   final List<InvoiceItemJson> taxItemJson,
                                   @QueryParam(QUERY_AUTO_COMMIT) @DefaultValue("false") final Boolean autoCommit,
                                   @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                   @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        verifyNonNullOrEmpty(taxItemJson, "Body should be specified");
        verifyNonNullOrEmpty(accountId, "AccountId needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);
        final Iterable<InvoiceItem> sanitizedTaxItemsJson = validateSanitizeAndTranformInputItems(account.getCurrency(), taxItemJson);

        final LocalDate requestedDate = toLocalDateDefaultToday(account, requestedDateTimeString, callContext);
        final List<InvoiceItem> createdTaxItems = invoiceApi.insertTaxItems(account.getId(), requestedDate, sanitizedTaxItemsJson, autoCommit, pluginProperties, callContext);

        final List<InvoiceItemJson> createdTaxItemJson = createdTaxItems.stream()
                                                                        .map(InvoiceItemJson::new)
                                                                        .collect(Collectors.toUnmodifiableList());

        return Response.status(Status.OK).entity(createdTaxItemJson).build();
    }




    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve payments associated with an invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InvoicePaymentJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied"),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response getPaymentsForInvoice(@PathParam("invoiceId") final UUID invoiceId,
                                          @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                          @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                          @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, InvoiceApiException {

        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Invoice invoice = invoiceApi.getInvoice(invoiceId, tenantContext);

        // Extract unique set of paymentId for this invoice
        final Set<UUID> invoicePaymentIds = new HashSet<UUID>();
        for (final InvoicePayment invoicePayment : invoice.getPayments()) {
            if (invoicePayment.getPaymentId() != null) {
                invoicePaymentIds.add(invoicePayment.getPaymentId());
            }
        }
        if (invoicePaymentIds.isEmpty()) {
            return Response.status(Status.OK).entity(Collections.emptyList()).build();
        }

        final List<Payment> payments = new ArrayList<>();
        for (final UUID paymentId : invoicePaymentIds) {
            final Payment payment = paymentApi.getPayment(paymentId, withPluginInfo, withAttempts, Collections.emptyList(), tenantContext);
            payments.add(payment);
        }
        
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        final Iterable<InvoicePaymentJson> result = payments.stream()
                                                            .map(input -> new InvoicePaymentJson(input, invoice.getId(), accountAuditLogs))
                                                            .sorted(Comparator.comparing(o -> o.getTransactions().get(0).getEffectiveDate()))
                                                            .collect(Collectors.toUnmodifiableList());

        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Operation(summary = "Trigger a payment for invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvoicePaymentJson.class))),
                           @ApiResponse(responseCode = "201", description = "Created payment Successfully"),
                           @ApiResponse(responseCode = "204", description = "Nothing to pay for"),
                           @ApiResponse(responseCode = "400", description = "Invalid account id or invoice id supplied"),
                           @ApiResponse(responseCode = "404", description = "Account not found")})
    public Response createInstantPayment(@PathParam("invoiceId") final UUID invoiceId,
                                         final InvoicePaymentJson payment,
                                         @QueryParam(QUERY_PAYMENT_EXTERNAL) @DefaultValue("false") final Boolean externalPayment,
                                         @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                         @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                         @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, PaymentApiException {
        verifyNonNullOrEmpty(payment, "InvoicePaymentJson body should be specified");
        verifyNonNullOrEmpty(payment.getAccountId(), "InvoicePaymentJson accountId needs to be set");
        Preconditions.checkArgument(!externalPayment || payment.getPaymentMethodId() == null, "InvoicePaymentJson should not contain a paymentMethodId when this is an external payment");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContext);
        final UUID paymentMethodId = externalPayment ? null :
                                     (payment.getPaymentMethodId() != null ? payment.getPaymentMethodId() : account.getPaymentMethodId());

        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(externalPayment, paymentControlPluginNames);
        final InvoicePayment result = createPurchaseForInvoice(account, invoiceId, payment.getPurchasedAmount(), paymentMethodId,
                                                               payment.getPaymentExternalKey(), null, pluginProperties, paymentOptions, callContext);
        return result != null ?
               uriBuilder.buildResponse(uriInfo, InvoicePaymentResource.class, "getInvoicePayment", result.getPaymentId(), request) :
               Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Path("/" + INVOICE_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Retrieves the invoice translation for the tenant")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(implementation = String.class))),
                           @ApiResponse(responseCode = "400", description = "Invalid locale supplied"),
                           @ApiResponse(responseCode = "404", description = "Translation not found")})
    public Response getInvoiceTranslation(@PathParam("locale") final String localeStr,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(localeStr, TenantKey.INVOICE_TRANSLATION_, request);
    }

    @TimedResource
    @POST
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Path("/" + INVOICE_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @Operation(summary = "Upload the invoice translation for the tenant")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(implementation = String.class))),
                           @ApiResponse(responseCode = "201", description = "Uploaded invoice translation Successfully")})
    public Response uploadInvoiceTranslation(@PathParam("locale") final String localeStr,
                                             final String invoiceTranslation,
                                             @QueryParam(QUERY_DELETE_IF_EXISTS) @DefaultValue("false") final boolean deleteIfExists,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                             @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
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
    @Operation(summary = "Retrieves the catalog translation for the tenant")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(implementation = String.class))),
                           @ApiResponse(responseCode = "400", description = "Invalid locale supplied"),
                           @ApiResponse(responseCode = "404", description = "Template not found")})
    public Response getCatalogTranslation(@PathParam("locale") final String localeStr,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(localeStr, TenantKey.CATALOG_TRANSLATION_, request);
    }

    @TimedResource
    @POST
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Path("/" + INVOICE_CATALOG_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @Operation(summary = "Upload the catalog translation for the tenant")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(implementation = String.class))),
                           @ApiResponse(responseCode = "201", description = "Uploaded catalog translation Successfully")})
    public Response uploadCatalogTranslation(@PathParam("locale") final String localeStr,
                                             final String catalogTranslation,
                                             @QueryParam(QUERY_DELETE_IF_EXISTS) @DefaultValue("false") final boolean deleteIfExists,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                             @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws Exception {

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
    @Operation(summary = "Retrieves the invoice template for the tenant")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_HTML, schema = @Schema(implementation = String.class))),
                           @ApiResponse(responseCode = "404", description = "Template not found")})
    public Response getInvoiceTemplate(@jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(null, TenantKey.INVOICE_TEMPLATE, request);
    }

    @TimedResource
    @POST
    @Produces(TEXT_HTML)
    @Consumes(TEXT_HTML)
    @Path("/" + INVOICE_TEMPLATE)
    @Operation(summary = "Upload the invoice template for the tenant")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_HTML, schema = @Schema(implementation = String.class))),
                           @ApiResponse(responseCode = "201", description = "Uploaded invoice template Successfully")})
    public Response uploadInvoiceTemplate(final String catalogTranslation,
                                          @QueryParam(QUERY_DELETE_IF_EXISTS) @DefaultValue("false") final boolean deleteIfExists,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                          @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
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
    @Operation(summary = "Retrieves the manualPay invoice template for the tenant")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_HTML, schema = @Schema(implementation = String.class))),
                           @ApiResponse(responseCode = "404", description = "Template not found")})
    public Response getInvoiceMPTemplate(@PathParam("locale") final String localeStr,
                                         @jakarta.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(null, TenantKey.INVOICE_MP_TEMPLATE, request);
    }

    @TimedResource
    @POST
    @Produces(TEXT_HTML)
    @Consumes(TEXT_HTML)
    @Path("/" + INVOICE_MP_TEMPLATE)
    @Operation(summary = "Upload the manualPay invoice template for the tenant")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = TEXT_HTML, schema = @Schema(implementation = String.class)))})
    public Response uploadInvoiceMPTemplate(final String catalogTranslation,
                                            @QueryParam(QUERY_DELETE_IF_EXISTS) @DefaultValue("false") final boolean deleteIfExists,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                            @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
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
            final InputStream stream = new ByteArrayInputStream(templateResource.getBytes(StandardCharsets.UTF_8));
            new PropertyResourceBundle(stream);
            final Locale locale = LocaleUtils.toLocale(localeStr);
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
        return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, getMethodStr, Objects.requireNonNullElse(localeStr, defaultLocale.toString()), request);
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
    @Operation(summary = "Retrieve invoice custom fields", operationId = "getInvoiceCustomFields")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CustomFieldJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @jakarta.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(id, auditMode, context.createTenantContextNoAccountId(request));
    }

    @TimedResource
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add custom fields to invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CustomField.class)))),
                           @ApiResponse(responseCode = "201", description = "Custom field created successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied")})
    public Response createInvoiceCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                              final List<CustomFieldJson> customFields,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                              @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request), uriInfo, request);
    }


    @TimedResource
    @PUT
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Modify custom fields to invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied")})
    public Response modifyInvoiceCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                              final List<CustomFieldJson> customFields,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @jakarta.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.modifyCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @DELETE
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Remove custom fields from invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied")})
    public Response deleteInvoiceCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                              @QueryParam(QUERY_CUSTOM_FIELD) final List<UUID> customFieldList,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @jakarta.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(id, customFieldList,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve invoice tags", operationId = "getInvoiceTags")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied"),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID invoiceId,
                            @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, InvoiceApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        // See https://github.com/killbill/killbill/issues/1273
        final UUID accountId = AuditLevel.NONE.equals(auditMode.getLevel()) ? null : invoiceApi.getInvoice(invoiceId, tenantContext).getAccountId();
        return super.getTags(accountId, invoiceId, auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add tags to invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagJson.class)))),
                           @ApiResponse(responseCode = "201", description = "Tag created successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied")})
    public Response createInvoiceTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                      final List<UUID> tagList,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @jakarta.ws.rs.core.Context final UriInfo uriInfo,
                                      @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(id, tagList, uriInfo,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request), request);
    }

    @TimedResource
    @DELETE
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Remove tags from invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied")})
    public Response deleteInvoiceTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                      @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(id, tagList,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @PUT
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + COMMIT_INVOICE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Perform the invoice status transition from DRAFT to COMMITTED")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response commitInvoice(@PathParam("invoiceId") final UUID invoiceId,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                  @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws InvoiceApiException {

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        invoiceApi.commitInvoice(invoiceId, callContext);
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @PUT
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + VOID_INVOICE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Perform the action of voiding an invoice")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice id supplied"),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response voidInvoice(@PathParam("invoiceId") final UUID invoiceId,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws InvoiceApiException {

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

                final PlanPhaseSpecifier planPhaseSpecifier;
                if (input.getPlanName() != null) {
                    planPhaseSpecifier = new PlanPhaseSpecifier(input.getPlanName());

                } else if (input.getProductName() != null && input.getProductCategory() != null && input.getBillingPeriod() != null) {
                    planPhaseSpecifier = new PlanPhaseSpecifier(input.getProductName(),
                                                                input.getBillingPeriod(),
                                                                input.getPriceListName(),
                                                                input.getPhaseType() != null ? input.getPhaseType() : null);
                } else {
                    planPhaseSpecifier = null;
                }
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
                    public Integer getQuantity() {
                        return null;
                    }

                    @Override
                    public String getExternalKey() {
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
