/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.jaxrs.json.AuditLogJson;
import org.killbill.billing.jaxrs.json.BillingExceptionJson;
import org.killbill.billing.jaxrs.json.BillingExceptionJson.StackTraceElementJson;
import org.killbill.billing.jaxrs.json.BlockingStateJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.InvoiceItemJson;
import org.killbill.billing.jaxrs.json.JsonBase;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.json.PluginPropertyJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.commons.utils.Joiner;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.Strings;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogsForObjectType;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.customfield.StringCustomField;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.TagDefinition;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;

public abstract class JaxRsResourceBase implements JaxrsResource {

    static final Logger log = LoggerFactory.getLogger(JaxRsResourceBase.class);

    // Catalog API don't quite support multiple catalogs per tenant
    protected static final String catalogName = "unused";

    protected static final ObjectMapper mapper = new ObjectMapper();

    protected final JaxrsUriBuilder uriBuilder;
    protected final TagUserApi tagUserApi;
    protected final CustomFieldUserApi customFieldUserApi;
    protected final AuditUserApi auditUserApi;
    protected final AccountUserApi accountUserApi;
    protected final PaymentApi paymentApi;
    protected final InvoicePaymentApi invoicePaymentApi;
    protected final SubscriptionApi subscriptionApi;
    protected final Context context;
    protected final Clock clock;

    protected final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTimeParser();
    protected final DateTimeFormatter LOCAL_DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");

    public JaxRsResourceBase(final JaxrsUriBuilder uriBuilder,
                             final TagUserApi tagUserApi,
                             final CustomFieldUserApi customFieldUserApi,
                             final AuditUserApi auditUserApi,
                             final AccountUserApi accountUserApi,
                             final PaymentApi paymentApi,
                             final InvoicePaymentApi invoicePaymentApi,
                             final SubscriptionApi subscriptionApi,
                             final Clock clock,
                             final Context context) {
        this.uriBuilder = uriBuilder;
        this.tagUserApi = tagUserApi;
        this.customFieldUserApi = customFieldUserApi;
        this.auditUserApi = auditUserApi;
        this.accountUserApi = accountUserApi;
        this.paymentApi = paymentApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.subscriptionApi = subscriptionApi;
        this.clock = clock;
        this.context = context;
    }

    protected ObjectType getObjectType() {
        return null;
    }

    protected Response addBlockingState(final BlockingStateJson json,
                                        final UUID accountId,
                                        final UUID blockableId,
                                        final BlockingStateType type,
                                        final String requestedDate,
                                        final List<String> pluginPropertiesString,
                                        final String createdBy,
                                        final String reason,
                                        final String comment,
                                        final HttpServletRequest request,
                                        @Nullable final UriInfo uriInfo) throws SubscriptionApiException, EntitlementApiException, AccountApiException {

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final boolean isBlockBilling = (json.isBlockBilling() != null && json.isBlockBilling());
        final boolean isBlockEntitlement = (json.isBlockEntitlement() != null && json.isBlockEntitlement());
        final boolean isBlockChange = (json.isBlockChange() != null && json.isBlockChange());

        final BlockingState input = new DefaultBlockingState(blockableId, type, json.getStateName(), json.getService(), isBlockChange, isBlockEntitlement, isBlockBilling, null);
        if (isDateTime(requestedDate)) {
            subscriptionApi.addBlockingState(input, toDateTime(requestedDate), pluginProperties, callContext);
        } else {
            subscriptionApi.addBlockingState(input, toLocalDate(requestedDate), pluginProperties, callContext);
        }
        return uriInfo != null ?
               uriBuilder.buildResponse(uriInfo, AccountResource.class, "getBlockingStates", accountId, Map.of(QUERY_BLOCKING_STATE_TYPES, type.name()) , request) :
               null;
    }

    protected Response getTags(@Nullable final UUID accountId, final UUID taggedObjectId, final AuditMode auditMode, final boolean includeDeleted, final TenantContext context) throws TagDefinitionApiException {
        final List<Tag> tags = tagUserApi.getTagsForObject(taggedObjectId, getObjectType(), includeDeleted, context);
        return createTagResponse(accountId, tags, auditMode, context);
    }

