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

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
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
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentApi;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.JaxrsExecutors;
import org.killbill.billing.jaxrs.json.AccountEmailJson;
import org.killbill.billing.jaxrs.json.AccountJson;
import org.killbill.billing.jaxrs.json.AccountTimelineJson;
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
import org.killbill.billing.overdue.OverdueInternalApi;
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
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.JaxrsConfig;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.MetricTag;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.ACCOUNTS_PATH)
@Api(value = JaxrsResource.ACCOUNTS_PATH, description = "Operations on accounts")
public class AccountResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "accountId";

    private final SubscriptionApi subscriptionApi;
    private final InvoiceUserApi invoiceApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final OverdueInternalApi overdueApi;
    private final PaymentConfig paymentConfig;
    private final JaxrsExecutors jaxrsExecutors;
    private final JaxrsConfig jaxrsConfig;

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
                           final AccountInternalApi accountInternalApi,
                           final OverdueInternalApi overdueApi,
                           final Clock clock,
                           final PaymentConfig paymentConfig,
                           final JaxrsExecutors jaxrsExecutors,
                           final JaxrsConfig jaxrsConfig,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountApi, paymentApi, clock, context);
        this.subscriptionApi = subscriptionApi;
        this.invoiceApi = invoiceApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.overdueApi = overdueApi;
        this.paymentConfig = paymentConfig;
        this.jaxrsExecutors = jaxrsExecutors;
        this.jaxrsConfig = jaxrsConfig;
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an account by id", response = AccountJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getAccount(@PathParam("accountId") final String accountId,
                               @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                               @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), tenantContext);
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
        final TenantContext tenantContext = context.createContext(request);
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
        final TenantContext tenantContext = context.createContext(request);
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
    public Response getAccountBundles(@PathParam("accountId") final String accountId,
                                      @QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, SubscriptionApiException {
        final TenantContext tenantContext = context.createContext(request);

        final UUID uuid = UUID.fromString(accountId);
       final Account account = accountUserApi.getAccountById(uuid, tenantContext);

        final List<SubscriptionBundle> bundles = (externalKey != null) ?
                                                 subscriptionApi.getSubscriptionBundlesForAccountIdAndExternalKey(uuid, externalKey, tenantContext) :
                                                 subscriptionApi.getSubscriptionBundlesForAccountId(uuid, tenantContext);

        final Collection<BundleJson> result = Collections2.transform(bundles, new Function<SubscriptionBundle, BundleJson>() {
            @Override
            public BundleJson apply(final SubscriptionBundle input) {
                try {
                    return new BundleJson(input, account.getCurrency(), null);
                } catch (final CatalogApiException e) {
                    // Not the cleanest thing, but guava Api don't allow throw..
                    throw new RuntimeException(e);
                }
            }
        });
        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve an account by external key", response = AccountJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account not found")})
    public Response getAccountByKey(@QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                    @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                                    @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createContext(request);
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
    @ApiOperation(value = "Create account")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account data supplied")})
    public Response createAccount(final AccountJson json,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException {
        verifyNonNullOrEmpty(json, "AccountJson body should be specified");

        final AccountData data = json.toAccountData();
        final Account account = accountUserApi.createAccount(data, context.createContext(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(uriInfo, AccountResource.class, "getAccount", account.getId());
    }

    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Update account")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account data supplied")})
    public Response updateAccount(final AccountJson json,
                                  @PathParam("accountId") final String accountId,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        verifyNonNullOrEmpty(json, "AccountJson body should be specified");

        final AccountData data = json.toAccountData();
        final UUID uuid = UUID.fromString(accountId);
        accountUserApi.updateAccount(uuid, data, context.createContext(createdBy, reason, comment, request));
        return getAccount(accountId, false, false, new AuditMode(AuditLevel.NONE.toString()), request);
    }

    // Not supported
    @TimedResource
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Delete account", hidden = true)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response cancelAccount(@PathParam("accountId") final String accountId,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) {
        /*
        try {
            accountUserApi.cancelAccount(accountId);
            return Response.status(Status.NO_CONTENT).build();
        } catch (AccountApiException e) {
            log.info(String.format("Failed to cancel account %s", accountId), e);
            return Response.status(Status.BAD_REQUEST).build();
        }
       */
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }


    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TIMELINE)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account timeline", response = AccountTimelineJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getAccountTimeline(@PathParam("accountId") final String accountIdString,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @QueryParam(QUERY_PARALLEL) @DefaultValue("false") final Boolean parallel,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException, SubscriptionApiException, InvoiceApiException, CatalogApiException {
        final TenantContext tenantContext = context.createContext(request);

        final UUID accountId = UUID.fromString(accountIdString);
        final Account account = accountUserApi.getAccountById(accountId, tenantContext);

        final Callable<List<SubscriptionBundle>> bundlesCallable = new Callable<List<SubscriptionBundle>>() {
            @Override
            public List<SubscriptionBundle> call() throws Exception {
                return subscriptionApi.getSubscriptionBundlesForAccountId(account.getId(), tenantContext);
            }
        };
        final Callable<List<Invoice>> invoicesCallable = new Callable<List<Invoice>>() {
            @Override
            public List<Invoice> call() throws Exception {
                return invoiceApi.getInvoicesByAccount(account.getId(), tenantContext);
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

            try {
                long ini = System.currentTimeMillis();
                do {
                    bundles = (bundles == null) ? runCallableAndHandleTimeout(futureBundlesCallable, 100) : bundles;
                    invoices = (invoices == null) ? runCallableAndHandleTimeout(futureInvoicesCallable, 100) : invoices;
                    invoicePayments = (invoicePayments == null) ? runCallableAndHandleTimeout(futureInvoicePaymentsCallable, 100) : invoicePayments;
                    payments = (payments == null) ? runCallableAndHandleTimeout(futurePaymentsCallable, 100) : payments;
                    accountAuditLogs = (accountAuditLogs == null) ? runCallableAndHandleTimeout(futureAuditsCallable, 100) : accountAuditLogs;
                } while ((System.currentTimeMillis() - ini < jaxrsConfig.getJaxrsTimeout().getMillis()) &&
                         (bundles == null || invoices == null || invoicePayments == null || payments == null || accountAuditLogs == null));

                if (bundles == null || invoices == null || invoicePayments == null || payments == null || accountAuditLogs == null) {
                    Response.status(Status.SERVICE_UNAVAILABLE).build();
                }
            } catch (InterruptedException e) {
                handleCallableException(e, ImmutableList.<Future>of(futureBundlesCallable, futureInvoicesCallable, futureInvoicePaymentsCallable, futurePaymentsCallable, futureAuditsCallable));
            } catch (ExecutionException e) {
                handleCallableException(e.getCause(), ImmutableList.<Future>of(futureBundlesCallable, futureInvoicesCallable, futureInvoicePaymentsCallable, futurePaymentsCallable, futureAuditsCallable));
            }

        } else {
            try {
                invoices = invoicesCallable.call();
                payments = paymentsCallable.call();
                bundles = bundlesCallable.call();
                accountAuditLogs = auditsCallable.call();
                invoicePayments = invoicePaymentsCallable.call();
            } catch (Exception e) {
                handleCallableException(e);
            }
        }

        json = new AccountTimelineJson(account, invoices, payments, invoicePayments, bundles, accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }

    private <T> T runCallableAndHandleTimeout(final Future<T> future, final long timeoutMsec) throws ExecutionException, InterruptedException {
        try {
            return future.get(timeoutMsec, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        }
    }

    private void handleCallableException(final Throwable causeOrException, final List<Future> toBeCancelled) throws AccountApiException, SubscriptionApiException, PaymentApiException, InvoiceApiException {
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
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getEmailNotificationsForAccount(@PathParam("accountId") final String accountId,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), context.createContext(request));
        final InvoiceEmailJson invoiceEmailJson = new InvoiceEmailJson(accountId, account.isNotifiedForInvoices());

        return Response.status(Status.OK).entity(invoiceEmailJson).build();
    }

    @TimedResource
    @PUT
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAIL_NOTIFICATIONS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Set account email notification")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response setEmailNotificationsForAccount(final InvoiceEmailJson json,
                                                    @PathParam("accountId") final String accountIdString,
                                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                    @HeaderParam(HDR_REASON) final String reason,
                                                    @HeaderParam(HDR_COMMENT) final String comment,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        verifyNonNullOrEmpty(json, "InvoiceEmailJson body should be specified");

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID accountId = UUID.fromString(accountIdString);
        final Account account = accountUserApi.getAccountById(accountId, callContext);

        final MutableAccountData mutableAccountData = account.toMutableAccountData();
        mutableAccountData.setIsNotifiedForInvoices(json.isNotifiedForInvoices());
        accountUserApi.updateAccount(accountId, mutableAccountData, callContext);

        return Response.status(Status.OK).build();
    }

    /*
     * ************************** INVOICE CBA REBALANCING ********************************
     */
    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CBA_REBALANCING)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Rebalance account CBA")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response rebalanceExistingCBAOnAccount(@PathParam("accountId") final String accountIdString,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID accountId = UUID.fromString(accountIdString);

        invoiceApi.consumeExstingCBAOnAccountWithUnpaidInvoices(accountId, callContext);
        return Response.status(Status.OK).build();
    }


        /*
     * ************************** INVOICES ********************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + INVOICES)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account invoices", response = InvoiceJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getInvoices(@PathParam("accountId") final String accountIdString,
                                @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                                @QueryParam(QUERY_UNPAID_INVOICES_ONLY) @DefaultValue("false") final boolean unpaidInvoicesOnly,
                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createContext(request);

        // Verify the account exists
        final UUID accountId = UUID.fromString(accountIdString);
        accountUserApi.getAccountById(accountId, tenantContext);

        final List<Invoice> invoices = unpaidInvoicesOnly ?
                                       new ArrayList<Invoice>(invoiceApi.getUnpaidInvoicesByAccountId(accountId, null, tenantContext)) :
                                       invoiceApi.getInvoicesByAccount(accountId, tenantContext);

        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(accountId, auditMode.getLevel(), tenantContext);

        final List<InvoiceJson> result = new LinkedList<InvoiceJson>();
        for (final Invoice invoice : invoices) {
            result.add(new InvoiceJson(invoice, withItems, accountAuditLogs));
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
    public Response getInvoicePayments(@PathParam("accountId") final String accountIdStr,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                       @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                       @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID accountId = UUID.fromString(accountIdStr);
        final TenantContext tenantContext = context.createContext(request);
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
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response payAllInvoices(@PathParam("accountId") final String accountId,
                                   @QueryParam(QUERY_PAYMENT_EXTERNAL) @DefaultValue("false") final Boolean externalPayment,
                                   @QueryParam(QUERY_PAYMENT_AMOUNT) final BigDecimal paymentAmount,
                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException, InvoiceApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), callContext);
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
                createPurchaseForInvoice(account, invoice.getId(), amountToPay, paymentMethodId, externalPayment, pluginProperties, callContext);
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
            invoiceApi.insertCredit(account.getId(), remainingRequestPayment, clock.getUTCToday(), account.getCurrency(), callContext);
        }
        return Response.status(Status.OK).build();
    }

    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add a payment method")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response createPaymentMethod(final PaymentMethodJson json,
                                        @PathParam("accountId") final String accountId,
                                        @QueryParam(QUERY_PAYMENT_METHOD_IS_DEFAULT) @DefaultValue("false") final Boolean isDefault,
                                        @QueryParam(QUERY_PAY_ALL_UNPAID_INVOICES) @DefaultValue("false") final Boolean payAllUnpaidInvoices,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        verifyNonNullOrEmpty(json, "PaymentMethodJson body should be specified");
        verifyNonNullOrEmpty(json.getPluginName(), "PaymentMethodJson pluginName should be specified");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final PaymentMethod data = json.toPaymentMethod(accountId);
        final Account account = accountUserApi.getAccountById(data.getAccountId(), callContext);

        final boolean hasDefaultPaymentMethod = account.getPaymentMethodId() != null || isDefault;
        final Collection<Invoice> unpaidInvoices = payAllUnpaidInvoices ? invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext) :
                                                   Collections.<Invoice>emptyList();
        if (payAllUnpaidInvoices && unpaidInvoices.size() > 0 && !hasDefaultPaymentMethod) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        final UUID paymentMethodId = paymentApi.addPaymentMethod(account, data.getExternalKey(), data.getPluginName(), isDefault, data.getPluginDetail(), pluginProperties, callContext);
        if (payAllUnpaidInvoices && unpaidInvoices.size() > 0) {
            for (final Invoice invoice : unpaidInvoices) {
                createPurchaseForInvoice(account, invoice.getId(), invoice.getBalance(), paymentMethodId, false, pluginProperties, callContext);
            }
        }
        return uriBuilder.buildResponse(PaymentMethodResource.class, "getPaymentMethod", paymentMethodId, uriInfo.getBaseUri().toString());
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account payment methods", response = PaymentMethodJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getPaymentMethods(@PathParam("accountId") final String accountId,
                                      @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                      @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), tenantContext);
        final List<PaymentMethod> methods = paymentApi.getAccountPaymentMethods(account.getId(), withPluginInfo, pluginProperties, tenantContext);
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
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS + "/refresh")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Refresh account payment methods")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response refreshPaymentMethods(@PathParam("accountId") final String accountId,
                                          @QueryParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), callContext);

        if (pluginName != null && !pluginName.isEmpty()) {
            paymentApi.refreshPaymentMethods(account, pluginName, pluginProperties, callContext);
        } else {
            paymentApi.refreshPaymentMethods(account, pluginProperties, callContext);
        }

        return Response.status(Status.OK).build();
    }


    @TimedResource
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS + "/{paymentMethodId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS_DEFAULT_PATH_POSTFIX)
    @ApiOperation(value = "Set the default payment method")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id or payment method id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response setDefaultPaymentMethod(@PathParam("accountId") final String accountId,
                                            @PathParam("paymentMethodId") final String paymentMethodId,
                                            @QueryParam(QUERY_PAY_ALL_UNPAID_INVOICES) @DefaultValue("false") final Boolean payAllUnpaidInvoices,
                                            @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), callContext);
        final UUID newPaymentMethodId = UUID.fromString(paymentMethodId);
        paymentApi.setDefaultPaymentMethod(account, newPaymentMethodId, pluginProperties, callContext);

        if (payAllUnpaidInvoices) {
            final Collection<Invoice> unpaidInvoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext);
            for (final Invoice invoice : unpaidInvoices) {
                createPurchaseForInvoice(account, invoice.getId(), invoice.getBalance(), newPaymentMethodId, false, pluginProperties, callContext);
            }
        }
        return Response.status(Status.OK).build();
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
    public Response getPayments(@PathParam("accountId") final String accountIdStr,
                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final UUID accountId = UUID.fromString(accountIdStr);
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);
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
    @ApiOperation(value = "Trigger a payment using the account external key (authorization, purchase or credit)")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account external key supplied"),
                           @ApiResponse(code = 404, message = "Account not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response processPaymentByExternalKey(@MetricTag(tag = "type", property = "transactionType") final PaymentTransactionJson json,
                                                @QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                                @QueryParam(QUERY_PAYMENT_METHOD_ID) final String paymentMethodIdStr,
                                                @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                @HeaderParam(HDR_REASON) final String reason,
                                                @HeaderParam(HDR_COMMENT) final String comment,
                                                @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Account account = accountUserApi.getAccountByKey(externalKey, callContext);

        return processPayment(json, account, paymentMethodIdStr, paymentControlPluginNames, pluginPropertiesString, uriInfo, callContext);
    }

    @TimedResource(name = "processPayment")
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Trigger a payment (authorization, purchase or credit)")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response processPayment(@MetricTag(tag = "type", property = "transactionType") final PaymentTransactionJson json,
                                   @PathParam(QUERY_ACCOUNT_ID) final String accountIdStr,
                                   @QueryParam(QUERY_PAYMENT_METHOD_ID) final String paymentMethodIdStr,
                                   @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final UUID accountId = UUID.fromString(accountIdStr);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Account account = accountUserApi.getAccountById(accountId, callContext);

        return processPayment(json, account, paymentMethodIdStr, paymentControlPluginNames, pluginPropertiesString, uriInfo, callContext);
    }

    private Response processPayment(final PaymentTransactionJson json,
                                    final Account account,
                                    final String paymentMethodIdStr,
                                    final List<String> paymentControlPluginNames,
                                    final List<String> pluginPropertiesString,
                                    final UriInfo uriInfo,
                                    final CallContext callContext) throws PaymentApiException {
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getTransactionType(), "PaymentTransactionJson transactionType needs to be set",
                             json.getAmount(), "PaymentTransactionJson amount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());
        final UUID paymentId = json.getPaymentId() == null ? null : UUID.fromString(json.getPaymentId());

        //
        // If paymentId was specified, it means we are attempting a payment completion. The preferred way is to use the PaymentResource
        // (PUT /1.0/kb/payments/{paymentId}/completeTransaction), but for backward compatibility we still allow the call to proceed
        // as long as the request/existing state is healthy (i.e there is a matching PENDING transaction)
        //
        final UUID paymentMethodId;
        if (paymentId != null) {
            final Payment initialPayment = paymentApi.getPayment(paymentId, false, false, pluginProperties, callContext);
            final PaymentTransaction pendingTransaction = lookupPendingTransaction(initialPayment,
                                                                                   json != null ? json.getTransactionId() : null,
                                                                                   json != null ? json.getTransactionExternalKey() : null,
                                                                                   json != null ? json.getTransactionType() : null);
            paymentMethodId = initialPayment.getPaymentMethodId();
        } else {
            paymentMethodId = paymentMethodIdStr == null ? account.getPaymentMethodId() : UUID.fromString(paymentMethodIdStr);
        }
        validatePaymentMethodForAccount(account.getId(), paymentMethodId, callContext);


        final TransactionType transactionType = TransactionType.valueOf(json.getTransactionType());
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final Payment result;
        switch (transactionType) {
            case AUTHORIZE:
                result = paymentApi.createAuthorizationWithPaymentControl(account, paymentMethodId, paymentId, json.getAmount(), currency,
                                                                          json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                                          pluginProperties, paymentOptions, callContext);
                break;
            case PURCHASE:
                result = paymentApi.createPurchaseWithPaymentControl(account, paymentMethodId, paymentId, json.getAmount(), currency,
                                                                     json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                                     pluginProperties, paymentOptions, callContext);
                break;
            case CREDIT:
                result = paymentApi.createCreditWithPaymentControl(account, paymentMethodId, paymentId, json.getAmount(), currency,
                                                                   json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                                   pluginProperties, paymentOptions, callContext);
                break;
            default:
                return Response.status(Status.PRECONDITION_FAILED).entity("TransactionType " + transactionType + " is not allowed for an account").build();
        }
        return createPaymentResponse(uriInfo, result, transactionType, json.getTransactionExternalKey());
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
    public Response getOverdueAccount(@PathParam("accountId") final String accountId,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, OverdueException, OverdueApiException {
        final TenantContext tenantContext = context.createContext(request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), tenantContext);
        final OverdueState overdueState = overdueApi.getOverdueStateFor(account, tenantContext);

        return Response.status(Status.OK).entity(new OverdueStateJson(overdueState, paymentConfig)).build();
    }

    /*
     * *************************      CUSTOM FIELDS     *****************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account custom fields", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to account")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(UUID.fromString(id), customFields, context.createContext(createdBy, reason,
                                             comment, request), uriInfo);
    }

    @TimedResource
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from account")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    /*
     * *************************     TAGS     *****************************
     */

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account tags", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final String accountIdString,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        final UUID accountId = UUID.fromString(accountIdString);
        return super.getTags(accountId, accountId, auditMode, includedDeleted, context.createContext(request));
    }

    @TimedResource
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + ALL_TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve account tags", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getAllTags(@PathParam(ID_PARAM_NAME) final String accountIdString,
                               @QueryParam(QUERY_OBJECT_TYPE) final ObjectType objectType,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        final UUID accountId = UUID.fromString(accountIdString);
        final TenantContext tenantContext = context.createContext(request);
        final List<Tag> tags = objectType != null ?
                               tagUserApi.getTagsForAccountType(accountId, objectType, includedDeleted, tenantContext) :
                               tagUserApi.getTagsForAccount(accountId, includedDeleted, tenantContext);
        return createTagResponse(accountId, tags, auditMode, tenantContext);
    }


    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to account")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
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

    @TimedResource
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from account")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied or account does not have a default payment method (AUTO_PAY_OFF tag only)")})
    public Response deleteTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        // Look if there is an AUTO_PAY_OFF for that account and check if the account has a default paymentMethod
        // If not we can't remove the AUTO_PAY_OFF tag
        final Collection<UUID> tagDefinitionUUIDs = getTagDefinitionUUIDs(tagList);
        boolean isTagAutoPayOff = false;
        for (final UUID cur : tagDefinitionUUIDs) {
            if (cur.equals(ControlTagType.AUTO_PAY_OFF.getId())) {
                isTagAutoPayOff = true;
                break;
            }
        }
        final UUID accountId = UUID.fromString(id);
        if (isTagAutoPayOff) {
            final Account account = accountUserApi.getAccountById(accountId, callContext);
            if (account.getPaymentMethodId() == null) {
                throw new TagApiException(ErrorCode.TAG_CANNOT_BE_REMOVED, ControlTagType.AUTO_PAY_OFF, " the account does not have a default payment method");
            }
        }

        return super.deleteTags(UUID.fromString(id), tagList, callContext);
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
    public Response getEmails(@PathParam(ID_PARAM_NAME) final String id,
                              @javax.ws.rs.core.Context final HttpServletRequest request) {
        final UUID accountId = UUID.fromString(id);
        final List<AccountEmail> emails = accountUserApi.getEmails(accountId, context.createContext(request));

        final List<AccountEmailJson> emailsJson = new ArrayList<AccountEmailJson>();
        for (final AccountEmail email : emails) {
            emailsJson.add(new AccountEmailJson(email.getAccountId().toString(), email.getEmail()));
        }
        return Response.status(Status.OK).entity(emailsJson).build();
    }

    @TimedResource
    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add account email")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response addEmail(final AccountEmailJson json,
                             @PathParam(ID_PARAM_NAME) final String id,
                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                             @HeaderParam(HDR_REASON) final String reason,
                             @HeaderParam(HDR_COMMENT) final String comment,
                             @javax.ws.rs.core.Context final HttpServletRequest request,
                             @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException {
        verifyNonNullOrEmpty(json, "AccountEmailJson body should be specified");
        verifyNonNullOrEmpty(json.getEmail(), "AccountEmailJson email needs to be set");

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID accountId = UUID.fromString(id);

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

        return uriBuilder.buildResponse(uriInfo, AccountResource.class, "getEmails", json.getAccountId());
    }

    @TimedResource
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS + "/{email}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Delete email from account")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response removeEmail(@PathParam(ID_PARAM_NAME) final String id,
                                @PathParam("email") final String email,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final HttpServletRequest request) {
        final UUID accountId = UUID.fromString(id);

        final List<AccountEmail> emails = accountUserApi.getEmails(accountId, context.createContext(request));
        for (final AccountEmail cur : emails) {
            if (cur.getEmail().equals(email)) {
                final AccountEmailJson accountEmailJson = new AccountEmailJson(accountId.toString(), email);
                final AccountEmail accountEmail = accountEmailJson.toAccountEmail(cur.getId());
                accountUserApi.removeEmail(accountId, accountEmail, context.createContext(createdBy, reason, comment, request));
            }
        }
        return Response.status(Status.OK).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.ACCOUNT;
    }
}
