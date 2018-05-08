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

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.OrderingType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentApi;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.JaxrsExecutors;
import org.killbill.billing.jaxrs.json.AccountEmailJson;
import org.killbill.billing.jaxrs.json.AccountJson;
import org.killbill.billing.jaxrs.json.AccountTimelineJson;
import org.killbill.billing.jaxrs.json.AuditLogJson;
import org.killbill.billing.jaxrs.json.BlockingStateJson;
import org.killbill.billing.jaxrs.json.BundleJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.InvoiceEmailJson;
import org.killbill.billing.jaxrs.json.InvoiceJson;
import org.killbill.billing.jaxrs.json.InvoicePaymentJson;
import org.killbill.billing.jaxrs.json.OverdueStateJson;
import org.killbill.billing.jaxrs.json.PaymentJson;
import org.killbill.billing.jaxrs.json.PaymentMethodJson;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.overdue.api.OverdueApi;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.definition.JaxrsConfig;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.MetricTag;
import org.killbill.commons.metrics.TimedResource;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.ACCOUNTS_PATH)
@Api(value = JaxrsResource.ACCOUNTS_PATH, description = "Operations on accounts", tags = "Account")
public class AccountResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "accountId";

    private final SubscriptionApi subscriptionApi;
    private final InvoiceUserApi invoiceApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final OverdueApi overdueApi;
    private final PaymentConfig paymentConfig;
    private final JaxrsExecutors jaxrsExecutors;
    private final JaxrsConfig jaxrsConfig;
    private final RecordIdApi recordIdApi;
    private final NotificationQueueService notificationQueueService;

    @Inject
    public AccountResource(final JaxrsUriBuilder uriBuilder,
                           final AccountUserApi accountApi,
                           final InvoiceUserApi invoiceApi,
                           final InvoicePaymentApi invoicePaymentApi,
                           final PaymentApi paymentApi,
                           final TagUserApi tagUserApi,
                           final AuditUserApi auditUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final SubscriptionApi subscriptionApi,
                           final OverdueApi overdueApi,
                           final Clock clock,
                           final PaymentConfig paymentConfig,
                           final JaxrsExecutors jaxrsExecutors,
                           final JaxrsConfig jaxrsConfig,
                           final Context context,
                           final RecordIdApi recordIdApi,
                           final NotificationQueueService notificationQueueService) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountApi, paymentApi, subscriptionApi, clock, context);
        this.subscriptionApi = subscriptionApi;
        this.invoiceApi = invoiceApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.overdueApi = overdueApi;
        this.paymentConfig = paymentConfig;
        this.jaxrsExecutors = jaxrsExecutors;
        this.jaxrsConfig = jaxrsConfig;
        this.recordIdApi = recordIdApi;
        this.notificationQueueService = notificationQueueService;
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an account by id", response = AccountJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getAccount(@PathParam("accountId") final UUID accountId,
                               @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                               @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final Account account = accountUserApi.getAccountById(accountId, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(account.getId(), auditMode.getLevel(), tenantContext);
        final AccountJson accountJson = getAccount(account, accountWithBalance, accountWithBalanceAndCBA, accountAuditLogs, tenantContext);
        return Response.status(Status.OK).entity(accountJson).build();
    }

    @TimedResource
    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "List accounts", response = AccountJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getAccounts(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                                @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<Account> accounts = accountUserApi.getAccounts(offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(AccountResource.class, "getAccounts", accounts.getNextOffset(), limit, ImmutableMap.<String, String>of(QUERY_ACCOUNT_WITH_BALANCE, accountWithBalance.toString(),
                                                                                                                                                           QUERY_ACCOUNT_WITH_BALANCE_AND_CBA, accountWithBalanceAndCBA.toString(),
                                                                                                                                                           QUERY_AUDIT, auditMode.getLevel().toString()));
        return buildStreamingPaginationResponse(accounts,
                                                new Function<Account, AccountJson>() {
                                                    @Override
                                                    public AccountJson apply(final Account account) {
                                                        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(account.getId(), auditMode.getLevel(), tenantContext);
                                                        return getAccount(account, accountWithBalance, accountWithBalanceAndCBA, accountAuditLogs, tenantContext);
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource
    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Search accounts", response = AccountJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response searchAccounts(@PathParam("searchKey") final String searchKey,
                                   @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                   @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                   @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                                   @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                                   @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<Account> accounts = accountUserApi.searchAccounts(searchKey, offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(AccountResource.class, "searchAccounts", accounts.getNextOffset(), limit, ImmutableMap.<String, String>of("searchKey", searchKey,
                                                                                                                                                              QUERY_ACCOUNT_WITH_BALANCE, accountWithBalance.toString(),
                                                                                                                                                              QUERY_ACCOUNT_WITH_BALANCE_AND_CBA, accountWithBalanceAndCBA.toString(),
                                                                                                                                                              QUERY_AUDIT, auditMode.getLevel().toString()));
        return buildStreamingPaginationResponse(accounts,
                                                new Function<Account, AccountJson>() {
                                                    @Override
                                                    public AccountJson apply(final Account account) {
                                                        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(account.getId(), auditMode.getLevel(), tenantContext);
                                                        return getAccount(account, accountWithBalance, accountWithBalanceAndCBA, accountAuditLogs, tenantContext);
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + BUNDLES)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve bundles for account", response = BundleJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getAccountBundles(@PathParam("accountId") final UUID accountId,
                                      @QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                      @QueryParam(QUERY_BUNDLES_FILTER) final String bundlesFilter,
                                      @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, SubscriptionApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);

        final Account account = accountUserApi.getAccountById(accountId, tenantContext);

        final List<SubscriptionBundle> bundles = (externalKey != null) ?
                                                 subscriptionApi.getSubscriptionBundlesForAccountIdAndExternalKey(accountId, externalKey, tenantContext) :
                                                 subscriptionApi.getSubscriptionBundlesForAccountId(accountId, tenantContext);

        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(account.getId(), auditMode.getLevel(), tenantContext);

        boolean filter = (null != bundlesFilter && !bundlesFilter.isEmpty());

        final Collection<BundleJson> result = Collections2.transform(
                (filter) ? filterBundles(bundles, Arrays.asList(bundlesFilter.split(","))) : bundles, new Function<SubscriptionBundle, BundleJson>() {
                    @Override
                    public BundleJson apply(final SubscriptionBundle input) {
                        try {
                            return new BundleJson(input, account.getCurrency(), accountAuditLogs);
                        } catch (final CatalogApiException e) {
                            // Not the cleanest thing, but guava Api don't allow throw..
                            throw new RuntimeException(e);
                        }
                    }
                });
        return Response.status(Status.OK).entity(result).build();
    }

    private List<SubscriptionBundle> filterBundles(final List<SubscriptionBundle> subscriptionBundlesForAccountId, final List<String> bundlesFilter) {
        List<SubscriptionBundle> result = new ArrayList<SubscriptionBundle>();
        for (SubscriptionBundle subscriptionBundle : subscriptionBundlesForAccountId) {
            if (bundlesFilter.contains(subscriptionBundle.getId().toString())) {
                result.add(subscriptionBundle);
            }
        }
        return result;
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an account by external key", response = AccountJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account not found")})
    public Response getAccountByKey(@ApiParam(required = true) @QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                    @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                                    @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Account account = accountUserApi.getAccountByKey(externalKey, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(account.getId(), auditMode.getLevel(), tenantContext);
        final AccountJson accountJson = getAccount(account, accountWithBalance, accountWithBalanceAndCBA, accountAuditLogs, tenantContext);
        return Response.status(Status.OK).entity(accountJson).build();
    }

    private AccountJson getAccount(final Account account, final Boolean accountWithBalance, final Boolean accountWithBalanceAndCBA,
                                   final AccountAuditLogs auditLogs, final TenantContext tenantContext) {
        if (accountWithBalanceAndCBA) {
            final BigDecimal accountBalance = invoiceApi.getAccountBalance(account.getId(), tenantContext);
            final BigDecimal accountCBA = invoiceApi.getAccountCBA(account.getId(), tenantContext);
            return new AccountJson(account, accountBalance, accountCBA, auditLogs);
        } else if (accountWithBalance) {
            final BigDecimal accountBalance = invoiceApi.getAccountBalance(account.getId(), tenantContext);
            return new AccountJson(account, accountBalance, null, auditLogs);
        } else {
            return new AccountJson(account, null, null, auditLogs);
        }
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create account", response = AccountJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Account created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account data supplied")})
    public Response createAccount(final AccountJson json,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException {
        verifyNonNullOrEmpty(json, "AccountJson body should be specified");

        final AccountData data = json.toAccount(null);
        final Account account = accountUserApi.createAccount(data, context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(uriInfo, AccountResource.class, "getAccount", account.getId(), request);
    }

    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Update account")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account data supplied")})
    public Response updateAccount(@PathParam("accountId") final UUID accountId,
                                  final AccountJson json,
                                  @QueryParam(QUERY_ACCOUNT_TREAT_NULL_AS_RESET) @DefaultValue("false") final Boolean treatNullValueAsReset,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        verifyNonNullOrEmpty(json, "AccountJson body should be specified");

        final Account data = json.toAccount(accountId);
        if (treatNullValueAsReset) {
            accountUserApi.updateAccount(data, context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request));
        } else {
            accountUserApi.updateAccount(accountId, data, context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request));
        }
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Close account")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response closeAccount(@PathParam("accountId") final UUID accountId,
                                 @QueryParam(QUERY_CANCEL_ALL_SUBSCRIPTIONS) @DefaultValue("false") final Boolean cancelAllSubscriptions,
                                 @QueryParam(QUERY_WRITE_OFF_UNPAID_INVOICES) @DefaultValue("false") final Boolean writeOffUnpaidInvoices,
                                 @QueryParam(QUERY_ITEM_ADJUST_UNPAID_INVOICES) @DefaultValue("false") final Boolean itemAdjustUnpaidInvoices,
                                 @QueryParam(QUERY_REMOVE_FUTURE_NOTIFICATIONS) @DefaultValue("true") final Boolean removeFutureNotifications,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, AccountApiException, EntitlementApiException, InvoiceApiException, TagApiException {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        if (cancelAllSubscriptions) {
            final List<SubscriptionBundle> bundles = subscriptionApi.getSubscriptionBundlesForAccountId(accountId, callContext);
            final Iterable<Subscription> subscriptions = Iterables.concat(Iterables.transform(bundles, new Function<SubscriptionBundle, List<Subscription>>() {
                @Override
                public List<Subscription> apply(final SubscriptionBundle input) {
                    return input.getSubscriptions();
                }
            }));

            final Iterable<Subscription> toBeCancelled = Iterables.filter(subscriptions, new Predicate<Subscription>() {
                @Override
                public boolean apply(final Subscription input) {
                    return input.getLastActiveProductCategory() != ProductCategory.ADD_ON && input.getBillingEndDate() == null;
                }
            });
            for (final Subscription cur : toBeCancelled) {
                cur.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
            }
        }

        final Collection<Invoice> unpaidInvoices = writeOffUnpaidInvoices || itemAdjustUnpaidInvoices ? invoiceApi.getUnpaidInvoicesByAccountId(accountId, null, callContext) : ImmutableList.<Invoice>of();
        if (writeOffUnpaidInvoices) {
            for (final Invoice cur : unpaidInvoices) {
                invoiceApi.tagInvoiceAsWrittenOff(cur.getId(), callContext);
            }
        } else if (itemAdjustUnpaidInvoices) {

            final List<InvoiceItemType> ADJUSTABLE_TYPES = ImmutableList.<InvoiceItemType>of(InvoiceItemType.EXTERNAL_CHARGE,
                                                                                             InvoiceItemType.FIXED,
                                                                                             InvoiceItemType.RECURRING,
                                                                                             InvoiceItemType.TAX,
                                                                                             InvoiceItemType.USAGE,
                                                                                             InvoiceItemType.PARENT_SUMMARY);
            final String description = comment != null ? comment : "Close Account";
            for (final Invoice invoice : unpaidInvoices) {
                for (final InvoiceItem item : invoice.getInvoiceItems()) {
                    if (ADJUSTABLE_TYPES.contains(item.getInvoiceItemType())) {
                        invoiceApi.insertInvoiceItemAdjustment(accountId, invoice.getId(), item.getId(), clock.getUTCToday(), description, null, callContext);
                    }
                }
            }
        }

        final BlockingStateJson blockingState = new BlockingStateJson(accountId, "CLOSE_ACCOUNT", "account-service", true, false, false, null, BlockingStateType.ACCOUNT, null);
        addBlockingState(blockingState, accountId, accountId, BlockingStateType.ACCOUNT, null, ImmutableList.<String>of(), createdBy, reason, comment, request, null);

        if (removeFutureNotifications) {
            final Long tenantRecordId = recordIdApi.getRecordId(callContext.getTenantId(), ObjectType.TENANT, callContext);
            final Long accountRecordId = accountId == null ? null : recordIdApi.getRecordId(accountId, ObjectType.ACCOUNT, callContext);
            for (final NotificationQueue notificationQueue : notificationQueueService.getNotificationQueues()) {
                log.debug("Removing future notifications for queueName={}", notificationQueue.getFullQName());
                notificationQueue.removeFutureNotificationsForSearchKeys(accountRecordId, tenantRecordId);
            }
        }
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TIMELINE)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account timeline", response = AccountTimelineJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getAccountTimeline(@PathParam("accountId") final UUID accountId,
                                       @QueryParam(QUERY_PARALLEL) @DefaultValue("false") final Boolean parallel,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException, SubscriptionApiException, InvoiceApiException, CatalogApiException {

        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);

        final Account account = accountUserApi.getAccountById(accountId, tenantContext);

        final Callable<List<SubscriptionBundle>> bundlesCallable = new Callable<List<SubscriptionBundle>>() {
            @Override
            public List<SubscriptionBundle> call() throws Exception {
                return subscriptionApi.getSubscriptionBundlesForAccountId(accountId, tenantContext);
            }
        };
        final Callable<List<Invoice>> invoicesCallable = new Callable<List<Invoice>>() {
            @Override
            public List<Invoice> call() throws Exception {
                return invoiceApi.getInvoicesByAccount(accountId, false, false, tenantContext);
            }
        };
        final Callable<List<InvoicePayment>> invoicePaymentsCallable = new Callable<List<InvoicePayment>>() {
            @Override
            public List<InvoicePayment> call() throws Exception {
                return invoicePaymentApi.getInvoicePaymentsByAccount(accountId, tenantContext);
            }
        };
        final Callable<List<Payment>> paymentsCallable = new Callable<List<Payment>>() {
            @Override
            public List<Payment> call() throws Exception {
                return paymentApi.getAccountPayments(accountId, false, false, ImmutableList.<PluginProperty>of(), tenantContext);
            }
        };
        final Callable<AccountAuditLogs> auditsCallable = new Callable<AccountAuditLogs>() {
            @Override
            public AccountAuditLogs call() throws Exception {
                return auditUserApi.getAccountAuditLogs(accountId, auditMode.getLevel(), tenantContext);
            }
        };

        final AccountTimelineJson json;

        List<Invoice> invoices = null;
        List<SubscriptionBundle> bundles = null;
        List<InvoicePayment> invoicePayments = null;
        List<Payment> payments = null;
        AccountAuditLogs accountAuditLogs = null;

        if (parallel) {
            final ExecutorService executor = jaxrsExecutors.getJaxrsExecutorService();
            final Future<List<SubscriptionBundle>> futureBundlesCallable = executor.submit(bundlesCallable);
            final Future<List<Invoice>> futureInvoicesCallable = executor.submit(invoicesCallable);
            final Future<List<InvoicePayment>> futureInvoicePaymentsCallable = executor.submit(invoicePaymentsCallable);
            final Future<List<Payment>> futurePaymentsCallable = executor.submit(paymentsCallable);
            final Future<AccountAuditLogs> futureAuditsCallable = executor.submit(auditsCallable);
            final ImmutableList<Future> toBeCancelled = ImmutableList.<Future>of(futureBundlesCallable, futureInvoicesCallable, futureInvoicePaymentsCallable, futurePaymentsCallable, futureAuditsCallable);
            final int timeoutMsec = 100;

            final long ini = System.currentTimeMillis();
            do {
                bundles = (bundles == null) ? waitOnFutureAndHandleTimeout("bundles", futureBundlesCallable, timeoutMsec, toBeCancelled) : bundles;
                invoices = (invoices == null) ? waitOnFutureAndHandleTimeout("invoices", futureInvoicesCallable, timeoutMsec, toBeCancelled) : invoices;
                invoicePayments = (invoicePayments == null) ? waitOnFutureAndHandleTimeout("invoicePayments", futureInvoicePaymentsCallable, timeoutMsec, toBeCancelled) : invoicePayments;
                payments = (payments == null) ? waitOnFutureAndHandleTimeout("payments", futurePaymentsCallable, timeoutMsec, toBeCancelled) : payments;
                accountAuditLogs = (accountAuditLogs == null) ? waitOnFutureAndHandleTimeout("accountAuditLogs", futureAuditsCallable, timeoutMsec, toBeCancelled) : accountAuditLogs;
            } while ((System.currentTimeMillis() - ini < jaxrsConfig.getJaxrsTimeout().getMillis()) &&
                     (bundles == null || invoices == null || invoicePayments == null || payments == null || accountAuditLogs == null));

            if (bundles == null || invoices == null || invoicePayments == null || payments == null || accountAuditLogs == null) {
                Response.status(Status.SERVICE_UNAVAILABLE).build();
            }
        } else {
            invoices = runCallable("invoices", invoicesCallable);
            payments = runCallable("payments", paymentsCallable);
            bundles = runCallable("bundles", bundlesCallable);
            accountAuditLogs = runCallable("accountAuditLogs", auditsCallable);
            invoicePayments = runCallable("invoicePayments", invoicePaymentsCallable);
        }

        json = new AccountTimelineJson(account, invoices, payments, invoicePayments, bundles, accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }

    private <T> T waitOnFutureAndHandleTimeout(final String logSuffix, final Future<T> future, final long timeoutMsec, final Iterable<Future> toBeCancelled) throws PaymentApiException, AccountApiException, InvoiceApiException, SubscriptionApiException {
        try {
            return waitOnFutureAndHandleTimeout(future, timeoutMsec);
        } catch (final InterruptedException e) {
            log.warn("InterruptedException while retrieving {}", logSuffix, e);
            handleCallableException(e, toBeCancelled);
        } catch (final ExecutionException e) {
            log.warn("ExecutionException while retrieving {}", logSuffix, e);
            handleCallableException(e.getCause(), toBeCancelled);
        }

        // Never reached
        return null;
    }

    private <T> T waitOnFutureAndHandleTimeout(final Future<T> future, final long timeoutMsec) throws ExecutionException, InterruptedException {
        try {
            return future.get(timeoutMsec, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            return null;
        }
    }

    private <T> T runCallable(final String logSuffix, final Callable<T> callable) throws PaymentApiException, AccountApiException, InvoiceApiException, SubscriptionApiException {
        try {
            return callable.call();
        } catch (final Exception e) {
            log.warn("InterruptedException while retrieving {}", logSuffix, e);
            handleCallableException(e);
        }

        // Never reached
        return null;
    }

    private void handleCallableException(final Throwable causeOrException, final Iterable<Future> toBeCancelled) throws AccountApiException, SubscriptionApiException, PaymentApiException, InvoiceApiException {
        for (final Future f : toBeCancelled) {
            f.cancel(true);
        }
        handleCallableException(causeOrException);
    }

    private void handleCallableException(final Throwable causeOrException) throws AccountApiException, SubscriptionApiException, PaymentApiException, InvoiceApiException {
        if (causeOrException instanceof AccountApiException) {
            throw (AccountApiException) causeOrException;
        } else if (causeOrException instanceof SubscriptionApiException) {
            throw (SubscriptionApiException) causeOrException;
        } else if (causeOrException instanceof InvoiceApiException) {
            throw (InvoiceApiException) causeOrException;
        } else if (causeOrException instanceof PaymentApiException) {
            throw (PaymentApiException) causeOrException;
        } else {
            if (causeOrException instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException(causeOrException.getMessage(), causeOrException);
        }
    }

    /*
    * ************************** EMAIL NOTIFICATIONS FOR INVOICES ********************************
    */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAIL_NOTIFICATIONS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account email notification", response = InvoiceEmailJson.class)
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getEmailNotificationsForAccount(@PathParam("accountId") final UUID accountId,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final Account account = accountUserApi.getAccountById(accountId, context.createTenantContextWithAccountId(accountId, request));
        final InvoiceEmailJson invoiceEmailJson = new InvoiceEmailJson(accountId, account.isNotifiedForInvoices());

        return Response.status(Status.OK).entity(invoiceEmailJson).build();
    }

    @TimedResource
    @PUT
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAIL_NOTIFICATIONS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Set account email notification")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response setEmailNotificationsForAccount(@PathParam("accountId") final UUID accountId,
                                                    final InvoiceEmailJson json,
                                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                    @HeaderParam(HDR_REASON) final String reason,
                                                    @HeaderParam(HDR_COMMENT) final String comment,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        verifyNonNullOrEmpty(json, "InvoiceEmailJson body should be specified");
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);

        final MutableAccountData mutableAccountData = account.toMutableAccountData();
        mutableAccountData.setIsNotifiedForInvoices(json.isNotifiedForInvoices());
        accountUserApi.updateAccount(accountId, mutableAccountData, callContext);

        return Response.status(Status.NO_CONTENT).build();
    }

    /*
     * ************************** INVOICE CBA REBALANCING ********************************
     */
    @TimedResource
    @PUT
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CBA_REBALANCING)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Rebalance account CBA")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response rebalanceExistingCBAOnAccount(@PathParam("accountId") final UUID accountId,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        invoiceApi.consumeExstingCBAOnAccountWithUnpaidInvoices(accountId, callContext);
        return Response.status(Status.NO_CONTENT).build();
    }


        /*
     * ************************** INVOICES ********************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + INVOICES)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account invoices", response = InvoiceJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getInvoicesForAccount(@PathParam("accountId") final UUID accountId,
                                          @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                                          @QueryParam(QUERY_WITH_MIGRATION_INVOICES) @DefaultValue("false") final boolean withMigrationInvoices,
                                          @QueryParam(QUERY_UNPAID_INVOICES_ONLY) @DefaultValue("false") final boolean unpaidInvoicesOnly,
                                          @QueryParam(QUERY_INCLUDE_VOIDED_INVOICES) @DefaultValue("false") final boolean includeVoidedInvoices,
                                          @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);

        // Verify the account exists
        accountUserApi.getAccountById(accountId, tenantContext);

        final List<Invoice> invoices = unpaidInvoicesOnly ?
                                       new ArrayList<Invoice>(invoiceApi.getUnpaidInvoicesByAccountId(accountId, null, tenantContext)) :
                                       invoiceApi.getInvoicesByAccount(accountId, withMigrationInvoices, includeVoidedInvoices, tenantContext);

        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(accountId, auditMode.getLevel(), tenantContext);

        final List<InvoiceJson> result = new LinkedList<InvoiceJson>();
        for (final Invoice invoice : invoices) {
            result.add(new InvoiceJson(invoice, withItems, null, accountAuditLogs));
        }

        return Response.status(Status.OK).entity(result).build();
    }

    /*
     * ************************** PAYMENTS ********************************
     */

    // STEPH should refactor code since very similar to @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + INVOICE_PAYMENTS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account invoice payments", response = InvoicePaymentJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getInvoicePayments(@PathParam("accountId") final UUID accountId,
                                       @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                       @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                       @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final Account account = accountUserApi.getAccountById(accountId, tenantContext);
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), withPluginInfo, withAttempts, pluginProperties, tenantContext);
        final List<InvoicePayment> invoicePayments = invoicePaymentApi.getInvoicePaymentsByAccount(accountId, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(accountId, auditMode.getLevel(), tenantContext);
        final List<InvoicePaymentJson> result = new ArrayList<InvoicePaymentJson>(payments.size());
        for (final Payment payment : payments) {
            final UUID invoiceId = getInvoiceId(invoicePayments, payment);
            result.add(new InvoicePaymentJson(payment, invoiceId, accountAuditLogs));
        }
        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}/" + INVOICE_PAYMENTS)
    @ApiOperation(value = "Trigger a payment for all unpaid invoices")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 404, message = "Invalid account id supplied")})
    public Response payAllInvoices(@PathParam("accountId") final UUID accountId,
                                   @QueryParam(QUERY_PAYMENT_EXTERNAL) @DefaultValue("false") final Boolean externalPayment,
                                   @QueryParam(QUERY_PAYMENT_AMOUNT) final BigDecimal paymentAmount,
                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException, InvoiceApiException {

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);
        final Collection<Invoice> unpaidInvoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext);

        BigDecimal remainingRequestPayment = paymentAmount;
        if (remainingRequestPayment == null) {
            remainingRequestPayment = BigDecimal.ZERO;
            for (final Invoice invoice : unpaidInvoices) {
                remainingRequestPayment = remainingRequestPayment.add(invoice.getBalance());
            }
        }

        for (final Invoice invoice : unpaidInvoices) {
            final BigDecimal amountToPay = (remainingRequestPayment.compareTo(invoice.getBalance()) >= 0) ?
                                           invoice.getBalance() : remainingRequestPayment;
            if (amountToPay.compareTo(BigDecimal.ZERO) > 0) {
                final UUID paymentMethodId = externalPayment ? null : account.getPaymentMethodId();
                createPurchaseForInvoice(account, invoice.getId(), amountToPay, paymentMethodId, externalPayment, null, null, pluginProperties, callContext);
            }
            remainingRequestPayment = remainingRequestPayment.subtract(amountToPay);
            if (remainingRequestPayment.compareTo(BigDecimal.ZERO) == 0) {
                break;
            }
        }
        //
        // If the amount requested is greater than what had to be paid and if this an for an external payment (check, ..)
        // then we apply some credit on the account.
        //
        if (externalPayment && remainingRequestPayment.compareTo(BigDecimal.ZERO) > 0) {
            invoiceApi.insertCredit(account.getId(), remainingRequestPayment, clock.getUTCToday(), account.getCurrency(), true, "pay all invoices", null, callContext);
        }
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add a payment method", response = PaymentMethodJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment method created"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createPaymentMethod(@PathParam("accountId") final UUID accountId,
                                        final PaymentMethodJson json,
                                        @QueryParam(QUERY_PAYMENT_METHOD_IS_DEFAULT) @DefaultValue("false") final Boolean isDefault,
                                        @QueryParam(QUERY_PAY_ALL_UNPAID_INVOICES) @DefaultValue("false") final Boolean payAllUnpaidInvoices,
                                        @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        verifyNonNullOrEmpty(json, "PaymentMethodJson body should be specified");
        verifyNonNullOrEmpty(json.getPluginName(), "PaymentMethodJson pluginName should be specified");
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final PaymentMethod data = json.toPaymentMethod(accountId);
        final Account account = accountUserApi.getAccountById(data.getAccountId(), callContext);

        final boolean hasDefaultPaymentMethod = account.getPaymentMethodId() != null || isDefault;
        final Collection<Invoice> unpaidInvoices = payAllUnpaidInvoices ? invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext) :
                                                   Collections.<Invoice>emptyList();
        if (payAllUnpaidInvoices && unpaidInvoices.size() > 0 && !hasDefaultPaymentMethod) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final UUID paymentMethodId = paymentApi.addPaymentMethodWithPaymentControl(account, data.getExternalKey(), data.getPluginName(), isDefault, data.getPluginDetail(), pluginProperties, paymentOptions, callContext);
        if (payAllUnpaidInvoices && unpaidInvoices.size() > 0) {
            for (final Invoice invoice : unpaidInvoices) {
                createPurchaseForInvoice(account, invoice.getId(), invoice.getBalance(), paymentMethodId, false, null, null, pluginProperties, callContext);
            }
        }
        return uriBuilder.buildResponse(uriInfo, PaymentMethodResource.class, "getPaymentMethod", paymentMethodId, request);
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account payment methods", response = PaymentMethodJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getPaymentMethodsForAccount(@PathParam("accountId") final UUID accountId,
                                                @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                                @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                                @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);

        final Account account = accountUserApi.getAccountById(accountId, tenantContext);
        final List<PaymentMethod> methods = paymentApi.getAccountPaymentMethods(account.getId(), includedDeleted, withPluginInfo, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(account.getId(), auditMode.getLevel(), tenantContext);
        final List<PaymentMethodJson> json = new ArrayList<PaymentMethodJson>(Collections2.transform(methods, new Function<PaymentMethod, PaymentMethodJson>() {
            @Override
            public PaymentMethodJson apply(final PaymentMethod input) {
                return PaymentMethodJson.toPaymentMethodJson(account, input, accountAuditLogs);
            }
        }));

        return Response.status(Status.OK).entity(json).build();
    }

    @TimedResource
    @PUT
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS + "/refresh")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Refresh account payment methods")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response refreshPaymentMethods(@PathParam("accountId") final UUID accountId,
                                          @QueryParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);

        if (pluginName != null && !pluginName.isEmpty()) {
            paymentApi.refreshPaymentMethods(account, pluginName, pluginProperties, callContext);
        } else {
            paymentApi.refreshPaymentMethods(account, pluginProperties, callContext);
        }

        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS + "/{paymentMethodId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS_DEFAULT_PATH_POSTFIX)
    @ApiOperation(value = "Set the default payment method")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id or payment method id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response setDefaultPaymentMethod(@PathParam("accountId") final UUID accountId,
                                            @PathParam("paymentMethodId") final UUID paymentMethodId,
                                            @QueryParam(QUERY_PAY_ALL_UNPAID_INVOICES) @DefaultValue("false") final Boolean payAllUnpaidInvoices,
                                            @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(accountId, callContext);
        paymentApi.setDefaultPaymentMethod(account, paymentMethodId, pluginProperties, callContext);

        if (payAllUnpaidInvoices) {
            final Collection<Invoice> unpaidInvoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext);
            for (final Invoice invoice : unpaidInvoices) {
                createPurchaseForInvoice(account, invoice.getId(), invoice.getBalance(), paymentMethodId, false, null, null, pluginProperties, callContext);
            }
        }
        return Response.status(Status.NO_CONTENT).build();
    }

    /*
     * ************************* PAYMENTS *****************************
     */
    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account payments", response = PaymentJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response getPaymentsForAccount(@PathParam("accountId") final UUID accountId,
                                          @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                          @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final List<Payment> payments = paymentApi.getAccountPayments(accountId, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(accountId, auditMode.getLevel(), tenantContext);
        final List<PaymentJson> result = ImmutableList.copyOf(Iterables.transform(payments, new Function<Payment, PaymentJson>() {
            @Override
            public PaymentJson apply(final Payment payment) {
                return new PaymentJson(payment, accountAuditLogs);
            }
        }));
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource(name = "processPayment")
    @POST
    @Path("/" + PAYMENTS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Trigger a payment using the account external key (authorization, purchase or credit)", response = PaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account external key supplied"),
                           @ApiResponse(code = 404, message = "Account not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response processPaymentByExternalKey(@MetricTag(tag = "type", property = "transactionType") final PaymentTransactionJson json,
                                                @ApiParam(required = true) @QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                                @QueryParam(QUERY_PAYMENT_METHOD_ID) final UUID paymentMethodId,
                                                @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                @HeaderParam(HDR_REASON) final String reason,
                                                @HeaderParam(HDR_COMMENT) final String comment,
                                                @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final Account account = accountUserApi.getAccountByKey(externalKey, callContext);

        return processPayment(json, account, paymentMethodId, paymentControlPluginNames, pluginPropertiesString, uriInfo, callContext, request);
    }

    @TimedResource(name = "processPayment")
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Trigger a payment (authorization, purchase or credit)", response = PaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response processPayment(@PathParam("accountId") final UUID accountId,
                                   @MetricTag(tag = "type", property = "transactionType") final PaymentTransactionJson json,
                                   @QueryParam(QUERY_PAYMENT_METHOD_ID) final UUID inputPaymentMethodId,
                                   @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        final Account account = accountUserApi.getAccountById(accountId, callContext);

        return processPayment(json, account, inputPaymentMethodId, paymentControlPluginNames, pluginPropertiesString, uriInfo, callContext, request);
    }

    private Response processPayment(final PaymentTransactionJson json,
                                    final Account account,
                                    final UUID inputPaymentMethodId,
                                    final List<String> paymentControlPluginNames,
                                    final List<String> pluginPropertiesString,
                                    final UriInfo uriInfo,
                                    final CallContext callContext,
                                    final HttpServletRequest request) throws PaymentApiException {
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getTransactionType(), "PaymentTransactionJson transactionType needs to be set",
                             json.getAmount(), "PaymentTransactionJson amount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : json.getCurrency();
        final UUID paymentId = json.getPaymentId();

        //
        // If paymentId was specified, it means we are attempting a payment completion. The preferred way is to use the PaymentResource
        // (PUT /1.0/kb/payments/{paymentId}/completeTransaction), but for backward compatibility we still allow the call to proceed
        // as long as the request/existing state is healthy (i.e there is a matching PENDING transaction)
        //
        final UUID paymentMethodId;
        if (paymentId != null) {
            final Payment initialPayment = paymentApi.getPayment(paymentId, false, false, pluginProperties, callContext);
            final PaymentTransaction pendingOrSuccessTransaction = lookupPendingOrSuccessTransaction(initialPayment,
                                                                                                     json != null ? json.getTransactionId() : null,
                                                                                                     json != null ? json.getTransactionExternalKey() : null,
                                                                                                     json != null ? json.getTransactionType() : null);
            // If transaction was already completed, return early (See #626)
            if (pendingOrSuccessTransaction.getTransactionStatus() == TransactionStatus.SUCCESS) {
                return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", pendingOrSuccessTransaction.getPaymentId(), request);
            }

            paymentMethodId = initialPayment.getPaymentMethodId();
        } else {
            paymentMethodId = inputPaymentMethodId == null ? account.getPaymentMethodId() : inputPaymentMethodId;
        }
        validatePaymentMethodForAccount(account.getId(), paymentMethodId, callContext);

        final TransactionType transactionType = json.getTransactionType();
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final Payment result;
        switch (transactionType) {
            case AUTHORIZE:
                result = paymentApi.createAuthorizationWithPaymentControl(account, paymentMethodId, paymentId, json.getAmount(), currency, json.getEffectiveDate(),
                                                                          json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                                          pluginProperties, paymentOptions, callContext);
                break;
            case PURCHASE:
                result = paymentApi.createPurchaseWithPaymentControl(account, paymentMethodId, paymentId, json.getAmount(), currency, json.getEffectiveDate(),
                                                                     json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                                     pluginProperties, paymentOptions, callContext);
                break;
            case CREDIT:
                result = paymentApi.createCreditWithPaymentControl(account, paymentMethodId, paymentId, json.getAmount(), currency, json.getEffectiveDate(),
                                                                   json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                                   pluginProperties, paymentOptions, callContext);
                break;
            default:
                return Response.status(Status.PRECONDITION_FAILED).entity("TransactionType " + transactionType + " is not allowed for an account").build();
        }
        return createPaymentResponse(uriInfo, result, transactionType, json.getTransactionExternalKey(), request);
    }

    /*
     * ************************** OVERDUE ********************************
     */
    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + OVERDUE)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve overdue state for account", response = OverdueStateJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getOverdueAccount(@PathParam("accountId") final UUID accountId,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, OverdueException, OverdueApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);

        final Account account = accountUserApi.getAccountById(accountId, tenantContext);
        final OverdueState overdueState = overdueApi.getOverdueStateFor(account.getId(), tenantContext);

        return Response.status(Status.OK).entity(new OverdueStateJson(overdueState, paymentConfig)).build();
    }


    /*
     * *************************      BLOCKING STATE     *****************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + BLOCK)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve blocking states for account", response = BlockingStateJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response getBlockingStates(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                      @QueryParam(QUERY_BLOCKING_STATE_TYPES) final List<BlockingStateType> typeFilter,
                                      @QueryParam(QUERY_BLOCKING_STATE_SVCS) final List<String> svcsFilter,
                                      @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException {

        final TenantContext tenantContext = this.context.createTenantContextWithAccountId(accountId, request);
        final Iterable<BlockingState> blockingStates = subscriptionApi.getBlockingStates(accountId, typeFilter, svcsFilter, OrderingType.ASCENDING, SubscriptionApi.ALL_EVENTS, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(accountId, auditMode.getLevel(), tenantContext);

        final List<BlockingStateJson> result = ImmutableList.copyOf(Iterables.transform(blockingStates, new Function<BlockingState, BlockingStateJson>() {
            @Override
            public BlockingStateJson apply(final BlockingState input) {
                return new BlockingStateJson(input, accountAuditLogs);
            }
        }));

        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + BLOCK)
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Block an account", response = BlockingStateJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Blocking state created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response addAccountBlockingState(@PathParam(ID_PARAM_NAME) final UUID id,
                                            final BlockingStateJson json,
                                            @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                            @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @javax.ws.rs.core.Context final HttpServletRequest request,
                                            @javax.ws.rs.core.Context final UriInfo uriInfo) throws SubscriptionApiException, EntitlementApiException, AccountApiException {
        return addBlockingState(json, id, id, BlockingStateType.ACCOUNT, requestedDate, pluginPropertiesString, createdBy, reason, comment, request, uriInfo);
    }


    /*
     * *************************      CUSTOM FIELDS     *****************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account custom fields", response = CustomFieldJson.class, responseContainer = "List", nickname = "getAccountCustomFields")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(accountId, auditMode, context.createTenantContextWithAccountId(accountId, request));
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + ALL_CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account customFields", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getAllCustomFields(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                       @QueryParam(QUERY_OBJECT_TYPE) final ObjectType objectType,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final List<CustomField> customFields = objectType != null ?
                                               customFieldUserApi.getCustomFieldsForAccountType(accountId, objectType, tenantContext) :
                                               customFieldUserApi.getCustomFieldsForAccount(accountId, tenantContext);
        return createCustomFieldResponse(customFields, auditMode, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to account", response = CustomField.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Custom field created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response createAccountCustomFields(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                              final List<CustomFieldJson> customFields,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @javax.ws.rs.core.Context final HttpServletRequest request,
                                              @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(accountId, customFields, context.createCallContextWithAccountId(accountId, createdBy, reason,
                                                                                                        comment, request), uriInfo, request);
    }

    @TimedResource
    @PUT
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Modify custom fields to account")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response modifyAccountCustomFields(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                              final List<CustomFieldJson> customFields,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.modifyCustomFields(accountId, customFields, context.createCallContextWithAccountId(accountId, createdBy, reason,
                                                                                                        comment, request));
    }

    @TimedResource
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from account")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response deleteAccountCustomFields(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                              @QueryParam(QUERY_CUSTOM_FIELD) final List<UUID> customFieldList,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(accountId, customFieldList,
                                        context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request));
    }

    /*
     * *************************     TAGS     *****************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account tags", response = TagJson.class, responseContainer = "List", nickname = "getAccountTags")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID accountId,
                            @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        return super.getTags(accountId, accountId, auditMode, includedDeleted, context.createTenantContextWithAccountId(accountId, request));
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + ALL_TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account tags", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getAllTags(@PathParam(ID_PARAM_NAME) final UUID accountId,
                               @QueryParam(QUERY_OBJECT_TYPE) final ObjectType objectType,
                               @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final List<Tag> tags = objectType != null ?
                               tagUserApi.getTagsForAccountType(accountId, objectType, includedDeleted, tenantContext) :
                               tagUserApi.getTagsForAccount(accountId, includedDeleted, tenantContext);
        return createTagResponse(accountId, tags, auditMode, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to account", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Tag created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response createAccountTags(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                      final List<UUID> tagList,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final UriInfo uriInfo,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(accountId, tagList, uriInfo,
                                context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request), request);
    }

    @TimedResource
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from account")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied or account does not have a default payment method (AUTO_PAY_OFF tag only)")})
    public Response deleteAccountTags(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                      @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException, AccountApiException {
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        // Look if there is an AUTO_PAY_OFF for that account and check if the account has a default paymentMethod
        // If not we can't remove the AUTO_PAY_OFF tag
        boolean isTagAutoPayOff = false;
        for (final UUID cur : tagList) {
            if (cur.equals(ControlTagType.AUTO_PAY_OFF.getId())) {
                isTagAutoPayOff = true;
                break;
            }
        }
        if (isTagAutoPayOff) {
            final Account account = accountUserApi.getAccountById(accountId, callContext);
            if (account.getPaymentMethodId() == null) {
                throw new TagApiException(ErrorCode.TAG_CANNOT_BE_REMOVED, ControlTagType.AUTO_PAY_OFF, " the account does not have a default payment method");
            }
        }

        return super.deleteTags(accountId, tagList, callContext);
    }

    /*
     * *************************     EMAILS     *****************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an account emails", response = AccountEmailJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response getEmails(@PathParam(ID_PARAM_NAME) final UUID accountId,
                              @javax.ws.rs.core.Context final HttpServletRequest request) {
        final List<AccountEmail> emails = accountUserApi.getEmails(accountId, context.createTenantContextWithAccountId(accountId, request));

        final List<AccountEmailJson> emailsJson = new ArrayList<AccountEmailJson>();
        for (final AccountEmail email : emails) {
            emailsJson.add(new AccountEmailJson(email.getAccountId(), email.getEmail()));
        }
        return Response.status(Status.OK).entity(emailsJson).build();
    }

    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add account email", response = AccountEmailJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Email created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response addEmail(@PathParam(ID_PARAM_NAME) final UUID accountId,
                             final AccountEmailJson json,
                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                             @HeaderParam(HDR_REASON) final String reason,
                             @HeaderParam(HDR_COMMENT) final String comment,
                             @javax.ws.rs.core.Context final HttpServletRequest request,
                             @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException {
        verifyNonNullOrEmpty(json, "AccountEmailJson body should be specified");
        verifyNonNullOrEmpty(json.getEmail(), "AccountEmailJson email needs to be set");
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);

        // Make sure the account exist or we will confuse the history and auditing code
        accountUserApi.getAccountById(accountId, callContext);

        // Make sure the email doesn't exist
        final AccountEmail existingEmail = Iterables.<AccountEmail>tryFind(accountUserApi.getEmails(accountId, callContext),
                                                                           new Predicate<AccountEmail>() {
                                                                               @Override
                                                                               public boolean apply(final AccountEmail input) {
                                                                                   return input.getEmail().equals(json.getEmail());
                                                                               }
                                                                           }
                                                                          )
                .orNull();
        if (existingEmail == null) {
            accountUserApi.addEmail(accountId, json.toAccountEmail(UUIDs.randomUUID()), callContext);
        }

        return uriBuilder.buildResponse(uriInfo, AccountResource.class, "getEmails", json.getAccountId(), request);
    }

    @TimedResource
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS + "/{email}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Delete email from account")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response removeEmail(@PathParam(ID_PARAM_NAME) final UUID accountId,
                                @PathParam("email") final String email,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final HttpServletRequest request) {
        final List<AccountEmail> emails = accountUserApi.getEmails(accountId, context.createTenantContextWithAccountId(accountId, request));
        for (final AccountEmail cur : emails) {
            if (cur.getEmail().equals(email)) {
                final AccountEmailJson accountEmailJson = new AccountEmailJson(accountId, email);
                final AccountEmail accountEmail = accountEmailJson.toAccountEmail(cur.getId());
                accountUserApi.removeEmail(accountId, accountEmail, context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request));
            }
        }
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS + "/{accountEmailId:" + UUID_PATTERN + "}/" + AUDIT_LOG_WITH_HISTORY)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account email audit logs with history by id", response = AuditLogJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account not found")})
    public Response getAccountEmailAuditLogsWithHistory(@PathParam("accountId") final UUID accountId,
                                                        @PathParam("accountEmailId") final UUID accountEmailId,
                                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final List<AuditLogWithHistory> auditLogWithHistory = accountUserApi.getEmailAuditLogsWithHistoryForId(accountEmailId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogsWithHistory(auditLogWithHistory)).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.ACCOUNT;
    }

    // -------------------------------------
    //      Parent and child accounts
    // -------------------------------------

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CHILDREN)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "List children accounts", response = AccountJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid parent account id supplied"),
                           @ApiResponse(code = 404, message = "Parent Account not found")})
    public Response getChildrenAccounts(@PathParam("accountId") final UUID parentAccountId,
                                        @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                                        @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                                        @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {

        final TenantContext tenantContext = context.createTenantContextWithAccountId(parentAccountId, request);
        final List<Account> accounts = accountUserApi.getChildrenAccounts(parentAccountId, tenantContext);

        final List<AccountJson> accountJson = new ArrayList<AccountJson>();
        for (final Account account : accounts) {
            final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(account.getId(), auditMode.getLevel(), tenantContext);
            accountJson.add(getAccount(account, accountWithBalance, accountWithBalanceAndCBA, accountAuditLogs, tenantContext));
        }
        return Response.status(Status.OK).entity(accountJson).build();
    }

    @TimedResource
    @PUT
    @Path("/{childAccountId:" + UUID_PATTERN + "}/" + TRANSFER_CREDIT)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Move a given child credit to the parent level")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Account does not have credit"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response transferChildCreditToParent(@PathParam("childAccountId") final UUID childAccountId,
                                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                @HeaderParam(HDR_REASON) final String reason,
                                                @HeaderParam(HDR_COMMENT) final String comment,
                                                @javax.ws.rs.core.Context final HttpServletRequest request,
                                                @javax.ws.rs.core.Context final UriInfo uriInfo) throws InvoiceApiException {

        final CallContext callContext = context.createCallContextWithAccountId(childAccountId, createdBy, reason, comment, request);

        invoiceApi.transferChildCreditToParent(childAccountId, callContext);
        return Response.status(Status.NO_CONTENT).build();
    }

    /*
     * *************************     AUDIT LOGS     *****************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + AUDIT_LOG)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve audit logs by account id", response = AuditLogJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account not found")})
    public Response getAccountAuditLogs(@PathParam("accountId") final UUID accountId,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(accountId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogs(accountAuditLogs)).build();
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + AUDIT_LOG_WITH_HISTORY)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account audit logs with history by account id", response = AuditLogJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account not found")})
    public Response getAccountAuditLogsWithHistory(@PathParam("accountId") final UUID accountId,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final List<AuditLogWithHistory> auditLogWithHistory = accountUserApi.getAuditLogsWithHistoryForId(accountId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogsWithHistory(auditLogWithHistory)).build();
    }

    private List<AuditLogJson> getAuditLogs(AccountAuditLogs accountAuditLogs) {
        if (accountAuditLogs.getAuditLogs() == null) {
            return null;
        }

        return ImmutableList.<AuditLogJson>copyOf(Collections2.transform(accountAuditLogs.getAuditLogs(), new Function<AuditLog, AuditLogJson>() {
            @Override
            public AuditLogJson apply(@Nullable final AuditLog input) {
                return new AuditLogJson(input);
            }
        }));
    }


}
