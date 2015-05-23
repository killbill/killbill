/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.InvoiceDryRunJson;
import org.killbill.billing.jaxrs.json.InvoiceItemJson;
import org.killbill.billing.jaxrs.json.InvoiceJson;
import org.killbill.billing.jaxrs.json.InvoicePaymentJson;
import org.killbill.billing.jaxrs.json.PhasePriceOverrideJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
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
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(JaxrsResource.INVOICES_PATH)
@Api(value = JaxrsResource.INVOICES_PATH, description = "Operations on invoices")
public class InvoiceResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(InvoiceResource.class);
    private static final String ID_PARAM_NAME = "invoiceId";
    private static final String LOCALE_PARAM_NAME = "locale";

    private final InvoiceUserApi invoiceApi;
    private final InvoiceNotifier invoiceNotifier;
    private final TenantUserApi tenantApi;
    private final Locale defaultLocale;

    @Inject
    public InvoiceResource(final AccountUserApi accountUserApi,
                           final InvoiceUserApi invoiceApi,
                           final PaymentApi paymentApi,
                           final InvoiceNotifier invoiceNotifier,
                           final Clock clock,
                           final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final TenantUserApi tenantApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
        this.invoiceApi = invoiceApi;
        this.invoiceNotifier = invoiceNotifier;
        this.tenantApi = tenantApi;
        this.defaultLocale = Locale.getDefault();
    }

    @Timed
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an invoice by id", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response getInvoice(@PathParam("invoiceId") final String invoiceId,
                               @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Invoice invoice = invoiceApi.getInvoice(UUID.fromString(invoiceId), tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
        } else {
            final InvoiceJson json = new InvoiceJson(invoice, withItems, accountAuditLogs);
            return Response.status(Status.OK).entity(json).build();
        }
    }

    @Timed
    @GET
    @Path("/{invoiceNumber:" + NUMBER_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an invoice by number", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Invoice not found")})
    public Response getInvoiceByNumber(@PathParam("invoiceNumber") final Integer invoiceNumber,
                                       @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Invoice invoice = invoiceApi.getInvoiceByNumber(invoiceNumber, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceNumber);
        } else {
            final InvoiceJson json = new InvoiceJson(invoice, withItems, accountAuditLogs);
            return Response.status(Status.OK).entity(json).build();
        }
    }

    @Timed
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/html")
    @Produces(TEXT_HTML)
    @ApiOperation(value = "Render an invoice as HTML", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Invoice not found")})
    public Response getInvoiceAsHTML(@PathParam("invoiceId") final String invoiceId,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, IOException, AccountApiException {
        return Response.status(Status.OK).entity(invoiceApi.getInvoiceAsHTML(UUID.fromString(invoiceId), context.createContext(request))).build();
    }

    @Timed
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
        final TenantContext tenantContext = context.createContext(request);
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
                                                        return new InvoiceJson(invoice, withItems, accountsAuditLogs.get().get(invoice.getAccountId()));
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @Timed
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
        final TenantContext tenantContext = context.createContext(request);
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
                                                        return new InvoiceJson(invoice, withItems, accountsAuditLogs.get().get(invoice.getAccountId()));
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @Timed
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Trigger an invoice generation", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id or target datetime supplied")})
    public Response createFutureInvoice(@QueryParam(QUERY_ACCOUNT_ID) final String accountId,
                                        @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final HttpServletRequest request,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final LocalDate inputDate = toLocalDate(UUID.fromString(accountId), targetDate, callContext);

        try {
            final Invoice generatedInvoice = invoiceApi.triggerInvoiceGeneration(UUID.fromString(accountId), inputDate, null,
                                                                                 callContext);
            return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, "getInvoice", generatedInvoice.getId());
        } catch (InvoiceApiException e) {
            if (e.getCode() == ErrorCode.INVOICE_NOTHING_TO_DO.getCode()) {
                return Response.status(Status.NOT_FOUND).build();
            }
            throw e;
        }
    }


    @Timed
    @POST
    @Path("/" + DRY_RUN)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Generate a dryRun invoice", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id or target datetime supplied")})
    public Response generateDryRunInvoice(@Nullable final InvoiceDryRunJson dryRunSubscriptionSpec,
                                          @QueryParam(QUERY_ACCOUNT_ID) final String accountId,
                                          @Nullable @QueryParam(QUERY_TARGET_DATE) final String targetDate,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final HttpServletRequest request,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final LocalDate inputDate;
        // In the case of subscription dryRun we set the targetDate to be the effective date of the change itself
        if (dryRunSubscriptionSpec != null && dryRunSubscriptionSpec.getEffectiveDate() != null) {
            inputDate = dryRunSubscriptionSpec.getEffectiveDate();
        // In case of Invoice dryRun we also allow the special value UPCOMING_INVOICE_TARGET_DATE where the system will automatically
        // generate the resulting targetDate for upcoming invoice; in terms of invoice api that maps to passing a null targetDate
        } else if (targetDate != null && targetDate.equals(UPCOMING_INVOICE_TARGET_DATE)) {
            inputDate = null;
        // Finally, in case of Invoice dryRun, we allow a null input date (will default to NOW), or extract the value provided
        } else {
            inputDate = toLocalDate(UUID.fromString(accountId), targetDate, callContext);
        }

        // Passing a null or empty body means we are trying to generate an invoice with a (future) targetDate
        // On the other hand if body is not null, we are attempting a dryRun subscription operation
        if (dryRunSubscriptionSpec != null && dryRunSubscriptionSpec.getDryRunAction() != null) {
            if (SubscriptionEventType.START_BILLING.toString().equals(dryRunSubscriptionSpec.getDryRunAction())) {
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getProductName(), "DryRun subscription product category should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBillingPeriod(), "DryRun subscription billingPeriod should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getProductCategory(), "DryRun subscription product category should be specified");
                if (dryRunSubscriptionSpec.getProductCategory().equals(ProductCategory.ADD_ON)) {
                    verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBundleId(), "DryRun bundle ID should be specified");
                }
            } else if (SubscriptionEventType.CHANGE.toString().equals(dryRunSubscriptionSpec.getDryRunAction())) {
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getProductName(), "DryRun subscription product category should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getBillingPeriod(), "DryRun subscription billingPeriod should be specified");
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getSubscriptionId(), "DryRun subscriptionID should be specified");
            } else if (SubscriptionEventType.STOP_BILLING.toString().equals(dryRunSubscriptionSpec.getDryRunAction())) {
                verifyNonNullOrEmpty(dryRunSubscriptionSpec.getSubscriptionId(), "DryRun subscriptionID should be specified");
            }
        }

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), callContext);

        final DryRunArguments dryRunArguments = new DefaultDryRunArguments(dryRunSubscriptionSpec, account.getTimeZone(), account.getCurrency(), clock);
        try {
            final Invoice generatedInvoice = invoiceApi.triggerInvoiceGeneration(UUID.fromString(accountId), inputDate, dryRunArguments,
                                                                                 callContext);
            return Response.status(Status.OK).entity(new InvoiceJson(generatedInvoice, true, null)).build();
        } catch (InvoiceApiException e) {
            if (e.getCode() == ErrorCode.INVOICE_NOTHING_TO_DO.getCode()) {
                return Response.status(Status.NOT_FOUND).build();
            }
            throw e;
        }
    }

    @Timed
    @DELETE
    @Path("/{invoiceId:" + UUID_PATTERN + "}" + "/{invoiceItemId:" + UUID_PATTERN + "}/cba")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Delete a CBA item")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id, invoice id or invoice item id supplied"),
                           @ApiResponse(code = 404, message = "Account or invoice not found")})
    public Response deleteCBA(@PathParam("invoiceId") final String invoiceId,
                              @PathParam("invoiceItemId") final String invoiceItemId,
                              @QueryParam(QUERY_ACCOUNT_ID) final String accountId,
                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                              @HeaderParam(HDR_REASON) final String reason,
                              @HeaderParam(HDR_COMMENT) final String comment,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), callContext);

        invoiceApi.deleteCBA(account.getId(), UUID.fromString(invoiceId), UUID.fromString(invoiceItemId), callContext);

        return Response.status(Status.OK).build();
    }

    @Timed
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Adjust an invoice item")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id, invoice id or invoice item id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response adjustInvoiceItem(final InvoiceItemJson json,
                                      @PathParam("invoiceId") final String invoiceId,
                                      @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final HttpServletRequest request,
                                      @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        verifyNonNullOrEmpty(json, "InvoiceItemJson body should be specified");
        verifyNonNullOrEmpty(json.getAccountId(), "InvoiceItemJson accountId needs to be set",
                             json.getInvoiceItemId(), "InvoiceItemJson invoiceItemId needs to be set");

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID accountId = UUID.fromString(json.getAccountId());
        final LocalDate requestedDate = toLocalDate(accountId, requestedDateTimeString, callContext);
        final InvoiceItem adjustmentItem;
        if (json.getAmount() == null) {
            adjustmentItem = invoiceApi.insertInvoiceItemAdjustment(accountId,
                                                                    UUID.fromString(invoiceId),
                                                                    UUID.fromString(json.getInvoiceItemId()),
                                                                    requestedDate,
                                                                    callContext);
        } else {
            adjustmentItem = invoiceApi.insertInvoiceItemAdjustment(accountId,
                                                                    UUID.fromString(invoiceId),
                                                                    UUID.fromString(json.getInvoiceItemId()),
                                                                    requestedDate,
                                                                    json.getAmount(),
                                                                    json.getCurrency(),
                                                                    callContext);
        }

        return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, "getInvoice", adjustmentItem.getInvoiceId());
    }

    @Timed
    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/" + CHARGES + "/{accountId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Create external charge(s)", response = InvoiceItemJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createExternalCharges(final Iterable<InvoiceItemJson> externalChargesJson,
                                          @PathParam("accountId") final String accountId,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                          @QueryParam(QUERY_PAY_INVOICE) @DefaultValue("false") final Boolean payInvoice,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), callContext);

        // TODO Get rid of that check once we truly support multiple currencies per account
        // See discussion https://github.com/killbill/killbill/commit/942e214d49e9c7ed89da76d972ee017d2d3ade58#commitcomment-6045547
        final Set<Currency> currencies = new HashSet<Currency>(Lists.<InvoiceItemJson, Currency>transform(ImmutableList.<InvoiceItemJson>copyOf(externalChargesJson),
                                                                                                          new Function<InvoiceItemJson, Currency>() {
                                                                                                              @Override
                                                                                                              public Currency apply(final InvoiceItemJson input) {
                                                                                                                  return input.getCurrency();
                                                                                                              }
                                                                                                          }
                                                                                                         ));
        if (currencies.size() != 1 || !currencies.iterator().next().equals(account.getCurrency())) {
            throw new InvoiceApiException(ErrorCode.CURRENCY_INVALID, currencies.iterator().next(), account.getCurrency());
        }

        // Get the effective date of the external charge, in the account timezone
        final LocalDate requestedDate = toLocalDate(account, requestedDateTimeString, callContext);

        final Iterable<InvoiceItem> externalCharges = Iterables.<InvoiceItemJson, InvoiceItem>transform(externalChargesJson,
                                                                                                        new Function<InvoiceItemJson, InvoiceItem>() {
                                                                                                            @Override
                                                                                                            public InvoiceItem apply(final InvoiceItemJson invoiceItemJson) {
                                                                                                                return invoiceItemJson.toInvoiceItem();
                                                                                                            }
                                                                                                        }
                                                                                                       );
        final List<InvoiceItem> createdExternalCharges = invoiceApi.insertExternalCharges(account.getId(), requestedDate, externalCharges, callContext);

        if (payInvoice) {
            final Collection<UUID> paidInvoices = new HashSet<UUID>();
            for (final InvoiceItem externalCharge : createdExternalCharges) {
                if (!paidInvoices.contains(externalCharge.getInvoiceId())) {
                    paidInvoices.add(externalCharge.getInvoiceId());
                    final Invoice invoice = invoiceApi.getInvoice(externalCharge.getInvoiceId(), callContext);
                    createPurchaseForInvoice(account, invoice.getId(), invoice.getBalance(), false, pluginProperties, callContext);
                }
            }
        }

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

    @Timed
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payments associated with an invoice", response = InvoicePaymentJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response getPayments(@PathParam("invoiceId") final String invoiceId,
                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, InvoiceApiException {
        final TenantContext tenantContext = context.createContext(request);

        final Invoice invoice = invoiceApi.getInvoice(UUID.fromString(invoiceId), tenantContext);
        final List<Payment> payments = new ArrayList<Payment>();
        for (InvoicePayment cur : invoice.getPayments()) {
            final Payment payment = paymentApi.getPayment(cur.getPaymentId(), withPluginInfo, ImmutableList.<PluginProperty>of(), tenantContext);
            payments.add(payment);
        }
        final List<InvoicePaymentJson> result = new ArrayList<InvoicePaymentJson>(payments.size());
        if (payments.isEmpty()) {
            return Response.status(Status.OK).entity(result).build();
        }
        for (final Payment cur : payments) {
            result.add(new InvoicePaymentJson(cur, invoice.getId(), null));
        }
        return Response.status(Status.OK).entity(result).build();
    }

    @Timed
    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @ApiOperation(value = "Trigger a payment for invoice")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id or invoice id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createInstantPayment(final InvoicePaymentJson payment,
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

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(payment.getAccountId()), callContext);
        final UUID invoiceId = UUID.fromString(payment.getTargetInvoiceId());
        final Payment result = createPurchaseForInvoice(account, invoiceId, payment.getPurchasedAmount(), externalPayment, pluginProperties, callContext);
        // STEPH should that live in InvoicePayment instead?
        return uriBuilder.buildResponse(uriInfo, InvoicePaymentResource.class, "getInvoicePayment", result.getId());
    }

    @Timed
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + EMAIL_NOTIFICATIONS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Trigger an email notification for invoice")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied"),
                           @ApiResponse(code = 404, message = "Account or invoice not found")})
    public Response triggerEmailNotificationForInvoice(@PathParam("invoiceId") final String invoiceId,
                                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                       @HeaderParam(HDR_REASON) final String reason,
                                                       @HeaderParam(HDR_COMMENT) final String comment,
                                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Invoice invoice = invoiceApi.getInvoice(UUID.fromString(invoiceId), callContext);
        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
        }

        final Account account = accountUserApi.getAccountById(invoice.getAccountId(), callContext);

        // Send the email (synchronous send)
        invoiceNotifier.notify(account, invoice, callContext);

        return Response.status(Status.OK).build();
    }

    @Timed
    @GET
    @Path("/" + INVOICE_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @Produces(TEXT_PLAIN)
    @ApiOperation(value = "Retrieves the invoice translation for the tenant", response = String.class, hidden = true)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid locale supplied"),
                           @ApiResponse(code = 404, message = "Translation not found")})
    public Response getInvoiceTranslation(@PathParam("locale") final String localeStr,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(localeStr, TenantKey.INVOICE_TRANSLATION_, request);
    }

    @Timed
    @POST
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Path("/" + INVOICE_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @ApiOperation(value = "Upload the invoice translation for the tenant")
    @ApiResponses(value = {})
    public Response uploadInvoiceTranslation(final String invoiceTranslation,
                                             @PathParam("locale") final String localeStr,
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

    @Timed
    @GET
    @Path("/" + INVOICE_CATALOG_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @Produces(TEXT_PLAIN)
    @ApiOperation(value = "Retrieves the catalog translation for the tenant", response = String.class, hidden = true)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid locale supplied"),
                           @ApiResponse(code = 404, message = "Template not found")})
    public Response getCatalogTranslation(@PathParam("locale") final String localeStr,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(localeStr, TenantKey.CATALOG_TRANSLATION_, request);
    }

    @Timed
    @POST
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Path("/" + INVOICE_CATALOG_TRANSLATION + "/{locale:" + ANYTHING_PATTERN + "}/")
    @ApiOperation(value = "Upload the catalog translation for the tenant")
    @ApiResponses(value = {})
    public Response uploadCatalogTranslation(final String catalogTranslation,
                                             @PathParam("locale") final String localeStr,
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

    @Timed
    @GET
    @Path("/" + INVOICE_TEMPLATE)
    @Produces(TEXT_HTML)
    @ApiOperation(value = "Retrieves the invoice template for the tenant", response = String.class, hidden = true)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Template not found")})
    public Response getInvoiceTemplate(@javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(null, TenantKey.INVOICE_TEMPLATE, request);
    }

    @Timed
    @POST
    @Produces(TEXT_HTML)
    @Consumes(TEXT_HTML)
    @Path("/" + INVOICE_TEMPLATE)
    @ApiOperation(value = "Upload the invoice template for the tenant")
    @ApiResponses(value = {})
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

    @Timed
    @GET
    @Path("/" + INVOICE_MP_TEMPLATE)
    @Produces(TEXT_HTML)
    @ApiOperation(value = "Retrieves the manualPay invoice template for the tenant", response = String.class, hidden = true)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Template not found")})
    public Response getInvoiceMPTemplate(@PathParam("locale") final String localeStr,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        return getTemplateResource(null, TenantKey.INVOICE_MP_TEMPLATE, request);
    }

    @Timed
    @POST
    @Produces(TEXT_HTML)
    @Consumes(TEXT_HTML)
    @Path("/" + INVOICE_MP_TEMPLATE)
    @ApiOperation(value = "Upload the manualPay invoice template for the tenant")
    @ApiResponses(value = {})
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

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        if (!tenantApi.getTenantValuesForKey(tenantKeyStr, callContext).isEmpty()) {
            if (deleteIfExists) {
                tenantApi.deleteTenantKey(tenantKeyStr, callContext);
            } else {
                return Response.status(Status.BAD_REQUEST).build();
            }
        }
        tenantApi.addTenantKeyValue(tenantKeyStr, templateResource, callContext);
        return uriBuilder.buildResponse(uriInfo, InvoiceResource.class, getMethodStr, localeStr);
    }

    private Response getTemplateResource(@Nullable final String localeStr,
                                         final TenantKey tenantKey,
                                         final HttpServletRequest request) throws InvoiceApiException, TenantApiException {
        final TenantContext tenantContext = context.createContext(request);
        final String tenantKeyStr = localeStr != null ?
                                    LocaleUtils.localeString(LocaleUtils.toLocale(localeStr), tenantKey.toString()) :
                                    tenantKey.toString();
        final List<String> result = tenantApi.getTenantValuesForKey(tenantKeyStr, tenantContext);
        return result.isEmpty() ? Response.status(Status.NOT_FOUND).build() : Response.status(Status.OK).entity(result.get(0)).build();
    }

    @Timed
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve invoice custom fields", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @Timed
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to invoice")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request), uriInfo);
    }

    @Timed
    @DELETE
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from invoice")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @Timed
    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve invoice tags", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final String invoiceIdString,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, InvoiceApiException {
        final UUID invoiceId = UUID.fromString(invoiceIdString);
        final TenantContext tenantContext = context.createContext(request);
        final Invoice invoice = invoiceApi.getInvoice(invoiceId, tenantContext);
        return super.getTags(invoice.getAccountId(), invoiceId, auditMode, includedDeleted, tenantContext);
    }

    @Timed
    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to invoice")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment, request));
    }

    @Timed
    @DELETE
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from invoice")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid invoice id supplied")})
    public Response deleteTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(UUID.fromString(id), tagList,
                                context.createContext(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE;
    }

    private static class DefaultDryRunArguments implements DryRunArguments {

        private final SubscriptionEventType action;
        private final UUID subscriptionId;
        private final DateTime effectiveDate;
        private final PlanPhaseSpecifier specifier;
        private final UUID bundleId;
        private final BillingActionPolicy billingPolicy;
        private final List<PlanPhasePriceOverride> overrides;

        public DefaultDryRunArguments(final InvoiceDryRunJson input, final DateTimeZone accountTimeZone, final Currency currency, final Clock clock) {
            if (input == null) {
                this.action = null;
                this.subscriptionId = null;
                this.effectiveDate = null;
                this.specifier = null;
                this.bundleId = null;
                this.billingPolicy = null;
                this.overrides = null;
            } else {
                this.action = input.getDryRunAction() != null ? SubscriptionEventType.valueOf(input.getDryRunAction()) : null;
                this.subscriptionId = input.getSubscriptionId() != null ? UUID.fromString(input.getSubscriptionId()) : null;
                this.bundleId = input.getBundleId() != null ? UUID.fromString(input.getBundleId()) : null;
                this.effectiveDate = input.getEffectiveDate() != null ? ClockUtil.computeDateTimeWithUTCReferenceTime(input.getEffectiveDate(), clock.getUTCNow().toLocalTime(), accountTimeZone, clock) : null;
                this.billingPolicy = input.getBillingPolicy() != null ? BillingActionPolicy.valueOf(input.getBillingPolicy()) : null;
                final PlanPhaseSpecifier planPhaseSpecifier  = (input.getProductName() != null &&
                                     input.getProductCategory() != null &&
                                     input.getBillingPeriod() != null) ?
                                    new PlanPhaseSpecifier(input.getProductName(),
                                                           ProductCategory.valueOf(input.getProductCategory()),
                                                           BillingPeriod.valueOf(input.getBillingPeriod()),
                                                           input.getPriceListName(),
                                                           input.getPhaseType() != null ? PhaseType.valueOf(input.getPhaseType()) : null) :
                                    null;
                this.specifier = planPhaseSpecifier;
                this.overrides = input.getPriceOverrides() != null ?
                                 ImmutableList.copyOf(Iterables.transform(input.getPriceOverrides(), new Function<PhasePriceOverrideJson, PlanPhasePriceOverride>() {
                    @Nullable
                    @Override
                    public PlanPhasePriceOverride apply(@Nullable final PhasePriceOverrideJson input) {
                        if (input.getPhaseName() != null) {
                            return new DefaultPlanPhasePriceOverride(input.getPhaseName(), currency, input.getFixedPrice(), input.getRecurringPrice());
                        } else {
                            return new DefaultPlanPhasePriceOverride(planPhaseSpecifier, currency, input.getFixedPrice(), input.getRecurringPrice());
                        }
                    }
                })) : ImmutableList.<PlanPhasePriceOverride>of();
            }
        }

        @Override
        public PlanPhaseSpecifier getPlanPhaseSpecifier() {
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
        public DateTime getEffectiveDate() {
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
        public List<PlanPhasePriceOverride> getPlanPhasePriceoverrides() {
            return overrides;
        }
    }

}
