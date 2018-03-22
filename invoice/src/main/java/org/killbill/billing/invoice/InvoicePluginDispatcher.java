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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.PriorInvoiceResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

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

    public DateTime priorCall(final LocalDate targetDate, final List<Invoice> existingInvoices, final boolean isDryRun, final boolean isRescheduled, final CallContext callContext, final InternalTenantContext internalTenantContext) throws InvoiceApiException {
        log.debug("Invoking invoice plugins priorCall: targetDate='{}', isDryRun='{}', isRescheduled='{}'", targetDate, isDryRun, isRescheduled);
        final Map<String, InvoicePluginApi> invoicePlugins = getInvoicePlugins(internalTenantContext);
        if (invoicePlugins.isEmpty()) {
            return null;
        }

        DateTime earliestRescheduleDate = null;
        final InvoiceContext invoiceContext = new DefaultInvoiceContext(targetDate, null, existingInvoices, isDryRun, isRescheduled, callContext);
        for (final String invoicePluginName : invoicePlugins.keySet()) {
            final PriorInvoiceResult priorInvoiceResult = invoicePlugins.get(invoicePluginName).priorCall(invoiceContext, ImmutableList.<PluginProperty>of());
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
                              final DefaultInvoice invoice,
                              final List<Invoice> existingInvoices,
                              final boolean isDryRun,
                              final boolean isRescheduled,
                              final CallContext callContext,
                              final InternalTenantContext internalTenantContext) {
        log.debug("Invoking invoice plugins onSuccessCall: targetDate='{}', isDryRun='{}', isRescheduled='{}', invoice='{}'", targetDate, isDryRun, isRescheduled, invoice);
        onCompletionCall(true, targetDate, invoice, existingInvoices, isDryRun, isRescheduled, callContext, internalTenantContext);
    }

    public void onFailureCall(final LocalDate targetDate,
                              final DefaultInvoice invoice,
                              final List<Invoice> existingInvoices,
                              final boolean isDryRun,
                              final boolean isRescheduled,
                              final CallContext callContext,
                              final InternalTenantContext internalTenantContext) {
        log.debug("Invoking invoice plugins onFailureCall: targetDate='{}', isDryRun='{}', isRescheduled='{}', invoice='{}'", targetDate, isDryRun, isRescheduled, invoice);
        onCompletionCall(false, targetDate, invoice, existingInvoices, isDryRun, isRescheduled, callContext, internalTenantContext);
    }

    private void onCompletionCall(final boolean isSuccess,
                                  final LocalDate targetDate,
                                  final DefaultInvoice originalInvoice,
                                  final List<Invoice> existingInvoices,
                                  final boolean isDryRun,
                                  final boolean isRescheduled,
                                  final CallContext callContext,
                                  final InternalTenantContext internalTenantContext) {
        final Collection<InvoicePluginApi> invoicePlugins = getInvoicePlugins(internalTenantContext).values();
        if (invoicePlugins.isEmpty()) {
            return;
        }

        // We clone the original invoice so plugins don't remove/add items
        final Invoice clonedInvoice = (Invoice) originalInvoice.clone();
        final InvoiceContext invoiceContext = new DefaultInvoiceContext(targetDate, clonedInvoice, existingInvoices, isDryRun, isRescheduled, callContext);

        for (final InvoicePluginApi invoicePlugin : invoicePlugins) {
            if (isSuccess) {
                invoicePlugin.onSuccessCall(invoiceContext, ImmutableList.<PluginProperty>of());
            } else {
                invoicePlugin.onFailureCall(invoiceContext, ImmutableList.<PluginProperty>of());
            }
        }
    }

    //
    // If we have multiple plugins there is a question of plugin ordering and also a 'product' questions to decide whether
    // subsequent plugins should have access to items added by previous plugins
    //
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice originalInvoice, final boolean isDryRun, final CallContext callContext, final InternalTenantContext tenantContext) throws InvoiceApiException {
        log.debug("Invoking invoice plugins getAdditionalInvoiceItems: isDryRun='{}', originalInvoice='{}'", isDryRun, originalInvoice);

        final List<InvoiceItem> additionalInvoiceItems = new LinkedList<InvoiceItem>();

        final Collection<InvoicePluginApi> invoicePlugins = getInvoicePlugins(tenantContext).values();
        if (invoicePlugins.isEmpty()) {
            return additionalInvoiceItems;
        }

        // We clone the original invoice so plugins don't remove/add items
        final Invoice clonedInvoice = (Invoice) ((DefaultInvoice) originalInvoice).clone();
        for (final InvoicePluginApi invoicePlugin : invoicePlugins) {
            final List<InvoiceItem> items = invoicePlugin.getAdditionalInvoiceItems(clonedInvoice, isDryRun, ImmutableList.<PluginProperty>of(), callContext);
            if (items != null) {
                for (final InvoiceItem item : items) {
                    validateInvoiceItemFromPlugin(item, invoicePlugin);
                    additionalInvoiceItems.add(item);
                }
            }
        }
        return additionalInvoiceItems;
    }

    private void validateInvoiceItemFromPlugin(final InvoiceItem invoiceItem, final InvoicePluginApi invoicePlugin) throws InvoiceApiException {
        if (!ALLOWED_INVOICE_ITEM_TYPES.contains(invoiceItem.getInvoiceItemType())) {
            log.warn("Ignoring invoice item of type {} from InvoicePlugin {}: {}", invoiceItem.getInvoiceItemType(), invoicePlugin, invoiceItem);
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_TYPE_INVALID, invoiceItem.getInvoiceItemType());
        }
    }

    private Map<String, InvoicePluginApi> getInvoicePlugins(final InternalTenantContext tenantContext) {
        final Collection<String> resultingPluginList = getResultingPluginNameList(tenantContext);

        final Map<String, InvoicePluginApi> invoicePlugins = new HashMap<String, InvoicePluginApi>();
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
