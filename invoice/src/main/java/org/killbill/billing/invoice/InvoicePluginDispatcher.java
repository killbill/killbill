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

package org.killbill.billing.invoice;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.DefaultInvoiceContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.DefaultInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoiceItem.Builder;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.InvoiceItemCatalogBase;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.TaxInvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.plugin.api.AdditionalItemsResult;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoiceGroup;
import org.killbill.billing.invoice.plugin.api.InvoiceGroupingResult;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.OnFailureInvoiceResult;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.invoice.plugin.api.PriorInvoiceResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.UUIDs;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvoicePluginDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvoicePluginDispatcher.class);

    public static final Collection<InvoiceItemType> ALLOWED_INVOICE_ITEM_TYPES = List.of(InvoiceItemType.EXTERNAL_CHARGE,
                                                                                         InvoiceItemType.ITEM_ADJ,
                                                                                         InvoiceItemType.CREDIT_ADJ,
                                                                                         InvoiceItemType.TAX);

    private final OSGIServiceRegistration<InvoicePluginApi> pluginRegistry;
    private final InvoiceConfig invoiceConfig;

    @Inject
    public InvoicePluginDispatcher(final OSGIServiceRegistration<InvoicePluginApi> pluginRegistry,
                                   final InvoiceConfig invoiceConfig) {
        this.pluginRegistry = pluginRegistry;
        this.invoiceConfig = invoiceConfig;
    }

    public static final class PriorCallResult {

        private final DateTime rescheduleDate;
        private final Iterable<PluginProperty> pluginProperties;

        public PriorCallResult(final DateTime rescheduleDate, final Iterable<PluginProperty> pluginProperties) {
            this.rescheduleDate = rescheduleDate;
            this.pluginProperties = pluginProperties;
        }

        public DateTime getRescheduleDate() {
            return rescheduleDate;
        }

        public Iterable<PluginProperty> getPluginProperties() {
            return pluginProperties;
        }
    }

    public PriorCallResult priorCall(final LocalDate targetDate,
                              final List<Invoice> existingInvoices,
                              final boolean isDryRun,
                              final boolean isRescheduled,
                              final CallContext callContext,
                              final Iterable<PluginProperty> pluginProperties,
                              final InternalTenantContext internalTenantContext) throws InvoiceApiException {
        log.debug("Invoking invoice plugins priorCall: targetDate='{}', isDryRun='{}', isRescheduled='{}'", targetDate, isDryRun, isRescheduled);

        Iterable<PluginProperty> inputPluginProperties = pluginProperties;
        final Map<String, InvoicePluginApi> invoicePlugins = getInvoicePlugins(internalTenantContext);
        if (invoicePlugins.isEmpty()) {
            return new PriorCallResult(null, inputPluginProperties);
        }

        DateTime earliestRescheduleDate = null;
        final InvoiceContext invoiceContext = new DefaultInvoiceContext(targetDate, null, existingInvoices, isDryRun, isRescheduled, callContext);
        for (final Entry<String, InvoicePluginApi> entry : invoicePlugins.entrySet()) {
            final String invoicePluginName = entry.getKey();
            final PriorInvoiceResult priorInvoiceResult = entry.getValue().priorCall(invoiceContext, inputPluginProperties);
            log.debug("Invoice plugin {} returned priorInvoiceResult='{}'", invoicePluginName, priorInvoiceResult);
            if (priorInvoiceResult == null) {
                // Naughty plugin...
                continue;
            }

            if (priorInvoiceResult.getRescheduleDate() != null &&
                (earliestRescheduleDate == null || earliestRescheduleDate.compareTo(priorInvoiceResult.getRescheduleDate()) > 0)) {
                earliestRescheduleDate = priorInvoiceResult.getRescheduleDate();
                log.info("Invoice plugin {} rescheduled invoice generation to {} for targetDate {}", invoicePluginName, earliestRescheduleDate, targetDate);
            }

            if (priorInvoiceResult.isAborted()) {
                log.info("Invoice plugin {} aborted invoice generation for targetDate {}", invoicePluginName, targetDate);
                throw new InvoiceApiException(ErrorCode.INVOICE_PLUGIN_API_ABORTED, invoicePluginName);
            }

            if (priorInvoiceResult.getAdjustedPluginProperties() != null) {
                inputPluginProperties = priorInvoiceResult.getAdjustedPluginProperties();
            }
        }

        return new PriorCallResult(earliestRescheduleDate, inputPluginProperties);
    }

    public void onSuccessCall(final LocalDate targetDate,
                              @Nullable final DefaultInvoice invoice,
                              final List<Invoice> existingInvoices,
                              final boolean isDryRun,
                              final boolean isRescheduled,
                              final CallContext callContext,
                              final Iterable<PluginProperty> properties,
                              final InternalTenantContext internalTenantContext) {
        log.debug("Invoking invoice plugins onSuccessCall: targetDate='{}', isDryRun='{}', isRescheduled='{}', invoice='{}'", targetDate, isDryRun, isRescheduled, invoice);
        onCompletionCall(true, targetDate, invoice, existingInvoices, isDryRun, isRescheduled, callContext, properties, internalTenantContext);
    }

    public void onFailureCall(final LocalDate targetDate,
                              @Nullable final DefaultInvoice invoice,
                              final List<Invoice> existingInvoices,
                              final boolean isDryRun,
                              final boolean isRescheduled,
                              final CallContext callContext,
                              final Iterable<PluginProperty> properties,
                              final InternalTenantContext internalTenantContext) {
        log.debug("Invoking invoice plugins onFailureCall: targetDate='{}', isDryRun='{}', isRescheduled='{}', invoice='{}'", targetDate, isDryRun, isRescheduled, invoice);
        onCompletionCall(false, targetDate, invoice, existingInvoices, isDryRun, isRescheduled, callContext, properties, internalTenantContext);
    }

    private void onCompletionCall(final boolean isSuccess,
                                  final LocalDate targetDate,
                                  @Nullable final DefaultInvoice originalInvoice,
                                  final List<Invoice> existingInvoices,
                                  final boolean isDryRun,
                                  final boolean isRescheduled,
                                  final CallContext callContext,
                                  final Iterable<PluginProperty> pluginProperties,
                                  final InternalTenantContext internalTenantContext) {
        final Collection<InvoicePluginApi> invoicePlugins = getInvoicePlugins(internalTenantContext).values();
        if (invoicePlugins.isEmpty()) {
            return;
        }

        // We clone the original invoice so plugins don't remove/add items
        final Invoice clonedInvoice = originalInvoice == null ? null : (Invoice) originalInvoice.clone();
        final InvoiceContext invoiceContext = new DefaultInvoiceContext(targetDate, clonedInvoice, existingInvoices, isDryRun, isRescheduled, callContext);

        Iterable<PluginProperty> inputPluginProperties = pluginProperties;
        for (final InvoicePluginApi invoicePlugin : invoicePlugins) {
            if (isSuccess) {
                final OnSuccessInvoiceResult res1 = invoicePlugin.onSuccessCall(invoiceContext, inputPluginProperties);
                if (res1 != null && res1.getAdjustedPluginProperties() != null) {
                    inputPluginProperties = res1.getAdjustedPluginProperties();
                }
            } else {
                final OnFailureInvoiceResult res2 = invoicePlugin.onFailureCall(invoiceContext, inputPluginProperties);
                if (res2 != null && res2.getAdjustedPluginProperties() != null) {
                    inputPluginProperties = res2.getAdjustedPluginProperties();
                }
            }
        }
    }

    public static class SplitInvoiceResult {

        private final List<DefaultInvoice> invoices;
        final Iterable<PluginProperty> pluginProperties;

        public SplitInvoiceResult(final List<DefaultInvoice> invoices, final Iterable<PluginProperty> pluginProperties) {
            this.invoices = invoices;
            this.pluginProperties = pluginProperties;
        }

        public List<DefaultInvoice> getInvoices() {
            return invoices;
        }

        public Iterable<PluginProperty> getPluginProperties() {
            return pluginProperties;
        }
    }

    public SplitInvoiceResult splitInvoices(final DefaultInvoice originalInvoice,
                                              final boolean isDryRun,
                                              final CallContext callContext,
                                              final Iterable<PluginProperty> pluginProperties,
                         					  final LocalDate targetDate,
                         					  final List<Invoice> existingInvoices,
                         					  final boolean isRescheduled,                                              
                                              final InternalTenantContext tenantContext) {
        log.debug("Invoking invoice plugins for splitInvoices operation: isDryRun='{}', originalInvoice='{}'", isDryRun, originalInvoice);

        Iterable<PluginProperty> inputPluginProperties = pluginProperties;
        final Collection<InvoicePluginApi> invoicePlugins = getInvoicePlugins(tenantContext).values();
        final Invoice clonedInvoice = (Invoice) originalInvoice.clone();
        for (final InvoicePluginApi invoicePlugin : invoicePlugins) {
            final InvoiceContext invoiceContext = new DefaultInvoiceContext(targetDate, clonedInvoice, existingInvoices, isDryRun, isRescheduled, callContext);
            final InvoiceGroupingResult grpResult = invoicePlugin.getInvoiceGrouping(clonedInvoice, isDryRun, inputPluginProperties, invoiceContext);
            if (grpResult != null) {

                if (grpResult.getAdjustedPluginProperties() != null) {
                    inputPluginProperties = grpResult.getAdjustedPluginProperties();
                }

                if (grpResult.getInvoiceGroups() != null && grpResult.getInvoiceGroups().size() > 0) {

                    final List<DefaultInvoice> result = new ArrayList<>();
                    final Map<UUID, InvoiceItem> itemMap = originalInvoice.getInvoiceItems()
                                                                          .stream()
                                                                          .map(new Function<InvoiceItem, SimpleEntry<UUID, InvoiceItem>>() {
                                                                              @Override
                                                                              public SimpleEntry<UUID, InvoiceItem> apply(final InvoiceItem invoiceItem) {
                                                                                  return new SimpleEntry<>(invoiceItem.getId(), invoiceItem);
                                                                              }
                                                                          }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                    final List<InvoiceGroup> groups = grpResult.getInvoiceGroups();
                    for (final InvoiceGroup grp : groups) {
                        final DefaultInvoice grpInvoice = new DefaultInvoice(UUIDs.randomUUID(),
                                                                             originalInvoice.getAccountId(),
                                                                             null,
                                                                             originalInvoice.getInvoiceDate(),
                                                                             originalInvoice.getTargetDate(),
                                                                             originalInvoice.getCurrency(),
                                                                             originalInvoice.isMigrationInvoice(),
                                                                             originalInvoice.getStatus());
                        for (final UUID itemId : grp.getInvoiceItemIds()) {
                            final InvoiceItem item = itemMap.get(itemId);
                            final DefaultInvoiceItem.Builder tmp = new Builder().source(item);
                            tmp.withInvoiceId(grpInvoice.getId());
                            grpInvoice.addInvoiceItem(tmp.build());
                        }
                        result.add(grpInvoice);
                    }
                    return new SplitInvoiceResult(result, inputPluginProperties);
                }

            }

        }
        return new SplitInvoiceResult(Collections.singletonList(originalInvoice), inputPluginProperties);
    }

    public static final class AdditionalInvoiceItemsResult {

        private final boolean invoiceUpdated;
        private final Iterable<PluginProperty> pluginProperties;

        public AdditionalInvoiceItemsResult(final boolean invoiceUpdated, final Iterable<PluginProperty> pluginProperties) {
            this.invoiceUpdated = invoiceUpdated;
            this.pluginProperties = pluginProperties;
        }

        public boolean isInvoiceUpdated() {
            return invoiceUpdated;
        }

        public Iterable<PluginProperty> getPluginProperties() {
            return pluginProperties;
        }
    }

    public AdditionalInvoiceItemsResult updateOriginalInvoiceWithPluginInvoiceItems(final DefaultInvoice originalInvoice,
                                                               						final boolean isDryRun,
                                                               						final CallContext callContext,
                                                               						final Iterable<PluginProperty> pluginProperties,
                                                               						final LocalDate targetDate,
                                                               						final List<Invoice> existingInvoices,
                                                               						final boolean isRescheduled,
                                                               						final InternalTenantContext tenantContext) throws InvoiceApiException {
        log.debug("Invoking invoice plugins getAdditionalInvoiceItems: isDryRun='{}', originalInvoice='{}'", isDryRun, originalInvoice);

        final Collection<InvoicePluginApi> invoicePlugins = getInvoicePlugins(tenantContext).values();
        if (invoicePlugins.isEmpty()) {
            return new AdditionalInvoiceItemsResult(false, pluginProperties);
        }

        // Look-up map for performance
        final Map<UUID, InvoiceItem> invoiceItemsByItemId = new HashMap<UUID, InvoiceItem>();
        for (final InvoiceItem invoiceItem : originalInvoice.getInvoiceItems()) {
            invoiceItemsByItemId.put(invoiceItem.getId(), invoiceItem);
        }

        Iterable<PluginProperty> inputPluginProperties = pluginProperties;
        boolean invoiceUpdated = false;
        for (final InvoicePluginApi invoicePlugin : invoicePlugins) {
            // We clone the original invoice so plugins don't remove/add items
            final Invoice clonedInvoice = (Invoice) originalInvoice.clone();
            final InvoiceContext invoiceContext = new DefaultInvoiceContext(targetDate, clonedInvoice, existingInvoices, isDryRun, isRescheduled, callContext);
            final AdditionalItemsResult res = invoicePlugin.getAdditionalInvoiceItems(clonedInvoice, isDryRun, inputPluginProperties, invoiceContext);

            if (res != null) {
                if (res.getAdditionalItems() != null &&
                    !res.getAdditionalItems().isEmpty()) {
                    final Collection<InvoiceItem> additionalInvoiceItems = new LinkedList<InvoiceItem>();
                    for (final InvoiceItem additionalInvoiceItem : res.getAdditionalItems()) {
                        final InvoiceItem sanitizedInvoiceItem = validateAndSanitizeInvoiceItemFromPlugin(originalInvoice.getId(),
                                                                                                          invoiceItemsByItemId,
                                                                                                          additionalInvoiceItem,
                                                                                                          invoicePlugin);
                        additionalInvoiceItems.add(sanitizedInvoiceItem);
                    }
                    invoiceUpdated = updateOriginalInvoiceWithPluginInvoiceItems(originalInvoice, additionalInvoiceItems) || invoiceUpdated;
                }

                if (res.getAdjustedPluginProperties() != null) {
                    inputPluginProperties = res.getAdjustedPluginProperties();
                }
            }
        }
        return new AdditionalInvoiceItemsResult(invoiceUpdated, inputPluginProperties);
    }

    private boolean updateOriginalInvoiceWithPluginInvoiceItems(final DefaultInvoice originalInvoice, final Collection<InvoiceItem> additionalInvoiceItems) {
        if (additionalInvoiceItems.isEmpty()) {
            return false;
        }

        // Add or update items from generated invoice
        for (final InvoiceItem additionalInvoiceItem : additionalInvoiceItems) {
            originalInvoice.removeInvoiceItemIfExists(additionalInvoiceItem);
            originalInvoice.addInvoiceItem(additionalInvoiceItem);
        }

        return true;
    }

    private InvoiceItem validateAndSanitizeInvoiceItemFromPlugin(final UUID originalInvoiceId, final Map<UUID, InvoiceItem> invoiceItemsByItemId, final InvoiceItem additionalInvoiceItem, final InvoicePluginApi invoicePlugin) throws InvoiceApiException {
        final InvoiceItem existingItem = invoiceItemsByItemId.get(additionalInvoiceItem.getId());

        if (!ALLOWED_INVOICE_ITEM_TYPES.contains(additionalInvoiceItem.getInvoiceItemType()) && existingItem == null) {
            log.warn("Ignoring invoice item of type {} from InvoicePlugin {}: {}", additionalInvoiceItem.getInvoiceItemType(), invoicePlugin, additionalInvoiceItem);
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_TYPE_INVALID, additionalInvoiceItem.getInvoiceItemType());
        }

        final UUID invoiceId = Objects.requireNonNullElse(
                mutableField("invoiceId", (existingItem != null ? existingItem.getInvoiceId() : null), additionalInvoiceItem.getInvoiceId(), invoicePlugin),
                originalInvoiceId);
        final UUID additionalInvoiceId = Objects.requireNonNullElse(additionalInvoiceItem.getId(), UUIDs.randomUUID());
        final InvoiceItemCatalogBase tmp = new InvoiceItemCatalogBase(additionalInvoiceId,
                                                                      mutableField("createdDate", existingItem != null ? existingItem.getCreatedDate() : null, additionalInvoiceItem.getCreatedDate(), invoicePlugin),
                                                                      invoiceId,
                                                                      immutableField("accountId", existingItem, existingItem != null ? existingItem.getAccountId() : null, additionalInvoiceItem.getAccountId(), invoicePlugin),
                                                                      immutableField("bundleId", existingItem, existingItem != null ? existingItem.getBundleId() : null, additionalInvoiceItem.getBundleId(), invoicePlugin),
                                                                      immutableField("subscriptionId", existingItem, existingItem != null ? existingItem.getSubscriptionId() : null, additionalInvoiceItem.getSubscriptionId(), invoicePlugin),
                                                                      mutableField("description", existingItem != null ? existingItem.getDescription() : null, additionalInvoiceItem.getDescription(), invoicePlugin),
                                                                      immutableField("productName", existingItem, existingItem != null ? existingItem.getProductName() : null, additionalInvoiceItem.getProductName(), invoicePlugin),
                                                                      immutableField("planName", existingItem, existingItem != null ? existingItem.getPlanName() : null, additionalInvoiceItem.getPlanName(), invoicePlugin),
                                                                      immutableField("phaseName", existingItem, existingItem != null ? existingItem.getPhaseName() : null, additionalInvoiceItem.getPhaseName(), invoicePlugin),
                                                                      immutableField("usageName", existingItem, existingItem != null ? existingItem.getUsageName() : null, additionalInvoiceItem.getUsageName(), invoicePlugin),
                                                                      immutableField("catalogEffectiveDate", existingItem, existingItem != null ? existingItem.getCatalogEffectiveDate() : null, additionalInvoiceItem.getCatalogEffectiveDate(), invoicePlugin),
                                                                      mutableField("prettyProductName", existingItem != null ? existingItem.getPrettyProductName() : null, additionalInvoiceItem.getPrettyProductName(), invoicePlugin),
                                                                      mutableField("prettyPlanName", existingItem != null ? existingItem.getPrettyPlanName() : null, additionalInvoiceItem.getPrettyPlanName(), invoicePlugin),
                                                                      mutableField("prettyPhaseName", existingItem != null ? existingItem.getPrettyPhaseName() : null, additionalInvoiceItem.getPrettyPhaseName(), invoicePlugin),
                                                                      mutableField("prettyUsageName", existingItem != null ? existingItem.getPrettyUsageName() : null, additionalInvoiceItem.getPrettyUsageName(), invoicePlugin),
                                                                      immutableField("startDate", existingItem, existingItem != null ? existingItem.getStartDate() : null, additionalInvoiceItem.getStartDate(), invoicePlugin),
                                                                      immutableField("endDate", existingItem, existingItem != null ? existingItem.getEndDate() : null, additionalInvoiceItem.getEndDate(), invoicePlugin),
                                                                      mutableField("amount", existingItem != null ? existingItem.getAmount() : null, additionalInvoiceItem.getAmount(), invoicePlugin),
                                                                      mutableField("rate", existingItem != null ? existingItem.getRate() : null, additionalInvoiceItem.getRate(), invoicePlugin),
                                                                      immutableField("currency", existingItem, existingItem != null ? existingItem.getCurrency() : null, additionalInvoiceItem.getCurrency(), invoicePlugin),
                                                                      immutableField("linkedItemId", existingItem, existingItem != null ? existingItem.getLinkedItemId() : null, additionalInvoiceItem.getLinkedItemId(), invoicePlugin),
                                                                      mutableField("quantity", existingItem != null ? existingItem.getQuantity() : null, additionalInvoiceItem.getQuantity(), invoicePlugin),
                                                                      mutableField("itemDetails", existingItem != null ? existingItem.getItemDetails() : null, additionalInvoiceItem.getItemDetails(), invoicePlugin),
                                                                      immutableField("invoiceItemType", existingItem, existingItem != null ? existingItem.getInvoiceItemType() : null, additionalInvoiceItem.getInvoiceItemType(), invoicePlugin));
        switch (tmp.getInvoiceItemType()) {
            case RECURRING:
                return new RecurringInvoiceItem(tmp);
            case USAGE:
                return new UsageInvoiceItem(tmp);
            case FIXED:
                return new FixedPriceInvoiceItem(tmp);
            case TAX:
                return new TaxInvoiceItem(tmp);
            case EXTERNAL_CHARGE:
                return new ExternalChargeInvoiceItem(tmp);
            default:
                // None of the other types extend InvoiceItemCatalogBase so we return item as-is
                // As long as there is no explicit cast to the expected type this works
                return tmp;
        }
    }

    private <T> T mutableField(final String fieldName, @Nullable final T existingValue, @Nullable final T updatedValue, final InvoicePluginApi invoicePlugin) {
        if (updatedValue != null) {
            log.debug("Overriding mutable invoice item value from InvoicePlugin {} for fieldName='{}': existingValue='{}', updatedValue='{}'",
                      invoicePlugin, fieldName, existingValue, updatedValue);
            return updatedValue;
        } else {
            return existingValue;
        }
    }

    private <T> T immutableField(final String fieldName, @Nullable final InvoiceItem existingItem, @Nullable final T existingValue, @Nullable final T updatedValue, final InvoicePluginApi invoicePlugin) {
        if (existingItem == null) {
            return updatedValue;
        }

        if (updatedValue != null && !updatedValue.equals(existingValue)) {
            log.warn("Ignoring immutable invoice item value from InvoicePlugin {} for fieldName='{}': existingValue='{}', updatedValue='{}'",
                     invoicePlugin, fieldName, existingValue, updatedValue);
        }
        return existingValue;
    }

    @VisibleForTesting
    Map<String, InvoicePluginApi> getInvoicePlugins(final InternalTenantContext tenantContext) {
        final Collection<String> resultingPluginList = getResultingPluginNameList(tenantContext);

        // Keys ordering matters!
        final Map<String, InvoicePluginApi> invoicePlugins = new LinkedHashMap<String, InvoicePluginApi>();
        for (final String name : resultingPluginList) {
            final InvoicePluginApi serviceForName = pluginRegistry.getServiceForName(name);
            invoicePlugins.put(name, serviceForName);
        }
        return invoicePlugins;
    }

    @VisibleForTesting
    final Collection<String> getResultingPluginNameList(final InternalTenantContext tenantContext) {
        final List<String> configuredPlugins = invoiceConfig.getInvoicePluginNames(tenantContext);
        final Set<String> registeredPlugins = pluginRegistry.getAllServices();
        // No configuration, we return undeterministic list of registered plugins
        if (configuredPlugins == null || configuredPlugins.isEmpty()) {
            return registeredPlugins;
        } else {
            final List<String> result = new ArrayList<>(configuredPlugins.size());
            for (final String name : configuredPlugins) {
                if (pluginRegistry.getServiceForName(name) != null) {
                    result.add(name);
                }
            }
            return result;
        }
    }
}
