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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

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

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.EntitlementRepairException;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.jaxrs.json.AccountEmailJson;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.AccountJsonWithBalance;
import com.ning.billing.jaxrs.json.AccountJsonWithBalanceAndCBA;
import com.ning.billing.jaxrs.json.AccountTimelineJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.InvoiceEmailJson;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.audit.AuditLogsForBundles;
import com.ning.billing.util.audit.AuditLogsForInvoicePayments;
import com.ning.billing.util.audit.AuditLogsForInvoices;
import com.ning.billing.util.audit.AuditLogsForPayments;
import com.ning.billing.util.audit.AuditLogsForRefunds;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.tag.ControlTagType;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.ACCOUNTS_PATH)
public class AccountResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "accountId";

    private final AccountUserApi accountApi;
    private final EntitlementUserApi entitlementApi;
    private final EntitlementTimelineApi timelineApi;
    private final InvoiceUserApi invoiceApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final PaymentApi paymentApi;

    @Inject
    public AccountResource(final JaxrsUriBuilder uriBuilder,
                           final AccountUserApi accountApi,
                           final EntitlementUserApi entitlementApi,
                           final InvoiceUserApi invoiceApi,
                           final InvoicePaymentApi invoicePaymentApi,
                           final PaymentApi paymentApi,
                           final EntitlementTimelineApi timelineApi,
                           final TagUserApi tagUserApi,
                           final AuditUserApi auditUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, context);
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.invoiceApi = invoiceApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.paymentApi = paymentApi;
        this.timelineApi = timelineApi;
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getAccount(@PathParam("accountId") final String accountId,
                               @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                               @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Account account = accountApi.getAccountById(UUID.fromString(accountId), tenantContext);
        return getAccount(account, accountWithBalance, accountWithBalanceAndCBA, tenantContext);
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + BUNDLES)
    @Produces(APPLICATION_JSON)
    public Response getAccountBundles(@PathParam("accountId") final String accountId,
                                      @QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, EntitlementUserApiException {
        final TenantContext tenantContext = context.createContext(request);

        final UUID uuid = UUID.fromString(accountId);
        accountApi.getAccountById(uuid, tenantContext);

        if (externalKey != null) {
            final SubscriptionBundle bundle = entitlementApi.getBundleForAccountAndKey(uuid, externalKey, tenantContext);
            final BundleJsonNoSubscriptions json = new BundleJsonNoSubscriptions(bundle);
            return Response.status(Status.OK).entity(json).build();
        } else {
            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(uuid, tenantContext);
            final Collection<BundleJsonNoSubscriptions> result = Collections2.transform(bundles, new Function<SubscriptionBundle, BundleJsonNoSubscriptions>() {
                @Override
                public BundleJsonNoSubscriptions apply(final SubscriptionBundle input) {
                    return new BundleJsonNoSubscriptions(input);
                }
            });
            return Response.status(Status.OK).entity(result).build();
        }
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getAccountByKey(@QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                    @QueryParam(QUERY_ACCOUNT_WITH_BALANCE) @DefaultValue("false") final Boolean accountWithBalance,
                                    @QueryParam(QUERY_ACCOUNT_WITH_BALANCE_AND_CBA) @DefaultValue("false") final Boolean accountWithBalanceAndCBA,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Account account = accountApi.getAccountByKey(externalKey, tenantContext);
        return getAccount(account, accountWithBalance, accountWithBalanceAndCBA, tenantContext);
    }

    private Response getAccount(final Account account, final Boolean accountWithBalance, final Boolean accountWithBalanceAndCBA, final TenantContext tenantContext) {
        final AccountJson json;
        if (accountWithBalanceAndCBA) {
            final BigDecimal accountBalance = invoiceApi.getAccountBalance(account.getId(), tenantContext);
            final BigDecimal accountCBA = invoiceApi.getAccountCBA(account.getId(), tenantContext);
            json = new AccountJsonWithBalanceAndCBA(account, accountBalance, accountCBA);
        } else if (accountWithBalance) {
            final BigDecimal accountBalance = invoiceApi.getAccountBalance(account.getId(), tenantContext);
            json = new AccountJsonWithBalance(account, accountBalance);
        } else {
            json = new AccountJson(account);
        }
        return Response.status(Status.OK).entity(json).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createAccount(final AccountJson json,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final AccountData data = json.toAccountData();
        final Account account = accountApi.createAccount(data, context.createContext(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(AccountResource.class, "getAccount", account.getId());
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}")
    public Response updateAccount(final AccountJson json,
                                  @PathParam("accountId") final String accountId,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final AccountData data = json.toAccountData();
        final UUID uuid = UUID.fromString(accountId);
        accountApi.updateAccount(uuid, data, context.createContext(createdBy, reason, comment, request));
        return getAccount(accountId, false, false, request);
    }

    // Not supported
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response cancelAccount(@PathParam("accountId") final String accountId,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) {
        /*
        try {
            accountApi.cancelAccount(accountId);
            return Response.status(Status.NO_CONTENT).build();
        } catch (AccountApiException e) {
            log.info(String.format("Failed to cancel account %s", accountId), e);
            return Response.status(Status.BAD_REQUEST).build();
        }
       */
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TIMELINE)
    @Produces(APPLICATION_JSON)
    public Response getAccountTimeline(@PathParam("accountId") final String accountIdString,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException, EntitlementRepairException {
        final TenantContext tenantContext = context.createContext(request);

        final UUID accountId = UUID.fromString(accountIdString);
        final Account account = accountApi.getAccountById(accountId, tenantContext);

        // Get the invoices
        final List<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), tenantContext);
        final AuditLogsForInvoices invoicesAuditLogs = auditUserApi.getAuditLogsForInvoices(invoices, auditMode.getLevel(), tenantContext);

        // Get the payments
        final List<Payment> payments = paymentApi.getAccountPayments(accountId, tenantContext);
        final AuditLogsForPayments paymentsAuditLogs = auditUserApi.getAuditLogsForPayments(payments, auditMode.getLevel(), tenantContext);

        // Get the refunds
        final List<Refund> refunds = paymentApi.getAccountRefunds(account, tenantContext);
        final AuditLogsForRefunds refundsAuditLogs = auditUserApi.getAuditLogsForRefunds(refunds, auditMode.getLevel(), tenantContext);
        final Multimap<UUID, Refund> refundsByPayment = ArrayListMultimap.<UUID, Refund>create();
        for (final Refund refund : refunds) {
            refundsByPayment.put(refund.getPaymentId(), refund);
        }

        // Get the chargebacks
        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByAccountId(accountId, tenantContext);
        final AuditLogsForInvoicePayments chargebacksAuditLogs = auditUserApi.getAuditLogsForInvoicePayments(chargebacks, auditMode.getLevel(), tenantContext);
        final Multimap<UUID, InvoicePayment> chargebacksByPayment = ArrayListMultimap.<UUID, InvoicePayment>create();
        for (final InvoicePayment chargeback : chargebacks) {
            chargebacksByPayment.put(chargeback.getPaymentId(), chargeback);
        }

        // Get the bundles
        final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(account.getId(), tenantContext);
        final List<BundleTimeline> bundlesTimeline = new LinkedList<BundleTimeline>();
        for (final SubscriptionBundle bundle : bundles) {
            bundlesTimeline.add(timelineApi.getBundleTimeline(bundle.getId(), tenantContext));
        }
        final AuditLogsForBundles bundlesAuditLogs = auditUserApi.getAuditLogsForBundles(bundlesTimeline, auditMode.getLevel(), tenantContext);

        final AccountTimelineJson json = new AccountTimelineJson(account, invoices, payments, bundlesTimeline,
                                                                 refundsByPayment, chargebacksByPayment,
                                                                 invoicesAuditLogs, paymentsAuditLogs, refundsAuditLogs,
                                                                 chargebacksAuditLogs, bundlesAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }

    /*
    * ************************** EMAIL NOTIFICATIONS FOR INVOICES ********************************
    */

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAIL_NOTIFICATIONS)
    @Produces(APPLICATION_JSON)
    public Response getEmailNotificationsForAccount(@PathParam("accountId") final String accountId,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(accountId), context.createContext(request));
        final InvoiceEmailJson invoiceEmailJson = new InvoiceEmailJson(accountId, account.isNotifiedForInvoices());

        return Response.status(Status.OK).entity(invoiceEmailJson).build();
    }

    @PUT
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAIL_NOTIFICATIONS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response getEmailNotificationsForAccount(final InvoiceEmailJson json,
                                                    @PathParam("accountId") final String accountIdString,
                                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                    @HeaderParam(HDR_REASON) final String reason,
                                                    @HeaderParam(HDR_COMMENT) final String comment,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID accountId = UUID.fromString(accountIdString);
        final Account account = accountApi.getAccountById(accountId, callContext);

        final MutableAccountData mutableAccountData = account.toMutableAccountData();
        mutableAccountData.setIsNotifiedForInvoices(json.isNotifiedForInvoices());
        accountApi.updateAccount(accountId, mutableAccountData, callContext);

        return Response.status(Status.OK).build();
    }

    /*
     * ************************** PAYMENTS ********************************
     */

    @GET
    @Path("/{accountId:\\w+-\\w+-\\w+-\\w+-\\w+}/" + PAYMENTS)
    @Produces(APPLICATION_JSON)
    public Response getPayments(@PathParam("accountId") final String accountId,
                                @QueryParam(QUERY_PAYMENT_LAST4_CC) final String last4CC,
                                @QueryParam(QUERY_PAYMENT_NAME_ON_CC) final String nameOnCC,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final List<Payment> payments = paymentApi.getAccountPayments(UUID.fromString(accountId), context.createContext(request));
        final List<PaymentJsonSimple> result = new ArrayList<PaymentJsonSimple>(payments.size());
        for (final Payment payment : payments) {
            result.add(new PaymentJsonSimple(payment));
        }
        return Response.status(Status.OK).entity(result).build();
    }

    @POST
    @Path("/{accountId:\\w+-\\w+-\\w+-\\w+-\\w+}/" + PAYMENT_METHODS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createPaymentMethod(final PaymentMethodJson json,
                                        @QueryParam(QUERY_PAYMENT_METHOD_IS_DEFAULT) @DefaultValue("false") final Boolean isDefault,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final PaymentMethod data = json.toPaymentMethod();
        final Account account = accountApi.getAccountById(data.getAccountId(), callContext);

        final UUID paymentMethodId = paymentApi.addPaymentMethod(data.getPluginName(), account, isDefault, data.getPluginDetail(), callContext);
        return uriBuilder.buildResponse(PaymentMethodResource.class, "getPaymentMethod", paymentMethodId, uriInfo.getBaseUri().toString());
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS)
    @Produces(APPLICATION_JSON)
    public Response getPaymentMethods(@PathParam("accountId") final String accountId,
                                      @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                      @QueryParam(QUERY_PAYMENT_LAST4_CC) final String last4CC,
                                      @QueryParam(QUERY_PAYMENT_NAME_ON_CC) final String nameOnCC,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);

        final Account account = accountApi.getAccountById(UUID.fromString(accountId), tenantContext);
        final List<PaymentMethod> methods = paymentApi.getPaymentMethods(account, withPluginInfo, tenantContext);
        final List<PaymentMethodJson> json = new ArrayList<PaymentMethodJson>(Collections2.transform(methods, new Function<PaymentMethod, PaymentMethodJson>() {
            @Override
            public PaymentMethodJson apply(final PaymentMethod input) {
                return PaymentMethodJson.toPaymentMethodJson(account, input);
            }
        }));

        return Response.status(Status.OK).entity(json).build();
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS + "/{paymentMethodId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS_DEFAULT_PATH_POSTFIX)
    public Response setDefaultPaymentMethod(@PathParam("accountId") final String accountId,
                                            @PathParam("paymentMethodId") final String paymentMethodId,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountApi.getAccountById(UUID.fromString(accountId), callContext);
        paymentApi.setDefaultPaymentMethod(account, UUID.fromString(paymentMethodId), callContext);
        return Response.status(Status.OK).build();
    }

    /*
     * ************************** REFUNDS ********************************
     */
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Produces(APPLICATION_JSON)
    public Response getRefunds(@PathParam("accountId") final String accountId,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);

        final Account account = accountApi.getAccountById(UUID.fromString(accountId), tenantContext);
        final List<Refund> refunds = paymentApi.getAccountRefunds(account, tenantContext);
        final List<RefundJson> result = new ArrayList<RefundJson>(Collections2.transform(refunds, new Function<Refund, RefundJson>() {
            @Override
            public RefundJson apply(Refund input) {
                // TODO Return adjusted items and audits
                return new RefundJson(input, null, null);
            }
        }));

        return Response.status(Status.OK).entity(result).build();
    }

    /*
     * *************************      CUSTOM FIELDS     *****************************
     */

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), context.createContext(request));
    }

    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    /*
     * *************************     TAGS     *****************************
     */

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("false") final Boolean withAudit,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        return super.getTags(UUID.fromString(id), withAudit, context.createContext(request));
    }

    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
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

    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
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
            final Account account = accountApi.getAccountById(accountId, callContext);
            if (account.getPaymentMethodId() == null) {
                throw new TagApiException(ErrorCode.TAG_CANNOT_BE_REMOVED, ControlTagType.AUTO_PAY_OFF, " the account does not have a default payment method");
            }
        }

        return super.deleteTags(UUID.fromString(id), tagList, callContext);
    }

    /*
     * *************************     EMAILS     *****************************
     */

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS)
    @Produces(APPLICATION_JSON)
    public Response getEmails(@PathParam(ID_PARAM_NAME) final String id,
                              @javax.ws.rs.core.Context final HttpServletRequest request) {
        final UUID accountId = UUID.fromString(id);
        final List<AccountEmail> emails = accountApi.getEmails(accountId, context.createContext(request));

        final List<AccountEmailJson> emailsJson = new ArrayList<AccountEmailJson>();
        for (final AccountEmail email : emails) {
            emailsJson.add(new AccountEmailJson(email.getAccountId().toString(), email.getEmail()));
        }
        return Response.status(Status.OK).entity(emailsJson).build();
    }

    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response addEmail(final AccountEmailJson json,
                             @PathParam(ID_PARAM_NAME) final String id,
                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                             @HeaderParam(HDR_REASON) final String reason,
                             @HeaderParam(HDR_COMMENT) final String comment,
                             @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID accountId = UUID.fromString(id);

        // Make sure the account exist or we will confuse the history and auditing code
        accountApi.getAccountById(accountId, callContext);

        accountApi.addEmail(accountId, json.toAccountEmail(), callContext);

        return uriBuilder.buildResponse(AccountResource.class, "getEmails", json.getAccountId());
    }

    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS + "/{email}")
    @Produces(APPLICATION_JSON)
    public Response removeEmail(@PathParam(ID_PARAM_NAME) final String id,
                                @PathParam("email") final String email,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final HttpServletRequest request) {
        final UUID accountId = UUID.fromString(id);
        final AccountEmailJson accountEmailJson = new AccountEmailJson(id, email);
        final AccountEmail accountEmail = accountEmailJson.toAccountEmail();
        accountApi.removeEmail(accountId, accountEmail, context.createContext(createdBy, reason, comment, request));

        return Response.status(Status.OK).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.ACCOUNT;
    }
}