    protected Response createTagResponse(@Nullable final UUID accountId, final List<Tag> tags, final AuditMode auditMode, final TenantContext context) throws TagDefinitionApiException {
        Preconditions.checkState(accountId != null || AuditLevel.NONE.equals(auditMode.getLevel()), "No accountId specified but auditLevel is " + auditMode.getLevel());

        final AccountAuditLogsForObjectType tagsAuditLogs = tags.isEmpty() ? null : auditUserApi.getAccountAuditLogs(accountId, ObjectType.TAG, auditMode.getLevel(), context);
        final Map<UUID, TagDefinition> tagDefinitionsCache = new HashMap<UUID, TagDefinition>();
        final Collection<TagJson> result = new LinkedList<TagJson>();
        for (final Tag tag : tags) {
            if (tagDefinitionsCache.get(tag.getTagDefinitionId()) == null) {
                tagDefinitionsCache.put(tag.getTagDefinitionId(), tagUserApi.getTagDefinition(tag.getTagDefinitionId(), context));
            }
            final TagDefinition tagDefinition = tagDefinitionsCache.get(tag.getTagDefinitionId());

            final List<AuditLog> auditLogs = tagsAuditLogs.getAuditLogs(tag.getId());
            result.add(new TagJson(tag, tagDefinition, auditLogs));
        }
        return Response.status(Response.Status.OK).entity(result).build();
    }

    protected Response createTags(final UUID id,
                                  final List<UUID> tagList,
                                  final UriInfo uriInfo,
                                  final CallContext context,
                                  final HttpServletRequest request) throws TagApiException {
        tagUserApi.addTags(id, getObjectType(), tagList, context);
        // TODO This will always return 201, even if some (or all) tags already existed (in which case we don't do anything)
        return uriBuilder.buildResponse(uriInfo, this.getClass(), "getTags", id, request);
    }

    protected Collection<UUID> getTagDefinitionUUIDs(final List<String> tagList) {
        return tagList.stream().map(UUID::fromString).collect(Collectors.toList());
    }

    protected Response deleteTags(final UUID id,
                                  final List<UUID> tagList,
                                  final CallContext context) throws TagApiException {
        tagUserApi.removeTags(id, getObjectType(), tagList, context);
        return Response.status(Status.NO_CONTENT).build();
    }

    protected Response getCustomFields(final UUID id, final AuditMode auditMode, final TenantContext context) {
        final List<CustomField> fields = customFieldUserApi.getCustomFieldsForObject(id, getObjectType(), context);
        return createCustomFieldResponse(fields, auditMode, context);
    }

    protected Response createCustomFieldResponse(final Iterable<CustomField> fields, final AuditMode auditMode, final TenantContext context) {
        final Collection<CustomFieldJson> result = new LinkedList<CustomFieldJson>();
        for (final CustomField cur : fields) {
            // TODO PIERRE - Bulk API
            final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(cur.getId(), ObjectType.CUSTOM_FIELD, auditMode.getLevel(), context);
            result.add(new CustomFieldJson(cur, auditLogs));
        }

        return Response.status(Response.Status.OK).entity(result).build();
    }

    protected Response createCustomFields(final UUID id,
                                          final List<CustomFieldJson> customFields,
                                          final CallContext context,
                                          final UriInfo uriInfo,
                                          final HttpServletRequest request) throws CustomFieldApiException {
        final LinkedList<CustomField> input = new LinkedList<CustomField>();
        for (final CustomFieldJson cur : customFields) {
            verifyNonNullOrEmpty(cur.getName(), "CustomFieldJson name needs to be set");
            verifyNonNullOrEmpty(cur.getValue(), "CustomFieldJson value needs to be set");
            input.add(new StringCustomField(cur.getName(), cur.getValue(), getObjectType(), id, context.getCreatedDate()));
        }

        customFieldUserApi.addCustomFields(input, context);
        return uriBuilder.buildResponse(uriInfo, this.getClass(), "getCustomFields", id, request);
    }

