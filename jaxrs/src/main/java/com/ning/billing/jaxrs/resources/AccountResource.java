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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

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
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.jaxrs.json.AccountEmailJson;
import com.ning.billing.jaxrs.json.AccountJson;
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
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;

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
    private final Context context;

    @Inject
    public AccountResource(final JaxrsUriBuilder uriBuilder,
                           final AccountUserApi accountApi,
                           final EntitlementUserApi entitlementApi,
                           final InvoiceUserApi invoiceApi,
                           final InvoicePaymentApi invoicePaymentApi,
                           final PaymentApi paymentApi,
                           final EntitlementTimelineApi timelineApi,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi);
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.invoiceApi = invoiceApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.paymentApi = paymentApi;
        this.timelineApi = timelineApi;
        this.context = context;
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getAccount(@PathParam("accountId") final String accountId) throws AccountApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(accountId));

        final AccountJson json = new AccountJson(account);
        return Response.status(Status.OK).entity(json).build();
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + BUNDLES)
    @Produces(APPLICATION_JSON)
    public Response getAccountBundles(@PathParam("accountId") final String accountId) throws AccountApiException {
        final UUID uuid = UUID.fromString(accountId);
        accountApi.getAccountById(uuid);

        final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(uuid);
        final Collection<BundleJsonNoSubscriptions> result = Collections2.transform(bundles, new Function<SubscriptionBundle, BundleJsonNoSubscriptions>() {
            @Override
            public BundleJsonNoSubscriptions apply(final SubscriptionBundle input) {
                return new BundleJsonNoSubscriptions(input);
            }
        });
        return Response.status(Status.OK).entity(result).build();
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getAccountByKey(@QueryParam(QUERY_EXTERNAL_KEY) final String externalKey) throws AccountApiException {
        final Account account = accountApi.getAccountByKey(externalKey);
        final AccountJson json = new AccountJson(account);

        return Response.status(Status.OK).entity(json).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createAccount(final AccountJson json,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment) throws AccountApiException {
        final AccountData data = json.toAccountData();
        final Account account = accountApi.createAccount(data, context.createContext(createdBy, reason, comment));
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
                                  @HeaderParam(HDR_COMMENT) final String comment) throws AccountApiException {
        final AccountData data = json.toAccountData();
        final UUID uuid = UUID.fromString(accountId);
        accountApi.updateAccount(uuid, data, context.createContext(createdBy, reason, comment));
        return getAccount(accountId);
    }

    // Not supported
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response cancelAccount(@PathParam("accountId") final String accountId) {
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
    public Response getAccountTimeline(@PathParam("accountId") final String accountIdString) throws AccountApiException, PaymentApiException, EntitlementRepairException {
        final UUID accountId = UUID.fromString(accountIdString);
        final Account account = accountApi.getAccountById(accountId);

        // Get the invoices
        final List<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId());

        // Get the payments
        final List<Payment> payments = paymentApi.getAccountPayments(accountId);

        // Get the refunds
        final List<Refund> refunds = paymentApi.getAccountRefunds(account);
        final Multimap<UUID, Refund> refundsByPayment = ArrayListMultimap.<UUID, Refund>create();
        for (final Refund refund : refunds) {
            refundsByPayment.put(refund.getPaymentId(), refund);
        }

        // Get the chargebacks
        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByAccountId(accountId);
        final Multimap<UUID, InvoicePayment> chargebacksByPayment = ArrayListMultimap.<UUID, InvoicePayment>create();
        for (final InvoicePayment chargeback : chargebacks) {
            chargebacksByPayment.put(chargeback.getPaymentId(), chargeback);
        }

        // Get the bundles
        final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(account.getId());
        final List<BundleTimeline> bundlesTimeline = new LinkedList<BundleTimeline>();
        for (final SubscriptionBundle cur : bundles) {
            bundlesTimeline.add(timelineApi.getBundleRepair(cur.getId()));
        }

        final AccountTimelineJson json = new AccountTimelineJson(account, invoices, payments, bundlesTimeline,
                                                                 refundsByPayment, chargebacksByPayment);

        return Response.status(Status.OK).entity(json).build();
    }

    /*
     * ************************** EMAIL NOTIFICATIONS FOR INVOICES ********************************
     */

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAIL_NOTIFICATIONS)
    @Produces(APPLICATION_JSON)
    public Response getEmailNotificationsForAccount(@PathParam("accountId") final String accountId) throws AccountApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(accountId));
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
                                                    @HeaderParam(HDR_COMMENT) final String comment) throws AccountApiException {
        final UUID accountId = UUID.fromString(accountIdString);
        final Account account = accountApi.getAccountById(accountId);

        final MutableAccountData mutableAccountData = account.toMutableAccountData();
        mutableAccountData.setIsNotifiedForInvoices(json.isNotifiedForInvoices());
        accountApi.updateAccount(accountId, mutableAccountData, context.createContext(createdBy, reason, comment));

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
                                @QueryParam(QUERY_PAYMENT_NAME_ON_CC) final String nameOnCC) throws PaymentApiException {
        final List<Payment> payments = paymentApi.getAccountPayments(UUID.fromString(accountId));
        final List<PaymentJsonSimple> result = new ArrayList<PaymentJsonSimple>(payments.size());
        for (final Payment cur : payments) {
            result.add(new PaymentJsonSimple(cur));
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
                                        @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, PaymentApiException {
        final PaymentMethod data = json.toPaymentMethod();
        final Account account = accountApi.getAccountById(data.getAccountId());

        final UUID paymentMethodId = paymentApi.addPaymentMethod(data.getPluginName(), account, isDefault, data.getPluginDetail(), context.createContext(createdBy, reason, comment));
        return uriBuilder.buildResponse(PaymentMethodResource.class, "getPaymentMethod", paymentMethodId, uriInfo.getBaseUri().toString());
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + PAYMENT_METHODS)
    @Produces(APPLICATION_JSON)
    public Response getPaymentMethods(@PathParam("accountId") final String accountId,
                                      @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                      @QueryParam(QUERY_PAYMENT_LAST4_CC) final String last4CC,
                                      @QueryParam(QUERY_PAYMENT_NAME_ON_CC) final String nameOnCC) throws AccountApiException, PaymentApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(accountId));
        final List<PaymentMethod> methods = paymentApi.getPaymentMethods(account, withPluginInfo);
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
                                            @HeaderParam(HDR_COMMENT) final String comment) throws AccountApiException, PaymentApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(accountId));
        paymentApi.setDefaultPaymentMethod(account, UUID.fromString(paymentMethodId), context.createContext(createdBy, reason, comment));
        return Response.status(Status.OK).build();
    }

    /*
     * ************************** REFUNDS ********************************
     */
    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Produces(APPLICATION_JSON)
    public Response getRefunds(@PathParam("accountId") final String accountId) throws AccountApiException, PaymentApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(accountId));
        final List<Refund> refunds = paymentApi.getAccountRefunds(account);
        final List<RefundJson> result = new ArrayList<RefundJson>(Collections2.transform(refunds, new Function<Refund, RefundJson>() {
            @Override
            public RefundJson apply(Refund input) {
                return new RefundJson(input);
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
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id) {
        return super.getCustomFields(UUID.fromString(id));
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
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) {
        return super.createCustomFields(UUID.fromString(id), customFields, uriInfo,
                                        context.createContext(createdBy, reason, comment));
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
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList, uriInfo,
                                        context.createContext(createdBy, reason, comment));
    }

    /*
     * *************************     TAGS     *****************************
     */

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id) {
        return super.getTags(UUID.fromString(id));
    }

    @POST
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo) {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment));
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
                               @javax.ws.rs.core.Context final UriInfo uriInfo) {
        return super.deleteTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment));
    }

    /*
     * *************************     EMAILS     *****************************
     */

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS)
    @Produces(APPLICATION_JSON)
    public Response getEmails(@PathParam(ID_PARAM_NAME) final String id) {
        final UUID accountId = UUID.fromString(id);
        final List<AccountEmail> emails = accountApi.getEmails(accountId);

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
                             @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException {
        final UUID accountId = UUID.fromString(id);

        // Make sure the account exist or we will confuse the history and auditing code
        accountApi.getAccountById(accountId);

        accountApi.addEmail(accountId, json.toAccountEmail(), context.createContext(createdBy, reason, comment));

        return uriBuilder.buildResponse(AccountResource.class, "getEmails", json.getAccountId());
    }

    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}/" + EMAILS + "/{email}")
    @Produces(APPLICATION_JSON)
    public Response removeEmail(@PathParam(ID_PARAM_NAME) final String id,
                                @PathParam("email") final String email,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment) {
        final UUID accountId = UUID.fromString(id);
        final AccountEmailJson accountEmailJson = new AccountEmailJson(id, email);
        final AccountEmail accountEmail = accountEmailJson.toAccountEmail();
        accountApi.removeEmail(accountId, accountEmail, context.createContext(createdBy, reason, comment));

        return Response.status(Status.OK).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.ACCOUNT;
    }
}
