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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.JsonBase;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogsForObjectType;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
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
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public abstract class JaxRsResourceBase implements JaxrsResource {

    static final Logger log = LoggerFactory.getLogger(JaxRsResourceBase.class);

    protected static final ObjectMapper mapper = new ObjectMapper();

    protected final JaxrsUriBuilder uriBuilder;
    protected final TagUserApi tagUserApi;
    protected final CustomFieldUserApi customFieldUserApi;
    protected final AuditUserApi auditUserApi;
    protected final AccountUserApi accountUserApi;
    protected final PaymentApi paymentApi;
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
                             final Clock clock,
                             final Context context) {
        this.uriBuilder = uriBuilder;
        this.tagUserApi = tagUserApi;
        this.customFieldUserApi = customFieldUserApi;
        this.auditUserApi = auditUserApi;
        this.accountUserApi = accountUserApi;
        this.paymentApi = paymentApi;
        this.clock = clock;
        this.context = context;
    }

    protected ObjectType getObjectType() {
        return null;
    }

    protected Response getTags(final UUID accountId, final UUID taggedObjectId, final AuditMode auditMode, final boolean includeDeleted, final TenantContext context) throws TagDefinitionApiException {
        final List<Tag> tags = tagUserApi.getTagsForObject(taggedObjectId, getObjectType(), includeDeleted, context);
        final AccountAuditLogsForObjectType tagsAuditLogs = auditUserApi.getAccountAuditLogs(accountId, ObjectType.TAG, auditMode.getLevel(), context);

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
                                  final String tagList,
                                  final UriInfo uriInfo,
                                  final CallContext context) throws TagApiException {
        final Collection<UUID> input = getTagDefinitionUUIDs(tagList);
        tagUserApi.addTags(id, getObjectType(), input, context);
        // TODO This will always return 201, even if some (or all) tags already existed (in which case we don't do anything)
        return uriBuilder.buildResponse(this.getClass(), "getTags", id, uriInfo.getBaseUri().toString());
    }

    protected Collection<UUID> getTagDefinitionUUIDs(final String tagList) {
        final String[] tagParts = tagList.split(",\\s*");
        return Collections2.transform(ImmutableList.copyOf(tagParts), new Function<String, UUID>() {
            @Override
            public UUID apply(final String input) {
                return UUID.fromString(input);
            }
        });
    }

    protected Response deleteTags(final UUID id,
                                  final String tagList,
                                  final CallContext context) throws TagApiException {
        final Collection<UUID> input = getTagDefinitionUUIDs(tagList);
        tagUserApi.removeTags(id, getObjectType(), input, context);

        return Response.status(Response.Status.OK).build();
    }

    protected Response getCustomFields(final UUID id, final AuditMode auditMode, final TenantContext context) {
        final List<CustomField> fields = customFieldUserApi.getCustomFieldsForObject(id, getObjectType(), context);

        final List<CustomFieldJson> result = new LinkedList<CustomFieldJson>();
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
                                          final UriInfo uriInfo) throws CustomFieldApiException {
        final LinkedList<CustomField> input = new LinkedList<CustomField>();
        for (final CustomFieldJson cur : customFields) {
            verifyNonNullOrEmpty(cur.getName(), "CustomFieldJson name needs to be set");
            verifyNonNullOrEmpty(cur.getValue(), "CustomFieldJson value needs to be set");
            input.add(new StringCustomField(cur.getName(), cur.getValue(), getObjectType(), id, context.getCreatedDate()));
        }

        customFieldUserApi.addCustomFields(input, context);
        return uriBuilder.buildResponse(uriInfo, this.getClass(), "getCustomFields", id);
    }

    /**
     * @param id              the if of the object for which the custom fields apply
     * @param customFieldList a comma separated list of custom field ids or null if they should all be removed
     * @param context         the context
     * @return
     * @throws CustomFieldApiException
     */
    protected Response deleteCustomFields(final UUID id,
                                          @Nullable final String customFieldList,
                                          final CallContext context) throws CustomFieldApiException {

        // Retrieve all the custom fields for the object
        final List<CustomField> fields = customFieldUserApi.getCustomFieldsForObject(id, getObjectType(), context);

        final String[] requestedIds = customFieldList != null ? customFieldList.split("\\s*,\\s*") : null;

        // Filter the proposed list to only keep the one that exist and indeed match our object
        final Iterable inputIterable = Iterables.filter(fields, new Predicate<CustomField>() {
            @Override
            public boolean apply(final CustomField input) {
                if (customFieldList == null) {
                    return true;
                }
                for (final String cur : requestedIds) {
                    final UUID curId = UUID.fromString(cur);
                    if (input.getId().equals(curId)) {
                        return true;
                    }
                }
                return false;
            }
        });

        if (inputIterable.iterator().hasNext()) {
            final List<CustomField> input = ImmutableList.<CustomField>copyOf(inputIterable);
            customFieldUserApi.removeCustomFields(input, context);
        }
        return Response.status(Response.Status.OK).build();
    }

    protected <E extends Entity, J extends JsonBase> Response buildStreamingPaginationResponse(final Pagination<E> entities,
                                                                                               final Function<E, J> toJson,
                                                                                               final URI nextPageUri) {
        final StreamingOutput json = new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException, WebApplicationException {
                final JsonGenerator generator = mapper.getFactory().createJsonGenerator(output);
                generator.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

                generator.writeStartArray();
                for (final E entity : entities) {
                    final J asJson = toJson.apply(entity);
                    if (asJson != null) {
                        generator.writeObject(asJson);
                    }
                }
                generator.writeEndArray();
                generator.close();
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

    protected LocalDate toLocalDate(final UUID accountId, final String inputDate, final TenantContext context) {

        final LocalDate maybeResult = extractLocalDate(inputDate);
        if (maybeResult != null) {
            return maybeResult;
        }
        Account account = null;
        try {
            account = accountId != null ? accountUserApi.getAccountById(accountId, context) : null;
        } catch (final AccountApiException e) {
            log.info("Failed to retrieve account for id " + accountId);
        }
        final DateTime inputDateTime = inputDate != null ? DATE_TIME_FORMATTER.parseDateTime(inputDate) : clock.getUTCNow();
        return toLocalDate(account, inputDateTime, context);
    }

    protected LocalDate toLocalDate(final Account account, final String inputDate, final TenantContext context) {

        final LocalDate maybeResult = extractLocalDate(inputDate);
        if (maybeResult != null) {
            return maybeResult;
        }
        final DateTime inputDateTime = inputDate != null ? DATE_TIME_FORMATTER.parseDateTime(inputDate) : clock.getUTCNow();
        return toLocalDate(account, inputDateTime, context);
    }

    private LocalDate toLocalDate(final Account account, final DateTime inputDate, final TenantContext context) {
        if (account == null && inputDate == null) {
            // We have no inputDate and so accountTimeZone so we default to LocalDate as seen in UTC
            return new LocalDate(clock.getUTCNow(), DateTimeZone.UTC);
        } else if (account == null && inputDate != null) {
            // We were given a date but can't get timezone, default in UTC
            return new LocalDate(inputDate, DateTimeZone.UTC);
        } else if (account != null && inputDate == null) {
            // We have no inputDate but for accountTimeZone so default to LocalDate as seen in account timezone
            return new LocalDate(clock.getUTCNow(), account.getTimeZone());
        } else {
            // Precise LocalDate as requested
            return new LocalDate(inputDate, account.getTimeZone());
        }
    }

    private LocalDate extractLocalDate(final String inputDate) {
        if (inputDate != null) {
            try {
                final LocalDate localDate = LocalDate.parse(inputDate, LOCAL_DATE_FORMATTER);
                return localDate;
            } catch (final IllegalArgumentException expectedAndIgnore) {
            }
        }
        return null;
    }

    protected Iterable<PluginProperty> extractPluginProperties(@Nullable final Iterable<String> pluginProperties, PluginProperty... additionalProperties) {
        final Collection<PluginProperty> properties = new LinkedList<PluginProperty>();
        if (pluginProperties == null) {
            return properties;
        }

        for (final String pluginProperty : pluginProperties) {
            final List<String> property = ImmutableList.<String>copyOf(pluginProperty.split("="));
            final String key = property.get(0);
            final String value = property.size() == 1 ? null : Joiner.on("=").join(property.subList(1, property.size()));
            properties.add(new PluginProperty(key, value, false));
        }
        for (PluginProperty cur : additionalProperties) {
            properties.add(cur);
        }
        return properties;
    }

    protected Payment createPurchaseForInvoice(final Account account, final UUID invoiceId, final BigDecimal amountToPay, final Boolean externalPayment, final CallContext callContext) throws PaymentApiException {

        final List<PluginProperty> properties = new ArrayList<PluginProperty>();
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        final PluginProperty invoiceProperty = new PluginProperty("IPCD_INVOICE_ID" /* InvoicePaymentControlPluginApi.PROP_IPCD_INVOICE_ID (contract with plugin)  */,
                                                                  invoiceId.toString(), false);
        properties.add(invoiceProperty);

        final UUID paymentMethodId = externalPayment ? null : account.getPaymentMethodId();
        return paymentApi.createPurchaseWithPaymentControl(account, paymentMethodId, null, amountToPay, account.getCurrency(), paymentExternalKey, transactionExternalKey,
                                                           properties, createInvoicePaymentControlPluginApiPaymentOptions(externalPayment), callContext);
    }

    protected PaymentOptions createInvoicePaymentControlPluginApiPaymentOptions(final boolean isExternalPayment) {
        return new PaymentOptions() {
            @Override
            public boolean isExternalPayment() {
                return isExternalPayment;
            }

            @Override
            public List<String> getPaymentControlPluginNames() {
                /* Will default to org.killbill.payment.control.plugin in payment sub-system */
                return null;
            }
        };
    }

    public static Iterable<PaymentTransaction> getPaymentTransactions(final List<Payment> payments, final TransactionType transactionType) {
        return Iterables.concat(Iterables.transform(payments, new Function<Payment, Iterable<PaymentTransaction>>() {
            @Override
            public Iterable<PaymentTransaction> apply(final Payment input) {
                return Iterables.filter(input.getTransactions(), new Predicate<PaymentTransaction>() {
                    @Override
                    public boolean apply(final PaymentTransaction input) {
                        return input.getTransactionType() == transactionType;
                    }
                });
            }
        }));
    }

    public static UUID getInvoiceId(final List<InvoicePayment> invoicePayments, final Payment payment) {
        final InvoicePayment invoicePayment = Iterables.tryFind(invoicePayments, new Predicate<InvoicePayment>() {
            @Override
            public boolean apply(final InvoicePayment input) {
                return input.getPaymentId().equals(payment.getId()) && input.getType() == InvoicePaymentType.ATTEMPT;
            }
        }).orNull();
        return invoicePayment != null ? invoicePayment.getInvoiceId() : null;
    }

    protected void verifyNonNullOrEmpty(final Object... elements) {
        Preconditions.checkArgument(elements.length % 2 == 0, "%s should have an even number of elements", Arrays.toString(elements));
        for (int i = 0; i < elements.length; i += 2) {
            final Object argument = elements[i];
            final Object errorMessage = elements[i + 1];
            final boolean expression = argument instanceof String ? Strings.emptyToNull((String) argument) != null : argument != null;
            Preconditions.checkArgument(expression, errorMessage);
        }
    }
}