    protected Response modifyCustomFields(final UUID id,
                                          final List<CustomFieldJson> customFields,
                                          final CallContext context) throws CustomFieldApiException {
        final LinkedList<CustomField> input = new LinkedList<CustomField>();
        for (final CustomFieldJson cur : customFields) {
            verifyNonNullOrEmpty(cur.getCustomFieldId(), "CustomFieldJson id needs to be set");
            verifyNonNullOrEmpty(cur.getValue(), "CustomFieldJson value needs to be set");
            input.add(new StringCustomField(cur.getCustomFieldId(), cur.getName(), cur.getValue(), getObjectType(), id, context.getCreatedDate()));
        }

        customFieldUserApi.updateCustomFields(input, context);
        return Response.status(Status.NO_CONTENT).build();
    }

    /**
     * @param id              the if of the object for which the custom fields apply
     * @param customFieldList a comma separated list of custom field ids or null if they should all be removed
     * @param context         the context
     * @return
     * @throws CustomFieldApiException
     */
    protected Response deleteCustomFields(final UUID id, final List<UUID> customFieldList, final CallContext context) throws CustomFieldApiException {
        // Retrieve all the custom fields for the object
        final List<CustomField> fields = customFieldUserApi.getCustomFieldsForObject(id, getObjectType(), context);

        // Filter the proposed list to only keep the one that exist and indeed match our object
        final List<CustomField> toRemove = fields.stream()
                .filter(input -> {
                    if (customFieldList == null || customFieldList.isEmpty()) {
                        return true;
                    }
                    for (final UUID curId : customFieldList) {
                        if (input.getId().equals(curId)) {
                            return true;
                        }
                    }
                    return false;
                }).collect(Collectors.toUnmodifiableList());

        if (!toRemove.isEmpty()) {
            customFieldUserApi.removeCustomFields(toRemove, context);
        }
        return Response.status(Status.NO_CONTENT).build();
    }

    protected <E extends Entity, J extends JsonBase> Response buildStreamingPaginationResponse(final Pagination<E> entities,
                                                                                               final Function<E, J> toJson,
                                                                                               final URI nextPageUri) {
        final StreamingOutput json = new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException, WebApplicationException {
                final Iterator<E> iterator = entities.iterator();

                try {
                    final JsonGenerator generator = mapper.getFactory().createGenerator(output);
                    generator.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

                    generator.writeStartArray();
                    while (iterator.hasNext()) {
                        final E entity = iterator.next();
                        final J asJson = toJson.apply(entity);
                        if (asJson != null) {
                            generator.writeObject(asJson);
                        }
                    }
                    generator.writeEndArray();
                    generator.close();
                } finally {
                    // In case the client goes away (IOException), make sure to close the underlying DB connection
                    entities.close();
                }
            }
        };

