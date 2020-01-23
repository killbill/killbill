/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.entitlement.api;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.entitlement.plugin.api.EntitlementContext;
import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApi;
import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApiException;
import org.killbill.billing.entitlement.plugin.api.OnFailureEntitlementResult;
import org.killbill.billing.entitlement.plugin.api.OnSuccessEntitlementResult;
import org.killbill.billing.entitlement.plugin.api.PriorEntitlementResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntitlementPluginExecution {

    private static final Logger log = LoggerFactory.getLogger(EntitlementPluginExecution.class);

    private final EntitlementApi entitlementApi;
    private final OSGIServiceRegistration<EntitlementPluginApi> pluginRegistry;

    public interface WithEntitlementPlugin<T> {
        T doCall(final EntitlementApi entitlementApi, final DefaultEntitlementContext updatedPluginContext) throws EntitlementApiException;
    }

    @Inject
    public EntitlementPluginExecution(final EntitlementApi entitlementApi, final OSGIServiceRegistration<EntitlementPluginApi> pluginRegistry) {
        this.entitlementApi = entitlementApi;
        this.pluginRegistry = pluginRegistry;
    }

    public void executeWithPlugin(final Callable<Void> preCallbacksCallback, final List<WithEntitlementPlugin> callbacks, final Iterable<EntitlementContext> pluginContexts) throws EntitlementApiException {
        final List<DefaultEntitlementContext> updatedPluginContexts = new LinkedList<DefaultEntitlementContext>();

        try {
            for (final EntitlementContext pluginContext : pluginContexts) {
                final PriorEntitlementResult priorEntitlementResult = executePluginPriorCalls(pluginContext);
                if (priorEntitlementResult != null && priorEntitlementResult.isAborted()) {
                    throw new EntitlementApiException(ErrorCode.ENT_PLUGIN_API_ABORTED, "");
                }
                updatedPluginContexts.add(new DefaultEntitlementContext(pluginContext, priorEntitlementResult));
            }

            preCallbacksCallback.call();

            try {
                for (int i = 0; i < updatedPluginContexts.size(); i++) {
                    final DefaultEntitlementContext updatedPluginContext = updatedPluginContexts.get(i);
                    final WithEntitlementPlugin callback = callbacks.get(i);

                    callback.doCall(entitlementApi, updatedPluginContext);
                    executePluginOnSuccessCalls(updatedPluginContext);
                }
            } catch (final EntitlementApiException e) {
                for (final EntitlementContext updatedPluginContext : updatedPluginContexts) {
                    executePluginOnFailureCalls(updatedPluginContext);
                }
                throw e;
            }
        } catch (final EntitlementPluginApiException e) {
            throw new EntitlementApiException(ErrorCode.ENT_PLUGIN_API_ABORTED, e.getMessage());
        } catch (final Exception e) {
            throw new EntitlementApiException(ErrorCode.ENT_PLUGIN_API_ABORTED, e.getMessage());
        }
    }

    public <T> T executeWithPlugin(final WithEntitlementPlugin<T> callback, final EntitlementContext pluginContext) throws EntitlementApiException {
        try {
            final PriorEntitlementResult priorEntitlementResult = executePluginPriorCalls(pluginContext);
            if (priorEntitlementResult != null && priorEntitlementResult.isAborted()) {
                throw new EntitlementApiException(ErrorCode.ENT_PLUGIN_API_ABORTED, "");
            }
            final DefaultEntitlementContext updatedPluginContext = new DefaultEntitlementContext(pluginContext, priorEntitlementResult);
            try {
                final T result = callback.doCall(entitlementApi, updatedPluginContext);
                executePluginOnSuccessCalls(updatedPluginContext);
                return result;
            } catch (final EntitlementApiException e) {
                executePluginOnFailureCalls(updatedPluginContext);
                throw e;
            }
        } catch (final EntitlementPluginApiException e) {
            throw new EntitlementApiException(ErrorCode.ENT_PLUGIN_API_ABORTED, e.getMessage());
        }
    }

    private PriorEntitlementResult executePluginPriorCalls(final EntitlementContext entitlementContextArg) throws EntitlementPluginApiException {

        // Return as soon as the first plugin aborts, or the last result for the last plugin
        PriorEntitlementResult prevResult = null;

        EntitlementContext currentContext = entitlementContextArg;
        for (final String pluginName : pluginRegistry.getAllServices()) {
            final EntitlementPluginApi plugin = pluginRegistry.getServiceForName(pluginName);
            if (plugin == null) {
                // First call to plugin, we log warn, if plugin is not registered
                log.warn("Skipping unknown entitlement control plugin {} when fetching results", pluginName);
                continue;
            }
            prevResult = plugin.priorCall(currentContext, currentContext.getPluginProperties());
            if (prevResult != null && prevResult.isAborted()) {
                break;
            }
            currentContext = new DefaultEntitlementContext(currentContext, prevResult);
        }
        return prevResult;
    }

    private OnSuccessEntitlementResult executePluginOnSuccessCalls(final EntitlementContext context) throws EntitlementPluginApiException {
        for (final String pluginName : pluginRegistry.getAllServices()) {
            final EntitlementPluginApi plugin = pluginRegistry.getServiceForName(pluginName);
            if (plugin != null) {
                plugin.onSuccessCall(context, context.getPluginProperties());
            }
        }
        return null;
    }

    private OnFailureEntitlementResult executePluginOnFailureCalls(final EntitlementContext context) throws EntitlementPluginApiException {
        for (final String pluginName : pluginRegistry.getAllServices()) {
            final EntitlementPluginApi plugin = pluginRegistry.getServiceForName(pluginName);
            if (plugin != null) {
                plugin.onFailureCall(context, context.getPluginProperties());
            }
        }
        return null;
    }
}
