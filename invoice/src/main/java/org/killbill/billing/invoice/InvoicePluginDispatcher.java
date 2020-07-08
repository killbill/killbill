/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
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

package org.killbill.billing.invoice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.InvoiceItemCatalogBase;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.TaxInvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.PriorInvoiceResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

public class InvoicePluginDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvoicePluginDispatcher.class);

    public static final Collection<InvoiceItemType> ALLOWED_INVOICE_ITEM_TYPES = ImmutableList.<InvoiceItemType>of(InvoiceItemType.EXTERNAL_CHARGE,
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

    public DateTime priorCall(final LocalDate targetDate,
                              final List<Invoice> existingInvoices,
                              final boolean isDryRun,
                              final boolean isRescheduled,
                              final CallContext callContext,
                              // The pluginProperties list passed to plugins is mutable by the plugins
                              @SuppressWarnings("TypeMayBeWeakened") final LinkedList<PluginProperty> properties,
                              final InternalTenantContext internalTenantContext) throws InvoiceApiException {
        log.debug("Invoking invoice plugins priorCall: targetDate='{}', isDryRun='{}', isRescheduled='{}'", targetDate, isDryRun, isRescheduled);
        final Map<String, InvoicePluginApi> invoicePlugins = getInvoicePlugins(internalTenantContext);
        if (invoicePlugins.isEmpty()) {
            return null;
        }

        DateTime earliestRescheduleDate = null;
        final InvoiceContext invoiceContext = new DefaultInvoiceContext(targetDate, null, existingInvoices, isDryRun, isRescheduled, callContext);
        for (final String invoicePluginName : invoicePlugins.keySet()) {
            final PriorInvoiceResult priorInvoiceResult = invoicePlugins.get(invoicePluginName).priorCall(invoiceContext, properties);
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
        }

        return earliestRescheduleDate;
    }

    public void onSuccessCall(final LocalDate targetDate,
                              @Nullable final DefaultInvoice invoice,
                              final List<Invoice> existingInvoices,
                              final boolean isDryRun,
                              final boolean isRescheduled,
                              final CallContext callContext,
                              final LinkedList<PluginProperty> properties,
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
                              final LinkedList<PluginProperty> properties,
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
                                  // The pluginProperties list passed to plugins is mutable by the plugins
                                  @SuppressWarnings("TypeMayBeWeakened") final LinkedList<PluginProperty> properties,
                                  final InternalTenantContext internalTenantContext) {
        final Collection<InvoicePluginApi> invoicePlugins = getInvoicePlugins(internalTenantContext).values();
        if (invoicePlugins.isEmpty()) {
            return;
        }

        // We clone the original invoice so plugins don't remove/add items
        final Invoice clonedInvoice = originalInvoice == null ? null : (Invoice) originalInvoice.clone();
        final InvoiceContext invoiceContext = new DefaultInvoiceContext(targetDate, clonedInvoice, existingInvoices, isDryRun, isRescheduled, callContext);

        for (final InvoicePluginApi invoicePlugin : invoicePlugins) {
            if (isSuccess) {
                invoicePlugin.onSuccessCall(invoiceContext, properties);
            } else {
                invoicePlugin.onFailureCall(invoiceContext, properties);
            }
        }
    }

    public List<DefaultInvoice> updateOriginalInvoiceWithPluginInvoiceItems(final DefaultInvoice originalInvoice,
                                                                            final boolean isDryRun,
                                                                            final CallContext callContext,
                                                                            // The pluginProperties list passed to plugins is mutable by the plugins
                                                                            @SuppressWarnings("TypeMayBeWeakened") final LinkedList<PluginProperty> properties,
                                                                            final InternalTenantContext tenantContext) throws InvoiceApiException {
        log.debug("Invoking invoice plugins getAdditionalInvoiceItems: isDryRun='{}', originalInvoice='{}'", isDryRun, originalInvoice);

        final Collection<InvoicePluginApi> invoicePlugins = getInvoicePlugins(tenantContext).values();
        if (invoicePlugins.isEmpty()) {
            // Optimization
            return null;
        }

        // Final mapping of invoiceId to invoice items
        final Multimap<UUID, InvoiceItem> perInvoiceInvoiceItems = HashMultimap.<UUID, InvoiceItem>create();
        for (final InvoiceItem invoiceItem : originalInvoice.getInvoiceItems()) {
            perInvoiceInvoiceItems.put(invoiceItem.getInvoiceId(), invoiceItem);
        }

        for (final InvoicePluginApi invoicePlugin : invoicePlugins) {
            // We clone the original invoice so plugins don't remove/add items
            final Invoice clonedInvoice = (Invoice) originalInvoice.clone();
            // Invoice items returned by the plugin: some can be new items, some can be updated items (on the same or new invoice)
            final List<InvoiceItem> additionalInvoiceItemsForPlugin = invoicePlugin.getAdditionalInvoiceItems(clonedInvoice, isDryRun, properties, callContext);
            if (additionalInvoiceItemsForPlugin == null || additionalInvoiceItemsForPlugin.isEmpty()) {
                continue;
            }

            for (final InvoiceItem additionalInvoiceItem : additionalInvoiceItemsForPlugin) {
                final InvoiceItem sanitizedInvoiceItem = validateAndSanitizeInvoiceItemFromPlugin(originalInvoice, additionalInvoiceItem, invoicePlugin);
                updatePerInvoiceInvoiceItems(perInvoiceInvoiceItems, sanitizedInvoiceItem);
            }
        }

        final List<DefaultInvoice> updatedInvoices = new LinkedList<DefaultInvoice>();
        for (final UUID invoiceId : perInvoiceInvoiceItems.keySet()) {
            final Collection<InvoiceItem> updatedInvoiceItems = perInvoiceInvoiceItems.get(invoiceId);
            if (updatedInvoiceItems.isEmpty()) {
                continue;
            }

            // Note that here, the clonedInvoice is just used as a container object, it doesn't actually represent the final invoice.
            // For instance, the plugin could return an ITEM_ADJ pointing to an existing invoice: the clonedInvoice object would
            // not include the items from the original invoice. This doesn't matter anyways, as the dao will create the appropriate entries
            // and we will re-hydrate the objects before returning (at least in the non dry-run path, see comments in processAccountWithLockAndInputTargetDate).
            final DefaultInvoice clonedInvoice = (DefaultInvoice) originalInvoice.clone();
            // Honor the ID set by the plugin (plugin implementors must know what they are doing here!)
            clonedInvoice.setId(invoiceId);
            // Clear non-shared state (not everything is strictly needed, but be explicit for the sake of completeness)
            clonedInvoice.getTrackingIds().clear();
            clonedInvoice.getPayments().clear();
            clonedInvoice.getInvoiceItems().clear();
            // Add the final invoice items for that invoice
            clonedInvoice.addInvoiceItems(updatedInvoiceItems);
            updatedInvoices.add(clonedInvoice);
        }
        return updatedInvoices;
    }

    private void updatePerInvoiceInvoiceItems(final Multimap<UUID, InvoiceItem> perInvoiceInvoiceItems,
                                              final InvoiceItem pluginInvoiceItem) {
        InvoiceItem foundInvoiceItem = null;
        invoicePluginDispatcher:
        for (final UUID invoiceId : perInvoiceInvoiceItems.keys()) {
            for (final InvoiceItem invoiceItem : perInvoiceInvoiceItems.get(invoiceId)) {
                if (invoiceItem.getId().equals(pluginInvoiceItem.getId())) {
                    foundInvoiceItem = invoiceItem;
                    break invoicePluginDispatcher;
                }
            }
        }

        if (foundInvoiceItem != null) {
            perInvoiceInvoiceItems.remove(foundInvoiceItem.getInvoiceId(), foundInvoiceItem);
        }

        Preconditions.checkNotNull(pluginInvoiceItem.getInvoiceId(), "Invoice plugin error: missing invoiceId for item %s", pluginInvoiceItem);
        perInvoiceInvoiceItems.put(pluginInvoiceItem.getInvoiceId(), pluginInvoiceItem);
    }

    private InvoiceItem validateAndSanitizeInvoiceItemFromPlugin(final Invoice originalInvoice, final InvoiceItem additionalInvoiceItem, final InvoicePluginApi invoicePlugin) throws InvoiceApiException {
        final InvoiceItem existingItem = Iterables.<InvoiceItem>tryFind(originalInvoice.getInvoiceItems(),
                                                                      new Predicate<InvoiceItem>() {
                                                                          @Override
                                                                          public boolean apply(final InvoiceItem originalInvoiceItem) {
                                                                              return originalInvoiceItem.getId().equals(additionalInvoiceItem.getId());
                                                                          }
                                                                      }).orNull();

        if (!ALLOWED_INVOICE_ITEM_TYPES.contains(additionalInvoiceItem.getInvoiceItemType()) && existingItem == null) {
            log.warn("Ignoring invoice item of type {} from InvoicePlugin {}: {}", additionalInvoiceItem.getInvoiceItemType(), invoicePlugin, additionalInvoiceItem);
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_TYPE_INVALID, additionalInvoiceItem.getInvoiceItemType());
        }

        final UUID invoiceId = MoreObjects.firstNonNull(mutableField("invoiceId", existingItem != null ? existingItem.getInvoiceId() : null, additionalInvoiceItem.getInvoiceId(), invoicePlugin),
                                                        originalInvoice.getId());

        final UUID additionalInvoiceId = MoreObjects.firstNonNull(additionalInvoiceItem.getId(), UUIDs.randomUUID());
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
                                          immutableField("rate", existingItem, existingItem != null ? existingItem.getRate() : null, additionalInvoiceItem.getRate(), invoicePlugin),
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
            final List<String> result = new ArrayList<String>(configuredPlugins.size());
            for (final String name : configuredPlugins) {
                if (pluginRegistry.getServiceForName(name) != null) {
                    result.add(name);
                }
            }
            return result;
        }
    }
}