        return Response.status(Status.OK)
                       .entity(json)
                       .header(HDR_PAGINATION_CURRENT_OFFSET, entities.getCurrentOffset())
                       .header(HDR_PAGINATION_NEXT_OFFSET, entities.getNextOffset())
                       .header(HDR_PAGINATION_TOTAL_NB_RECORDS, entities.getTotalNbRecords())
                       .header(HDR_PAGINATION_MAX_NB_RECORDS, entities.getMaxNbRecords())
                       .header(HDR_PAGINATION_NEXT_PAGE_URI, nextPageUri)
                       .build();
    }

    protected void validatePaymentMethodForAccount(final UUID accountId, final UUID paymentMethodId, final CallContext callContext) throws PaymentApiException {
        if (paymentMethodId != null) {
            final PaymentMethod paymentMethod = paymentApi.getPaymentMethodById(paymentMethodId, false, false, Collections.emptyList(), callContext);
            if (!paymentMethod.getAccountId().equals(accountId)) {
                throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
            }
        }
    }

    protected Payment getPaymentByIdOrKey(@Nullable final UUID paymentId, @Nullable final String externalKey, final Iterable<PluginProperty> pluginProperties, final TenantContext tenantContext) throws PaymentApiException {
        Preconditions.checkArgument(paymentId != null || externalKey != null, "Need to set either paymentId or payment externalKey");
        if (paymentId != null) {
            return paymentApi.getPayment(paymentId, false, false, pluginProperties, tenantContext);
        } else {
            return paymentApi.getPaymentByExternalKey(externalKey, false, false, pluginProperties, tenantContext);
        }
    }

    protected void completeTransactionInternal(final PaymentTransactionJson json,
                                                   final Payment initialPayment,
                                                   final List<String> paymentControlPluginNames,
                                                   final Iterable<PluginProperty> pluginProperties,
                                                   final TenantContext contextNoAccountId,
                                                   final String createdBy,
                                                   final String reason,
                                                   final String comment,
                                                   final UriInfo uriInfo,
                                                   final HttpServletRequest request) throws PaymentApiException, AccountApiException {

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), contextNoAccountId);
        final BigDecimal amount = json == null ? null : json.getAmount();
        final Currency currency = json == null ? null: json.getCurrency();

        final CallContext callContext = context.createCallContextWithAccountId(account.getId(), createdBy, reason, comment, request);

        final PaymentTransaction pendingOrSuccessTransaction = lookupPendingOrSuccessTransaction(initialPayment,
                                                                                                 json != null ? json.getTransactionId() : null,
                                                                                                 json != null ? json.getTransactionExternalKey() : null,
                                                                                                 json != null ? json.getTransactionType() : null);
        // If transaction was already completed, return early (See #626)
        if (pendingOrSuccessTransaction.getTransactionStatus() == TransactionStatus.SUCCESS) {
            return;
        }

        final PaymentTransaction pendingTransaction = pendingOrSuccessTransaction;
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        switch (pendingTransaction.getTransactionType()) {
            case AUTHORIZE:
                paymentApi.createAuthorizationWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), amount, currency, null,
                                                                 initialPayment.getExternalKey(), pendingTransaction.getExternalKey(),
                                                                 pluginProperties, paymentOptions, callContext);
                break;
            case CAPTURE:
                paymentApi.createCaptureWithPaymentControl(account, initialPayment.getId(), amount, currency, null, pendingTransaction.getExternalKey(),
                                                           pluginProperties, paymentOptions, callContext);
                break;
            case PURCHASE:
                paymentApi.createPurchaseWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), amount, currency, null,
                                                            initialPayment.getExternalKey(), pendingTransaction.getExternalKey(),
                                                            pluginProperties, paymentOptions, callContext);
                break;
            case CREDIT:
                paymentApi.createCreditWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), amount, currency, null,
                                                          initialPayment.getExternalKey(), pendingTransaction.getExternalKey(),
                                                          pluginProperties, paymentOptions, callContext);
                break;
            case REFUND:
                paymentApi.createRefundWithPaymentControl(account, initialPayment.getId(), amount, currency, null,
                                                          pendingTransaction.getExternalKey(), pluginProperties, paymentOptions, callContext);
                break;
            default:
                throw new IllegalStateException("TransactionType " + pendingTransaction.getTransactionType() + " cannot be completed");
        }
    }

    protected PaymentTransaction lookupPendingOrSuccessTransaction(final Payment initialPayment, @Nullable final UUID transactionId, @Nullable final String transactionExternalKey, @Nullable final TransactionType transactionType) throws PaymentApiException {
        final Collection<PaymentTransaction> pendingTransaction = initialPayment.getTransactions().stream()
                .filter(input -> {
                    if (input.getTransactionStatus() != TransactionStatus.PENDING && input.getTransactionStatus() != TransactionStatus.SUCCESS) {
                        return false;
                    }
                    if (transactionId != null && !transactionId.equals(input.getId())) {
                        return false;
                    }
                    if (transactionExternalKey != null && !transactionExternalKey.equals(input.getExternalKey())) {
                        return false;
                    }
                    if (transactionType != null && !transactionType.equals(input.getTransactionType())) {
                        return false;
                    }
                    //
                    // If we were given a transactionId or a transactionExternalKey or a transactionType we checked there was a match;
                    // In the worst case, if we were given nothing, we return the PENDING transaction for that payment
                    //
                    return true;
                })
                .collect(Collectors.toUnmodifiableList());

        switch (pendingTransaction.size()) {
            // Nothing: invalid input...
            case 0:
                final String parameterType;
                final String parameterValue;
                if (transactionId != null) {
                    parameterType = "transactionId";
                    parameterValue = transactionId.toString();
                } else if (transactionExternalKey != null) {
                    parameterType = "transactionExternalKey";
                    parameterValue = transactionExternalKey;
                } else if (transactionType != null) {
                    parameterType = "transactionType";
                    parameterValue = transactionType.name();
                } else {
                    parameterType = "paymentId";
                    parameterValue = initialPayment.getId().toString();
                }
                throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, parameterType, parameterValue);
            case 1:
                return pendingTransaction.iterator().next();
            default:
                throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, String.format("Illegal payment state: Found multiple PENDING payment transactions for paymentId='%s'", initialPayment.getId()));

        }
    }

    protected LocalDate toLocalDateDefaultToday(final UUID accountId, @Nullable final String inputDate, final TenantContext context) throws AccountApiException {
        final Account account = accountId != null ? accountUserApi.getAccountById(accountId, context) : null;
        return toLocalDateDefaultToday(account, inputDate, context);
    }

    protected LocalDate toLocalDateDefaultToday(final Account account, @Nullable final String inputDate, final TenantContext context) {
        // TODO Switch to cached normalized timezone when available
        return Objects.requireNonNullElse(toLocalDate(inputDate), clock.getToday(account.getTimeZone()));
    }

    // API for subscription and invoice generation: keep null, the lower layers will default to now()
    protected LocalDate toLocalDate(@Nullable final String inputDate) {
        return inputDate == null || inputDate.isEmpty() ? null : LocalDate.parse(inputDate, LOCAL_DATE_FORMATTER);
    }
    
    protected DateTime toDateTime(@Nullable final String inputDate) {
        return inputDate == null || inputDate.isEmpty() ? null : DateTime.parse(inputDate, DATE_TIME_FORMATTER);
    }  

    protected boolean isDateTime(@Nullable final String inputDate) {
    	return inputDate != null && inputDate.contains("T") ? true : false;
    }

    protected Iterable<PluginProperty> extractPluginProperties(@Nullable final Iterable<PluginPropertyJson> pluginProperties) {
        return pluginProperties != null ? Iterables.toStream(pluginProperties)
                                                   .map(PluginPropertyJson::toPluginProperty)
                                                   .collect(Collectors.toUnmodifiableList())
                                        : Collections.emptyList();

    }

    protected Iterable<PluginProperty> extractPluginProperties(@Nullable final Iterable<String> pluginProperties, final PluginProperty... additionalProperties) {
        final Collection<PluginProperty> properties = new LinkedList<PluginProperty>();
        if (pluginProperties == null) {
            return properties;
        }

        for (final String pluginProperty : pluginProperties) {
            final List<String> property = List.of(pluginProperty.split("="));
            // Skip entries for which there is no value
            if (property.size() == 1) {
                continue;
            }

            final String key = property.get(0);
            // Should we URL decode the value?
            String value = Joiner.on("=").join(property.subList(1, property.size()));
            if (pluginProperty.endsWith("=")) {
                value += "=";
            }
            properties.add(new PluginProperty(key, value, false));
        }
        Collections.addAll(properties, additionalProperties);
        return properties;
    }

    protected InvoicePayment createPurchaseForInvoice(final Account account,
                                                      final UUID invoiceId,
                                                      final BigDecimal amountToPay,
                                                      final UUID paymentMethodId,
                                                      final Boolean externalPayment,
                                                      final String paymentExternalKey,
                                                      final String transactionExternalKey,
                                                      final Iterable<PluginProperty> pluginProperties,
                                                      final CallContext callContext) throws PaymentApiException {
        return createPurchaseForInvoice(account,
                                        invoiceId,
                                        amountToPay,
                                        paymentMethodId,
                                        paymentExternalKey,
                                        transactionExternalKey,
                                        pluginProperties,
                                        createInvoicePaymentControlPluginApiPaymentOptions(externalPayment),
                                        callContext);
    }

    protected InvoicePayment createPurchaseForInvoice(final Account account,
                                                      final UUID invoiceId,
                                                      final BigDecimal amountToPay,
                                                      final UUID paymentMethodId,
                                                      final String paymentExternalKey,
                                                      final String transactionExternalKey,
                                                      final Iterable<PluginProperty> pluginProperties,
                                                      final PaymentOptions paymentOptions,
                                                      final CallContext callContext) throws PaymentApiException {
        try {
            return invoicePaymentApi.createPurchaseForInvoicePayment(account,
                                                                     invoiceId,
                                                                     paymentMethodId,
                                                                     null, amountToPay,
                                                                     account.getCurrency(),
                                                                     null,
                                                                     paymentExternalKey,
                                                                     transactionExternalKey,
                                                                     pluginProperties,
                                                                     paymentOptions,
                                                                     callContext);
        } catch (final PaymentApiException e) {
            if (e.getCode() == ErrorCode.PAYMENT_PLUGIN_EXCEPTION.getCode() /* &&
                e.getMessage().contains("Invalid amount") */) { /* Plugin received bad input */
                throw e;
            } else if (e.getCode() == ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode()) { /* Plugin aborted the call (e.g invoice already paid) */
                return null;
            }
            throw e;
        }
    }

    protected PaymentOptions createInvoicePaymentControlPluginApiPaymentOptions(final boolean isExternalPayment) {
        return createControlPluginApiPaymentOptions(isExternalPayment, Collections.emptyList());
    }

    protected PaymentOptions createControlPluginApiPaymentOptions(@Nullable final List<String> paymentControlPluginNames) {
        return createControlPluginApiPaymentOptions(false, paymentControlPluginNames);
    }

    protected PaymentOptions createControlPluginApiPaymentOptions(final boolean isExternalPayment, final List<String> paymentControlPluginNames) {
        return new PaymentOptions() {
            @Override
            public boolean isExternalPayment() {
                return isExternalPayment;
            }

            @Override
            public List<String> getPaymentControlPluginNames() {
                // DefaultPaymentApi will add the default configured ones to this list
                return paymentControlPluginNames;
            }
        };
    }

    protected Iterable<InvoiceItem> validateSanitizeAndTranformInputItems(final Currency accountCurrency, final Iterable<InvoiceItemJson> inputItems) throws InvoiceApiException {
        try {
            return Iterables.toStream(inputItems)
                            .map(input -> {
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
                                                               input.getCatalogEffectiveDate(),
                                                               null,
                                                               null);
                                }
                            })
                            .map(InvoiceItemJson::toInvoiceItem)
                            .collect(Collectors.toUnmodifiableList());
        } catch (IllegalArgumentException e) {
            throw new InvoiceApiException(ErrorCode.CURRENCY_INVALID, accountCurrency, e.getMessage());
        }
    }


    public static Iterable<PaymentTransaction> getPaymentTransactions(final List<Payment> payments, final TransactionType transactionType) {
        return payments.stream()
                       .flatMap(payment -> payment.getTransactions().stream())
                       .filter(transaction -> transaction.getTransactionType() == transactionType)
                       .collect(Collectors.toUnmodifiableList());
    }

    public static UUID getInvoiceId(final List<InvoicePayment> invoicePayments, final Payment payment) {
        final InvoicePayment invoicePayment = invoicePayments.stream()
                .filter(input -> input.getPaymentId().equals(payment.getId()) && input.getType() == InvoicePaymentType.ATTEMPT)
                .findFirst().orElse(null);
        return invoicePayment != null ? invoicePayment.getInvoiceId() : null;
    }

    protected void verifyNonNullOrEmpty(final Object... elements) {
        Preconditions.checkArgument(elements.length % 2 == 0, "%s should have an even number of elements", Arrays.toString(elements));
        for (int i = 0; i < elements.length; i += 2) {
            final Object argument = elements[i];
            final Object errorMessage = elements[i + 1];
            final boolean expression = argument instanceof String ? Strings.emptyToNull((String) argument) != null : argument != null;
            Preconditions.checkArgument(expression, String.valueOf(errorMessage));
        }
    }

    protected void verifyNonNull(final Object... elements) {
        Preconditions.checkArgument(elements.length % 2 == 0, "%s should have an even number of elements", Arrays.toString(elements));
        for (int i = 0; i < elements.length; i += 2) {
            final Object argument = elements[i];
            final Object errorMessage = elements[i + 1];
            final boolean expression = argument != null;
            Preconditions.checkArgument(expression, String.valueOf(errorMessage));
        }
    }


    protected Response createPaymentResponse(final UriInfo uriInfo, final Payment payment, final TransactionType transactionType, @Nullable final String transactionExternalKey, final HttpServletRequest request) {
        final PaymentTransaction createdTransaction = findCreatedTransaction(payment, transactionType, transactionExternalKey);
        Preconditions.checkNotNull(createdTransaction, "No transaction of type '%s' found", transactionType);

        final ResponseBuilder responseBuilder;
        final BillingExceptionJson exception;
        switch (createdTransaction.getTransactionStatus()) {
            case PENDING:
            case SUCCESS:
                return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", payment.getId(), request);
            case PAYMENT_FAILURE:
                // 402 - Payment Required
                responseBuilder = Response.status(402);
                exception = createBillingException(String.format("Payment decline by gateway. Error message: %s", createdTransaction.getGatewayErrorMsg()));
                break;
            case PAYMENT_SYSTEM_OFF:
                // 503 - Service Unavailable
                responseBuilder = Response.status(Status.SERVICE_UNAVAILABLE);
                exception = createBillingException("Payment system is off.");
                break;
            case UNKNOWN:
                // 503 - Service Unavailable
                responseBuilder = Response.status(Status.SERVICE_UNAVAILABLE);
                exception = createBillingException("Payment in unknown status, failed to receive gateway response.");
                break;
            case PLUGIN_FAILURE:
                // 502 - Bad Gateway
                responseBuilder = Response.status(502);
                exception = createBillingException("Failed to submit payment transaction");
                break;
            default:
                // Should never happen
                responseBuilder = Response.serverError();
                exception = createBillingException("This should never have happened!!!");
        }
        addExceptionToResponse(responseBuilder, exception);
        return uriBuilder.buildResponse(responseBuilder, uriInfo, PaymentResource.class, "getPayment", payment.getId(), request);
    }

    private void addExceptionToResponse(final ResponseBuilder responseBuilder, final BillingExceptionJson exception) {
        try {
            responseBuilder.entity(mapper.writeValueAsString(exception)).type(MediaType.APPLICATION_JSON);
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize exception", exception);
            responseBuilder.entity(e.toString()).type(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    private BillingExceptionJson createBillingException(final String message) {
        final BillingExceptionJson exception;
        exception = new BillingExceptionJson(PaymentApiException.class.getName(), null, message, null, null, Collections.<StackTraceElementJson>emptyList());
        return exception;
    }

    private PaymentTransaction findCreatedTransaction(final Payment payment, final TransactionType transactionType, @Nullable final String transactionExternalKey) {
        // Make sure we start looking from the latest transaction created
        final List<PaymentTransaction> reversedTransactions = new ArrayList<>(payment.getTransactions());
        Collections.reverse(reversedTransactions);
        final Iterable<PaymentTransaction> matchingTransactions = reversedTransactions
                .stream()
                .filter(input -> input.getTransactionType() == transactionType)
                .collect(Collectors.toUnmodifiableList());

        if (transactionExternalKey != null) {
            for (final PaymentTransaction transaction : matchingTransactions) {
                if (transactionExternalKey.equals(transaction.getExternalKey())) {
                    return transaction;
                }
            }
        }

        // If nothing is found, return the latest transaction of given type
        return Iterables.getFirst(matchingTransactions, null);
    }

    protected List<AuditLogJson> getAuditLogsWithHistory(final List<AuditLogWithHistory> auditLogWithHistory) {
        return auditLogWithHistory.stream()
                .map(AuditLogJson::new)
                .collect(Collectors.toUnmodifiableList());
    }
}
